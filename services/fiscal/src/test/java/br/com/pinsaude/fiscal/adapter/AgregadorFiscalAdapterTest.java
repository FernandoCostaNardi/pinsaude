package br.com.pinsaude.fiscal.adapter;

import br.com.pinsaude.fiscal.port.DadosNota;
import br.com.pinsaude.fiscal.port.ResultadoEmissao;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class AgregadorFiscalAdapterTest {

    private static final String INSTANCE = "agregador-fiscal";

    private WireMockServer wireMock;
    private AgregadorFiscalProperties props;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        props = new AgregadorFiscalProperties();
        props.setEnabled(true);
        props.setApiToken("test-token");
        props.setBaseUrl("http://localhost:" + wireMock.port());
        props.setTimeoutSegundos(1);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // --- helper methods ---

    private AgregadorFiscalAdapter buildAdapter() {
        return new AgregadorFiscalAdapter(props, retryRegistry(), cbRegistry());
    }

    private RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(10), 2.0))
            .retryExceptions(IOException.class, ResourceAccessException.class, HttpServerErrorException.class)
            .build();
        return RetryRegistry.of(Map.of(INSTANCE, config));
    }

    private CircuitBreakerRegistry cbRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(100.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();
        return CircuitBreakerRegistry.of(Map.of(INSTANCE, config));
    }

    private DadosNota dadosTeste() {
        return new DadosNota(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "12345678000195", "2026-06",
            1000000L, 150000L, 50000L, 15000L, 10000L, 6500L, 30000L, 850000L
        );
    }

    private String successJson() {
        String pdfB64 = Base64.getEncoder().encodeToString("PDF-BYTES".getBytes());
        return """
            {
              "numeroNota": "NF-2026-001",
              "protocolo": "PROTO-ABC-123",
              "xmlNota": "<NFS-e><numero>NF-2026-001</numero></NFS-e>",
              "pdfBase64": "%s"
            }
            """.formatted(pdfB64);
    }

    // --- Critério 1: emissão com sucesso retorna XML e PDF ---

    @Test
    void emitirComSucesso_retornaXmlEPdfArmazenados() {
        wireMock.stubFor(post(urlEqualTo("/nfse/rps"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successJson())));

        ResultadoEmissao resultado = buildAdapter().emitir(dadosTeste());

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.numeroNota()).isEqualTo("NF-2026-001");
        assertThat(resultado.protocolo()).isEqualTo("PROTO-ABC-123");
        assertThat(resultado.xmlNota()).contains("<NFS-e>");
        assertThat(resultado.pdfNota()).isEqualTo("PDF-BYTES".getBytes());
        wireMock.verify(1, postRequestedFor(urlEqualTo("/nfse/rps"))
            .withHeader("X-API-Key", equalTo("test-token")));
    }

    // --- Critério 3: retry recupera falha temporária ---

    @Test
    void retry_falhaTemporaria_recuperaNaTerceiraConsulta() {
        wireMock.stubFor(post(urlEqualTo("/nfse/rps"))
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("attempt-2"));

        wireMock.stubFor(post(urlEqualTo("/nfse/rps"))
            .inScenario("retry")
            .whenScenarioStateIs("attempt-2")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("success"));

        wireMock.stubFor(post(urlEqualTo("/nfse/rps"))
            .inScenario("retry")
            .whenScenarioStateIs("success")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successJson())));

        ResultadoEmissao resultado = buildAdapter().emitir(dadosTeste());

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.numeroNota()).isEqualTo("NF-2026-001");
        wireMock.verify(3, postRequestedFor(urlEqualTo("/nfse/rps")));
    }

    // --- Critério 4: circuit breaker abre após 5 falhas consecutivas ---

    @Test
    void circuitBreaker_abreApos5FalhasConsecutivas() {
        wireMock.stubFor(post(urlEqualTo("/nfse/rps"))
            .willReturn(aResponse().withStatus(503)));

        var adapter = buildAdapter();

        // 5 chamadas — cada uma esgota 3 tentativas → CB registra 5 falhas
        for (int i = 0; i < 5; i++) {
            ResultadoEmissao r = adapter.emitir(dadosTeste());
            assertThat(r.aguardandoEmissaoManual()).isTrue();
        }

        // 6ª chamada: CB está ABERTO → bloqueada sem atingir o WireMock
        ResultadoEmissao bloqueada = adapter.emitir(dadosTeste());
        assertThat(bloqueada.aguardandoEmissaoManual()).isTrue();
        assertThat(bloqueada.mensagemErro()).contains("circuit breaker");

        // Confirma: WireMock recebeu exatamente 15 requests (5 chamadas × 3 tentativas)
        wireMock.verify(15, postRequestedFor(urlEqualTo("/nfse/rps")));
    }

    // --- Critério 2: timeout de 30s respeitado (adapter configurado com 1s) ---

    @Test
    void timeout_excedido_retornaAguardandoManual() {
        wireMock.stubFor(post(urlEqualTo("/nfse/rps"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(2000))); // 2s de delay > 1s de timeout

        ResultadoEmissao resultado = buildAdapter().emitir(dadosTeste());

        assertThat(resultado.aguardandoEmissaoManual()).isTrue();
        assertThat(resultado.sucesso()).isFalse();
    }

    // --- Integração desabilitada retorna aguardandoManual sem chamar o serviço ---

    @Test
    void desabilitado_retornaAguardandoManual_semChamarAgregador() {
        props.setEnabled(false);

        ResultadoEmissao resultado = buildAdapter().emitir(dadosTeste());

        assertThat(resultado.aguardandoEmissaoManual()).isTrue();
        wireMock.verify(0, postRequestedFor(urlEqualTo("/nfse/rps")));
    }
}
