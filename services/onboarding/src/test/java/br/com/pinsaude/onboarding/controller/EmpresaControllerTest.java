package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.RegimeTributario;
import br.com.pinsaude.onboarding.dto.EmpresaPageResponse;
import br.com.pinsaude.onboarding.dto.EmpresaRequest;
import br.com.pinsaude.onboarding.dto.EmpresaResponse;
import br.com.pinsaude.onboarding.service.EmpresaService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
class EmpresaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    EmpresaService empresaService;

    private static final String TENANT_CNPJ = "11.222.333/0001-81";
    private static final UUID ID = UUID.randomUUID();

    private EmpresaResponse empresaResponse() {
        return new EmpresaResponse(ID, TENANT_CNPJ, "Clínica Teste", "1234/2024",
            "São Paulo", "3550308", RegimeTributario.SIMPLES_NACIONAL, null,
            true, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private EmpresaPageResponse pageResponse() {
        return new EmpresaPageResponse(List.of(empresaResponse()), 0, 20, 1, 1);
    }

    private EmpresaRequest requestValido() {
        return new EmpresaRequest(TENANT_CNPJ, "Clínica Teste", "1234/2024",
            "São Paulo", "3550308", RegimeTributario.SIMPLES_NACIONAL, null);
    }

    @Test
    void gestao_podeListar() throws Exception {
        when(empresaService.listar(anyString(), anyInt(), anyInt())).thenReturn(pageResponse());

        mockMvc.perform(get("/api/empresas")
                .with(jwt()
                    .jwt(j -> j.claim("cnpj_id", TENANT_CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void operacao_podeListar() throws Exception {
        when(empresaService.listar(anyString(), anyInt(), anyInt())).thenReturn(pageResponse());

        mockMvc.perform(get("/api/empresas")
                .with(jwt()
                    .jwt(j -> j.claim("cnpj_id", TENANT_CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isOk());
    }

    @Test
    void medico_naoPodelListar_retorna403() throws Exception {
        mockMvc.perform(get("/api/empresas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/empresas"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getBorId_retorna200() throws Exception {
        when(empresaService.buscarPorId(any(UUID.class), anyString()))
            .thenReturn(empresaResponse());

        mockMvc.perform(get("/api/empresas/{id}", ID)
                .with(jwt()
                    .jwt(j -> j.claim("cnpj_id", TENANT_CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cnpj").value(TENANT_CNPJ));
    }

    @Test
    void getById_inexistente_retorna404() throws Exception {
        when(empresaService.buscarPorId(any(UUID.class), anyString()))
            .thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Empresa não encontrada"));

        mockMvc.perform(get("/api/empresas/{id}", UUID.randomUUID())
                .with(jwt()
                    .jwt(j -> j.claim("cnpj_id", TENANT_CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void post_cnpjInvalido_retorna400() throws Exception {
        EmpresaRequest req = new EmpresaRequest("11.222.333/0001-44", "Clínica", "1234/2024",
            null, null, RegimeTributario.SIMPLES_NACIONAL, null);

        mockMvc.perform(post("/api/empresas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.erros[0]").value(org.hamcrest.Matchers.containsString("cnpj")));
    }

    @Test
    void post_corpoValido_retorna201() throws Exception {
        when(empresaService.criar(any(EmpresaRequest.class))).thenReturn(empresaResponse());

        mockMvc.perform(post("/api/empresas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido()))
                .with(jwt()
                    .jwt(j -> j.claim("cnpj_id", TENANT_CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"));
    }

    @Test
    void post_operacaoNaoPodeCriar_retorna403() throws Exception {
        mockMvc.perform(post("/api/empresas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido()))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void put_retorna200() throws Exception {
        when(empresaService.atualizar(any(UUID.class), any(EmpresaRequest.class), anyString()))
            .thenReturn(empresaResponse());

        mockMvc.perform(put("/api/empresas/{id}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestValido()))
                .with(jwt()
                    .jwt(j -> j.claim("cnpj_id", TENANT_CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk());
    }

    @Test
    void delete_retorna204() throws Exception {
        doNothing().when(empresaService).deletar(any(UUID.class), anyString());

        mockMvc.perform(delete("/api/empresas/{id}", ID)
                .with(jwt()
                    .jwt(j -> j.claim("cnpj_id", TENANT_CNPJ))
                    .authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isNoContent());
    }
}
