package br.com.pinsaude.gestao.service;

import br.com.pinsaude.gestao.config.KeycloakAdminProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@Service
public class KeycloakAdminService {

    private static final Set<String> PERFIS_NEGOCIO = Set.of(
        "medico", "operacao", "financeiro", "contabil", "gestao"
    );

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

    public List<Map<String, Object>> listUsers(String cnpjId) {
        URI uri = UriComponentsBuilder
            .fromHttpUrl(adminUrl("/users"))
            .queryParam("q", "cnpj_id:{v}")
            .queryParam("max", 200)
            .queryParam("briefRepresentation", false)
            .buildAndExpand(cnpjId)
            .encode()
            .toUri();
        return restClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    public List<String> getUserRealmRoles(String userId) {
        List<Map<String, Object>> roles = restClient.get()
            .uri(adminUrl("/users/" + userId + "/role-mappings/realm"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (roles == null) return Collections.emptyList();
        return roles.stream()
            .map(r -> (String) r.get("name"))
            .filter(PERFIS_NEGOCIO::contains)
            .toList();
    }

    public Map<String, Object> getRoleByName(String roleName) {
        return restClient.get()
            .uri(adminUrl("/roles/" + roleName))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    public String createUser(String email, String nome, String cnpjId) {
        String[] parts = nome.trim().split("\\s+", 2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", email.toLowerCase());
        body.put("email", email.toLowerCase());
        body.put("firstName", parts[0]);
        body.put("lastName", parts.length > 1 ? parts[1] : "");
        body.put("enabled", true);
        body.put("emailVerified", false);
        body.put("requiredActions", List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        body.put("attributes", Map.of("cnpj_id", List.of(cnpjId)));

        var response = restClient.post()
            .uri(adminUrl("/users"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (location == null) throw new IllegalStateException("Keycloak did not return Location header");
        return location.substring(location.lastIndexOf('/') + 1);
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

    public void removeRole(String userId, String roleName) {
        Map<String, Object> role = getRoleByName(roleName);
        restClient.method(HttpMethod.DELETE)
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

    public void sendInvitationEmail(String userId) {
        restClient.put()
            .uri(adminUrl("/users/" + userId + "/execute-actions-email"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"))
            .retrieve()
            .toBodilessEntity();
    }

    public Map<String, Object> getUser(String userId) {
        return restClient.get()
            .uri(adminUrl("/users/" + userId))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }
}
