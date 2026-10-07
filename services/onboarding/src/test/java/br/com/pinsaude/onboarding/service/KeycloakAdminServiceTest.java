package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.config.KeycloakAdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminServiceTest {

    private static final String KC = "http://kc.test";

    private MockRestServiceServer server;
    private KeycloakAdminService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new KeycloakAdminService(builder,
            new KeycloakAdminProperties(KC, "pinsaude", "admin", "secret"));

        server.expect(requestTo(KC + "/realms/master/protocol/openid-connect/token"))
            .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":300}",
                MediaType.APPLICATION_JSON));
    }

    @Test
    void findUserIdByEmail_codificaArrobaUmaUnicaVez_eEncontraConta() {
        // Antes: "%2540" (dupla codificação) → Keycloak nunca achava a conta existente
        server.expect(requestTo(KC + "/admin/realms/pinsaude/users?exact=true&email=fulano%40hotmail.com"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-123\"}]", MediaType.APPLICATION_JSON));

        assertThat(service.findUserIdByEmail("Fulano@Hotmail.com")).contains("kc-123");
        server.verify();
    }

    @Test
    void findUserIdByEmail_nenhumResultado_retornaVazio() {
        server.expect(requestTo(KC + "/admin/realms/pinsaude/users?exact=true&email=ninguem%40gmail.com"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(service.findUserIdByEmail("ninguem@gmail.com")).isEmpty();
        server.verify();
    }
}
