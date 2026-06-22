package br.com.pinsaude.fiscal.job;

import br.com.pinsaude.fiscal.service.NfseBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;

/**
 * Job de emissão mensal em lote (EPIC-05.7).
 *
 * Executa no dia 1 de cada mês às 02:00 (horário do servidor).
 * Processa todas as notas PENDENTE e ERRO da competência do mês anterior.
 * Lock distribuído: lança 409 se já existe lote EM_ANDAMENTO para a mesma competência.
 */
@Component
public class EmissaoLoteJob {

    private static final Logger log = LoggerFactory.getLogger(EmissaoLoteJob.class);

    private final NfseBatchService batchService;

    public EmissaoLoteJob(NfseBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "0 0 2 1 * *")
    public void executarEmissaoMensal() {
        String competencia = YearMonth.now().minusMonths(1).toString(); // ex: "2026-05"
        log.info("Job de emissão em lote iniciado para competência {}", competencia);
        try {
            var progresso = batchService.emitirLote(competencia);
            log.info("Job concluído: loteId={}, total={} notas enfileiradas para competência {}",
                progresso.loteId(), progresso.total(), competencia);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.warn("Job ignorado: lote já em andamento para competência {}", competencia);
            } else {
                log.error("Erro no job de emissão em lote para competência {}: {}",
                    competencia, e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Erro inesperado no job de emissão em lote para competência {}", competencia, e);
        }
    }
}
