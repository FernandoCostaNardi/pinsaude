package br.com.pinsaude.ledger.controller;

import br.com.pinsaude.ledger.config.SecurityConfig;
import br.com.pinsaude.ledger.repository.ContaLedgerRepository;
import br.com.pinsaude.ledger.service.AjusteManualService;
import br.com.pinsaude.ledger.service.LancamentoService;
import br.com.pinsaude.ledger.service.SaldoCalculator;
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

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERFIL-22 — regressão do catálogo perm_* (ADR-004) em LedgerController.
 * Grupo único (financeiro,gestao,contabil) via GET /saldo/{medicoId} (PERFIL-20).
 * Cobertura extra: perm_ledger NUNCA deve destravar POST /lancamentos, que é
 * deliberadamente hasRole('service')-only (carve-out documentado em PERFIL-20/CLAUDE.md).
 */
@WebMvcTest(LedgerController.class)
@Import(SecurityConfig.class)
class LedgerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LancamentoService lancamentoService;

    @MockBean
    SaldoCalculator saldoCalculator;

    @MockBean
    AjusteManualService ajusteManualService;

    @MockBean
    ContaLedgerRepository contaRepo;

    private static final SimpleGrantedAuthority PERM_LEDGER = new SimpleGrantedAuthority("ROLE_perm_ledger");
    private static final SimpleGrantedAuthority PERM_OUTRO_DOMINIO = new SimpleGrantedAuthority("ROLE_perm_producao");

    private static final String CRIAR_LANCAMENTO_BODY = """
            {"cnpjIdTenant":"11.222.333/0001-81","competencia":"2026-08","tipoOrigem":"AJUSTE",
             "descricao":"teste","correlationId":"TESTE:%s",
             "partidas":[{"contaCodigo":"2.1.02","tipo":"DEBITO","valorCentavos":100},
                         {"contaCodigo":"1.1.02","tipo":"CREDITO","valorCentavos":100}]}
            """.formatted(UUID.randomUUID());

    // ─── Único grupo: financeiro,gestao,contabil — GET /saldo/{medicoId} ──────

    @ParameterizedTest
    @ValueSource(strings = {"financeiro", "gestao", "contabil"})
    void saldo_papelLegado_retorna200(String role) throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(saldoCalculator.saldoCentavos(medicoId)).thenReturn(0L);
        mockMvc.perform(get("/api/ledger/saldo/{medicoId}", medicoId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
    }

    @Test
    void saldo_permLedger_retorna200() throws Exception {
        UUID medicoId = UUID.randomUUID();
        when(saldoCalculator.saldoCentavos(medicoId)).thenReturn(0L);
        mockMvc.perform(get("/api/ledger/saldo/{medicoId}", medicoId).with(jwt().authorities(PERM_LEDGER)))
                .andExpect(status().isOk());
    }

    @Test
    void saldo_medico_retorna403() throws Exception {
        mockMvc.perform(get("/api/ledger/saldo/{medicoId}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void saldo_permOutroDominio_retorna403() throws Exception {
        mockMvc.perform(get("/api/ledger/saldo/{medicoId}", UUID.randomUUID()).with(jwt().authorities(PERM_OUTRO_DOMINIO)))
                .andExpect(status().isForbidden());
    }

    // ─── Carve-out deliberado: POST /lancamentos continua hasRole('service') only ──

    @Test
    void criar_permLedger_retorna403() throws Exception {
        // perm_ledger cobre leitura/ajuste, mas NUNCA a criação bruta de lançamento —
        // esse endpoint é exclusivo de service accounts internas (ROLE_service).
        mockMvc.perform(post("/api/ledger/lancamentos")
                        .contentType(MediaType.APPLICATION_JSON).content(CRIAR_LANCAMENTO_BODY)
                        .with(jwt().authorities(PERM_LEDGER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void criar_gestao_retorna403() throws Exception {
        // Nem o papel legado com mais acesso no resto do sistema tem ROLE_service.
        mockMvc.perform(post("/api/ledger/lancamentos")
                        .contentType(MediaType.APPLICATION_JSON).content(CRIAR_LANCAMENTO_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/ledger/saldo/{medicoId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
