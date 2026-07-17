package br.com.pinsaude.ledger.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa apenas a camada de autorização (short-circuit antes de tocar o banco).
 * Papéis negados recebem 403 sem chegar ao controller; os cenários felizes (200) e os
 * critérios de aceite funcionais são validados em {@code LedgerApiIntegrationTest} (Testcontainers).
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
class RbacIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static final String LANCAMENTOS = "/api/ledger/lancamentos";

    // Corpo válido: garante que o gate seja a autorização (@PreAuthorize), não a validação (@Valid).
    private static final String BODY_VALIDO = """
        {"cnpjIdTenant":"12345678000195","competencia":"2026-07","tipoOrigem":"NOTA",
         "descricao":"rbac","correlationId":"rbac-test",
         "partidas":[{"contaCodigo":"1.1.01","tipo":"DEBITO","valorCentavos":100},
                     {"contaCodigo":"2.1.02","tipo":"CREDITO","valorCentavos":100}]}
        """;

    // ─── Leitura: médico e operação são bloqueados ────────────────────────────

    @Test
    void medico_naoPodeListarLancamentos_403() throws Exception {
        mockMvc.perform(get(LANCAMENTOS)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void operacao_naoPodeListarLancamentos_403() throws Exception {
        mockMvc.perform(get(LANCAMENTOS)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void saldo_medico_403() throws Exception {
        mockMvc.perform(get("/api/ledger/saldo/" + UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    // ─── Escrita (POST): só service token; papéis de usuário final são bloqueados ──

    @Test
    void financeiro_naoPodeCriarLancamento_403() throws Exception {
        mockMvc.perform(post(LANCAMENTOS).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestao_naoPodeCriarLancamento_403() throws Exception {
        mockMvc.perform(post(LANCAMENTOS).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isForbidden());
    }

    // ─── Sem autenticação ─────────────────────────────────────────────────────

    @Test
    void semAutenticacao_401() throws Exception {
        mockMvc.perform(get(LANCAMENTOS)).andExpect(status().isUnauthorized());
    }
}
