package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.dto.FechamentoResponse;
import br.com.pinsaude.faturamento.service.FechamentoService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em FechamentoController.
 * 2 grupos distintos (PERFIL-15): leitura (4 papéis) e escrita (2 papéis).
 */
@WebMvcTest(FechamentoController.class)
@Import(SecurityConfig.class)
class FechamentoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    FechamentoService service;

    private static final SimpleGrantedAuthority PERM_FECHAMENTOS = new SimpleGrantedAuthority("ROLE_perm_fechamentos");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_tomadores");
    private static final String EXECUTAR_BODY =
            "{\"tomadorId\":\"" + UUID.randomUUID() + "\",\"competencia\":\"2026-08\"}";

    // ─── Grupo 1: leitura (operacao,gestao,financeiro,contabil) — GET / (listar) ──

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "financeiro", "contabil"})
    void listar_papelLegado_retorna200(String role) throws Exception {
        when(service.listar(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/fechamentos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_permFechamentos_retorna200() throws Exception {
        when(service.listar(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/fechamentos").with(jwt().authorities(PERM_FECHAMENTOS)))
                .andExpect(status().isOk());
    }

    @Test
    void listar_medico_retorna403() throws Exception {
        mockMvc.perform(get("/api/fechamentos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 2: escrita (operacao,gestao) — POST / (executar) ────────────────

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao"})
    void executar_papelLegado_retorna201(String role) throws Exception {
        when(service.executar(any())).thenReturn(mock(FechamentoResponse.class));
        mockMvc.perform(post("/api/fechamentos")
                        .contentType(MediaType.APPLICATION_JSON).content(EXECUTAR_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isCreated());
    }

    @Test
    void executar_permFechamentos_retorna201() throws Exception {
        when(service.executar(any())).thenReturn(mock(FechamentoResponse.class));
        mockMvc.perform(post("/api/fechamentos")
                        .contentType(MediaType.APPLICATION_JSON).content(EXECUTAR_BODY)
                        .with(jwt().authorities(PERM_FECHAMENTOS)))
                .andExpect(status().isCreated());
    }

    @Test
    void executar_financeiro_retorna403() throws Exception {
        // 'financeiro' tem acesso de leitura mas nunca ao tier de escrita (irreversível na prática).
        mockMvc.perform(post("/api/fechamentos")
                        .contentType(MediaType.APPLICATION_JSON).content(EXECUTAR_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void executar_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(post("/api/fechamentos")
                        .contentType(MediaType.APPLICATION_JSON).content(EXECUTAR_BODY)
                        .with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/fechamentos"))
                .andExpect(status().isUnauthorized());
    }
}
