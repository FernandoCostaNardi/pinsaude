package br.com.pinsaude.faturamento.conciliacao;

import br.com.pinsaude.faturamento.conciliacao.messaging.MatchingProducer;
import br.com.pinsaude.faturamento.config.SecurityConfig;
import br.com.pinsaude.faturamento.controller.ConciliacaoController;
import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.dto.ExtratoResponse;
import br.com.pinsaude.faturamento.dto.LancamentoExtratoResponse;
import br.com.pinsaude.faturamento.service.ExtratoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConciliacaoController.class)
@Import(SecurityConfig.class)
class ConciliacaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ExtratoService service;

    @MockBean
    MatchingProducer matchingProducer;

    private static final ExtratoResponse EXTRATO_MOCK = new ExtratoResponse(
            UUID.randomUUID(), "extrato-inter.csv", BancoEnum.INTER,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
            StatusImportacao.OK, 2, "operacao@pinsaude.com.br", OffsetDateTime.now()
    );

    private static final LancamentoExtratoResponse LANCAMENTO_MOCK = new LancamentoExtratoResponse(
            UUID.randomUUID(), UUID.randomUUID(),
            LocalDate.of(2026, 6, 1), "PIX RECEBIDO",
            150_000L, TipoLancamentoExtrato.CREDITO, "FIT001",
            StatusConciliacao.PENDENTE, 0
    );

    // ─── Upload ───────────────────────────────────────────────────────────────

    @Test
    void upload_operacao_interCsv_retorna201() throws Exception {
        when(service.upload(any(), eq(BancoEnum.INTER), any(), any())).thenReturn(EXTRATO_MOCK);

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato-inter.csv",
                "text/csv", "Data;Tipo;Desc;Valor;Saldo\n01/06/2026;Entrada;PIX;500,00;1000,00".getBytes());

        mockMvc.perform(multipart("/api/conciliacao/extratos/upload")
                        .file(arquivo)
                        .param("banco", "INTER")
                        .param("data_inicio", "2026-06-01")
                        .param("data_fim", "2026-06-30")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeArquivo").value("extrato-inter.csv"))
                .andExpect(jsonPath("$.statusImportacao").value("OK"))
                .andExpect(jsonPath("$.totalLancamentos").value(2));
    }

    @Test
    void upload_financeiro_btgCsv_retorna201() throws Exception {
        ExtratoResponse btgResp = new ExtratoResponse(
                UUID.randomUUID(), "extrato-btg.csv", BancoEnum.BTG,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                StatusImportacao.OK, 1, "financeiro@pinsaude.com.br", OffsetDateTime.now()
        );
        when(service.upload(any(), eq(BancoEnum.BTG), any(), any())).thenReturn(btgResp);

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato-btg.csv",
                "text/csv", "Data,Lancamento,Valor,Saldo\n01/06/2026,PIX,1500.00,10000.00".getBytes());

        mockMvc.perform(multipart("/api/conciliacao/extratos/upload")
                        .file(arquivo)
                        .param("banco", "BTG")
                        .param("data_inicio", "2026-06-01")
                        .param("data_fim", "2026-06-30")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_financeiro"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.banco").value("BTG"));
    }

    @Test
    void upload_extratoJaExiste_retorna409() throws Exception {
        when(service.upload(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(CONFLICT,
                        "Extrato já importado: mesmo arquivo e período já existem para este tenant."));

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato.csv",
                "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/conciliacao/extratos/upload")
                        .file(arquivo)
                        .param("banco", "INTER")
                        .param("data_inicio", "2026-06-01")
                        .param("data_fim", "2026-06-30")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isConflict());
    }

    @Test
    void upload_arquivoInvalido_retorna400() throws Exception {
        when(service.upload(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Erro ao processar arquivo: CSV inválido"));

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "invalido.csv",
                "text/csv", "conteudo_invalido".getBytes());

        mockMvc.perform(multipart("/api/conciliacao/extratos/upload")
                        .file(arquivo)
                        .param("banco", "INTER")
                        .param("data_inicio", "2026-06-01")
                        .param("data_fim", "2026-06-30")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_medico_retorna403() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato.csv",
                "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/conciliacao/extratos/upload")
                        .file(arquivo)
                        .param("banco", "INTER")
                        .param("data_inicio", "2026-06-01")
                        .param("data_fim", "2026-06-30")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_medico"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_semAutenticacao_retorna401() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato.csv",
                "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/conciliacao/extratos/upload")
                        .file(arquivo)
                        .param("banco", "INTER")
                        .param("data_inicio", "2026-06-01")
                        .param("data_fim", "2026-06-30"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Listar extratos ──────────────────────────────────────────────────────

    @Test
    void listarExtratos_gestao_retorna200ComLista() throws Exception {
        when(service.listarExtratos()).thenReturn(List.of(EXTRATO_MOCK));

        mockMvc.perform(get("/api/conciliacao/extratos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_gestao"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].banco").value("INTER"));
    }

    // ─── Listar lançamentos ───────────────────────────────────────────────────

    @Test
    void listarLancamentos_contabil_retorna200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listarLancamentos(id, null)).thenReturn(List.of(LANCAMENTO_MOCK));

        mockMvc.perform(get("/api/conciliacao/extratos/" + id + "/lancamentos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_contabil"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("CREDITO"))
                .andExpect(jsonPath("$[0].statusConciliacao").value("PENDENTE"));
    }

    @Test
    void listarLancamentos_comFiltroStatus_passaStatusAoService() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listarLancamentos(id, "PENDENTE")).thenReturn(List.of(LANCAMENTO_MOCK));

        mockMvc.perform(get("/api/conciliacao/extratos/" + id + "/lancamentos")
                        .param("status", "PENDENTE")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operacao"))))
                .andExpect(status().isOk());

        verify(service).listarLancamentos(id, "PENDENTE");
    }
}
