package br.com.pinsaude.onboarding.controller;

import br.com.pinsaude.onboarding.domain.RegimeTributario;
import br.com.pinsaude.onboarding.dto.EmpresaRequest;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
@Testcontainers
class ConfiguracaoFiscalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EmpresaRepository empresaRepository;

    private String empresaId;

    @BeforeEach
    void setup() throws Exception {
        empresaRepository.deleteAll();
        EmpresaRequest req = new EmpresaRequest(
            "11.222.333/0001-81", "Clínica Fiscal Teste", "IM-001",
            "São Paulo", "3550308", RegimeTributario.SIMPLES_NACIONAL);

        String json = mockMvc.perform(post("/api/empresas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        empresaId = objectMapper.readTree(json).get("id").asText();
    }

    private Map<String, Object> payloadCompleto() {
        return Map.of(
            "cnaeCodigo", "8630503",
            "cnaeDescricao", "Atividade médica ambulatorial",
            "codigoLc116", "4.02",
            "indicadorEquiparacaoHospitalar", false,
            "vencimentoCertificadoA1", "2027-12-31",
            "aliquota", Map.of(
                "competencia", "2026-06",
                "iss", 2.00,
                "ir", 1.50,
                "csll", 1.00,
                "pis", 0.65,
                "cofins", 3.00,
                "regimePresuncao", "CHEIA"
            )
        );
    }

    @Test
    void buscar_semConfigSalva_retornaConfigVazia() throws Exception {
        mockMvc.perform(get("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.empresaId").value(empresaId))
            .andExpect(jsonPath("$.cnaeCodigo").value(nullValue()))
            .andExpect(jsonPath("$.statusCertificado").value("NAO_CONFIGURADO"))
            .andExpect(jsonPath("$.historicoAliquotas").isArray())
            .andExpect(jsonPath("$.historicoAliquotas").isEmpty());
    }

    @Test
    void salvar_configCompleta_persisteERetorna() throws Exception {
        mockMvc.perform(put("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payloadCompleto()))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cnaeCodigo").value("8630503"))
            .andExpect(jsonPath("$.codigoLc116").value("4.02"))
            .andExpect(jsonPath("$.statusCertificado").value("VALIDO"))
            .andExpect(jsonPath("$.historicoAliquotas").isArray())
            .andExpect(jsonPath("$.historicoAliquotas[0].competencia").value("2026-06"))
            .andExpect(jsonPath("$.historicoAliquotas[0].iss").value(2.00));
    }

    @Test
    void salvar_duasCompetencias_mantemHistoricoSeparado() throws Exception {
        mockMvc.perform(put("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payloadCompleto()))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk());

        Map<String, Object> payload2 = Map.of(
            "cnaeCodigo", "8630503",
            "cnaeDescricao", "Atividade médica ambulatorial",
            "codigoLc116", "4.02",
            "indicadorEquiparacaoHospitalar", false,
            "aliquota", Map.of(
                "competencia", "2026-07",
                "iss", 3.00,
                "ir", 1.50,
                "csll", 1.00,
                "pis", 0.65,
                "cofins", 3.00,
                "regimePresuncao", "REDUZIDA"
            )
        );

        mockMvc.perform(put("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.historicoAliquotas", hasSize(2)))
            .andExpect(jsonPath("$.historicoAliquotas[0].competencia").value("2026-07"))
            .andExpect(jsonPath("$.historicoAliquotas[1].competencia").value("2026-06"));
    }

    @Test
    void salvar_mesmaCompetencia_atualizaSemDuplicar() throws Exception {
        mockMvc.perform(put("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payloadCompleto()))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk());

        Map<String, Object> payloadAtualizado = Map.of(
            "cnaeCodigo", "8630503",
            "cnaeDescricao", "Atividade médica ambulatorial",
            "codigoLc116", "4.03",
            "indicadorEquiparacaoHospitalar", true,
            "aliquota", Map.of(
                "competencia", "2026-06",
                "iss", 5.00,
                "ir", 1.50,
                "csll", 1.00,
                "pis", 0.65,
                "cofins", 3.00,
                "regimePresuncao", "CHEIA"
            )
        );

        mockMvc.perform(put("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payloadAtualizado))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.codigoLc116").value("4.03"))
            .andExpect(jsonPath("$.indicadorEquiparacaoHospitalar").value(true))
            .andExpect(jsonPath("$.historicoAliquotas", hasSize(1)))
            .andExpect(jsonPath("$.historicoAliquotas[0].iss").value(5.00));
    }

    @Test
    void salvar_aliquotaForaDoRange_retorna400() throws Exception {
        Map<String, Object> invalido = Map.of(
            "cnaeCodigo", "8630503",
            "cnaeDescricao", "Atividade médica",
            "codigoLc116", "4.02",
            "indicadorEquiparacaoHospitalar", false,
            "aliquota", Map.of(
                "competencia", "2026-06",
                "iss", 150.00,   // inválido — acima de 100%
                "ir", 1.50, "csll", 1.00, "pis", 0.65, "cofins", 3.00,
                "regimePresuncao", "CHEIA"
            )
        );

        mockMvc.perform(put("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalido))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void salvar_competenciaFormatoInvalido_retorna400() throws Exception {
        Map<String, Object> invalido = Map.of(
            "cnaeCodigo", "8630503",
            "cnaeDescricao", "Atividade médica",
            "codigoLc116", "4.02",
            "indicadorEquiparacaoHospitalar", false,
            "aliquota", Map.of(
                "competencia", "06/2026",  // formato inválido
                "iss", 2.00, "ir", 1.50, "csll", 1.00, "pis", 0.65, "cofins", 3.00,
                "regimePresuncao", "CHEIA"
            )
        );

        mockMvc.perform(put("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalido))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void operacao_naoPodeAcessar_retorna403() throws Exception {
        mockMvc.perform(get("/api/empresas/{id}/configuracao-fiscal", empresaId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void empresaInexistente_retorna404() throws Exception {
        String idInexistente = "00000000-0000-0000-0000-000000000000";
        mockMvc.perform(get("/api/empresas/{id}/configuracao-fiscal", idInexistente)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
            .andExpect(status().isNotFound());
    }
}
