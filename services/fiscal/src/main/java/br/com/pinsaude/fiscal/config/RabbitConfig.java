package br.com.pinsaude.fiscal.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE       = "nfse.exchange";
    public static final String QUEUE          = "nfse.emissao";
    public static final String DLQ_EXCHANGE   = "nfse.dlq.exchange";
    public static final String DLQ            = "nfse.emissao.dlq";
    public static final String ROUTING_KEY    = "nfse.emissao";
    public static final String DLQ_ROUTING    = "nfse.emissao.dlq";
    public static final String EMAIL_QUEUE    = "email.envio";

    // ── Exchanges ───────────────────────────────────────────────────────────

    @Bean
    public DirectExchange nfseExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange nfseDlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE, true, false);
    }

    // ── Queues ──────────────────────────────────────────────────────────────

    @Bean
    public Queue nfseEmissaoQueue() {
        return QueueBuilder.durable(QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", DLQ_ROUTING)
            .build();
    }

    @Bean
    public Queue nfseEmissaoDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue emailEnvioQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE).build();
    }

    // ── Bindings ────────────────────────────────────────────────────────────

    @Bean
    public Binding nfseBinding() {
        return BindingBuilder.bind(nfseEmissaoQueue()).to(nfseExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding nfseDlqBinding() {
        return BindingBuilder.bind(nfseEmissaoDlq()).to(nfseDlqExchange()).with(DLQ_ROUTING);
    }

    // ── Serialização JSON ────────────────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        var template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
