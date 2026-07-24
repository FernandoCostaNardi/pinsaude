package br.com.pinsaude.gestao.controller;

import br.com.pinsaude.gestao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UsuarioService usuarioService;

    private static final org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor gestaoJwt =
        jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))
             .jwt(j -> j.claim("cnpj_id", "11.222.333/0001-44"));

    @Test
    void keycloak409_retornaMensagemEmailJaCadastrado() throws Exception {
        when(usuarioService.listar())
            .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null));

        mockMvc.perform(get("/api/usuarios").with(gestaoJwt))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.mensagem").value("Já existe um usuário com este e-mail cadastrado."));
    }

    @Test
    void keycloakIndisponivel_retornaMensagemAmigavel() throws Exception {
        when(usuarioService.listar())
            .thenThrow(new ResourceAccessException("Connection refused", new ConnectException()));

        mockMvc.perform(get("/api/usuarios").with(gestaoJwt))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.mensagem").value(
                "Não foi possível conectar ao servidor de identidade. Verifique se o Keycloak está em execução."));
    }

    @Test
    void keycloak401_retornaMensagemAutenticacao() throws Exception {
        when(usuarioService.listar())
            .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        mockMvc.perform(get("/api/usuarios").with(gestaoJwt))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.mensagem").value(
                "Falha na autenticação com o servidor de identidade. Verifique as credenciais do admin."));
    }
}
