package br.com.pinsaude.faturamento.conciliacao.matching;

import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.dto.CandidatoMatchResponse;
import br.com.pinsaude.faturamento.repository.ConciliacaoRepository;
import br.com.pinsaude.faturamento.repository.ExtratoBancarioRepository;
import br.com.pinsaude.faturamento.repository.LancamentoExtratoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.service.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

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

    /** CBS-31: diferença de arredondamento das retenções (um tributo a mais ou a menos em centavos). */
    static final long TOLERANCIA_EXATO_CENTAVOS = 10;
    /** Janela de pagamento contada da emissão da NFS-e (o depósito vem depois da nota). */
    static final int DIAS_ANTES_EMISSAO = 3;
    static final int DIAS_APOS_EMISSAO_FORTE = 45;
    static final int DIAS_APOS_EMISSAO_FRACO = 90;

    private final ProducaoRepository producaoRepo;
    private final ConciliacaoRepository conciliacaoRepo;
    private final LancamentoExtratoRepository lancamentoRepo;
    private final ExtratoBancarioRepository extratoRepo;
    private final CryptoService cryptoService;
    private final TomadorAliquotaRepository aliquotaRepo;

    public MatchingService(ProducaoRepository producaoRepo,
                           ConciliacaoRepository conciliacaoRepo,
                           LancamentoExtratoRepository lancamentoRepo,
                           ExtratoBancarioRepository extratoRepo,
                           CryptoService cryptoService,
                           TomadorAliquotaRepository aliquotaRepo) {
        this.producaoRepo    = producaoRepo;
        this.conciliacaoRepo = conciliacaoRepo;
        this.lancamentoRepo  = lancamentoRepo;
        this.extratoRepo     = extratoRepo;
        this.cryptoService   = cryptoService;
        this.aliquotaRepo    = aliquotaRepo;
    }

    @Transactional
    public void processarExtrato(UUID extratoId, String cnpjTenant) {
        List<LancamentoExtrato> lancamentos = lancamentoRepo
                .findByExtratoIdAndTipoAndStatusConciliacao(
                        extratoId, TipoLancamentoExtrato.CREDITO.name(), StatusConciliacao.PENDENTE.name());

        if (lancamentos.isEmpty()) return;

        List<Producao> candidatas = new ArrayList<>(buscarCandidatas(cnpjTenant));

        if (candidatas.isEmpty()) return;

        // Pré-descriptografa CNPJs para evitar N×M chamadas ao banco
        Map<UUID, String> cnpjDigitosMap = pré_decriptografar(candidatas);
        Map<UUID, Long> liquidoMap = liquidosEsperados(candidatas);

        for (LancamentoExtrato lancamento : lancamentos) {
            if (conciliacaoRepo.existsByLancamentoExtratoId(lancamento.getId())) {
                log.debug("Lançamento {} já conciliado — ignorado (idempotência)", lancamento.getId());
                continue;
            }

            CandidatoMatch melhor = candidatas.stream()
                    .map(p -> new CandidatoMatch(p, calcularScore(lancamento, p,
                            cnpjDigitosMap.get(p.getId()), liquidoMap.get(p.getId()))))
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

        List<Producao> candidatas = new ArrayList<>(buscarCandidatas(cnpjTenant));

        if (candidatas.isEmpty()) return List.of();

        Map<UUID, String> cnpjMap = pré_decriptografar(candidatas);
        Map<UUID, Long> liquidoMap = liquidosEsperados(candidatas);

        return candidatas.stream()
                .map(p -> {
                    Long liquido = liquidoMap.get(p.getId());
                    int score = calcularScore(lancamento, p, cnpjMap.get(p.getId()), liquido);
                    String nome = p.getTomador() != null
                            ? p.getTomador().getRazaoSocialNome()
                            : "—";
                    return new CandidatoMatchResponse(p.getId(), nome, p.getValorBruto(),
                            liquido != null ? liquido : p.getValorBruto(),
                            p.getCompetencia(), score);
                })
                .filter(c -> c.score() >= LIMIAR_SUGESTAO)
                .sorted(Comparator.comparingInt(CandidatoMatchResponse::score).reversed())
                .limit(5)
                .toList();
    }

    /**
     * Produções EMITIDA ainda não conciliadas. Extrato sem tenant (importado por gestão antes
     * de o upload exigir a empresa) procura em todos os tenants, em vez de não sugerir nada.
     */
    private List<Producao> buscarCandidatas(String cnpjTenant) {
        List<String> statuses = List.of(StatusProducao.EMITIDA.name());
        return cnpjTenant == null || cnpjTenant.isBlank()
                ? producaoRepo.findCandidatasParaMatchTodosTenants(statuses)
                : producaoRepo.findCandidatasParaMatch(cnpjTenant, statuses);
    }

    /** Pontua com o líquido esperado gravado na produção (ou o bruto, se ainda não houver). */
    public int calcularScore(LancamentoExtrato lancamento, Producao producao, String cnpjDigitosPlain) {
        return calcularScore(lancamento, producao, cnpjDigitosPlain, producao.getValorLiquidoEsperado());
    }

    /**
     * CBS-31: o tomador deposita o líquido da nota (bruto − o que reteve na fonte), então o valor é
     * comparado com o líquido esperado e também com o bruto (tomador que não reteve). Vale o melhor dos
     * dois. A data conta da emissão da NFS-e quando conhecida; senão, do início da competência.
     */
    public int calcularScore(LancamentoExtrato lancamento, Producao producao, String cnpjDigitosPlain,
                             Long liquidoEsperado) {
        int scoreValor = scoreValor(lancamento.getValor(), producao.getValorBruto());
        if (liquidoEsperado != null && liquidoEsperado != producao.getValorBruto()) {
            scoreValor = Math.max(scoreValor, scoreValor(lancamento.getValor(), liquidoEsperado));
        }
        if (scoreValor == 0) {
            return 0; // divergência > 1% do bruto e do líquido → descarta candidato
        }

        int scoreId   = calcularScoreIdentidade(lancamento.getDescricao(), producao.getTomador(), cnpjDigitosPlain);
        int scoreData = producao.getNotaEmitidaEm() != null
                ? calcularScoreDataEmissao(lancamento.getDataLancamento(), producao.getNotaEmitidaEm())
                : calcularScoreData(lancamento.getDataLancamento(), producao.getCompetencia());

        return scoreValor + scoreId + scoreData;
    }

    private static int scoreValor(long recebido, long esperado) {
        if (esperado <= 0) return 0;
        long diff = Math.abs(recebido - esperado);
        if (diff <= TOLERANCIA_EXATO_CENTAVOS) return SCORE_VALOR_EXATO;
        if ((double) diff / esperado <= 0.01) return SCORE_VALOR_TOLERANCIA;
        return 0;
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

    private int calcularScoreDataEmissao(LocalDate dataLancamento, LocalDate emitidaEm) {
        if (dataLancamento == null) return 0;
        long dias = ChronoUnit.DAYS.between(emitidaEm, dataLancamento);
        if (dias < -DIAS_ANTES_EMISSAO) return 0;
        if (dias <= DIAS_APOS_EMISSAO_FORTE) return SCORE_DATA_7;
        if (dias <= DIAS_APOS_EMISSAO_FRACO) return SCORE_DATA_30;
        return 0;
    }

    /**
     * Líquido esperado por produção. Produções emitidas depois da CBS-31 trazem o valor da nota
     * (enviado pelo Fiscal). As anteriores são estimadas pelo cadastro do tomador, na regra de 2026
     * (todas são do regime LEGADO): PJ com retenção federal retém IR, CSLL, PIS e COFINS; com retenção
     * de ISS, retém o ISS. Alíquota do tomador sobrepõe a do serviço (percentual, 1.5000 = 1,5%).
     */
    public Map<UUID, Long> liquidosEsperados(List<Producao> candidatas) {
        Map<UUID, Long> mapa = new HashMap<>();
        List<Producao> semValor = new ArrayList<>();
        for (Producao p : candidatas) {
            if (p.getValorLiquidoEsperado() != null) mapa.put(p.getId(), p.getValorLiquidoEsperado());
            else if (p.getTomador() != null) semValor.add(p);
        }
        if (semValor.isEmpty()) return mapa;

        Set<UUID> tomadorIds = new HashSet<>();
        semValor.forEach(p -> tomadorIds.add(p.getTomador().getId()));
        tomadorIds.remove(null);
        Map<UUID, Map<String, BigDecimal>> aliquotasPorTomador = new HashMap<>();
        if (!tomadorIds.isEmpty()) {
            for (TomadorAliquota a : aliquotaRepo.findByTomadorIdIn(tomadorIds)) {
                if (a.getTipoTributo() != null && a.getValorAliquota() != null) {
                    aliquotasPorTomador.computeIfAbsent(a.getTomadorId(), k -> new HashMap<>())
                            .put(a.getTipoTributo().toUpperCase(), a.getValorAliquota());
                }
            }
        }
        for (Producao p : semValor) {
            Long estimado = estimarLiquidoLegado(p,
                    aliquotasPorTomador.getOrDefault(p.getTomador().getId(), Map.of()));
            if (estimado != null) mapa.put(p.getId(), estimado);
        }
        return mapa;
    }

    static Long estimarLiquidoLegado(Producao p, Map<String, BigDecimal> aliquotasTomador) {
        Tomador t = p.getTomador();
        if (t == null || t.getTipo() == TipoTomador.PACIENTE_PF) return null;
        List<String> retidos = new ArrayList<>();
        if (t.isIndicadorRetencaoIss()) retidos.add("ISS");
        if (t.isIndicadorRetencaoFederal()) retidos.addAll(List.of("IR", "CSLL", "PIS", "COFINS"));
        if (retidos.isEmpty()) return null;

        Map<String, Function<Servico, BigDecimal>> doServico = Map.of(
                "ISS", Servico::getAliquotaIss,
                "IR", Servico::getAliquotaIr,
                "CSLL", Servico::getAliquotaCsll,
                "PIS", Servico::getAliquotaPis,
                "COFINS", Servico::getAliquotaCofins);
        long bruto = p.getValorBruto();
        long retencoes = 0;
        for (String tributo : retidos) {
            BigDecimal pct = aliquotasTomador.containsKey(tributo)
                    ? aliquotasTomador.get(tributo)
                    : (p.getServico() != null ? doServico.get(tributo).apply(p.getServico()) : null);
            if (pct == null) continue;
            retencoes += BigDecimal.valueOf(bruto).multiply(pct)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
        }
        return retencoes > 0 && retencoes < bruto ? bruto - retencoes : null;
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
