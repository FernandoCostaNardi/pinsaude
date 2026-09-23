package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.dto.ParticipacaoRequest;
import br.com.pinsaude.faturamento.dto.ProducaoRequest;
import br.com.pinsaude.faturamento.dto.ProducaoResponse;
import br.com.pinsaude.faturamento.service.ProducaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em ProducaoController.
 * Um endpoint representativo por cada um dos 4 grupos distintos de @PreAuthorize (PERFIL-13).
 * POST usa corpo 100% válido (Bean Validation roda antes do @PreAuthorize — ver CLAUDE.md).
 */
@WebMvcTest(ProducaoController.class)
@Import(SecurityConfig.class)
class ProducaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ProducaoService service;

    private static final SimpleGrantedAuthority PERM_PRODUCAO = new SimpleGrantedAuthority("ROLE_perm_producao");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_tomadores");

    private String producaoRequestValido() throws Exception {
        ProducaoRequest req = new ProducaoRequest(
                UUID.randomUUID(), UUID.randomUUID(), "2026-08", null, null, null,
                List.of(new ParticipacaoRequest(UUID.randomUUID(), 1000L, null)));
        return objectMapper.writeValueAsString(req);
    }

    // ─── Grupo 1: leitura (operacao,gestao,financeiro,contabil) — GET / ────────

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "financeiro", "contabil"})
    void listar_papelLegado_retorna200(String role) throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/producoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_permProducao_retorna200() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/producoes").with(jwt().authorities(PERM_PRODUCAO)))
                .andExpect(status().isOk());
    }

    @Test
    void listar_medico_retorna403() throws Exception {
        mockMvc.perform(get("/api/producoes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 2: criar (operacao,gestao,medico) — POST / ──────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "medico"})
    void criar_papelLegado_retorna201(String role) throws Exception {
        when(service.criar(any())).thenReturn(mock(ProducaoResponse.class));
        mockMvc.perform(post("/api/producoes")
                        .contentType(MediaType.APPLICATION_JSON).content(producaoRequestValido())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isCreated());
    }

    @Test
    void criar_permProducao_retorna201() throws Exception {
        when(service.criar(any())).thenReturn(mock(ProducaoResponse.class));
        mockMvc.perform(post("/api/producoes")
                        .contentType(MediaType.APPLICATION_JSON).content(producaoRequestValido())
                        .with(jwt().authorities(PERM_PRODUCAO)))
                .andExpect(status().isCreated());
    }

    @Test
    void criar_financeiro_retorna403() throws Exception {
        mockMvc.perform(post("/api/producoes")
                        .contentType(MediaType.APPLICATION_JSON).content(producaoRequestValido())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 3: escrita restrita (operacao,gestao) — PUT /{id}/servico ───────

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao"})
    void atualizarServico_papelLegado_retorna200(String role) throws Exception {
        UUID id = UUID.randomUUID();
        when(service.atualizarServico(any(), any())).thenReturn(mock(ProducaoResponse.class));
        String body = "{\"servicoId\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/producoes/{id}/servico", id)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarServico_permProducao_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.atualizarServico(any(), any())).thenReturn(mock(ProducaoResponse.class));
        String body = "{\"servicoId\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/producoes/{id}/servico", id)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(PERM_PRODUCAO)))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarServico_medico_retorna403() throws Exception {
        // Diferente do grupo 2 (criar), este endpoint nunca liberou 'medico'.
        String body = "{\"servicoId\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/producoes/{id}/servico", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 4: leitura mais ampla (operacao,gestao,financeiro,contabil,medico) — POST /preview-calculo ──

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "financeiro", "contabil", "medico"})
    void previewCalculo_papelLegado_retorna200(String role) throws Exception {
        when(service.calcularPreview(any())).thenReturn(
                org.mockito.Mockito.mock(br.com.pinsaude.faturamento.dto.PreviewCalculoResponse.class));
        String body = "{\"servicoId\":\"" + UUID.randomUUID() + "\",\"tomadorId\":\"" + UUID.randomUUID()
                + "\",\"valorBruto\":1000}";
        mockMvc.perform(post("/api/producoes/preview-calculo")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void previewCalculo_permProducao_retorna200() throws Exception {
        when(service.calcularPreview(any())).thenReturn(
                org.mockito.Mockito.mock(br.com.pinsaude.faturamento.dto.PreviewCalculoResponse.class));
        String body = "{\"servicoId\":\"" + UUID.randomUUID() + "\",\"tomadorId\":\"" + UUID.randomUUID()
                + "\",\"valorBruto\":1000}";
        mockMvc.perform(post("/api/producoes/preview-calculo")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(PERM_PRODUCAO)))
                .andExpect(status().isOk());
    }

    @Test
    void previewCalculo_permOutroDominio_retorna403() throws Exception {
        String body = "{\"servicoId\":\"" + UUID.randomUUID() + "\",\"tomadorId\":\"" + UUID.randomUUID()
                + "\",\"valorBruto\":1000}";
        mockMvc.perform(post("/api/producoes/preview-calculo")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/producoes"))
                .andExpect(status().isUnauthorized());
    }
}
