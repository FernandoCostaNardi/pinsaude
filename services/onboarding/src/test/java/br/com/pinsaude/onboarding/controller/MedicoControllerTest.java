package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.config.SecurityConfig;
import br.com.pinsaude.onboarding.dto.MedicoListResponse;
import br.com.pinsaude.onboarding.dto.MedicoResponse;
import br.com.pinsaude.onboarding.service.MedicoService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em MedicoController.
 * Um endpoint representativo por cada um dos 5 grupos distintos de @PreAuthorize
 * (PERFIL-09): papel legado continua autorizando, perm_medicos sozinho também
 * autoriza, e um papel/perm de outro domínio continua barrado.
 */
@WebMvcTest(MedicoController.class)
@Import(SecurityConfig.class)
class MedicoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MedicoService service;

    private static final SimpleGrantedAuthority PERM_MEDICOS = new SimpleGrantedAuthority("ROLE_perm_medicos");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_empresas");

    // ─── Grupo 1: gestao,operacao — GET /fila-aprovacao ────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "operacao"})
    void filaAprovacao_papelLegado_retorna200(String role) throws Exception {
        when(service.listarFilaAprovacao()).thenReturn(List.of());
        mockMvc.perform(get("/api/medicos/fila-aprovacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void filaAprovacao_permMedicos_retorna200() throws Exception {
        when(service.listarFilaAprovacao()).thenReturn(List.of());
        mockMvc.perform(get("/api/medicos/fila-aprovacao").with(jwt().authorities(PERM_MEDICOS)))
                .andExpect(status().isOk());
    }

    @Test
    void filaAprovacao_papelSemAcesso_retorna403() throws Exception {
        mockMvc.perform(get("/api/medicos/fila-aprovacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void filaAprovacao_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/medicos/fila-aprovacao").with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 2: gestao,operacao,financeiro,contabil — GET / (listar) ─────────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "operacao", "financeiro", "contabil"})
    void listar_papelLegado_retorna200(String role) throws Exception {
        when(service.listar(0, 20, null)).thenReturn(mock(MedicoListResponse.class));
        mockMvc.perform(get("/api/medicos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void listar_permMedicos_retorna200() throws Exception {
        when(service.listar(0, 20, null)).thenReturn(mock(MedicoListResponse.class));
        mockMvc.perform(get("/api/medicos").with(jwt().authorities(PERM_MEDICOS)))
                .andExpect(status().isOk());
    }

    @Test
    void listar_papelMedico_retorna403() throws Exception {
        mockMvc.perform(get("/api/medicos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 3: gestao,operacao,financeiro,contabil,medico — GET /{id} ───────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "operacao", "financeiro", "contabil", "medico"})
    void buscarPorId_papelLegado_retorna200(String role) throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id)).thenReturn(mock(MedicoResponse.class));
        mockMvc.perform(get("/api/medicos/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void buscarPorId_permMedicos_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id)).thenReturn(mock(MedicoResponse.class));
        mockMvc.perform(get("/api/medicos/{id}", id).with(jwt().authorities(PERM_MEDICOS)))
                .andExpect(status().isOk());
    }

    @Test
    void buscarPorId_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/medicos/{id}", UUID.randomUUID()).with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 4: gestao,operacao,medico — DELETE /{id}/documentos/{docId} ─────

    @ParameterizedTest
    @ValueSource(strings = {"gestao", "operacao", "medico"})
    void deletarDocumento_papelLegado_retorna204(String role) throws Exception {
        UUID id = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        doNothing().when(service).deletarDocumento(id, docId);
        mockMvc.perform(delete("/api/medicos/{id}/documentos/{docId}", id, docId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletarDocumento_permMedicos_retorna204() throws Exception {
        UUID id = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        doNothing().when(service).deletarDocumento(id, docId);
        mockMvc.perform(delete("/api/medicos/{id}/documentos/{docId}", id, docId).with(jwt().authorities(PERM_MEDICOS)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletarDocumento_papelSemAcesso_retorna403() throws Exception {
        mockMvc.perform(delete("/api/medicos/{id}/documentos/{docId}", UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    // ─── Grupo 5: gestao (sozinho) — GET /{id}/vinculos ────────────────────────

    @Test
    void listarVinculos_gestao_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listarVinculos(id)).thenReturn(List.of());
        mockMvc.perform(get("/api/medicos/{id}/vinculos", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isOk());
    }

    @Test
    void listarVinculos_permMedicos_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listarVinculos(id)).thenReturn(List.of());
        mockMvc.perform(get("/api/medicos/{id}/vinculos", id).with(jwt().authorities(PERM_MEDICOS)))
                .andExpect(status().isOk());
    }

    @Test
    void listarVinculos_operacao_semPermMedicos_retorna403() throws Exception {
        // 'operacao' tem acesso a quase tudo neste controller, mas /vinculos sempre foi só 'gestao'
        // (ver EPIC-03.8) — perm_medicos precisa cobrir esse tier mais restrito também.
        mockMvc.perform(get("/api/medicos/{id}/vinculos", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarVinculos_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/medicos/{id}/vinculos", UUID.randomUUID()).with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── Sem autenticação — 401, igual em qualquer grupo ───────────────────────

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/medicos"))
                .andExpect(status().isUnauthorized());
    }
}
