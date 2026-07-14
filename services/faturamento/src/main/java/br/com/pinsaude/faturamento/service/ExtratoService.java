package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingMessage;
import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingProducer;
import br.com.pinsaude.faturamento.conciliacao.parser.ExtratoBancarioParser;
import br.com.pinsaude.faturamento.conciliacao.parser.LancamentoParseado;
import br.com.pinsaude.faturamento.config.TenantContext;
import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.dto.ExtratoResponse;
import br.com.pinsaude.faturamento.dto.LancamentoExtratoResponse;
import br.com.pinsaude.faturamento.repository.ExtratoBancarioRepository;
import br.com.pinsaude.faturamento.repository.LancamentoExtratoRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class ExtratoService {

    private static final Logger log = LoggerFactory.getLogger(ExtratoService.class);

    private final ExtratoBancarioRepository  extratoRepo;
    private final LancamentoExtratoRepository lancamentoRepo;
    private final List<ExtratoBancarioParser> parsers;
    private final MatchingProducer            matchingProducer;

    public ExtratoService(ExtratoBancarioRepository extratoRepo,
                          LancamentoExtratoRepository lancamentoRepo,
                          List<ExtratoBancarioParser> parsers,
                          MatchingProducer matchingProducer) {
        this.extratoRepo      = extratoRepo;
        this.lancamentoRepo   = lancamentoRepo;
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
                    .findByExtratoIdAndStatusConciliacaoOrderByDataLancamentoDesc(extratoId, status);
        } else {
            lancamentos = lancamentoRepo.findByExtratoIdOrderByDataLancamentoDesc(extratoId);
        }
        return lancamentos.stream().map(LancamentoExtratoResponse::from).toList();
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
