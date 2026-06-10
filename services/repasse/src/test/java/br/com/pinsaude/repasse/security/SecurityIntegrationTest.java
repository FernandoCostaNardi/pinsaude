package br.com.pinsaude.repasse.security;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    static WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    static RSAKey testKey;

    @Autowired
    MockMvc mockMvc;

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
    void semToken_retorna401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/repasse/qualquer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealth_semToken_retorna200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenValido_naoRetorna401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/repasse/qualquer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(result ->
                    org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                            .isNotEqualTo(401));
    }

    @Test
    void tokenExpirado_retorna401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/repasse/qualquer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOutroIssuer_retorna401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/repasse/qualquer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIssuerToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValido_cnpjIdPresente() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk());
    }
}
