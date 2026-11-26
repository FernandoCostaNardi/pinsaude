package br.com.pinsaude.fiscal.controller;

import br.com.pinsaude.fiscal.apuracao.ResultadoApuracao;
import br.com.pinsaude.fiscal.config.SecurityConfig;
import br.com.pinsaude.fiscal.dto.ApuracaoMensalResponse;
import br.com.pinsaude.fiscal.service.ApuracaoMensalService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** APU-01 — apuração mensal: mesmos perfis de leitura das regras de apuração. */
@WebMvcTest(ApuracaoMensalController.class)
@Import(SecurityConfig.class)
class ApuracaoMensalControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ApuracaoMensalService service;

    private static ApuracaoMensalResponse resposta() {
        var r = new ResultadoApuracao("2026-12", "12345678000190", "LEGADO", null, "2026-01", true, false, true,
            List.of(), 0, 0, 0, List.of());
        return new ApuracaoMensalResponse(r, "COMPETENCIA_NOTA", false, List.of(), List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"contabil", "gestao", "financeiro", "perm_fiscal"})
    void apurar_perfisDeLeitura_retorna200(String perfil) throws Exception {
        when(service.apurar("2026-12", "12345678000190")).thenReturn(resposta());

        mockMvc.perform(get("/api/fiscal/apuracao").param("competencia", "2026-12").param("cnpj", "12345678000190")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + perfil))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apuracao.aHomologar").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"medico", "operacao", "perm_notas"})
    void apurar_outrosPerfis_retorna403(String perfil) throws Exception {
        mockMvc.perform(get("/api/fiscal/apuracao").param("competencia", "2026-12")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + perfil))))
            .andExpect(status().isForbidden());
    }
}
