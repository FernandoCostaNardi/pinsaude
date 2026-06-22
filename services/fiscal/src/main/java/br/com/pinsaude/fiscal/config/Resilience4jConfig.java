package br.com.pinsaude.fiscal.config;

import br.com.pinsaude.fiscal.adapter.AgregadorFiscalProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(AgregadorFiscalProperties.class)
public class Resilience4jConfig {

    static final String INSTANCE = "agregador-fiscal";

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2.0))
            .retryExceptions(IOException.class, ResourceAccessException.class, HttpServerErrorException.class)
            .build();
        return RetryRegistry.of(Map.of(INSTANCE, config));
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Abre após 5 falhas consecutivas (window=5, threshold=100%)
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(100.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
        return CircuitBreakerRegistry.of(Map.of(INSTANCE, config));
    }
}
