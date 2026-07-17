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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração da API do Ledger contra PostgreSQL real (Testcontainers),
 * com Flyway aplicando V1 (schema/triggers) e V2 (enums→varchar).
 * Cobre os 5 critérios de aceite do EPIC-08.2.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
@Testcontainers
class LedgerApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private static final String TENANT = "12345678000195";
    private static final String CONTA_RECEBER  = "1.1.01"; // Honorários a Receber (ATIVO)
    private static final String CONTA_REPASSE  = "2.1.02"; // Repasses a Médicos a Pagar (PASSIVO)
    private static final String CONTA_TRANSIT  = "9.1.01"; // Conta Transitória (INTERMEDIARIO)

    private static SimpleGrantedAuthority role(String r) { return new SimpleGrantedAuthority("ROLE_" + r); }

    private Map<String, Object> partida(String contaCodigo, String tipo, long valorCentavos) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("contaCodigo", contaCodigo);
        p.put("tipo", tipo);
        p.put("valorCentavos", valorCentavos);
        return p;
    }

    @SafeVarargs
    private MvcResult postLancamento(String role, UUID medicoId, String tipoOrigem, String data,
                                     String competencia, String correlationId,
                                     Map<String, Object>... partidas) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cnpjIdTenant", TENANT);
        body.put("medicoId", medicoId);
        body.put("dataLancamento", data);
        body.put("competencia", competencia);
        body.put("tipoOrigem", tipoOrigem);
        body.put("descricao", "Lançamento de teste " + correlationId);
        body.put("correlationId", correlationId);
        body.put("partidas", List.of(partidas));
        return mockMvc.perform(post("/api/ledger/lancamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))
                        .with(jwt().authorities(role(role))))
                .andReturn();
    }

    // ─── Critério 3: lançamento desbalanceado retorna 422 ─────────────────────

    @Test
    void criarLancamentoDesbalanceado_retorna422() throws Exception {
        MvcResult res = postLancamento("service", UUID.randomUUID(), "AJUSTE", "2026-07-01",
                "2026-07", "DESBAL-" + UUID.randomUUID(),
                partida(CONTA_RECEBER, "DEBITO", 100000),
                partida(CONTA_REPASSE, "CREDITO", 50000));
        assertThat(res.getResponse().getStatus()).isEqualTo(422);
    }

    // ─── Critério 4: POST só com service token ────────────────────────────────

    @Test
    void criarLancamento_comServiceToken_201_comFinanceiro_403() throws Exception {
        UUID medico = UUID.randomUUID();
        // service token → 201
        MvcResult ok = postLancamento("service", medico, "NOTA", "2026-07-01", "2026-07",
                "SVC-" + UUID.randomUUID(),
                partida(CONTA_RECEBER, "DEBITO", 850000),
                partida(CONTA_REPASSE, "CREDITO", 850000));
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);

        // token de usuário final (financeiro) → 403
        MvcResult denied = postLancamento("financeiro", medico, "NOTA", "2026-07-01", "2026-07",
                "FIN-" + UUID.randomUUID(),
                partida(CONTA_RECEBER, "DEBITO", 850000),
                partida(CONTA_REPASSE, "CREDITO", 850000));
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
    }

    // ─── Critério 1: saldo = sum(créditos) - sum(débitos) na conta do médico ──

    @Test
    void saldoDoMedico_creditosMenosDebitos() throws Exception {
        UUID medico = UUID.randomUUID();
        // A: médico recebe 8500 (crédito no repasse)
        postLancamento("service", medico, "NOTA", "2026-07-01", "2026-07", "A-" + medico,
                partida(CONTA_RECEBER, "DEBITO", 850000),
                partida(CONTA_REPASSE, "CREDITO", 850000));
        // B: médico recebe 5000
        postLancamento("service", medico, "NOTA", "2026-07-05", "2026-07", "B-" + medico,
                partida(CONTA_RECEBER, "DEBITO", 500000),
                partida(CONTA_REPASSE, "CREDITO", 500000));
        // C: repasse de 8500 pago (débito no repasse)
        postLancamento("service", medico, "REPASSE", "2026-07-10", "2026-07", "C-" + medico,
                partida(CONTA_REPASSE, "DEBITO", 850000),
                partida(CONTA_TRANSIT, "CREDITO", 850000));

        // saldo = 8500 + 5000 - 8500 = 5000.00
        MvcResult res = mockMvc.perform(get("/api/ledger/saldo/" + medico)
                        .with(jwt().authorities(role("gestao"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = json.readTree(res.getResponse().getContentAsString());
        assertThat(new BigDecimal(node.get("saldo").asText())).isEqualByComparingTo("5000.00");
    }

    // ─── Critério 2: extrato com saldo running em ordem cronológica ───────────

    @Test
    void extratoDoMedico_saldoRunningCronologico() throws Exception {
        UUID medico = UUID.randomUUID();
        postLancamento("service", medico, "NOTA", "2026-07-01", "2026-07", "EA-" + medico,
                partida(CONTA_RECEBER, "DEBITO", 850000),
                partida(CONTA_REPASSE, "CREDITO", 850000));
        postLancamento("service", medico, "NOTA", "2026-07-05", "2026-07", "EB-" + medico,
                partida(CONTA_RECEBER, "DEBITO", 500000),
                partida(CONTA_REPASSE, "CREDITO", 500000));
        postLancamento("service", medico, "REPASSE", "2026-07-10", "2026-07", "EC-" + medico,
                partida(CONTA_REPASSE, "DEBITO", 850000),
                partida(CONTA_TRANSIT, "CREDITO", 850000));

        MvcResult res = mockMvc.perform(get("/api/ledger/extrato/" + medico)
                        .with(jwt().authorities(role("gestao"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = json.readTree(res.getResponse().getContentAsString());

        assertThat(arr).hasSize(3);
        // Ordem cronológica com saldo running: 8500 → 13500 → 5000
        assertThat(new BigDecimal(arr.get(0).get("saldoApos").asText())).isEqualByComparingTo("8500.00");
        assertThat(new BigDecimal(arr.get(1).get("saldoApos").asText())).isEqualByComparingTo("13500.00");
        assertThat(new BigDecimal(arr.get(2).get("saldoApos").asText())).isEqualByComparingTo("5000.00");
        // valor do repasse é negativo (débito reduz o que a Pin deve)
        assertThat(new BigDecimal(arr.get(2).get("valor").asText())).isEqualByComparingTo("-8500.00");
    }

    // ─── Critério 5: paginação ────────────────────────────────────────────────

    @Test
    void listagem_paginacaoFunciona() throws Exception {
        UUID medico = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            postLancamento("service", medico, "NOTA", "2026-07-0" + (i + 1), "2026-07",
                    "PAG-" + medico + "-" + i,
                    partida(CONTA_RECEBER, "DEBITO", 100000),
                    partida(CONTA_REPASSE, "CREDITO", 100000));
        }

        mockMvc.perform(get("/api/ledger/lancamentos")
                        .param("medicoId", medico.toString())
                        .param("size", "2")
                        .param("page", "0")
                        .with(jwt().authorities(role("financeiro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2));

        // última página tem 1 item
        mockMvc.perform(get("/api/ledger/lancamentos")
                        .param("medicoId", medico.toString())
                        .param("size", "2")
                        .param("page", "2")
                        .with(jwt().authorities(role("financeiro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ─── Detalhe + filtro por tipo_origem (enum varchar) + idempotência ───────

    @Test
    void detalhe_comPartidas_eFiltroPorTipoOrigem() throws Exception {
        UUID medico = UUID.randomUUID();
        String corr = "DET-" + medico;
        MvcResult criado = postLancamento("service", medico, "NOTA", "2026-07-01", "2026-07", corr,
                partida(CONTA_RECEBER, "DEBITO", 850000),
                partida(CONTA_REPASSE, "CREDITO", 850000));
        assertThat(criado.getResponse().getStatus()).isEqualTo(201);
        UUID id = UUID.fromString(json.readTree(criado.getResponse().getContentAsString()).get("id").asText());

        // Detalhe com 2 partidas e valorTotal em reais
        mockMvc.perform(get("/api/ledger/lancamentos/" + id)
                        .with(jwt().authorities(role("contabil"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidas.length()").value(2))
                .andExpect(jsonPath("$.valorTotal").value(8500.00));

        // Filtro por tipo_origem (coluna varchar) funciona
        mockMvc.perform(get("/api/ledger/lancamentos")
                        .param("medicoId", medico.toString())
                        .param("tipoOrigem", "NOTA")
                        .with(jwt().authorities(role("financeiro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Idempotência: repetir o mesmo correlationId não duplica
        postLancamento("service", medico, "NOTA", "2026-07-01", "2026-07", corr,
                partida(CONTA_RECEBER, "DEBITO", 850000),
                partida(CONTA_REPASSE, "CREDITO", 850000));
        mockMvc.perform(get("/api/ledger/lancamentos")
                        .param("medicoId", medico.toString())
                        .with(jwt().authorities(role("financeiro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
