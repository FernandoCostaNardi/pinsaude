package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.RegraApuracaoResponse;
import br.com.pinsaude.fiscal.service.RegraApuracaoService;
import br.com.pinsaude.fiscal.service.SimuladorApuracaoService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** APU-00 — acesso às regras de apuração: leitura com financeiro; escrita sem financeiro. */
@WebMvcTest(RegraApuracaoController.class)
@Import(SecurityConfig.class)
class RegraApuracaoControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean RegraApuracaoService service;
    @MockBean SimuladorApuracaoService simulador;

    private static final String BODY = """
        {"competenciaInicio":"2027-01","presuncaoIrpjEquiparado":0.08,"presuncaoCsllEquiparado":0.12,
         "presuncaoIrpjPadrao":0.32,"presuncaoCsllPadrao":0.32,"aliqAdicionalIrpj":0.10,
         "limiteAdicionalIrpjMensal":2000000,"tetoReceitaAnual":7800000000,"alertaTetoPercentual":0.8,
         "valorMinimoGuia":1000,
         "tributos":[{"tributo":"PIS","ativo":true,"aplicaEm":"LEGADO","aliquota":0.0065,
           "baseCalculo":"RECEITA_BRUTA","regimeReconhecimento":"CAIXA","periodicidade":"MENSAL",
           "deduzRetencao":true,"codigoReceita":"8109","guia":"DARF","vencimentoDia":25,
           "vencimentoMesesApos":1,"vencimentoAjuste":"ANTECIPA"}]}
        """;

    private static final String SIMULACAO = """
        {"competencia":"2026-03","meses":[{"competencia":"2026-03","receitaCompetenciaEquiparada":1000000,
         "receitaCompetenciaPadrao":0,"receitaCaixaEquiparada":1000000,"receitaCaixaPadrao":0,
         "retencoes":{"PIS":6500},"cbsNotas":0,"ibsNotas":0}]}
        """;

    private static SimpleGrantedAuthority role(String r) {
        return new SimpleGrantedAuthority("ROLE_" + r);
    }

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "financeiro", "perm_fiscal"})
    void leituraESimulacao_papeisDeLeitura_200(String r) throws Exception {
        when(service.listar(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/fiscal/regras-apuracao").with(jwt().authorities(role(r))))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/fiscal/regras-apuracao/simular").contentType(MediaType.APPLICATION_JSON)
                .content(SIMULACAO).with(jwt().authorities(role(r))))
            .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"operacao", "medico", "perm_notas"})
    void leitura_semAcesso_403(String r) throws Exception {
        mockMvc.perform(get("/api/fiscal/regras-apuracao").with(jwt().authorities(role(r))))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "perm_fiscal"})
    void escrita_papeisDeEscrita(String r) throws Exception {
        UUID id = UUID.randomUUID();
        when(service.criar(any())).thenReturn(mock(RegraApuracaoResponse.class));
        when(service.homologar(eq(id))).thenReturn(mock(RegraApuracaoResponse.class));
        mockMvc.perform(post("/api/fiscal/regras-apuracao").contentType(MediaType.APPLICATION_JSON)
                .content(BODY).with(jwt().authorities(role(r))))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/api/fiscal/regras-apuracao/" + id + "/homologar").with(jwt().authorities(role(r))))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/fiscal/regras-apuracao/" + id).with(jwt().authorities(role(r))))
            .andExpect(status().isNoContent());
    }

    @Test
    void escrita_financeiro_403() throws Exception {
        mockMvc.perform(post("/api/fiscal/regras-apuracao").contentType(MediaType.APPLICATION_JSON)
                .content(BODY).with(jwt().authorities(role("financeiro"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/fiscal/regras-apuracao/" + UUID.randomUUID() + "/homologar")
                .with(jwt().authorities(role("financeiro"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void criar_presuncaoAcimaDe1_400() throws Exception {
        mockMvc.perform(post("/api/fiscal/regras-apuracao").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"presuncaoIrpjPadrao\":0.32", "\"presuncaoIrpjPadrao\":32"))
                .with(jwt().authorities(role("gestao"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void criar_competenciaInvalida_400() throws Exception {
        mockMvc.perform(post("/api/fiscal/regras-apuracao").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("2027-01", "2027-13"))
                .with(jwt().authorities(role("gestao"))))
            .andExpect(status().isBadRequest());
    }
}
