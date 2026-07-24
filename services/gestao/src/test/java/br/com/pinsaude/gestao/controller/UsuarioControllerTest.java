package br.com.pinsaude.gestao.controller;

import br.com.pinsaude.gestao.dto.ConviteRequest;
import br.com.pinsaude.gestao.dto.UsuarioDto;
import br.com.pinsaude.gestao.service.UsuarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy",
    "keycloak.admin.server-url=http://localhost:9999",
    "keycloak.admin.realm=pinsaude",
    "keycloak.admin.username=admin",
    "keycloak.admin.password=admin"
})
@AutoConfigureMockMvc
class UsuarioControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean UsuarioService usuarioService;

    private static final UsuarioDto USUARIO_FIXTURE = new UsuarioDto(
        "uuid-123", "novo@pinsaude.com.br", "Novo Usuario", "operacao", true, true
    );

    @Test
    void gestao_podeListarUsuarios() throws Exception {
        when(usuarioService.listar()).thenReturn(List.of(USUARIO_FIXTURE));

        mockMvc.perform(get("/api/usuarios")
                .with(jwt()
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))
                    .jwt(j -> j.claim("cnpj_id", "12.345.678/0001-90"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("novo@pinsaude.com.br"))
            .andExpect(jsonPath("$[0].perfil").value("operacao"));
    }

    @Test
    void semToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilOperacao_naoPodeListarUsuarios_retorna403() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void perfilFinanceiro_naoPodeListarUsuarios_retorna403() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void perfilMedico_naoPodeListarUsuarios_retorna403() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void gestao_podeConvidarUsuario() throws Exception {
        when(usuarioService.convidar(any(), anyString())).thenReturn(USUARIO_FIXTURE);

        ConviteRequest request = new ConviteRequest("novo@pinsaude.com.br", "Novo Usuario", "operacao");

        mockMvc.perform(post("/api/usuarios")
                .with(jwt()
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))
                    .jwt(j -> j.claim("cnpj_id", "12.345.678/0001-90")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("novo@pinsaude.com.br"));
    }

    @Test
    void gestao_semCnpjId_aindaAssimListaTodosOsUsuarios() throws Exception {
        // gestão é o papel cross-tenant do sistema — listar usuários nunca deve depender do
        // cnpj_id de quem está logado (ver KeycloakAdminService.listAllUsers).
        when(usuarioService.listar()).thenReturn(List.of(USUARIO_FIXTURE));

        mockMvc.perform(get("/api/usuarios")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("novo@pinsaude.com.br"));
    }

    @Test
    void gestao_podeReenviarConvite() throws Exception {
        mockMvc.perform(post("/api/usuarios/uuid-123/reenviar-convite")
                .with(jwt()
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))
                    .jwt(j -> j.claim("cnpj_id", "12.345.678/0001-90"))))
            .andExpect(status().isNoContent());
    }
}
