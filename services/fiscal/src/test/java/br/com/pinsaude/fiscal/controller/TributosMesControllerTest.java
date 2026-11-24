package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.TributosMesResponse;
import br.com.pinsaude.fiscal.dto.TributosMesResponse.CbsIbs;
import br.com.pinsaude.fiscal.dto.TributosMesResponse.Retencoes;
import br.com.pinsaude.fiscal.service.TributosMesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CBS-32 — GET /api/fiscal/tributos-mes. */
@WebMvcTest(TributosMesController.class)
@Import(SecurityConfig.class)
class TributosMesControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TributosMesService service;

    private static TributosMesResponse vazio(String mes) {
        return new TributosMesResponse(mes, 0, 0, new Retencoes(0, 0, 0, 0, 0, 0), new CbsIbs(0, 0, 0), List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "gestao", "financeiro", "contabil", "perm_caixa", "perm_conciliacao", "perm_notas"})
    void perfisDaPosicaoDeCaixa_200(String role) throws Exception {
        when(service.calcular(YearMonth.of(2027, 1))).thenReturn(vazio("2027-01"));
        mockMvc.perform(get("/api/fiscal/tributos-mes").param("mes", "2027-01")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mes").value("2027-01"))
            .andExpect(jsonPath("$.cbsIbs.total").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"medico", "perm_producao"})
    void semAcesso_403(String role) throws Exception {
        mockMvc.perform(get("/api/fiscal/tributos-mes").param("mes", "2027-01")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isForbidden());
    }

    @Test
    void semMes_todosOsPeriodos() throws Exception {
        when(service.calcular(null)).thenReturn(vazio(null));
        mockMvc.perform(get("/api/fiscal/tributos-mes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
            .andExpect(status().isOk());
        verify(service).calcular(null);
    }

    @Test
    void mesInvalido_400() throws Exception {
        mockMvc.perform(get("/api/fiscal/tributos-mes").param("mes", "01/2027")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
            .andExpect(status().isBadRequest());
        verify(service, never()).calcular(any());
    }

    @Test
    void semToken_401() throws Exception {
        mockMvc.perform(get("/api/fiscal/tributos-mes")).andExpect(status().isUnauthorized());
    }
}
