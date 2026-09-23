package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.NotaFiscalStatusResponse;
import br.com.pinsaude.fiscal.service.NfseService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em NfseController.
 * Um endpoint representativo por cada um dos 6 grupos distintos de @PreAuthorize (PERFIL-18).
 */
@WebMvcTest(NfseController.class)
@Import(SecurityConfig.class)
class NfseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    NfseService service;

    private static final SimpleGrantedAuthority PERM_NOTAS = new SimpleGrantedAuthority("ROLE_perm_notas");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_notas_lote");

    // ─── Grupo 1: contabil,gestao,operacao,financeiro — GET / (listar) ─────────

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "operacao", "financeiro"})
    void listar_papelLegado_retorna200(String role) throws Exception {
        when(service.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_permNotas_retorna200() throws Exception {
        when(service.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse").with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isOk());
    }

    @Test
    void listar_medico_retorna403() throws Exception {
        mockMvc.perform(get("/api/nfse")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 2: +medico — GET /status/{producaoId} ───────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "operacao", "financeiro", "medico"})
    void getStatus_papelLegado_retorna200(String role) throws Exception {
        UUID producaoId = UUID.randomUUID();
        when(service.getStatus(producaoId)).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(get("/api/nfse/status/{producaoId}", producaoId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void getStatus_permNotas_retorna200() throws Exception {
        UUID producaoId = UUID.randomUUID();
        when(service.getStatus(producaoId)).thenReturn(mock(NotaFiscalStatusResponse.class));
        mockMvc.perform(get("/api/nfse/status/{producaoId}", producaoId).with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isOk());
    }

    @Test
    void getStatus_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/nfse/status/{producaoId}", UUID.randomUUID()).with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 3: gestao,contabil — POST /{notaId}/aprovar ─────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil"})
    void aprovar_papelLegado_retorna204(String role) throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).aprovar(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/aprovar", notaId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void aprovar_permNotas_retorna204() throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).aprovar(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/aprovar", notaId).with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aprovar_operacao_retorna403() throws Exception {
        // Diferente dos grupos 1/2, operacao/financeiro nunca tiveram acesso a aprovar.
        mockMvc.perform(post("/api/nfse/{notaId}/aprovar", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 4: gestao,contabil,financeiro — PUT /{notaId}/cancelar ──────────

    private static final String MOTIVO_BODY = "{\"motivo\":\"Nota emitida com valor incorreto\"}";

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "financeiro"})
    void cancelar_papelLegado_retorna204(String role) throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).cancelar(any(), any());
        mockMvc.perform(put("/api/nfse/{notaId}/cancelar", notaId)
                        .contentType(MediaType.APPLICATION_JSON).content(MOTIVO_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelar_permNotas_retorna204() throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).cancelar(any(), any());
        mockMvc.perform(put("/api/nfse/{notaId}/cancelar", notaId)
                        .contentType(MediaType.APPLICATION_JSON).content(MOTIVO_BODY)
                        .with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelar_operacao_retorna403() throws Exception {
        mockMvc.perform(put("/api/nfse/{notaId}/cancelar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(MOTIVO_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 5: gestao,contabil,operacao — POST /{notaId}/reprocessar ────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "operacao"})
    void reprocessar_papelLegado_retorna204(String role) throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).reprocessarNota(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/reprocessar", notaId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void reprocessar_permNotas_retorna204() throws Exception {
        UUID notaId = UUID.randomUUID();
        doNothing().when(service).reprocessarNota(notaId);
        mockMvc.perform(post("/api/nfse/{notaId}/reprocessar", notaId).with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void reprocessar_financeiro_retorna403() throws Exception {
        // Diferente do grupo 4 (cancelar), financeiro nunca teve acesso a reprocessar.
        mockMvc.perform(post("/api/nfse/{notaId}/reprocessar", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 6: gestao,contabil,operacao,financeiro — GET /excecoes ──────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "operacao", "financeiro"})
    void listarExcecoes_papelLegado_retorna200(String role) throws Exception {
        when(service.listarExcecoes()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse/excecoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listarExcecoes_permNotas_retorna200() throws Exception {
        when(service.listarExcecoes()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse/excecoes").with(jwt().authorities(PERM_NOTAS)))
                .andExpect(status().isOk());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/nfse"))
                .andExpect(status().isUnauthorized());
    }
}
