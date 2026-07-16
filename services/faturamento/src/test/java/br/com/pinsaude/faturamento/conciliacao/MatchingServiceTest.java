package br.com.pinsaude.faturamento.conciliacao;

import br.com.pinsaude.faturamento.conciliacao.matching.MatchingService;
import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.repository.ConciliacaoRepository;
import br.com.pinsaude.faturamento.repository.LancamentoExtratoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import br.com.pinsaude.faturamento.service.CryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingServiceTest {

    @Mock ProducaoRepository       producaoRepo;
    @Mock ConciliacaoRepository    conciliacaoRepo;
    @Mock LancamentoExtratoRepository lancamentoRepo;
    @Mock CryptoService            cryptoService;

    @InjectMocks MatchingService service;

    // ─── calcularScore — unitários sobre a função de pontuação ─────────────────

    @Test
    void calcularScore_valorExatoECnpj_retorna80() {
        // valor exato (+40) + CNPJ (+40) + data longe > 30 dias (+0) = 80
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 1, 1), "PIX 12345678000190");
        Producao p = producao(100_000L, "2026-06");

        int score = service.calcularScore(l, p, "12345678000190");

        assertThat(score).isEqualTo(80);
    }

    @Test
    void calcularScore_valorExatoEDataDentro7Dias_retorna60() {
        // valor exato (+40) + data ≤ 7 dias (+20) + sem identidade (+0) = 60
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 6, 5), "PIX RECEBIDO");
        Producao p = producao(100_000L, "2026-06"); // firstDay = 2026-06-01, diff = 4 dias

        int score = service.calcularScore(l, p, null);

        assertThat(score).isEqualTo(60);
    }

    @Test
    void calcularScore_scorePerfeito_retorna100() {
        // valor exato (+40) + CNPJ (+40) + data ≤ 7 dias (+20) = 100
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 6, 3), "PIX 12345678000190");
        Producao p = producao(100_000L, "2026-06"); // firstDay = 2026-06-01, diff = 2 dias

        int score = service.calcularScore(l, p, "12345678000190");

        assertThat(score).isEqualTo(100);
    }

    @Test
    void calcularScore_valorToleranciaENome_retorna40() {
        // valor dentro de 1% (+20) + nome na descrição (+20) + data longe (+0) = 40
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 1, 1), "PIX EMPRESA XYZ LTDA");
        Producao p = producao(100_500L, "2026-06"); // diff = 0.5%
        p.getTomador().setRazaoSocialNome("EMPRESA XYZ LTDA");

        int score = service.calcularScore(l, p, null);

        assertThat(score).isEqualTo(40);
    }

    @Test
    void calcularScore_valorForaDe1pct_retornaZero() {
        // diferença de 50% → par descartado
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 6, 1), "PIX EMPRESA XYZ");
        Producao p = producao(150_000L, "2026-06");

        int score = service.calcularScore(l, p, null);

        assertThat(score).isZero();
    }

    @Test
    void calcularScore_dataDentro30Dias_retorna10DeData() {
        // valor exato (+40) + data 20 dias (+10) + sem identidade (+0) = 50
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 6, 21), "PIX");
        Producao p = producao(100_000L, "2026-06"); // firstDay = 2026-06-01, diff = 20 dias

        int score = service.calcularScore(l, p, null);

        assertThat(score).isEqualTo(50);
    }

    @Test
    void calcularScore_nomeFantasiaPresente_retorna20DeIdentidade() {
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 1, 1), "TED CLINICA ABC");
        Producao p = producao(100_000L, "2026-06");
        p.getTomador().setNomeFantasia("CLINICA ABC");

        int score = service.calcularScore(l, p, null);

        assertThat(score).isEqualTo(60); // 40 (valor) + 20 (nome fantasia)
    }

    // ─── processarExtrato — comportamento integrado ────────────────────────────

    @Test
    void processarExtrato_scoreAcimaDe90_criaConciliacaoEAtualiza() {
        UUID extratoId = UUID.randomUUID();
        String tenant  = "12345678000190";

        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 6, 3), "PIX 12345678000190");
        Producao p = producao(100_000L, "2026-06"); // score = 100
        p.getTomador().setCnpjCpfTomadorCriptografado(new byte[]{1, 2, 3});

        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(
                extratoId, TipoLancamentoExtrato.CREDITO.name(), StatusConciliacao.PENDENTE.name()))
                .thenReturn(List.of(l));
        when(producaoRepo.findCandidatasParaMatch(tenant,
                List.of(StatusProducao.EMITIDA.name())))
                .thenReturn(new ArrayList<>(List.of(p)));
        when(conciliacaoRepo.existsByLancamentoExtratoId(any())).thenReturn(false);
        when(cryptoService.decrypt(any())).thenReturn("12345678000190");
        when(conciliacaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lancamentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processarExtrato(extratoId, tenant);

        verify(conciliacaoRepo).save(argThat(c ->
                c.getTipoMatch() == TipoMatchEnum.AUTOMATICO && c.getScoreConfianca() == 100));
        verify(lancamentoRepo).save(argThat(le ->
                le.getStatusConciliacao() == StatusConciliacao.CONCILIADO));
    }

    @Test
    void processarExtrato_score50a89_atualizaScoreApenasNaoCriaConciliacao() {
        UUID extratoId = UUID.randomUUID();
        String tenant  = "12345678000190";

        // score = 40 (valor exato) + 20 (nome) + 0 (data > 30 dias) = 60
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 1, 1), "PIX EMPRESA XYZ");
        Producao p = producao(100_000L, "2026-06");
        p.getTomador().setRazaoSocialNome("EMPRESA XYZ");
        p.getTomador().setCnpjCpfTomadorCriptografado(null);

        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(any(), any(), any()))
                .thenReturn(List.of(l));
        when(producaoRepo.findCandidatasParaMatch(any(), any()))
                .thenReturn(new ArrayList<>(List.of(p)));
        when(conciliacaoRepo.existsByLancamentoExtratoId(any())).thenReturn(false);
        when(lancamentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processarExtrato(extratoId, tenant);

        verify(conciliacaoRepo, never()).save(any());
        verify(lancamentoRepo).save(argThat(le -> le.getScoreMatch() == 60));
    }

    @Test
    void processarExtrato_scoreMenorQue50_naoAlteraNada() {
        UUID extratoId = UUID.randomUUID();
        String tenant  = "12345678000190";

        // score = 40 (valor exato) + 0 (nome diferente) + 0 (data) = 40 < 50
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 1, 1), "PIX DESCONHECIDO");
        Producao p = producao(100_000L, "2026-06");
        p.getTomador().setRazaoSocialNome("OUTRO EMPRESA");
        p.getTomador().setCnpjCpfTomadorCriptografado(null);

        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(any(), any(), any()))
                .thenReturn(List.of(l));
        when(producaoRepo.findCandidatasParaMatch(any(), any()))
                .thenReturn(new ArrayList<>(List.of(p)));
        when(conciliacaoRepo.existsByLancamentoExtratoId(any())).thenReturn(false);

        service.processarExtrato(extratoId, tenant);

        verify(conciliacaoRepo, never()).save(any());
        verify(lancamentoRepo, never()).save(any());
    }

    @Test
    void processarExtrato_semCandidatos_naoFazNada() {
        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(any(), any(), any()))
                .thenReturn(List.of(lancamento(100_000L, LocalDate.now(), "PIX")));
        when(producaoRepo.findCandidatasParaMatch(any(), any()))
                .thenReturn(new ArrayList<>());

        service.processarExtrato(UUID.randomUUID(), "12345678000190");

        verify(conciliacaoRepo, never()).save(any());
    }

    @Test
    void processarExtrato_idempotente_pularLancamentosJaConciliados() {
        LancamentoExtrato l = lancamento(100_000L, LocalDate.now(), "PIX");
        Producao p = producao(100_000L, "2026-06");

        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(any(), any(), any()))
                .thenReturn(List.of(l));
        when(producaoRepo.findCandidatasParaMatch(any(), any()))
                .thenReturn(new ArrayList<>(List.of(p)));
        when(conciliacaoRepo.existsByLancamentoExtratoId(any())).thenReturn(true); // já conciliado

        service.processarExtrato(UUID.randomUUID(), "12345678000190");

        verify(conciliacaoRepo, never()).save(any());
        verify(lancamentoRepo, never()).save(any());
    }

    @Test
    void processarExtrato_tenantVazio_retornaImediatamente() {
        service.processarExtrato(UUID.randomUUID(), "");

        verifyNoInteractions(producaoRepo, conciliacaoRepo, lancamentoRepo);
    }

    @Test
    void processarExtrato_semLancamentosPendentes_naoConsultaProducoes() {
        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(any(), any(), any()))
                .thenReturn(List.of());

        service.processarExtrato(UUID.randomUUID(), "12345678000190");

        verifyNoInteractions(producaoRepo);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private LancamentoExtrato lancamento(long valor, LocalDate data, String descricao) {
        LancamentoExtrato l = new LancamentoExtrato();
        l.setValor(valor);
        l.setDataLancamento(data);
        l.setDescricao(descricao);
        l.setTipo(TipoLancamentoExtrato.CREDITO);
        l.setStatusConciliacao(StatusConciliacao.PENDENTE);
        return l;
    }

    private Producao producao(long valorBruto, String competencia) {
        Producao p = new Producao();
        p.setId(UUID.randomUUID());
        p.setValorBruto(valorBruto);
        p.setCompetencia(competencia);
        p.setStatus(StatusProducao.EMITIDA);
        Tomador tomador = new Tomador();
        tomador.setRazaoSocialNome("TOMADOR PADRAO");
        p.setTomador(tomador);
        return p;
    }
}
