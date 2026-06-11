package br.com.pinsaude.gestao.service;

import br.com.pinsaude.gestao.dto.ConviteRequest;
import br.com.pinsaude.gestao.dto.UsuarioDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private static final Set<String> PERFIS_VALIDOS = Set.of(
        "medico", "operacao", "financeiro", "contabil", "gestao"
    );

    private final KeycloakAdminService keycloak;

    public UsuarioService(KeycloakAdminService keycloak) {
        this.keycloak = keycloak;
    }

    public List<UsuarioDto> listar(String cnpjId) {
        List<Map<String, Object>> users = keycloak.listUsers(cnpjId);
        if (users == null) return List.of();
        return users.stream().map(this::toDto).toList();
    }

    public UsuarioDto convidar(ConviteRequest request, String cnpjId) {
        if (!PERFIS_VALIDOS.contains(request.perfil())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido: " + request.perfil());
        }
        String userId = keycloak.createUser(request.email(), request.nome(), cnpjId);
        keycloak.assignRole(userId, request.perfil());
        try {
            keycloak.sendInvitationEmail(userId);
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail de convite para userId={}: {}", userId, e.getMessage());
        }
        Map<String, Object> user = keycloak.getUser(userId);
        return toDto(user);
    }

    public UsuarioDto alterarPerfil(String userId, String novoPerfil) {
        if (!PERFIS_VALIDOS.contains(novoPerfil)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido: " + novoPerfil);
        }
        List<String> rolesAtuais = keycloak.getUserRealmRoles(userId);
        for (String role : rolesAtuais) {
            keycloak.removeRole(userId, role);
        }
        keycloak.assignRole(userId, novoPerfil);
        Map<String, Object> user = keycloak.getUser(userId);
        return toDto(user);
    }

    public UsuarioDto alterarStatus(String userId, boolean ativo) {
        keycloak.updateUserEnabled(userId, ativo);
        Map<String, Object> user = keycloak.getUser(userId);
        return toDto(user);
    }

    public void reenviarConvite(String userId) {
        keycloak.sendInvitationEmail(userId);
    }

    @SuppressWarnings("unchecked")
    private UsuarioDto toDto(Map<String, Object> user) {
        String id = (String) user.get("id");
        String email = (String) user.getOrDefault("email", "");
        String firstName = (String) user.getOrDefault("firstName", "");
        String lastName = (String) user.getOrDefault("lastName", "");
        String nome = (firstName + " " + lastName).trim();
        boolean ativo = Boolean.TRUE.equals(user.get("enabled"));

        List<String> requiredActions = (List<String>) user.getOrDefault("requiredActions", List.of());
        boolean pendente = !requiredActions.isEmpty();

        List<String> roles = keycloak.getUserRealmRoles(id);
        String perfil = roles.isEmpty() ? "" : roles.get(0);

        return new UsuarioDto(id, email, nome, perfil, ativo, pendente);
    }
}
