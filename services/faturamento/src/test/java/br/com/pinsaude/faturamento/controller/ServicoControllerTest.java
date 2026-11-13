package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.dto.ServicoResponse;
import br.com.pinsaude.faturamento.service.ServicoService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CBS-12 — classificação tributária de CBS/IBS no catálogo de serviços. */
@WebMvcTest(ServicoController.class)
@Import(SecurityConfig.class)
class ServicoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ServicoService service;

    private static final UUID ID = UUID.randomUUID();
    private static final String BODY_VALIDO =
        "{\"nbs\":\"1.2301.19.00\",\"cstIbsCbs\":\"200\",\"cClassTrib\":\"200029\",\"reducaoIbsCbs\":0.6}";

    private ServicoResponse classificado() {
        return new ServicoResponse(ID, "4.01", "8630-5/01", "Medicina", false,
            new BigDecimal("5.0000"), new BigDecimal("1.5000"), new BigDecimal("1.0000"),
            new BigDecimal("0.6500"), new BigDecimal("3.0000"),
            "1.2301.19.00", "200", "200029", new BigDecimal("0.6000"));
    }

    @Test
    void listar_expoeClassificacao() throws Exception {
        when(service.listar()).thenReturn(List.of(classificado()));
        mockMvc.perform(get("/api/servicos").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nbs").value("1.2301.19.00"))
            .andExpect(jsonPath("$[0].cstIbsCbs").value("200"))
            .andExpect(jsonPath("$[0].cClassTrib").value("200029"))
            .andExpect(jsonPath("$[0].reducaoIbsCbs").value(0.6));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_contabil", "ROLE_gestao", "ROLE_perm_fiscal"})
    void atualizar_comPermissao_retorna200(String role) throws Exception {
        when(service.atualizarClassificacao(eq(ID), any())).thenReturn(classificado());
        mockMvc.perform(put("/api/servicos/{id}/classificacao-tributaria", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority(role)))
                .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cClassTrib").value("200029"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_operacao", "ROLE_financeiro", "ROLE_medico", "ROLE_perm_tomadores"})
    void atualizar_semPermissao_retorna403(String role) throws Exception {
        mockMvc.perform(put("/api/servicos/{id}/classificacao-tributaria", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority(role)))
                .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
            .andExpect(status().isForbidden());
        verify(service, never()).atualizarClassificacao(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"nbs\":\"123019\",\"cstIbsCbs\":\"200\",\"cClassTrib\":\"200029\",\"reducaoIbsCbs\":0.6}",
        "{\"nbs\":\"1.2301.19.00\",\"cstIbsCbs\":\"20\",\"cClassTrib\":\"200029\",\"reducaoIbsCbs\":0.6}",
        "{\"nbs\":\"1.2301.19.00\",\"cstIbsCbs\":\"200\",\"cClassTrib\":\"2000\",\"reducaoIbsCbs\":0.6}",
        "{\"nbs\":\"1.2301.19.00\",\"cstIbsCbs\":\"200\",\"cClassTrib\":\"200029\",\"reducaoIbsCbs\":60}"
    })
    void atualizar_valorInvalido_retorna400(String body) throws Exception {
        mockMvc.perform(put("/api/servicos/{id}/classificacao-tributaria", ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_contabil")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }
}
