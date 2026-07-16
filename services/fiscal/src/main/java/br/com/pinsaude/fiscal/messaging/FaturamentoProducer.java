package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FaturamentoProducer {

    private static final Logger log = LoggerFactory.getLogger(FaturamentoProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public FaturamentoProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void notificarProducaoEmitida(UUID producaoId, String cnpjTenant) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.PRODUCAO_EMITIDA_QUEUE,
                    new ProducaoEmitidaMessage(producaoId, cnpjTenant));
            log.info("Evento ProducaoEmitida publicado: producaoId={}", producaoId);
        } catch (Exception e) {
            log.warn("Falha ao publicar ProducaoEmitida producaoId={}: {}", producaoId, e.getMessage());
        }
    }
}
