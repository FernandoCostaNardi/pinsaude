package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.service.TomadorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em TomadorController.
 * Um endpoint representativo por cada um dos 3 grupos distintos de @PreAuthorize (PERFIL-12).
 */
@WebMvcTest(TomadorController.class)
@Import(SecurityConfig.class)
class TomadorControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TomadorService service;

    private static final SimpleGrantedAuthority PERM_TOMADORES = new SimpleGrantedAuthority("ROLE_perm_tomadores");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_producao");

    // ─── Grupo 1: leitura ampla (operacao,gestao,financeiro,contabil,medico) — GET / ──

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "financeiro", "contabil", "medico"})
    void buscar_papelLegado_retorna200(String role) throws Exception {
        when(service.buscar(any(), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/tomadores")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void buscar_permTomadores_retorna200() throws Exception {
        when(service.buscar(any(), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/tomadores").with(jwt().authorities(PERM_TOMADORES)))
                .andExpect(status().isOk());
    }

    @Test
    void buscar_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/tomadores").with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 2: leitura restrita (operacao,gestao,financeiro,contabil) — GET /{id}/aliquotas ──

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "financeiro", "contabil"})
    void listarAliquotas_papelLegado_retorna200(String role) throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listarAliquotas(id)).thenReturn(List.of());
        mockMvc.perform(get("/api/tomadores/{id}/aliquotas", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listarAliquotas_permTomadores_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listarAliquotas(id)).thenReturn(List.of());
        mockMvc.perform(get("/api/tomadores/{id}/aliquotas", id).with(jwt().authorities(PERM_TOMADORES)))
                .andExpect(status().isOk());
    }

    @Test
    void listarAliquotas_medico_retorna403() throws Exception {
        // Diferente do grupo 1 (leitura ampla), aliquotas nunca liberou 'medico'.
        mockMvc.perform(get("/api/tomadores/{id}/aliquotas", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 3: escrita (operacao,gestao) — DELETE /{id} ─────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao"})
    void deletar_papelLegado_retorna204(String role) throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(service).deletar(id);
        mockMvc.perform(delete("/api/tomadores/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletar_permTomadores_retorna204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(service).deletar(id);
        mockMvc.perform(delete("/api/tomadores/{id}", id).with(jwt().authorities(PERM_TOMADORES)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletar_financeiro_retorna403() throws Exception {
        mockMvc.perform(delete("/api/tomadores/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    // ─── Sem autenticação ───────────────────────────────────────────────────────

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/tomadores"))
                .andExpect(status().isUnauthorized());
    }
}
