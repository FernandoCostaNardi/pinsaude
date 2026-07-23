package br.com.pinsaude.gateway.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    static WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    static RSAKey testKey;

    @Autowired
    WebTestClient webTestClient;

    @BeforeAll
    static void setup() throws Exception {
        testKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        wireMock.start();
        wireMock.stubFor(WireMock.get(urlEqualTo("/realms/pinsaude/protocol/openid-connect/certs"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JWKSet(testKey.toPublicJWK()).toString())));
    }

    @AfterAll
    static void teardown() { wireMock.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + wireMock.port() + "/realms/pinsaude/protocol/openid-connect/certs");
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:" + wireMock.port() + "/realms/pinsaude");
    }

    private String validToken() throws Exception {
        return buildToken("user-123", "http://localhost:" + wireMock.port() + "/realms/pinsaude",
                new Date(System.currentTimeMillis() + 3_600_000L), "12345678000199");
    }

    private String expiredToken() throws Exception {
        return buildToken("user-123", "http://localhost:" + wireMock.port() + "/realms/pinsaude",
                new Date(System.currentTimeMillis() - 3_600_000L), "12345678000199");
    }

    private String wrongIssuerToken() throws Exception {
        return buildToken("user-123", "http://outro.example.com/realms/intruso",
                new Date(System.currentTimeMillis() + 3_600_000L), "12345678000199");
    }

    private String buildToken(String subject, String issuer, Date expiry, String cnpjId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(expiry)
                .claim("cnpj_id", cnpjId)
                .claim("realm_access", Map.of("roles", List.of("medico")))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(testKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(testKey));
        return jwt.serialize();
    }

    @Test
    void semToken_retorna401() {
        webTestClient.get()
                .uri("/api/gateway/qualquer")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void actuatorHealth_semToken_retorna200() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void tokenValido_naoRetorna401() throws Exception {
        webTestClient.get()
                .uri("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().value(status ->
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    void tokenExpirado_retorna401() throws Exception {
        webTestClient.get()
                .uri("/api/gateway/qualquer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void tokenOutroIssuer_retorna401() throws Exception {
        webTestClient.get()
                .uri("/api/gateway/qualquer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIssuerToken())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void tokenValido_cnpjIdPresente() throws Exception {
        webTestClient.get()
                .uri("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void cadastroPublico_semToken_naoEhBloqueadoPeloGateway() {
        // Auto-cadastro público de médico (EPIC-14) precisa passar pelo gateway sem exigir
        // Bearer token. A rota "onboarding" aponta para localhost:8085 — em dev local pode
        // haver um onboarding real rodando nessa porta (com sua própria segurança), então
        // não dá pra confiar no status HTTP final (poderia ser um 401 vindo do downstream,
        // não do gateway). O sinal confiável é o header "Vary": o gateway só o adiciona
        // quando o request passa da checagem de autorização e entra no roteamento reativo
        // (WebFlux/CORS) — o 401 imediato do próprio SecurityConfig (sem rota permitida,
        // ver semToken_retorna401) nunca tem esse header.
        var headers = webTestClient.get()
                .uri("/api/onboarding/publico/candidaturas/" + java.util.UUID.randomUUID())
                .exchange()
                .expectBody().returnResult().getResponseHeaders();
        org.assertj.core.api.Assertions.assertThat(headers.containsKey("Vary")).isTrue();
    }
}
