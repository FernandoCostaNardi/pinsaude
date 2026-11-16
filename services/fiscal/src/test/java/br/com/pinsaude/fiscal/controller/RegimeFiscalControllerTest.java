package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.RegimeCompetenciaResponse;
import br.com.pinsaude.fiscal.service.RegimeFiscalService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CBS-15 — GET /api/fiscal/regime: perfis das telas fiscais e empresa do token. */
@WebMvcTest(RegimeFiscalController.class)
@Import(SecurityConfig.class)
class RegimeFiscalControllerTest {

    private static final String CNPJ = "12345678000195";

    @Autowired MockMvc mockMvc;
    @MockBean RegimeFiscalService service;

    private static RegimeCompetenciaResponse legado() {
        return new RegimeCompetenciaResponse("2026-12", "LEGADO",
            List.of("ISS", "IR", "CSLL", "PIS", "COFINS"), false, true, "2024-01", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "financeiro", "operacao", "perm_fiscal", "perm_notas", "perm_producao"})
    void perfisDasTelasFiscais_200(String role) throws Exception {
        when(service.regime(CNPJ, "2026-12")).thenReturn(legado());
        mockMvc.perform(get("/api/fiscal/regime").param("competencia", "2026-12")
                .with(jwt().jwt(j -> j.claim("cnpj_id", "12.345.678/0001-95"))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.regime").value("LEGADO"))
            .andExpect(jsonPath("$.tributosVisiveis[3]").value("PIS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"medico", "perm_caixa"})
    void semAcesso_403(String role) throws Exception {
        mockMvc.perform(get("/api/fiscal/regime").param("competencia", "2026-12")
                .with(jwt().jwt(j -> j.claim("cnpj_id", CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isForbidden());
    }

    @Test
    void semToken_401() throws Exception {
        mockMvc.perform(get("/api/fiscal/regime").param("competencia", "2026-12"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void cnpjDoParametroIgnoradoQuandoTokenTemEmpresa() throws Exception {
        when(service.regime(CNPJ, "2026-12")).thenReturn(legado());
        mockMvc.perform(get("/api/fiscal/regime").param("competencia", "2026-12")
                .param("cnpj", "99999999000199")
                .with(jwt().jwt(j -> j.claim("cnpj_id", CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isOk());
        verify(service).regime(CNPJ, "2026-12");
    }

    @Test
    void gestaoSemEmpresaNoToken_usaCnpjInformado() throws Exception {
        when(service.regime(eq(CNPJ), eq("2027-01"))).thenReturn(legado());
        mockMvc.perform(get("/api/fiscal/regime").param("competencia", "2027-01")
                .param("cnpj", "12.345.678/0001-95")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk());
        verify(service).regime(CNPJ, "2027-01");
    }

    @Test
    void gestaoSemEmpresaNemCnpj_400() throws Exception {
        mockMvc.perform(get("/api/fiscal/regime").param("competencia", "2027-01")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isBadRequest());
        verify(service, never()).regime(anyString(), anyString());
    }
}
