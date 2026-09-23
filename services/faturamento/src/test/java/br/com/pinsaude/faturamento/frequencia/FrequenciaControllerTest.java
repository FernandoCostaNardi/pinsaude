package br.com.pinsaude.faturamento.frequencia;

import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.controller.FrequenciaController;
import br.com.pinsaude.faturamento.dto.FrequenciaMedicaResponse;
import br.com.pinsaude.faturamento.service.FrequenciaService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Regressão: DELETE /{id}/itens/{itemId} não liberava a role 'medico', diferente de todo o
// resto do CRUD de itens (POST/PUT já liberavam) — médico não conseguia excluir o próprio
// plantão lançado pelo Portal, mesmo o botão de excluir existindo na tela (reportado pelo
// cliente logo após a PINSAUDE-13.26).
@WebMvcTest(FrequenciaController.class)
@Import(SecurityConfig.class)
class FrequenciaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    FrequenciaService service;

    @Test
    void removerItem_medico_retorna204() throws Exception {
        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        doNothing().when(service).removerItem(freqId, itemId);

        mockMvc.perform(delete("/api/frequencias/{id}/itens/{itemId}", freqId, itemId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void removerItem_operacao_retorna204() throws Exception {
        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        doNothing().when(service).removerItem(freqId, itemId);

        mockMvc.perform(delete("/api/frequencias/{id}/itens/{itemId}", freqId, itemId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void removerItem_gestao_retorna204() throws Exception {
        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        doNothing().when(service).removerItem(freqId, itemId);

        mockMvc.perform(delete("/api/frequencias/{id}/itens/{itemId}", freqId, itemId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void removerItem_financeiro_retorna403() throws Exception {
        mockMvc.perform(delete("/api/frequencias/{id}/itens/{itemId}", UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void removerItem_semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(delete("/api/frequencias/{id}/itens/{itemId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // Edição de Competência + Grupo + Setor Operacional pós-criação — mesmas 3 roles do resto do CRUD.
    private static final String EDIT_BODY = """
            {"competencia":"2026-08","grupoId":"%s","servicoOperacionalId":"%s"}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void atualizar_medico_retorna200() throws Exception {
        UUID freqId = UUID.randomUUID();
        when(service.atualizar(eq(freqId), any())).thenReturn(mockResponse());

        mockMvc.perform(put("/api/frequencias/{id}", freqId)
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isOk());
    }

    @Test
    void atualizar_operacao_retorna200() throws Exception {
        UUID freqId = UUID.randomUUID();
        when(service.atualizar(eq(freqId), any())).thenReturn(mockResponse());

        mockMvc.perform(put("/api/frequencias/{id}", freqId)
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isOk());
    }

    @Test
    void atualizar_gestao_retorna200() throws Exception {
        UUID freqId = UUID.randomUUID();
        when(service.atualizar(eq(freqId), any())).thenReturn(mockResponse());

        mockMvc.perform(put("/api/frequencias/{id}", freqId)
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isOk());
    }

    // O @Valid do corpo roda antes do @PreAuthorize (ver CLAUDE.md) — corpo precisa ser válido
    // pra este teste realmente exercitar o gate de autorização, não uma falha de validação.
    @Test
    void atualizar_financeiro_retorna403() throws Exception {
        mockMvc.perform(put("/api/frequencias/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(put("/api/frequencias/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isUnauthorized());
    }

    private static FrequenciaMedicaResponse mockResponse() {
        return org.mockito.Mockito.mock(FrequenciaMedicaResponse.class);
    }

    // ─── PERFIL-22 — regressão do catálogo perm_* (ADR-004), PERFIL-14 ─────────
    // Grupo escrita (operacao,gestao,medico) já coberto acima via removerItem/atualizar —
    // faltava só perm_frequencias sozinho. Grupo leitura (operacao,gestao,medico,financeiro,
    // contabil) cobre TODOS os 5 papéis legados — sem "papel sem acesso" possível ali, só
    // perm_frequencias positivo e um perm de outro domínio negativo.

    @Test
    void removerItem_permFrequencias_retorna204() throws Exception {
        UUID freqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        doNothing().when(service).removerItem(freqId, itemId);

        mockMvc.perform(delete("/api/frequencias/{id}/itens/{itemId}", freqId, itemId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_perm_frequencias"))))
                .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "medico", "financeiro", "contabil"})
    void listar_papelLegado_retorna200(String role) throws Exception {
        when(service.listar(any(), any(), any(), any(), any())).thenReturn(java.util.List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/frequencias")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_permFrequencias_retorna200() throws Exception {
        when(service.listar(any(), any(), any(), any(), any())).thenReturn(java.util.List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/frequencias")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_perm_frequencias"))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/frequencias")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_perm_tomadores"))))
                .andExpect(status().isForbidden());
    }
}
