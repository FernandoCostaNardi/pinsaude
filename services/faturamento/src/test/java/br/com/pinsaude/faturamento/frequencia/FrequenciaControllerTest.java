package br.com.pinsaude.faturamento.frequencia;

import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.controller.FrequenciaController;
import br.com.pinsaude.faturamento.service.FrequenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
}
