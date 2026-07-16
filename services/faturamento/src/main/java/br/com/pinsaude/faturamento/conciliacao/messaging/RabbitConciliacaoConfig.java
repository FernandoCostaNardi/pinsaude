package br.com.pinsaude.faturamento.conciliacao.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConciliacaoConfig {

    public static final String MATCHING_QUEUE          = "conciliacao.matching";
    public static final String PRODUCAO_EMITIDA_QUEUE  = "faturamento.producao.emitida";

    @Bean
    public Queue matchingQueue() {
        return new Queue(MATCHING_QUEUE, true);
    }

    @Bean
    public Queue producaoEmitidaQueue() {
        return new Queue(PRODUCAO_EMITIDA_QUEUE, true);
    }

    @Bean
    public Jackson2JsonMessageConverter conciliacaoMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
