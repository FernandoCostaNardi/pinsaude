package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingMessage;
import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingProducer;
import br.com.pinsaude.faturamento.conciliacao.parser.ExtratoBancarioParser;
import br.com.pinsaude.faturamento.conciliacao.parser.LancamentoParseado;
import br.com.pinsaude.faturamento.config.TenantContext;
import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.dto.ExtratoResponse;
import br.com.pinsaude.faturamento.dto.LancamentoExtratoResponse;
import br.com.pinsaude.faturamento.dto.ProducaoCandidataResponse;
import br.com.pinsaude.faturamento.repository.ConciliacaoRepository;
import br.com.pinsaude.faturamento.repository.ExtratoBancarioRepository;
import br.com.pinsaude.faturamento.repository.LancamentoExtratoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExtratoService {

    private static final Logger log = LoggerFactory.getLogger(ExtratoService.class);

    private final ExtratoBancarioRepository   extratoRepo;
    private final LancamentoExtratoRepository lancamentoRepo;
    private final ConciliacaoRepository       conciliacaoRepo;
    private final ProducaoRepository          producaoRepo;
    private final List<ExtratoBancarioParser> parsers;
    private final MatchingProducer            matchingProducer;

    public ExtratoService(ExtratoBancarioRepository extratoRepo,
                          LancamentoExtratoRepository lancamentoRepo,
                          ConciliacaoRepository conciliacaoRepo,
                          ProducaoRepository producaoRepo,
                          List<ExtratoBancarioParser> parsers,
                          MatchingProducer matchingProducer) {
        this.extratoRepo      = extratoRepo;
        this.lancamentoRepo   = lancamentoRepo;
        this.conciliacaoRepo  = conciliacaoRepo;
        this.producaoRepo     = producaoRepo;
        this.parsers          = parsers;
        this.matchingProducer = matchingProducer;
    }

    @Transactional
    public ExtratoResponse upload(MultipartFile arquivo, BancoEnum banco,
                                  LocalDate dataInicio, LocalDate dataFim) {
        if (dataFim.isBefore(dataInicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "data_fim não pode ser anterior a data_inicio");
        }

        String tenant = TenantContext.get();
        String nomeArquivo = arquivo.getOriginalFilename() != null
                ? arquivo.getOriginalFilename()
                : arquivo.getName();

        if (extratoRepo.existsByNomeArquivoAndPeriodoInicioAndPeriodoFimAndCnpjIdTenant(
                nomeArquivo, dataInicio, dataFim, tenant)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Extrato já importado: mesmo arquivo e período já existem para este tenant.");
        }

        byte[] content;
        try {
            content = arquivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não foi possível ler o arquivo: " + e.getMessage());
        }

        ExtratoBancarioParser parser = parsers.stream()
                .filter(p -> p.suporta(banco, nomeArquivo))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Formato não suportado para banco=" + banco + " arquivo=" + nomeArquivo));

        ExtratoBancario extrato = new ExtratoBancario();
        extrato.setCnpjIdTenant(tenant);
        extrato.setNomeArquivo(nomeArquivo);
        extrato.setBanco(banco);
        extrato.setPeriodoInicio(dataInicio);
        extrato.setPeriodoFim(dataFim);
        extrato.setStatusImportacao(StatusImportacao.PROCESSANDO);
        extrato.setCreatedBy(resolverEmail());
        extratoRepo.save(extrato);

        List<LancamentoParseado> lancamentos;
        try {
            lancamentos = parser.parse(content);
        } catch (ParseException e) {
            extrato.setStatusImportacao(StatusImportacao.ERRO);
            extratoRepo.save(extrato);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Erro ao processar arquivo: " + e.getMessage());
        }

        int salvos = 0;
        for (LancamentoParseado lp : lancamentos) {
            if (lp.identificadorExterno() != null &&
                    lancamentoRepo.existsByExtratoIdAndIdentificadorExterno(
                            extrato.getId(), lp.identificadorExterno())) {
                log.debug("Lançamento duplicado ignorado: {}", lp.identificadorExterno());
                continue;
            }
            LancamentoExtrato le = new LancamentoExtrato();
            le.setExtratoId(extrato.getId());
            le.setDataLancamento(lp.data());
            le.setDescricao(lp.descricao());
            le.setValor(lp.valorCentavos());
            le.setTipo(lp.tipo());
            le.setIdentificadorExterno(lp.identificadorExterno());
            le.setStatusConciliacao(StatusConciliacao.PENDENTE);
            lancamentoRepo.save(le);
            salvos++;
        }

        extrato.setStatusImportacao(StatusImportacao.OK);
        extrato.setTotalLancamentos(salvos);
        extratoRepo.save(extrato);

        matchingProducer.publicar(new MatchingMessage(extrato.getId(), tenant));

        return ExtratoResponse.from(extrato);
    }

    @Transactional(readOnly = true)
    public List<ExtratoResponse> listarExtratos() {
        String tenant = TenantContext.get();
        return extratoRepo.findAllByCnpjIdTenantOrderByDataUploadDesc(tenant)
                .stream().map(ExtratoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LancamentoExtratoResponse> listarLancamentos(UUID extratoId, String statusParam) {
        List<LancamentoExtrato> lancamentos;
        if (statusParam != null && !statusParam.isBlank()) {
            StatusConciliacao status;
            try {
                status = StatusConciliacao.valueOf(statusParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Status inválido: " + statusParam);
            }
            lancamentos = lancamentoRepo
                    .findByExtratoIdAndStatusConciliacaoOrderByDataLancamentoDesc(extratoId, status.name());
        } else {
            lancamentos = lancamentoRepo.findByExtratoIdOrderByDataLancamentoDesc(extratoId);
        }

        // Batch load conciliações para evitar N+1
        List<UUID> lancamentoIds = lancamentos.stream().map(LancamentoExtrato::getId).toList();
        Map<UUID, Conciliacao> conciliacaoMap = conciliacaoRepo
                .findByLancamentoExtratoIdIn(lancamentoIds)
                .stream()
                .collect(Collectors.toMap(Conciliacao::getLancamentoExtratoId, c -> c));

        // Batch load producoes para exibir dados da conciliação (tomador, valor, competência)
        List<UUID> producaoIds = conciliacaoMap.values().stream()
                .map(Conciliacao::getNotaId)
                .distinct()
                .toList();
        Map<UUID, Producao> producaoMap = producaoIds.isEmpty()
                ? Map.of()
                : producaoRepo.findAllByIdWithTomador(producaoIds)
                        .stream()
                        .collect(Collectors.toMap(Producao::getId, p -> p));

        return lancamentos.stream().map(l -> {
            Conciliacao c = conciliacaoMap.get(l.getId());
            if (c == null) return LancamentoExtratoResponse.from(l);

            Producao p = producaoMap.get(c.getNotaId());
            String tomadorNome = (p != null && p.getTomador() != null)
                    ? p.getTomador().getRazaoSocialNome()
                    : null;
            long valorBruto = p != null ? p.getValorBruto() : 0L;
            String competencia = p != null ? p.getCompetencia() : null;

            return LancamentoExtratoResponse.from(l, c, tomadorNome, valorBruto, competencia);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ProducaoCandidataResponse> listarCandidatas() {
        String tenant = TenantContext.get();
        if (tenant == null || tenant.isBlank()) return List.of();
        return producaoRepo
                .findCandidatasParaMatch(tenant, List.of(StatusProducao.EMITIDA.name()))
                .stream()
                .map(ProducaoCandidataResponse::from)
                .toList();
    }

    @Transactional
    public void conciliarManual(UUID lancamentoId, UUID producaoId, String usuarioId, String observacao) {
        LancamentoExtrato lancamento = lancamentoRepo.findById(lancamentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lançamento não encontrado"));

        // Valida que a produção existe e tem NFS-e emitida
        Producao producao = producaoRepo.findById(producaoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Produção não encontrada"));

        if (producao.getStatus() != StatusProducao.EMITIDA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Conciliação permitida apenas para produções com NFS-e emitida.");
        }

        // Desfaz conciliação anterior deste lançamento se existir
        if (StatusConciliacao.CONCILIADO.equals(lancamento.getStatusConciliacao())) {
            conciliacaoRepo.deleteByLancamentoExtratoId(lancamentoId);
        }

        // Valida que a produção não está conciliada com outro lançamento
        if (conciliacaoRepo.existsByNotaId(producaoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta produção já está conciliada com outro lançamento bancário.");
        }

        Conciliacao c = new Conciliacao();
        c.setLancamentoExtratoId(lancamentoId);
        c.setNotaId(producaoId);
        c.setTipoMatch(TipoMatchEnum.MANUAL);
        c.setScoreConfianca(0);
        c.setUsuarioId(usuarioId);
        c.setObservacao(observacao);
        conciliacaoRepo.save(c);

        lancamento.setStatusConciliacao(StatusConciliacao.CONCILIADO);
        lancamentoRepo.save(lancamento);
    }

    @Transactional
    public void ignorar(UUID lancamentoId) {
        LancamentoExtrato lancamento = lancamentoRepo.findById(lancamentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lançamento não encontrado"));

        if (StatusConciliacao.CONCILIADO.equals(lancamento.getStatusConciliacao())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lançamento já conciliado — desfaça a conciliação antes de ignorar.");
        }

        lancamento.setStatusConciliacao(StatusConciliacao.IGNORADO);
        lancamentoRepo.save(lancamento);
    }

    @Transactional
    public void desfazerConciliacao(UUID lancamentoId) {
        LancamentoExtrato lancamento = lancamentoRepo.findById(lancamentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lançamento não encontrado"));

        if (StatusConciliacao.CONCILIADO.equals(lancamento.getStatusConciliacao())) {
            conciliacaoRepo.deleteByLancamentoExtratoId(lancamentoId);
        } else if (!StatusConciliacao.IGNORADO.equals(lancamento.getStatusConciliacao())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lançamento não está conciliado nem ignorado.");
        }

        lancamento.setStatusConciliacao(StatusConciliacao.PENDENTE);
        lancamentoRepo.save(lancamento);
    }

    private String resolverEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String email = jwt.getToken().getClaimAsString("email");
            if (email != null) return email;
            return jwt.getToken().getClaimAsString("preferred_username");
        }
        return null;
    }
}
