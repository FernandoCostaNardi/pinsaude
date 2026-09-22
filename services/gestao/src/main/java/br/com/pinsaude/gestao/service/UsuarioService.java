package br.com.pinsaude.gestao.service;

import br.com.pinsaude.gestao.dto.ConviteRequest;
import br.com.pinsaude.gestao.dto.UsuarioDto;
import br.com.pinsaude.gestao.repository.PerfilCustomizadoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UsuarioService {

    private static final Set<String> PERFIS_VALIDOS = Set.of(
        "medico", "operacao", "financeiro", "contabil", "gestao"
    );

    private final KeycloakAdminService keycloak;
    private final PerfilCustomizadoRepository perfilRepo;

    public UsuarioService(KeycloakAdminService keycloak, PerfilCustomizadoRepository perfilRepo) {
        this.keycloak = keycloak;
        this.perfilRepo = perfilRepo;
    }

    public List<UsuarioDto> listar() {
        List<Map<String, Object>> users = keycloak.listAllUsers();
        if (users == null) return List.of();
        return users.stream()
            .map(this::toDto)
            // Exclui contas sem nenhuma role de negócio (ex.: service-account do client
            // pinsaude-gateway) — não são usuários geridos por esta tela.
            .filter(u -> !u.perfil().isBlank())
            .toList();
    }

    public UsuarioDto convidar(ConviteRequest request, String cnpjId) {
        if (!perfilValido(request.perfil())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido: " + request.perfil());
        }
        String userId = keycloak.createUser(request.email(), request.nome(), cnpjId);
        keycloak.assignRole(userId, request.perfil());
        keycloak.sendInvitationEmail(userId);
        Map<String, Object> user = keycloak.getUser(userId);
        return toDto(user);
    }

    public UsuarioDto alterarPerfil(String userId, String novoPerfil) {
        if (!perfilValido(novoPerfil)) {
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

    /** Aceita os 5 papéis legados ou o keycloak_role_name de qualquer perfil customizado (PERFIL-04). */
    private boolean perfilValido(String perfil) {
        return PERFIS_VALIDOS.contains(perfil) || perfilRepo.findByKeycloakRoleName(perfil).isPresent();
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
