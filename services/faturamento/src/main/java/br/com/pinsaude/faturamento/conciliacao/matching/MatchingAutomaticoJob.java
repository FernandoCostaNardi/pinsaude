package br.com.pinsaude.faturamento.conciliacao.matching;

import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingMessage;
import br.com.pinsaude.faturamento.conciliacao.messaging.RabbitConciliacaoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MatchingAutomaticoJob {

    private static final Logger log = LoggerFactory.getLogger(MatchingAutomaticoJob.class);

    private final MatchingService matchingService;

    public MatchingAutomaticoJob(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @RabbitListener(queues = RabbitConciliacaoConfig.MATCHING_QUEUE)
    public void consumir(MatchingMessage message) {
        log.info("Motor de matching iniciado — extratoId={} tenant={}",
                message.extratoId(), message.cnpjTenant());
        try {
            matchingService.processarExtrato(message.extratoId(), message.cnpjTenant());
            log.info("Matching concluído — extratoId={}", message.extratoId());
        } catch (Exception e) {
            log.error("Falha no motor de matching — extratoId={}: {}",
                    message.extratoId(), e.getMessage(), e);
            throw e; // propaga para DLQ após max-attempts do RabbitMQ
        }
    }
}
