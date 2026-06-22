package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import br.com.pinsaude.fiscal.dto.EmitirNfseResponse;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoMessage;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoProducer;
import br.com.pinsaude.fiscal.port.DadosNota;
import br.com.pinsaude.fiscal.port.EmissaoNfsePort;
import br.com.pinsaude.fiscal.port.ResultadoEmissao;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class NfseService {

    private static final Logger log = LoggerFactory.getLogger(NfseService.class);

    private final NotaFiscalRepository notaRepo;
    private final EmissaoNfsePort emissaoPort;
    private final NfseEmissaoProducer producer;

    public NfseService(NotaFiscalRepository notaRepo,
                       EmissaoNfsePort emissaoPort,
                       NfseEmissaoProducer producer) {
        this.notaRepo = notaRepo;
        this.emissaoPort = emissaoPort;
        this.producer = producer;
    }

    /**
     * Cria NotaFiscal PENDENTE e publica na fila — ambos na mesma transação (outbox pattern).
     * Idempotente: mesma producaoId retorna a nota existente sem reemitir.
     */
    @Transactional
    public EmitirNfseResponse emitir(EmitirNfseRequest req, String cnpjTenant) {
        // Idempotência: mesma producaoId não cria duplicata
        if (notaRepo.existsByProducaoId(req.producaoId())) {
            log.info("Nota já existente para producaoId={}, retornando existente", req.producaoId());
            return notaRepo.findByProducaoId(req.producaoId())
                .map(EmitirNfseResponse::from)
                .orElseThrow();
        }

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
        nota.setStatus(StatusNota.PENDENTE);

        nota = notaRepo.save(nota);

        // Outbox: enfileira na mesma transação → consistência garantida
        producer.enviar(new NfseEmissaoMessage(nota.getId()));

        log.info("NotaFiscal criada id={} producaoId={}", nota.getId(), nota.getProducaoId());
        return EmitirNfseResponse.from(nota);
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
    }
}
