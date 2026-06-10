package br.com.pinsaude.faturamento.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
class RbacIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void perfil_medico_naoPodeAcessar_faturamento_retorna403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/faturamento")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfil_financeiro_naoPodeAcessar_faturamento_retorna403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/faturamento")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfil_contabil_naoPodeAcessar_faturamento_retorna403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/faturamento")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_contabil"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfil_operacao_podeAcessar_faturamento_retorna200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/faturamento")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isOk());
    }

    @Test
    void perfil_gestao_podeAcessar_faturamento_retorna200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/faturamento")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isOk());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/faturamento"))
                .andExpect(status().isUnauthorized());
    }
}
