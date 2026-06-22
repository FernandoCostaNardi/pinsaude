package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.LoteEmissao;
import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.LoteProgressoResponse;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoMessage;
import br.com.pinsaude.fiscal.messaging.NfseEmissaoProducer;
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
public class NfseBatchService {

    private static final Logger log = LoggerFactory.getLogger(NfseBatchService.class);

    private static final String STATUS_EM_ANDAMENTO = "EM_ANDAMENTO";
    private static final String STATUS_CONCLUIDO    = "CONCLUIDO";
    private static final String STATUS_FALHA        = "FALHA";

    private final LoteEmissaoRepository loteRepo;
    private final NotaFiscalRepository  notaRepo;
    private final NfseEmissaoProducer   producer;

    public NfseBatchService(LoteEmissaoRepository loteRepo,
                            NotaFiscalRepository notaRepo,
                            NfseEmissaoProducer producer) {
        this.loteRepo = loteRepo;
        this.notaRepo = notaRepo;
        this.producer = producer;
    }

    /**
     * Inicia emissão em lote para a competência informada.
     *
     * Lock distribuído via banco: lança 409 se já existe lote EM_ANDAMENTO para a competência.
     * Idempotência: notas já PENDENTE são reenfileiradas; notas ERRO são resetadas para PENDENTE.
     * Notas em outros status (EMITIDA, CANCELADA, PROCESSANDO, AGUARDANDO_*) são ignoradas.
     */
    @Transactional
    public LoteProgressoResponse emitirLote(String competencia) {
        if (loteRepo.existsByCompetenciaAndStatus(competencia, STATUS_EM_ANDAMENTO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um lote em andamento para a competência " + competencia);
        }

        List<NotaFiscal> paraProcessar = notaRepo.findByCompetenciaAndStatusIn(
            competencia, List.of(StatusNota.PENDENTE, StatusNota.ERRO));

        LoteEmissao lote = new LoteEmissao();
        lote.setCompetencia(competencia);
        lote.setTotal(paraProcessar.size());
        lote.setEmProcessamento(paraProcessar.size());
        lote = loteRepo.save(lote);

        int enfileiradas = 0;
        for (NotaFiscal nota : paraProcessar) {
            if (nota.getStatus() == StatusNota.ERRO) {
                nota.setStatus(StatusNota.PENDENTE);
                notaRepo.save(nota);
            }
            producer.enviar(new NfseEmissaoMessage(nota.getId()));
            enfileiradas++;
        }

        log.info("Lote {} criado para competência {}: {} notas enfileiradas",
            lote.getId(), competencia, enfileiradas);

        return LoteProgressoResponse.from(lote);
    }

    /**
     * Retorna o progresso atual de um lote.
     * Se emitidas + falhas == total, finaliza o lote lazily (CONCLUIDO ou FALHA).
     */
    @Transactional
    public LoteProgressoResponse getProgresso(UUID loteId) {
        LoteEmissao lote = loteRepo.findById(loteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Lote não encontrado: " + loteId));

        // Finalização lazy: verifica se todas as notas foram processadas
        if (STATUS_EM_ANDAMENTO.equals(lote.getStatus()) && lote.getTotal() > 0
                && lote.getEmitidas() + lote.getFalhas() >= lote.getTotal()) {
            lote.setStatus(lote.getFalhas() > 0 ? STATUS_FALHA : STATUS_CONCLUIDO);
            lote.setConcluidoEm(OffsetDateTime.now());
            lote = loteRepo.save(lote);
            log.info("Lote {} finalizado com status {}", loteId, lote.getStatus());
        }

        return LoteProgressoResponse.from(lote);
    }

    /**
     * Lista todos os lotes ordenados do mais recente para o mais antigo.
     */
    @Transactional(readOnly = true)
    public List<LoteProgressoResponse> listarLotes() {
        return loteRepo.findAllByOrderByIniciadoEmDesc().stream()
            .map(LoteProgressoResponse::from)
            .toList();
    }
}
