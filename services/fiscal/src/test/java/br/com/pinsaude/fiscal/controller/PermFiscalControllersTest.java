package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.dto.ParametroFiscalResponse;
import br.com.pinsaude.fiscal.service.MotorFiscalService;
import br.com.pinsaude.fiscal.service.ParametroFiscalService;
import br.com.pinsaude.fiscal.service.ParametrosFiscaisService;
import br.com.pinsaude.fiscal.service.RegraEquiparacaoService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004), perm_fiscal 2/2 (PERFIL-17).
 * Cobre os 5 controllers que compartilham perm_fiscal: FiscalController,
 * FiscalParametrosController, MotorFiscalController, RegraEquiparacaoController
 * (todos hasRole('contabil')/hasRole('gestao')) e ParametroFiscalController (2 grupos próprios).
 */
@WebMvcTest(controllers = {
        FiscalController.class, FiscalParametrosController.class, MotorFiscalController.class,
        RegraEquiparacaoController.class, ParametroFiscalController.class
})
@Import(SecurityConfig.class)
class PermFiscalControllersTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ParametrosFiscaisService parametrosFiscaisService;

    @MockBean
    MotorFiscalService motorFiscalService;

    @MockBean
    RegraEquiparacaoService regraEquiparacaoService;

    @MockBean
    ParametroFiscalService parametroFiscalService;

    private static final SimpleGrantedAuthority PERM_FISCAL = new SimpleGrantedAuthority("ROLE_perm_fiscal");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_notas");

    // ─── FiscalController — stub, hasRole('contabil') or hasRole('gestao') ─────

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao"})
    void fiscalStub_papelLegado_retorna200(String role) throws Exception {
        mockMvc.perform(get("/api/fiscal")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void fiscalStub_permFiscal_retorna200() throws Exception {
        mockMvc.perform(get("/api/fiscal").with(jwt().authorities(PERM_FISCAL)))
                .andExpect(status().isOk());
    }

    @Test
    void fiscalStub_operacao_retorna403() throws Exception {
        mockMvc.perform(get("/api/fiscal")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    // ─── FiscalParametrosController — mesmo grupo ───────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao"})
    void fiscalParametros_papelLegado_retorna200(String role) throws Exception {
        mockMvc.perform(get("/api/fiscal/parametros")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void fiscalParametros_permFiscal_retorna200() throws Exception {
        mockMvc.perform(get("/api/fiscal/parametros").with(jwt().authorities(PERM_FISCAL)))
                .andExpect(status().isOk());
    }

    @Test
    void fiscalParametros_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/fiscal/parametros").with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── MotorFiscalController — mesmo grupo + financeiro/operacao ─────────────

    private static final String CALCULO_BODY = """
            {"valorBruto":10000,"competencia":"2026-08","tomadorPj":true,
             "indicadorRetencaoFederal":false,"indicadorRetencaoIss":false,
             "equiparacaoHospitalar":false}
            """;

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "financeiro", "operacao"})
    void motorFiscalCalcular_papelLegado_retorna200(String role) throws Exception {
        when(motorFiscalService.calcular(any(), any())).thenReturn(mock(CalculoFiscalResponse.class));
        mockMvc.perform(post("/api/motor-fiscal/calcular")
                        .contentType(MediaType.APPLICATION_JSON).content(CALCULO_BODY)
                        .with(jwt().jwt(j -> j.claim("cnpj_id", "12.345.678/0001-90"))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void motorFiscalCalcular_permFiscal_retorna200() throws Exception {
        when(motorFiscalService.calcular(any(), any())).thenReturn(mock(CalculoFiscalResponse.class));
        mockMvc.perform(post("/api/motor-fiscal/calcular")
                        .contentType(MediaType.APPLICATION_JSON).content(CALCULO_BODY)
                        .with(jwt().jwt(j -> j.claim("cnpj_id", "12.345.678/0001-90")).authorities(PERM_FISCAL)))
                .andExpect(status().isOk());
    }

    @Test
    void motorFiscalCalcular_medico_retorna403() throws Exception {
        mockMvc.perform(post("/api/motor-fiscal/calcular")
                        .contentType(MediaType.APPLICATION_JSON).content(CALCULO_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── RegraEquiparacaoController — mesmo grupo (contabil,gestao) ────────────

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao"})
    void regrasEquiparacao_papelLegado_retorna200(String role) throws Exception {
        when(regraEquiparacaoService.listar(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/fiscal/regras-equiparacao")
                        .with(jwt().jwt(j -> j.claim("cnpj_id", "12.345.678/0001-90"))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void regrasEquiparacao_permFiscal_retorna200() throws Exception {
        when(regraEquiparacaoService.listar(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/fiscal/regras-equiparacao")
                        .with(jwt().jwt(j -> j.claim("cnpj_id", "12.345.678/0001-90")).authorities(PERM_FISCAL)))
                .andExpect(status().isOk());
    }

    @Test
    void regrasEquiparacao_financeiro_retorna403() throws Exception {
        mockMvc.perform(get("/api/fiscal/regras-equiparacao")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    // ─── ParametroFiscalController — Grupo A: leitura (contabil,gestao,financeiro) ──

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "financeiro"})
    void parametrosFiscaisListar_papelLegado_retorna200(String role) throws Exception {
        when(parametroFiscalService.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/parametros-fiscais")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void parametrosFiscaisListar_permFiscal_retorna200() throws Exception {
        when(parametroFiscalService.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/parametros-fiscais").with(jwt().authorities(PERM_FISCAL)))
                .andExpect(status().isOk());
    }

    @Test
    void parametrosFiscaisListar_operacao_retorna403() throws Exception {
        mockMvc.perform(get("/api/parametros-fiscais")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    // ─── ParametroFiscalController — Grupo B: escrita (contabil,gestao) — PUT /{id}/homologar ──

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao"})
    void homologar_papelLegado_retorna200(String role) throws Exception {
        UUID id = UUID.randomUUID();
        when(parametroFiscalService.homologar(id)).thenReturn(mock(ParametroFiscalResponse.class));
        mockMvc.perform(put("/api/parametros-fiscais/{id}/homologar", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void homologar_permFiscal_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(parametroFiscalService.homologar(id)).thenReturn(mock(ParametroFiscalResponse.class));
        mockMvc.perform(put("/api/parametros-fiscais/{id}/homologar", id).with(jwt().authorities(PERM_FISCAL)))
                .andExpect(status().isOk());
    }

    @Test
    void homologar_financeiro_retorna403() throws Exception {
        // Diferente do Grupo A (leitura), financeiro nunca teve acesso a homologar (escrita).
        mockMvc.perform(put("/api/parametros-fiscais/{id}/homologar", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/fiscal"))
                .andExpect(status().isUnauthorized());
    }
}
