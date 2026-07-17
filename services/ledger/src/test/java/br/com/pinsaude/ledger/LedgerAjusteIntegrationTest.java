package br.com.pinsaude.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajuste manual com dupla aprovação (EPIC-08.4). Valida os critérios de aceite de backend:
 * aprovação exige segundo usuário com perfil diferente, e o lançamento aprovado aparece
 * imediatamente no extrato. Contra PostgreSQL real (Flyway V1..V4).
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
@Testcontainers
class LedgerAjusteIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private static final String TENANT = "12345678000195";

    private RequestPostProcessor auth(String userId, String perfil) {
        return jwt().jwt(j -> j.subject(userId).claim("cnpj_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil));
    }

    private String criarAjusteBody(UUID medicoId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("medicoId", medicoId);
        body.put("competencia", "2026-07");
        body.put("contaDebitoCodigo", "2.1.02");   // Repasses a Médicos a Pagar
        body.put("contaCreditoCodigo", "3.1.01");  // Receita de Honorários
        body.put("valorCentavos", 20_000L);
        body.put("motivo", "Estorno de taxa cobrada indevidamente");
        return json.writeValueAsString(body);
    }

    @Test
    void fluxoDuplaAprovacao_completo() throws Exception {
        UUID medico = UUID.randomUUID();

        // 1) Solicitante (financeiro, user-A) cria ajuste → PENDENTE
        MvcResult criado = mockMvc.perform(post("/api/ledger/ajustes")
                        .contentType(MediaType.APPLICATION_JSON).content(criarAjusteBody(medico))
                        .with(auth("user-A", "financeiro")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.lancamentoId").doesNotExist())
                .andReturn();
        UUID ajusteId = UUID.fromString(json.readTree(criado.getResponse().getContentAsString()).get("id").asText());

        // 2) Mesmo solicitante tenta aprovar → 422 (precisa de um segundo usuário)
        mockMvc.perform(post("/api/ledger/ajustes/" + ajusteId + "/aprovar")
                        .with(auth("user-A", "financeiro")))
                .andExpect(status().isUnprocessableEntity());

        // 3) Usuário diferente, MESMO perfil (financeiro) → 422 (perfil deve ser diferente)
        mockMvc.perform(post("/api/ledger/ajustes/" + ajusteId + "/aprovar")
                        .with(auth("user-B", "financeiro")))
                .andExpect(status().isUnprocessableEntity());

        // 4) Segundo usuário com PERFIL DIFERENTE (gestao) → aprova e gera o lançamento
        mockMvc.perform(post("/api/ledger/ajustes/" + ajusteId + "/aprovar")
                        .with(auth("user-C", "gestao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.lancamentoId").exists());

        // 5) Lançamento de ajuste aparece IMEDIATAMENTE no extrato (lista filtrada por AJUSTE)
        mockMvc.perform(get("/api/ledger/lancamentos")
                        .param("medicoId", medico.toString())
                        .param("tipoOrigem", "AJUSTE")
                        .with(auth("user-C", "gestao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // 6) Extrato do médico traz o item com origemId = ajusteId (link da coluna Origem)
        MvcResult extrato = mockMvc.perform(get("/api/ledger/extrato/" + medico)
                        .with(auth("user-C", "gestao")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = json.readTree(extrato.getResponse().getContentAsString());
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("origemId").asText()).isEqualTo(ajusteId.toString());
        assertThat(arr.get(0).get("tipoOrigem").asText()).isEqualTo("AJUSTE");
    }

    @Test
    void aprovarAjusteJaDecidido_retorna409() throws Exception {
        UUID medico = UUID.randomUUID();
        MvcResult criado = mockMvc.perform(post("/api/ledger/ajustes")
                        .contentType(MediaType.APPLICATION_JSON).content(criarAjusteBody(medico))
                        .with(auth("user-A", "financeiro")))
                .andExpect(status().isCreated()).andReturn();
        UUID ajusteId = UUID.fromString(json.readTree(criado.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/ledger/ajustes/" + ajusteId + "/aprovar")
                        .with(auth("user-C", "gestao")))
                .andExpect(status().isOk());
        // segunda aprovação do mesmo ajuste → 409
        mockMvc.perform(post("/api/ledger/ajustes/" + ajusteId + "/aprovar")
                        .with(auth("user-D", "contabil")))
                .andExpect(status().isConflict());
    }

    @Test
    void listarPendentes_retornaSomentePendentes() throws Exception {
        mockMvc.perform(post("/api/ledger/ajustes")
                        .contentType(MediaType.APPLICATION_JSON).content(criarAjusteBody(UUID.randomUUID()))
                        .with(auth("user-A", "financeiro")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/ledger/ajustes").param("status", "PENDENTE")
                        .with(auth("user-C", "gestao")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }
}
