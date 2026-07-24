package br.com.pinsaude.gestao.service;

import br.com.pinsaude.gestao.dto.UsuarioDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock KeycloakAdminService keycloak;

    @InjectMocks UsuarioService service;

    private Map<String, Object> usuario(String id, String email, boolean enabled) {
        return Map.of(
            "id", id, "email", email, "firstName", "Nome", "lastName", "Sobrenome",
            "enabled", enabled, "requiredActions", List.of()
        );
    }

    @Test
    void listar_usuarioComRoleDeNegocio_apareceNaLista() {
        when(keycloak.listAllUsers()).thenReturn(List.of(usuario("u1", "op@pinsaude.com.br", true)));
        when(keycloak.getUserRealmRoles("u1")).thenReturn(List.of("operacao"));

        List<UsuarioDto> resp = service.listar();

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).perfil()).isEqualTo("operacao");
    }

    @Test
    void listar_contaSemRoleDeNegocio_naoAparece() {
        // ex.: service-account do client pinsaude-gateway — não tem nenhuma role de negócio
        when(keycloak.listAllUsers()).thenReturn(List.of(
            usuario("u1", "op@pinsaude.com.br", true),
            usuario("sa-gateway", "service-account-pinsaude-gateway", true)
        ));
        when(keycloak.getUserRealmRoles("u1")).thenReturn(List.of("operacao"));
        when(keycloak.getUserRealmRoles("sa-gateway")).thenReturn(List.of());

        List<UsuarioDto> resp = service.listar();

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).id()).isEqualTo("u1");
    }

    @Test
    void listar_keycloakRetornaNull_naoLancaExcecao() {
        when(keycloak.listAllUsers()).thenReturn(null);

        List<UsuarioDto> resp = service.listar();

        assertThat(resp).isEmpty();
    }
}
