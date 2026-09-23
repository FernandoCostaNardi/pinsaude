package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.LoteProgressoResponse;
import br.com.pinsaude.fiscal.service.NfseBatchService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em NfseBatchController.
 * 2 grupos distintos (PERFIL-19): escrita (3 papéis) e leitura (4 papéis).
 */
@WebMvcTest(NfseBatchController.class)
@Import(SecurityConfig.class)
class NfseBatchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    NfseBatchService service;

    private static final SimpleGrantedAuthority PERM_NOTAS_LOTE = new SimpleGrantedAuthority("ROLE_perm_notas_lote");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_notas");
    private static final String EMITIR_BODY = "{\"competencia\":\"2026-08\"}";

    // ─── Grupo 1: escrita (gestao,contabil,operacao) — POST /emitir ────────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "operacao"})
    void emitirLote_papelLegado_retorna202(String role) throws Exception {
        when(service.emitirLote(any())).thenReturn(mock(LoteProgressoResponse.class));
        mockMvc.perform(post("/api/nfse/lote/emitir")
                        .contentType(MediaType.APPLICATION_JSON).content(EMITIR_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isAccepted());
    }

    @Test
    void emitirLote_permNotasLote_retorna202() throws Exception {
        when(service.emitirLote(any())).thenReturn(mock(LoteProgressoResponse.class));
        mockMvc.perform(post("/api/nfse/lote/emitir")
                        .contentType(MediaType.APPLICATION_JSON).content(EMITIR_BODY)
                        .with(jwt().authorities(PERM_NOTAS_LOTE)))
                .andExpect(status().isAccepted());
    }

    @Test
    void emitirLote_financeiro_retorna403() throws Exception {
        // 'financeiro' tem acesso de leitura (grupo 2) mas nunca ao disparo do lote.
        mockMvc.perform(post("/api/nfse/lote/emitir")
                        .contentType(MediaType.APPLICATION_JSON).content(EMITIR_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 2: leitura (gestao,contabil,operacao,financeiro) — GET / ────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "contabil", "operacao", "financeiro"})
    void listarLotes_papelLegado_retorna200(String role) throws Exception {
        when(service.listarLotes()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse/lote")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listarLotes_permNotasLote_retorna200() throws Exception {
        when(service.listarLotes()).thenReturn(List.of());
        mockMvc.perform(get("/api/nfse/lote").with(jwt().authorities(PERM_NOTAS_LOTE)))
                .andExpect(status().isOk());
    }

    @Test
    void listarLotes_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/nfse/lote").with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/nfse/lote"))
                .andExpect(status().isUnauthorized());
    }
}
