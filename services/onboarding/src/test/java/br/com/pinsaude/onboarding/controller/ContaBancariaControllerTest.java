package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.TipoConta;
import br.com.pinsaude.onboarding.dto.ContaBancariaRequest;
import br.com.pinsaude.onboarding.dto.ContaBancariaResponse;
import br.com.pinsaude.onboarding.service.ContaBancariaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
class ContaBancariaControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ContaBancariaService contaBancariaService;

    private static final UUID EMPRESA_ID = UUID.randomUUID();
    private static final UUID CONTA_ID   = UUID.randomUUID();

    private ContaBancariaResponse contaResponse() {
        return new ContaBancariaResponse(CONTA_ID, "Itaú", "1234", "56789-0",
            TipoConta.CORRENTE, null, false, true, OffsetDateTime.now());
    }

    private ContaBancariaRequest requestValido() {
        return new ContaBancariaRequest("Itaú", "1234", "56789-0", TipoConta.CORRENTE, null, false);
    }

    @Test
    void gestao_podeListar_retorna200() throws Exception {
        when(contaBancariaService.listar(any(UUID.class)))
            .thenReturn(List.of(contaResponse()));

        mockMvc.perform(get("/api/empresas/{empresaId}/contas", EMPRESA_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].banco").value("Itaú"));
    }

    @Test
    void operacao_naoPodeListar_retorna403() throws Exception {
        mockMvc.perform(get("/api/empresas/{empresaId}/contas", EMPRESA_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void medico_naoPodeListar_retorna403() throws Exception {
        mockMvc.perform(get("/api/empresas/{empresaId}/contas", EMPRESA_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/empresas/{empresaId}/contas", EMPRESA_ID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void gestao_podeCriar_retorna201() throws Exception {
        when(contaBancariaService.criar(any(UUID.class), any(ContaBancariaRequest.class)))
            .thenReturn(contaResponse());

        mockMvc.perform(post("/api/empresas/{empresaId}/contas", EMPRESA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido()))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"));
    }

    @Test
    void operacao_naoPodeCriar_retorna403() throws Exception {
        mockMvc.perform(post("/api/empresas/{empresaId}/contas", EMPRESA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido()))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void post_camposObrigatoriosFaltando_retorna400() throws Exception {
        String payload = """
            {"banco":"","agencia":"","conta":"","tipoConta":null}
            """;

        mockMvc.perform(post("/api/empresas/{empresaId}/contas", EMPRESA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void gestao_podeAtualizar_retorna200() throws Exception {
        when(contaBancariaService.atualizar(any(UUID.class), any(UUID.class), any(ContaBancariaRequest.class)))
            .thenReturn(contaResponse());

        mockMvc.perform(put("/api/empresas/{empresaId}/contas/{id}", EMPRESA_ID, CONTA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido()))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk());
    }

    @Test
    void gestao_podeDeletar_retorna204() throws Exception {
        doNothing().when(contaBancariaService).deletar(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/api/empresas/{empresaId}/contas/{id}", EMPRESA_ID, CONTA_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isNoContent());
    }
}
