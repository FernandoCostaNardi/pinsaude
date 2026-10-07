package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.config.KeycloakAdminProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        // Só UPDATE_PASSWORD: o médico define a senha pelo link enviado ao próprio e-mail
        // (reset-credentials), o que já prova a posse do endereço. VERIFY_EMAIL pendente faz o
        // login ROPC do frontend falhar com "Account is not fully set up" mesmo com senha válida.
        body.put("requiredActions", List.of("UPDATE_PASSWORD"));
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

    /**
     * Procura um usuário pelo e-mail exato. Usado antes de criar a conta do médico na
     * ativação: a conta pode já existir, criada à parte pela tela de Usuários
     * (services/gestao) ou por uma candidatura pública anterior — criar de novo faria o
     * Keycloak responder 409 (e-mail duplicado).
     */
    public Optional<String> findUserIdByEmail(String email) {
        List<Map<String, Object>> encontrados = restClient.get()
            .uri(adminUrl("/users?exact=true&email="
                + URLEncoder.encode(email.toLowerCase(), StandardCharsets.UTF_8)))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        if (encontrados == null || encontrados.isEmpty()) return Optional.empty();
        return Optional.ofNullable((String) encontrados.get(0).get("id"));
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

    /**
     * Habilita/desabilita o usuário. Ao habilitar, também marca o e-mail como verificado e
     * remove VERIFY_EMAIL das required actions — contas criadas antes desta correção nasceram
     * com [UPDATE_PASSWORD, VERIFY_EMAIL], e o VERIFY_EMAIL pendente bloqueia o login ROPC do
     * frontend mesmo depois de o médico definir a senha. Faz GET da representação completa e
     * reenvia tudo no PUT (mesmo motivo de updateUserAttributeCnpjId: não zerar campos do perfil).
     */
    public void updateUserEnabled(String userId, boolean enabled) {
        Map<String, Object> atual = restClient.get()
            .uri(adminUrl("/users/" + userId))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        Map<String, Object> body = new LinkedHashMap<>(atual != null ? atual : Map.of());
        body.put("enabled", enabled);
        if (enabled) {
            body.put("emailVerified", true);
            Object acoes = body.get("requiredActions");
            if (acoes instanceof List<?> lista) {
                body.put("requiredActions", lista.stream()
                    .filter(a -> !"VERIFY_EMAIL".equals(a))
                    .toList());
            }
        }

        restClient.put()
            .uri(adminUrl("/users/" + userId))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * Seta o atributo cnpj_id do usuário — necessário para que o médico apareça na tela de
     * Usuários (services/gestao). Médicos de auto-cadastro nascem sem cnpj_id (sem vínculo
     * empresa ainda); sincronizado quando o primeiro vínculo é atribuído
     * (MedicoService.adicionarVinculo).
     *
     * ⚠️ Faz GET da representação COMPLETA do usuário e reenvia tudo no PUT, só sobrescrevendo
     * "attributes" — o realm tem User Profile habilitado (Keycloak 24) e cnpj_id está em
     * userProfileConfig.attributes. Um PUT contendo SÓ "attributes" é tratado como submissão
     * completa do formulário de perfil e ZERA qualquer campo do perfil ausente do corpo —
     * confirmado empiricamente que isso inclui firstName/lastName **e também email** (uma
     * primeira versão deste método só reenviava firstName/lastName e ainda assim zerou o
     * email de dois usuários reais). updateUserEnabled também reenvia a representação
     * completa, pelo mesmo cuidado. Copiar a representação inteira (em vez de
     * escolher campos a dedo) evita essa classe de bug se o Keycloak um dia gerenciar mais
     * campos do perfil.
     */
    public void updateUserAttributeCnpjId(String userId, String cnpjId) {
        Map<String, Object> atual = restClient.get()
            .uri(adminUrl("/users/" + userId))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        Map<String, Object> body = new LinkedHashMap<>(atual != null ? atual : Map.of());
        body.put("attributes", Map.of("cnpj_id", List.of(cnpjId)));

        restClient.put()
            .uri(adminUrl("/users/" + userId))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }
}
