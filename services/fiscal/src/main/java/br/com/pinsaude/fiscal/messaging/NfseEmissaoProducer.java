package br.com.pinsaude.fiscal.messaging;

import br.com.pinsaude.fiscal.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class NfseEmissaoProducer {

    private final RabbitTemplate rabbitTemplate;

    public NfseEmissaoProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enviar(NfseEmissaoMessage mensagem) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, mensagem);
    }
}
