package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.config.KeycloakAdminProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cliente da Admin API do Keycloak duplicado dentro do onboarding — mesmo padrão já usado
 * em services/gestao/.../KeycloakAdminService.java, evitando acoplamento cross-service para
 * uma chamada tão simples (mesma convenção de duplicar records/serviços simples entre
 * serviços já usada no projeto, ex. EmailEnvioMessage).
 *
 * Só implementa o subconjunto de métodos que o auto-cadastro público (EPIC-14.4) precisa:
 * criar o usuário desabilitado ao finalizar a candidatura, e habilitar + atribuir role
 * quando o médico é aprovado/ativado. Não duplica listUsers/removeRole/sendInvitationEmail/
 * getUser do gestao, que não são usados aqui.
 */
@Service
public class KeycloakAdminService {

    private final RestClient restClient;
    private final KeycloakAdminProperties props;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public KeycloakAdminService(RestClient.Builder builder, KeycloakAdminProperties props) {
        this.restClient = builder.build();
        this.props = props;
    }

    private synchronized String adminToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        String tokenUrl = props.serverUrl() + "/realms/master/protocol/openid-connect/token";
        Map<?, ?> response = restClient.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=password&client_id=admin-cli&username="
                + props.username() + "&password=" + props.password())
            .retrieve()
            .body(Map.class);
        cachedToken = (String) Objects.requireNonNull(response).get("access_token");
        int expiresIn = (int) response.get("expires_in");
        tokenExpiry = Instant.now().plusSeconds(expiresIn - 30L);
        return cachedToken;
    }

    private String adminUrl(String path) {
        return props.serverUrl() + "/admin/realms/" + props.realm() + path;
    }

    /**
     * Cria o usuário Keycloak do médico já DESABILITADO (enabled=false) — diferente do
     * padrão do gestao (sempre enabled=true) — e sem role atribuída ainda. O acesso só é
     * liberado depois, quando o médico é aprovado (ver MedicoService.ativar /
     * verificarAtivacaoAutomatica). cnpjId é opcional (pode ser null/vazio): o portal do
     * médico resolve o usuário por e-mail, não depende de tenant/cnpj_id.
     */
    public String createUserDesabilitado(String email, String nome, String cnpjId) {
        String[] partes = nome.trim().split("\\s+", 2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", email.toLowerCase());
        body.put("email", email.toLowerCase());
        body.put("firstName", partes[0]);
        body.put("lastName", partes.length > 1 ? partes[1] : "");
        body.put("enabled", false);
        body.put("emailVerified", false);
        body.put("requiredActions", List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        if (cnpjId != null && !cnpjId.isBlank()) {
            body.put("attributes", Map.of("cnpj_id", List.of(cnpjId)));
        }

        var response = restClient.post()
            .uri(adminUrl("/users"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (location == null) throw new IllegalStateException("Keycloak não retornou header Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    public Map<String, Object> getRoleByName(String roleName) {
        return restClient.get()
            .uri(adminUrl("/roles/" + roleName))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    public void assignRole(String userId, String roleName) {
        Map<String, Object> role = getRoleByName(roleName);
        restClient.post()
            .uri(adminUrl("/users/" + userId + "/role-mappings/realm"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(List.of(role))
            .retrieve()
            .toBodilessEntity();
    }

    public void updateUserEnabled(String userId, boolean enabled) {
        restClient.put()
            .uri(adminUrl("/users/" + userId))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("enabled", enabled))
            .retrieve()
            .toBodilessEntity();
    }
}
