package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.EmitirNfseResponse;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.messaging.EmailNotificacaoProducer;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoMessage;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoProducer;
import br.com.pinsaude.fiscal.port.DadosNota;
import br.com.pinsaude.fiscal.port.EmissaoNfsePort;
import br.com.pinsaude.fiscal.port.ResultadoEmissao;
import br.com.pinsaude.fiscal.repository.LoteEmissaoRepository;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NfseService {

    private static final Logger log = LoggerFactory.getLogger(NfseService.class);

    private final NotaFiscalRepository  notaRepo;
    private final LoteEmissaoRepository loteRepo;
    private final EmissaoNfsePort emissaoPort;
    private final NfseEmissaoProducer producer;
    private final EmailNotificacaoProducer emailProducer;

    public NfseService(NotaFiscalRepository notaRepo,
                       LoteEmissaoRepository loteRepo,
                       EmissaoNfsePort emissaoPort,
                       NfseEmissaoProducer producer,
                       EmailNotificacaoProducer emailProducer) {
        this.notaRepo      = notaRepo;
        this.loteRepo      = loteRepo;
        this.emissaoPort   = emissaoPort;
        this.producer      = producer;
        this.emailProducer = emailProducer;
    }

    /**
     * Cria NotaFiscal e publica na fila — ambos na mesma transação (outbox pattern).
     * Idempotente: mesma producaoId retorna a nota existente sem reemitir.
     * 1ª nota de um médico: fica em AGUARDANDO_VALIDACAO até aprovação manual.
     */
    @Transactional
    public EmitirNfseResponse emitir(EmitirNfseRequest req, String cnpjTenant) {
        if (notaRepo.existsByProducaoId(req.producaoId())) {
            log.info("Nota já existente para producaoId={}, retornando existente", req.producaoId());
            return notaRepo.findByProducaoId(req.producaoId())
                .map(n -> EmitirNfseResponse.from(n, false))
                .orElseThrow();
        }

        // Multi-médico (medicoId null) → sem verificação de primeira nota; vai direto para fila
        boolean primeiraNotaMedico = req.medicoId() != null
            && !notaRepo.existsByMedicoIdAndStatus(req.medicoId(), StatusNota.EMITIDA);
        StatusNota statusInicial = primeiraNotaMedico ? StatusNota.AGUARDANDO_VALIDACAO : StatusNota.PENDENTE;

        var nota = new NotaFiscal();
        nota.setCnpjIdTenant(cnpjTenant);
        nota.setProducaoId(req.producaoId());
        nota.setMedicoId(req.medicoId());
        nota.setTomadorId(req.tomadorId());
        nota.setCompetencia(req.competencia());
        nota.setValorBruto(req.valorBruto());
        nota.setTaxaPin(req.taxaPin());
        nota.setValorLiquidoMedico(req.valorBruto() - req.taxaPin());
        nota.setValorIss(req.valorIss() != null ? req.valorIss() : 0L);
        nota.setValorIr(req.valorIr() != null ? req.valorIr() : 0L);
        nota.setValorCsll(req.valorCsll() != null ? req.valorCsll() : 0L);
        nota.setValorPis(req.valorPis() != null ? req.valorPis() : 0L);
        nota.setValorCofins(req.valorCofins() != null ? req.valorCofins() : 0L);
        nota.setTomadorNome(req.tomadorNome());
        nota.setStatus(statusInicial);

        nota = notaRepo.save(nota);

        if (statusInicial == StatusNota.PENDENTE) {
            producer.enviar(new NfseEmissaoMessage(nota.getId()));
            log.info("NotaFiscal criada id={} producaoId={} — enfileirada para processamento", nota.getId(), nota.getProducaoId());
        } else {
            log.info("NotaFiscal criada id={} producaoId={} — aguardando validação (1ª nota do médico)", nota.getId(), nota.getProducaoId());
        }

        return EmitirNfseResponse.from(nota, primeiraNotaMedico);
    }

    /**
     * Chamado pelo consumer RabbitMQ: processa a emissão real via adapter.
     * Idempotente: notas fora de PENDENTE são ignoradas silenciosamente.
     */
    @Transactional
    public void processarEmissao(UUID notaId) {
        var nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new IllegalArgumentException("Nota não encontrada: " + notaId));

        if (nota.getStatus() != StatusNota.PENDENTE) {
            log.info("Nota {} já processada (status={}), ignorando", notaId, nota.getStatus());
            return;
        }

        nota.setStatus(StatusNota.PROCESSANDO);
        notaRepo.save(nota);

        var dados = new DadosNota(
            nota.getProducaoId(), nota.getMedicoId(), nota.getTomadorId(),
            nota.getCnpjIdTenant(), nota.getCompetencia(),
            nota.getValorBruto(), nota.getTaxaPin(),
            nota.getValorIss(), nota.getValorIr(), nota.getValorCsll(),
            nota.getValorPis(), nota.getValorCofins(), nota.getValorLiquidoMedico()
        );

        ResultadoEmissao resultado = emissaoPort.emitir(dados);

        if (resultado.aguardandoEmissaoManual()) {
            nota.setStatus(StatusNota.AGUARDANDO_EMISSAO_MANUAL);
            nota.setObservacoes(resultado.mensagemErro());
        } else if (resultado.sucesso()) {
            nota.setStatus(StatusNota.EMITIDA);
            nota.setNumeroNota(resultado.numeroNota());
            nota.setProtocoloEmissao(resultado.protocolo());
            nota.setXmlNota(resultado.xmlNota());
            nota.setPdfNota(resultado.pdfNota());
            nota.setEmitidaAt(OffsetDateTime.now());
        } else {
            nota.setStatus(StatusNota.ERRO);
            nota.setObservacoes(resultado.mensagemErro());
        }

        notaRepo.save(nota);
        log.info("NotaFiscal {} atualizada para status={}", notaId, nota.getStatus());

        if (nota.getStatus() == StatusNota.EMITIDA) {
            emailProducer.notificarNotaEmitida(nota);
        }

        atualizarContadoresLote(nota.getCompetencia(), nota.getStatus());
    }

    // Atualiza contadores do lote EM_ANDAMENTO atomicamente (se houver lote ativo para a competência)
    private void atualizarContadoresLote(String competencia, StatusNota statusFinal) {
        if (statusFinal == StatusNota.EMITIDA) {
            loteRepo.incrementarEmitida(competencia);
        } else if (statusFinal == StatusNota.ERRO || statusFinal == StatusNota.AGUARDANDO_EMISSAO_MANUAL) {
            loteRepo.incrementarFalha(competencia);
        }
    }

    /**
     * Retorna o status atual de uma nota pelo producaoId — usado para polling do frontend.
     */
    @Transactional(readOnly = true)
    public NotaFiscalStatusResponse getStatus(UUID producaoId) {
        return notaRepo.findByProducaoId(producaoId)
            .map(NotaFiscalStatusResponse::from)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Nota não encontrada para producaoId: " + producaoId));
    }

    /**
     * Lista todas as notas fiscais ordenadas por criação (mais recentes primeiro).
     */
    @Transactional(readOnly = true)
    public List<NotaFiscalStatusResponse> listar() {
        return notaRepo.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(NotaFiscalStatusResponse::from)
            .toList();
    }

    /**
     * Retorna o XML da nota para download.
     */
    @Transactional(readOnly = true)
    public String getXml(UUID notaId) {
        var nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota não encontrada"));
        if (nota.getXmlNota() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "XML não disponível — nota ainda não emitida");
        }
        return nota.getXmlNota();
    }

    /**
     * Retorna o PDF da nota (DANFSE) para download.
     */
    @Transactional(readOnly = true)
    public byte[] getPdf(UUID notaId) {
        var nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota não encontrada"));
        if (nota.getPdfNota() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF não disponível — nota ainda não emitida");
        }
        return nota.getPdfNota();
    }

    /**
     * Aprova a 1ª nota de um médico (AGUARDANDO_VALIDACAO → PENDENTE + enfileira).
     */
    @Transactional
    public void aprovar(UUID notaId) {
        var nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota não encontrada"));
        if (nota.getStatus() != StatusNota.AGUARDANDO_VALIDACAO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Nota não está aguardando validação (status atual: " + nota.getStatus() + ")");
        }
        nota.setStatus(StatusNota.PENDENTE);
        notaRepo.save(nota);
        producer.enviar(new NfseEmissaoMessage(nota.getId()));
        log.info("1ª nota do médico aprovada — enfileirada: notaId={}", notaId);
    }

    /**
     * Cancela uma nota emitida (EMITIDA → CANCELADA). Registra motivo em observacoes.
     */
    @Transactional
    public void cancelar(UUID notaId, String motivo) {
        var nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota não encontrada"));
        if (nota.getStatus() != StatusNota.EMITIDA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Apenas notas EMITIDAS podem ser canceladas (status atual: " + nota.getStatus() + ")");
        }
        nota.setStatus(StatusNota.CANCELADA);
        nota.setObservacoes("Cancelada: " + motivo);
        notaRepo.save(nota);
        log.info("NotaFiscal {} cancelada. Motivo: {}", notaId, motivo);
    }

    /**
     * Rejeita uma nota na fila de exceções (AGUARDANDO_VALIDACAO → CANCELADA). Registra motivo.
     */
    @Transactional
    public void rejeitar(UUID notaId, String motivo) {
        var nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota não encontrada"));
        if (nota.getStatus() != StatusNota.AGUARDANDO_VALIDACAO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Apenas notas AGUARDANDO_VALIDACAO podem ser rejeitadas (status atual: " + nota.getStatus() + ")");
        }
        nota.setStatus(StatusNota.CANCELADA);
        nota.setObservacoes("Rejeitada: " + motivo);
        notaRepo.save(nota);
        log.info("NotaFiscal {} rejeitada (fila de exceções). Motivo: {}", notaId, motivo);
    }

    /**
     * Lista notas na fila de exceções (AGUARDANDO_VALIDACAO — 1ª nota de médico pendente validação).
     */
    @Transactional(readOnly = true)
    public List<NotaFiscalStatusResponse> listarExcecoes() {
        return notaRepo.findAllByStatus(StatusNota.AGUARDANDO_VALIDACAO)
            .stream()
            .map(NotaFiscalStatusResponse::from)
            .toList();
    }

    /**
     * Reprocessa uma nota individual com status ERRO ou AGUARDANDO_EMISSAO_MANUAL.
     * Reset para PENDENTE e republica na fila — idempotente pelo consumer.
     */
    @Transactional
    public void reprocessarNota(UUID notaId) {
        var nota = notaRepo.findById(notaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota não encontrada"));
        if (nota.getStatus() != StatusNota.ERRO && nota.getStatus() != StatusNota.AGUARDANDO_EMISSAO_MANUAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Apenas notas ERRO ou AGUARDANDO_EMISSAO_MANUAL podem ser reprocessadas (status: " + nota.getStatus() + ")");
        }
        nota.setStatus(StatusNota.PENDENTE);
        notaRepo.save(nota);
        producer.enviar(new NfseEmissaoMessage(nota.getId()));
        log.info("NotaFiscal {} reenfileirada para reprocessamento", notaId);
    }
}
