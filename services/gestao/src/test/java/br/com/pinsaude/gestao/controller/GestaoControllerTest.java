package br.com.pinsaude.gestao.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em GestaoController.
 * Único grupo (classe): gestao sozinho (PERFIL-21).
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
class GestaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void gestao_podeAcessar_retorna200() throws Exception {
        mockMvc.perform(get("/api/gestao")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk());
    }

    @Test
    void permGestao_podeAcessar_retorna200() throws Exception {
        mockMvc.perform(get("/api/gestao")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_perm_gestao"))))
            .andExpect(status().isOk());
    }

    @Test
    void operacao_naoPodeAcessar_retorna403() throws Exception {
        mockMvc.perform(get("/api/gestao")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void permOutroDominio_naoPodeAcessar_retorna403() throws Exception {
        mockMvc.perform(get("/api/gestao")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_perm_usuarios"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/gestao"))
            .andExpect(status().isUnauthorized());
    }
}
