package br.com.pinsaude.faturamento.conciliacao.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class MatchingProducer {

    private static final Logger log = LoggerFactory.getLogger(MatchingProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public MatchingProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(MatchingMessage msg) {
        try {
            rabbitTemplate.convertAndSend(RabbitConciliacaoConfig.MATCHING_QUEUE, msg);
            log.info("Matching enfileirado — extratoId={}", msg.extratoId());
        } catch (Exception e) {
            log.warn("Falha ao enfileirar matching — extratoId={}: {}", msg.extratoId(), e.getMessage());
        }
    }
}
