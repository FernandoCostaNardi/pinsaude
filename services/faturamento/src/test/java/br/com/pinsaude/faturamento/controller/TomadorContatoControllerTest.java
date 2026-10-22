package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.dto.TomadorContatoResponse;
import br.com.pinsaude.faturamento.service.TomadorContatoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TomadorContatoController.class)
@Import(SecurityConfig.class)
class TomadorContatoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TomadorContatoService service;

    private static final SimpleGrantedAuthority PERM_TOMADORES = new SimpleGrantedAuthority("ROLE_perm_tomadores");

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "financeiro", "contabil"})
    void listar_papelLegado_retorna200(String role) throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listar(id)).thenReturn(List.of());
        mockMvc.perform(get("/api/tomadores/{id}/contatos", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_medico_retorna403() throws Exception {
        mockMvc.perform(get("/api/tomadores/{id}/contatos", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void criar_permTomadores_retorna201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.criar(eq(id), any())).thenReturn(
            new TomadorContatoResponse(UUID.randomUUID(), id, "Financeiro", "Fernando", "f@x.com", null, null));
        mockMvc.perform(post("/api/tomadores/{id}/contatos", id)
                        .with(jwt().authorities(PERM_TOMADORES))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apelido\":\"Financeiro\",\"nome\":\"Fernando\",\"email\":\"f@x.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apelido").value("Financeiro"));
    }

    @Test
    void criar_semApelido_retorna400() throws Exception {
        mockMvc.perform(post("/api/tomadores/{id}/contatos", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apelido\":\" \",\"email\":\"f@x.com\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).criar(any(), any());
    }

    @Test
    void criar_emailInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/api/tomadores/{id}/contatos", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apelido\":\"Financeiro\",\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_financeiro_retorna403() throws Exception {
        mockMvc.perform(post("/api/tomadores/{id}/contatos", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apelido\":\"Financeiro\",\"email\":\"f@x.com\"}"))
                .andExpect(status().isForbidden());
    }
}
