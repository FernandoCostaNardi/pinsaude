package br.com.pinsaude.fiscal.adapter;

import br.com.pinsaude.fiscal.port.DadosNota;
import br.com.pinsaude.fiscal.port.EmissaoNfsePort;
import br.com.pinsaude.fiscal.port.ResultadoEmissao;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Base64;

@Component
public class AgregadorFiscalAdapter implements EmissaoNfsePort {

    private static final Logger log = LoggerFactory.getLogger(AgregadorFiscalAdapter.class);

    private final AgregadorFiscalProperties properties;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final RestClient restClient;

    public AgregadorFiscalAdapter(AgregadorFiscalProperties properties,
                                   RetryRegistry retryRegistry,
                                   CircuitBreakerRegistry cbRegistry) {
        this.properties = properties;
        this.retry = retryRegistry.retry("agregador-fiscal", "agregador-fiscal");
        this.circuitBreaker = cbRegistry.circuitBreaker("agregador-fiscal", "agregador-fiscal");

        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSegundos()));

        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(factory)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Override
    public ResultadoEmissao emitir(DadosNota dados) {
        if (!properties.isEnabled()) {
            return ResultadoEmissao.aguardandoManual("Integração com agregador fiscal desabilitada");
        }

        CheckedSupplier<ResultadoEmissao> decorated =
            CircuitBreaker.decorateCheckedSupplier(circuitBreaker,
                Retry.decorateCheckedSupplier(retry, () -> doEmitir(dados)));

        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aberto para agregador fiscal: {}", e.getMessage());
            return ResultadoEmissao.aguardandoManual("Agregador fiscal temporariamente indisponível (circuit breaker aberto)");
        } catch (Throwable t) {
            log.error("Falha após todas as tentativas no agregador fiscal", t);
            return ResultadoEmissao.aguardandoManual("Falha após 3 tentativas: " + t.getMessage());
        }
    }

    private ResultadoEmissao doEmitir(DadosNota dados) {
        NfseRpsRequest request = NfseRpsRequest.from(dados);

        NfseRpsResponse response = restClient.post()
            .uri("/nfse/rps")
            .header("X-API-Key", properties.getApiToken())
            .body(request)
            .retrieve()
            .body(NfseRpsResponse.class);

        if (response == null) {
            throw new RestClientException("Resposta nula do agregador fiscal");
        }

        byte[] pdf = null;
        if (response.pdfBase64() != null && !response.pdfBase64().isBlank()) {
            pdf = Base64.getDecoder().decode(response.pdfBase64());
        }

        log.info("NFS-e emitida com sucesso: numero={} protocolo={}", response.numeroNota(), response.protocolo());

        return ResultadoEmissao.sucesso(response.numeroNota(), response.protocolo(), response.xmlNota(), pdf);
    }
}
