package br.com.pinsaude.faturamento.adapter;

import br.com.pinsaude.faturamento.port.MotorFiscalPort.MotorFiscalException;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.PedidoCalculo;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.ResultadoCalculo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CBS-30 — contrato HTTP com o motor fiscal: rota, ?cnpj=, token do usuário e mapeamento de erros. */
class MotorFiscalAdapterTest {

    HttpServer server;
    MotorFiscalAdapter adapter;
    final AtomicReference<String> uri = new AtomicReference<>();
    final AtomicReference<String> auth = new AtomicReference<>();
    final AtomicReference<String> corpo = new AtomicReference<>();
    volatile int status = 200;
    volatile String resposta = "{}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            uri.set(ex.getRequestURI().toString());
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            corpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = resposta.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        adapter = new MotorFiscalAdapter(RestClient.builder(),
            "http://127.0.0.1:" + server.getAddress().getPort());

        Jwt jwt = Jwt.withTokenValue("tok-123").header("alg", "none").subject("u")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    private PedidoCalculo pedido() {
        return new PedidoCalculo(1_000_000L, "2027-01", true, true, false, false,
            Map.of("ISS", new BigDecimal("0.05")), new BigDecimal("0.6"), "200", "200029", null);
    }

    @Test
    void chamaOMotorComTokenECnpjELeAResposta() {
        resposta = """
            {"valorBruto":1000000,"taxaPin":150000,"valorIss":50000,"valorIr":15000,"valorCsll":10000,
             "valorPis":0,"valorCofins":0,"totalRetencoes":25000,"totalTributos":132000,"valorLiquidoMedico":850000,
             "resultadoPin":18000,"cenario":"A","competencia":"2027-01","auditoria":{"x":1},"regime":"IBS_CBS",
             "valorCbs":43000,"valorIbs":14000,"aliqCbs":0.0448,"aliqIbs":0.0004,"fatorIss":1,
             "cstIbsCbs":"200","cClassTrib":"200029","cbsIbsPorFora":false,"baseCalculoIbsCbs":960000,
             "valorTotalNota":1000000,"tributosRetidos":["IR","CSLL"]}
            """;

        ResultadoCalculo r = adapter.calcular("12345678000190", pedido());

        assertThat(uri.get()).isEqualTo("/api/motor-fiscal/calcular?cnpj=12345678000190");
        assertThat(auth.get()).isEqualTo("Bearer tok-123");
        assertThat(corpo.get()).contains("\"competencia\":\"2027-01\"").contains("\"reducaoIbsCbs\":0.6")
            .contains("\"cClassTrib\":\"200029\"").contains("\"aliquotasOverride\":{\"ISS\":0.05}");
        assertThat(r.regime()).isEqualTo("IBS_CBS");
        assertThat(r.valorCbs()).isEqualTo(43_000L);
        assertThat(r.aliqCbs()).isEqualByComparingTo("0.0448");
        assertThat(r.tributosRetidos()).containsExactly("IR", "CSLL");
    }

    @Test
    void recusaDoFiscalRepassaStatusEMensagem() {
        status = 422;
        resposta = "{\"status\":422,\"message\":\"Cronograma da reforma não tem o ano 2034\"}";

        assertThatThrownBy(() -> adapter.calcular("12345678000190", pedido()))
            .isInstanceOf(MotorFiscalException.class)
            .satisfies(e -> {
                MotorFiscalException m = (MotorFiscalException) e;
                assertThat(m.status()).isEqualTo(422);
                assertThat(m.getMessage()).isEqualTo("Cronograma da reforma não tem o ano 2034");
            });
    }

    @Test
    void fiscalForaDoArVira503() {
        server.stop(0);
        assertThatThrownBy(() -> adapter.calcular(null, pedido()))
            .isInstanceOf(MotorFiscalException.class)
            .satisfies(e -> assertThat(((MotorFiscalException) e).status()).isEqualTo(503));
    }
}
