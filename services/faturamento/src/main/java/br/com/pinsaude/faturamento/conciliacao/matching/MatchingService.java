package br.com.pinsaude.faturamento.conciliacao.matching;

import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.dto.CandidatoMatchResponse;
import br.com.pinsaude.faturamento.repository.ConciliacaoRepository;
import br.com.pinsaude.faturamento.repository.ExtratoBancarioRepository;
import br.com.pinsaude.faturamento.repository.LancamentoExtratoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import br.com.pinsaude.faturamento.service.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    static final int LIMIAR_AUTO = 90;
    static final int LIMIAR_SUGESTAO = 50;
    static final int SCORE_VALOR_EXATO = 40;
    static final int SCORE_VALOR_TOLERANCIA = 20;
    static final int SCORE_CNPJ = 40;
    static final int SCORE_NOME = 20;
    static final int SCORE_DATA_7 = 20;
    static final int SCORE_DATA_30 = 10;

    private final ProducaoRepository producaoRepo;
    private final ConciliacaoRepository conciliacaoRepo;
    private final LancamentoExtratoRepository lancamentoRepo;
    private final ExtratoBancarioRepository extratoRepo;
    private final CryptoService cryptoService;

    public MatchingService(ProducaoRepository producaoRepo,
                           ConciliacaoRepository conciliacaoRepo,
                           LancamentoExtratoRepository lancamentoRepo,
                           ExtratoBancarioRepository extratoRepo,
                           CryptoService cryptoService) {
        this.producaoRepo    = producaoRepo;
        this.conciliacaoRepo = conciliacaoRepo;
        this.lancamentoRepo  = lancamentoRepo;
        this.extratoRepo     = extratoRepo;
        this.cryptoService   = cryptoService;
    }

    @Transactional
    public void processarExtrato(UUID extratoId, String cnpjTenant) {
        if (cnpjTenant == null || cnpjTenant.isBlank()) {
            log.debug("Matching ignorado — tenant vazio (gestão sem CNPJ)");
            return;
        }

        List<LancamentoExtrato> lancamentos = lancamentoRepo
                .findByExtratoIdAndTipoAndStatusConciliacao(
                        extratoId, TipoLancamentoExtrato.CREDITO, StatusConciliacao.PENDENTE);

        if (lancamentos.isEmpty()) return;

        List<Producao> candidatas = new ArrayList<>(producaoRepo.findCandidatasParaMatch(
                cnpjTenant, List.of(StatusProducao.CONFIRMADA, StatusProducao.EMITIDA)));

        if (candidatas.isEmpty()) return;

        // Pré-descriptografa CNPJs para evitar N×M chamadas ao banco
        Map<UUID, String> cnpjDigitosMap = pré_decriptografar(candidatas);

        for (LancamentoExtrato lancamento : lancamentos) {
            if (conciliacaoRepo.existsByLancamentoExtratoId(lancamento.getId())) {
                log.debug("Lançamento {} já conciliado — ignorado (idempotência)", lancamento.getId());
                continue;
            }

            CandidatoMatch melhor = candidatas.stream()
                    .map(p -> new CandidatoMatch(p, calcularScore(lancamento, p, cnpjDigitosMap.get(p.getId()))))
                    .filter(c -> c.score() > 0)
                    .max(Comparator.comparingInt(CandidatoMatch::score))
                    .orElse(null);

            if (melhor == null) continue;

            if (melhor.score() >= LIMIAR_AUTO) {
                Conciliacao conciliacao = new Conciliacao();
                conciliacao.setLancamentoExtratoId(lancamento.getId());
                conciliacao.setNotaId(melhor.producao().getId());
                conciliacao.setTipoMatch(TipoMatchEnum.AUTOMATICO);
                conciliacao.setScoreConfianca(melhor.score());
                conciliacaoRepo.save(conciliacao);

                lancamento.setStatusConciliacao(StatusConciliacao.CONCILIADO);
                lancamentoRepo.save(lancamento);

                log.info("Match automático — lancamento={} producao={} score={}",
                        lancamento.getId(), melhor.producao().getId(), melhor.score());

                // Remove do pool para não ser associado a outro lançamento
                candidatas.remove(melhor.producao());

            } else if (melhor.score() >= LIMIAR_SUGESTAO) {
                lancamento.setScoreMatch(melhor.score());
                lancamentoRepo.save(lancamento);

                log.debug("Sugestão de match — lancamento={} producao={} score={}",
                        lancamento.getId(), melhor.producao().getId(), melhor.score());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CandidatoMatchResponse> getSugestoes(UUID lancamentoId) {
        LancamentoExtrato lancamento = lancamentoRepo.findById(lancamentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lançamento não encontrado"));

        ExtratoBancario extrato = extratoRepo.findById(lancamento.getExtratoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Extrato não encontrado"));

        String cnpjTenant = extrato.getCnpjIdTenant();
        if (cnpjTenant == null || cnpjTenant.isBlank()) return List.of();

        List<Producao> candidatas = new ArrayList<>(producaoRepo.findCandidatasParaMatch(
                cnpjTenant, List.of(StatusProducao.CONFIRMADA, StatusProducao.EMITIDA)));

        if (candidatas.isEmpty()) return List.of();

        Map<UUID, String> cnpjMap = pré_decriptografar(candidatas);

        return candidatas.stream()
                .map(p -> {
                    int score = calcularScore(lancamento, p, cnpjMap.get(p.getId()));
                    String nome = p.getTomador() != null
                            ? p.getTomador().getRazaoSocialNome()
                            : "—";
                    return new CandidatoMatchResponse(p.getId(), nome, p.getValorBruto(),
                            p.getCompetencia(), score);
                })
                .filter(c -> c.score() >= LIMIAR_SUGESTAO)
                .sorted(Comparator.comparingInt(CandidatoMatchResponse::score).reversed())
                .limit(5)
                .toList();
    }

    public int calcularScore(LancamentoExtrato lancamento, Producao producao, String cnpjDigitosPlain) {
        long diff = Math.abs(lancamento.getValor() - producao.getValorBruto());
        double pct = producao.getValorBruto() > 0
                ? (double) diff / producao.getValorBruto()
                : 1.0;

        int scoreValor;
        if (diff == 0) {
            scoreValor = SCORE_VALOR_EXATO;
        } else if (pct <= 0.01) {
            scoreValor = SCORE_VALOR_TOLERANCIA;
        } else {
            return 0; // divergência > 1% → descarta candidato
        }

        int scoreId   = calcularScoreIdentidade(lancamento.getDescricao(), producao.getTomador(), cnpjDigitosPlain);
        int scoreData = calcularScoreData(lancamento.getDataLancamento(), producao.getCompetencia());

        return scoreValor + scoreId + scoreData;
    }

    private int calcularScoreIdentidade(String descricao, Tomador tomador, String cnpjDigitosPlain) {
        if (descricao == null || tomador == null) return 0;
        String descUpper = descricao.toUpperCase().trim();

        if (cnpjDigitosPlain != null && !cnpjDigitosPlain.isEmpty()) {
            String descDigitos = descUpper.replaceAll("\\D", "");
            if (descDigitos.contains(cnpjDigitosPlain)) {
                return SCORE_CNPJ;
            }
        }

        String nome = tomador.getRazaoSocialNome();
        if (nome != null && !nome.isBlank() && descUpper.contains(nome.toUpperCase().trim())) {
            return SCORE_NOME;
        }

        String nomeFantasia = tomador.getNomeFantasia();
        if (nomeFantasia != null && !nomeFantasia.isBlank()
                && descUpper.contains(nomeFantasia.toUpperCase().trim())) {
            return SCORE_NOME;
        }

        return 0;
    }

    private int calcularScoreData(LocalDate dataLancamento, String competencia) {
        if (competencia == null || dataLancamento == null) return 0;
        try {
            LocalDate primeiroDia = YearMonth.parse(competencia).atDay(1);
            long dias = Math.abs(ChronoUnit.DAYS.between(dataLancamento, primeiroDia));
            if (dias <= 7)  return SCORE_DATA_7;
            if (dias <= 30) return SCORE_DATA_30;
        } catch (Exception ignored) {
        }
        return 0;
    }

    private Map<UUID, String> pré_decriptografar(List<Producao> candidatas) {
        Map<UUID, String> mapa = new HashMap<>();
        for (Producao p : candidatas) {
            byte[] enc = p.getTomador() != null
                    ? p.getTomador().getCnpjCpfTomadorCriptografado()
                    : null;
            if (enc != null) {
                try {
                    String plain = cryptoService.decrypt(enc);
                    if (plain != null) mapa.put(p.getId(), plain.replaceAll("\\D", ""));
                } catch (Exception e) {
                    log.debug("Falha ao decriptografar CNPJ do tomador producao={}: {}", p.getId(), e.getMessage());
                }
            }
        }
        return mapa;
    }
}
