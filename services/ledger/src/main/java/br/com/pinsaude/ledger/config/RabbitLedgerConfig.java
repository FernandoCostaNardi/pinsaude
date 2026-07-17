package br.com.pinsaude.ledger.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas de eventos que geram lançamentos automáticos no ledger.
 * Cada fila principal encaminha para a DLX (dead-letter exchange) após esgotar as
 * retentativas (max-attempts=3 + default-requeue-rejected=false no application.yml);
 * todas as mensagens mortas caem numa única DLQ para inspeção.
 */
@Configuration
public class RabbitLedgerConfig {

    public static final String QUEUE_NOTA_EMITIDA          = "ledger.nota.emitida";
    public static final String QUEUE_RECEBIMENTO_CONCILIADO = "ledger.recebimento.conciliado";
    public static final String QUEUE_REPASSE_EFETUADO      = "ledger.repasse.efetuado";
    public static final String QUEUE_AJUSTE_MANUAL         = "ledger.ajuste.manual";

    public static final String DLX = "ledger.dlx";
    public static final String DLQ = "ledger.dlq";

    private Queue comDlq(String nome) {
        return QueueBuilder.durable(nome)
            .withArgument("x-dead-letter-exchange", DLX)
            .withArgument("x-dead-letter-routing-key", nome)
            .build();
    }

    @Bean public Queue notaEmitidaQueue()          { return comDlq(QUEUE_NOTA_EMITIDA); }
    @Bean public Queue recebimentoConciliadoQueue() { return comDlq(QUEUE_RECEBIMENTO_CONCILIADO); }
    @Bean public Queue repasseEfetuadoQueue()      { return comDlq(QUEUE_REPASSE_EFETUADO); }
    @Bean public Queue ajusteManualQueue()         { return comDlq(QUEUE_AJUSTE_MANUAL); }

    @Bean public DirectExchange ledgerDlx()        { return new DirectExchange(DLX, true, false); }
    @Bean public Queue ledgerDlq()                 { return QueueBuilder.durable(DLQ).build(); }

    // Uma única DLQ recebe as mensagens mortas de todas as filas (routing key = nome da fila)
    @Bean public Binding dlqBindingNota()        { return bindDlq(QUEUE_NOTA_EMITIDA); }
    @Bean public Binding dlqBindingRecebimento() { return bindDlq(QUEUE_RECEBIMENTO_CONCILIADO); }
    @Bean public Binding dlqBindingRepasse()     { return bindDlq(QUEUE_REPASSE_EFETUADO); }
    @Bean public Binding dlqBindingAjuste()      { return bindDlq(QUEUE_AJUSTE_MANUAL); }

    private Binding bindDlq(String routingKey) {
        return BindingBuilder.bind(ledgerDlq()).to(ledgerDlx()).with(routingKey);
    }

    // ── Serialização JSON + retry/DLQ do application.yml ──────────────────────

    @Bean
    public Jackson2JsonMessageConverter jacksonConverter() {
        var converter = new Jackson2JsonMessageConverter();
        // Usa o tipo inferido do parâmetro do @RabbitListener (não o header __TypeId__ do produtor).
        // Assim, eventos publicados por OUTROS serviços (classes/pacotes diferentes) desserializam
        // corretamente no contrato local do ledger.
        var mapper = new DefaultJackson2JavaTypeMapper();
        mapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        mapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(mapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        var template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory cf,
            Jackson2JsonMessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, cf);   // aplica retry (max-attempts=3) e requeue=false do yml
        factory.setMessageConverter(converter);
        return factory;
    }
}
