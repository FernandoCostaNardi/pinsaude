package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.CronogramaReformaResponse;
import br.com.pinsaude.fiscal.service.CronogramaReformaService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CBS-10 — acesso ao cronograma da reforma: leitura contabil/gestao/financeiro/perm_fiscal; escrita sem financeiro. */
@WebMvcTest(CronogramaReformaController.class)
@Import(SecurityConfig.class)
class CronogramaReformaControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean CronogramaReformaService service;

    private static final String BODY_VALIDO = """
        {"aliqCbs":0.1110,"aliqIbsUf":0.0005,"aliqIbsMun":0.0005,"fatorIss":1.0,
         "aliquotasDefinitivas":true,"fonte":"Resolução do Senado"}
        """;

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "financeiro", "perm_fiscal"})
    void listar_papeisDeLeitura_200(String role) throws Exception {
        when(service.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/fiscal/cronograma-reforma")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "medico", "perm_notas"})
    void listar_semAcesso_403(String role) throws Exception {
        mockMvc.perform(get("/api/fiscal/cronograma-reforma")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "perm_fiscal"})
    void atualizar_papeisDeEscrita_200(String role) throws Exception {
        when(service.atualizar(eq(2027), any())).thenReturn(mock(CronogramaReformaResponse.class));
        mockMvc.perform(put("/api/fiscal/cronograma-reforma/2027")
                .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk());
    }

    @Test
    void atualizar_financeiro_403() throws Exception {
        mockMvc.perform(put("/api/fiscal/cronograma-reforma/2027")
                .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_aliquotaAcimaDe1_400() throws Exception {
        mockMvc.perform(put("/api/fiscal/cronograma-reforma/2027")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aliqCbs\":11.2,\"fatorIss\":1.0,\"aliquotasDefinitivas\":false}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_contabil"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void semToken_401() throws Exception {
        mockMvc.perform(get("/api/fiscal/cronograma-reforma")).andExpect(status().isUnauthorized());
    }
}
