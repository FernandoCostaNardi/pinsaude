package br.com.pinsaude.faturamento.conciliacao;

import br.com.pinsaude.faturamento.conciliacao.matching.MatchingService;
import br.com.pinsaude.faturamento.domain.*;
import br.com.pinsaude.faturamento.repository.ConciliacaoRepository;
import br.com.pinsaude.faturamento.repository.ExtratoBancarioRepository;
import br.com.pinsaude.faturamento.repository.LancamentoExtratoRepository;
import br.com.pinsaude.faturamento.repository.ProducaoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.service.CryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
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
    @Mock ExtratoBancarioRepository extratoRepo;
    @Mock TomadorAliquotaRepository aliquotaRepo;

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
    void processarExtrato_tenantVazio_buscaCandidatasDeTodosOsTenants() {
        UUID extratoId = UUID.randomUUID();
        LancamentoExtrato l = lancamento(100_000L, LocalDate.of(2026, 6, 3), "PIX 12345678000190");
        Producao p = producao(100_000L, "2026-06");
        p.getTomador().setCnpjCpfTomadorCriptografado(new byte[]{1, 2, 3});

        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(any(), any(), any()))
                .thenReturn(List.of(l));
        when(producaoRepo.findCandidatasParaMatchTodosTenants(List.of(StatusProducao.EMITIDA.name())))
                .thenReturn(new ArrayList<>(List.of(p)));
        when(cryptoService.decrypt(any())).thenReturn("12345678000190");
        when(conciliacaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processarExtrato(extratoId, "");

        verify(producaoRepo, never()).findCandidatasParaMatch(any(), any());
        verify(conciliacaoRepo).save(argThat(c -> c.getNotaId().equals(p.getId())));
    }

    @Test
    void getSugestoes_extratoSemTenant_sugereProducaoDeQualquerTenant() {
        UUID extratoId = UUID.randomUUID();
        LancamentoExtrato l = lancamento(1_000_000L, LocalDate.of(2026, 10, 25), "PIX RECEBIDO TOMADOR");
        l.setExtratoId(extratoId);
        ExtratoBancario extrato = new ExtratoBancario();
        extrato.setCnpjIdTenant("");
        Producao p = producao(1_000_000L, "2026-10");

        UUID lancId = UUID.randomUUID();
        when(lancamentoRepo.findById(lancId)).thenReturn(java.util.Optional.of(l));
        when(extratoRepo.findById(extratoId)).thenReturn(java.util.Optional.of(extrato));
        when(producaoRepo.findCandidatasParaMatchTodosTenants(List.of(StatusProducao.EMITIDA.name())))
                .thenReturn(new ArrayList<>(List.of(p)));

        var sugestoes = service.getSugestoes(lancId);

        assertThat(sugestoes).extracting(s -> s.producaoId()).containsExactly(p.getId());
    }

    @Test
    void processarExtrato_semLancamentosPendentes_naoConsultaProducoes() {
        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(any(), any(), any()))
                .thenReturn(List.of());

        service.processarExtrato(UUID.randomUUID(), "12345678000190");

        verifyNoInteractions(producaoRepo);
    }

    // ─── CBS-31 — conciliação pelo líquido esperado da nota ────────────────────

    @Test
    void cbs31_nota2026Com615Retido_conciliaSozinha() {
        // Bruto R$ 10.000; tomador reteve IR+CSLL+PIS+COFINS (6,15%) e depositou R$ 9.385
        UUID extratoId = UUID.randomUUID();
        String tenant  = "12345678000190";
        LancamentoExtrato l = lancamento(938_500L, LocalDate.of(2026, 12, 15), "TED 12345678000190 HOSPITAL");
        Producao p = producao(1_000_000L, "2026-10");
        p.setValorLiquidoEsperado(938_500L);
        p.setNotaEmitidaEm(LocalDate.of(2026, 11, 5)); // 40 dias antes do depósito
        p.getTomador().setCnpjCpfTomadorCriptografado(new byte[]{1});
        stubProcessar(extratoId, tenant, l, p);

        service.processarExtrato(extratoId, tenant);

        verify(conciliacaoRepo).save(argThat(c -> c.getTipoMatch() == TipoMatchEnum.AUTOMATICO
                && c.getNotaId().equals(p.getId()) && c.getScoreConfianca() == 100));
    }

    @Test
    void cbs31_nota2027Com25Retido_conciliaSozinha() {
        // 2027: só IRRF 1,5% + CSLL 1% retidos → depósito R$ 9.750
        UUID extratoId = UUID.randomUUID();
        String tenant  = "12345678000190";
        LancamentoExtrato l = lancamento(975_000L, LocalDate.of(2027, 2, 20), "PIX 12.345.678/0001-90");
        Producao p = producao(1_000_000L, "2027-01");
        p.setValorLiquidoEsperado(975_000L);
        p.setNotaEmitidaEm(LocalDate.of(2027, 2, 3));
        p.getTomador().setCnpjCpfTomadorCriptografado(new byte[]{1});
        stubProcessar(extratoId, tenant, l, p);

        service.processarExtrato(extratoId, tenant);

        verify(conciliacaoRepo).save(argThat(c -> c.getTipoMatch() == TipoMatchEnum.AUTOMATICO
                && c.getNotaId().equals(p.getId())));
    }

    @Test
    void cbs31_depositoDoBrutoComMenos615_antesNaoCasava() {
        // Sem o líquido esperado o depósito de R$ 9.385 ficava 6,15% abaixo do bruto → descartado
        LancamentoExtrato l = lancamento(938_500L, LocalDate.of(2026, 12, 15), "TED");
        Producao p = producao(1_000_000L, "2026-10");

        assertThat(service.calcularScore(l, p, null)).isZero();

        p.setValorLiquidoEsperado(938_500L);
        assertThat(service.calcularScore(l, p, null)).isEqualTo(40);
    }

    @Test
    void cbs31_tomadorQueNaoReteve_aindaCasaPeloBruto() {
        LancamentoExtrato l = lancamento(1_000_000L, LocalDate.of(2026, 12, 1), "TED");
        Producao p = producao(1_000_000L, "2026-10");
        p.setValorLiquidoEsperado(938_500L);

        assertThat(service.calcularScore(l, p, null)).isEqualTo(40);
    }

    @Test
    void cbs31_diferencaDeArredondamentoDeCentavos_contaComoExato() {
        LancamentoExtrato l = lancamento(938_503L, LocalDate.of(2026, 1, 1), "TED");
        Producao p = producao(1_000_000L, "2026-10");
        p.setValorLiquidoEsperado(938_500L);

        assertThat(service.calcularScore(l, p, null)).isEqualTo(40);
    }

    @Test
    void cbs31_dataPelaEmissaoDaNota() {
        Producao p = producao(100_000L, "2026-06");
        p.setNotaEmitidaEm(LocalDate.of(2026, 8, 10));

        // até 45 dias depois da emissão: +20
        assertThat(service.calcularScore(lancamento(100_000L, LocalDate.of(2026, 9, 24), "TED"), p, null))
                .isEqualTo(60);
        // de 46 a 90 dias: +10
        assertThat(service.calcularScore(lancamento(100_000L, LocalDate.of(2026, 10, 30), "TED"), p, null))
                .isEqualTo(50);
        // muito antes da emissão: +0
        assertThat(service.calcularScore(lancamento(100_000L, LocalDate.of(2026, 7, 1), "TED"), p, null))
                .isEqualTo(40);
    }

    @Test
    void cbs31_producaoAntiga_estimaLiquidoPeloCadastroDoTomador() {
        Producao p = producao(1_000_000L, "2026-09");
        p.getTomador().setId(UUID.randomUUID());
        p.getTomador().setTipo(TipoTomador.HOSPITAL);
        p.getTomador().setIndicadorRetencaoFederal(true);
        Servico s = new Servico();
        s.setAliquotaIss(new BigDecimal("5.0000"));
        s.setAliquotaIr(new BigDecimal("1.5000"));
        s.setAliquotaCsll(new BigDecimal("1.0000"));
        s.setAliquotaPis(new BigDecimal("0.6500"));
        s.setAliquotaCofins(new BigDecimal("3.0000"));
        p.setServico(s);
        TomadorAliquota irDoTomador = new TomadorAliquota();
        irDoTomador.setTomadorId(p.getTomador().getId());
        irDoTomador.setTipoTributo("IR");
        irDoTomador.setValorAliquota(new BigDecimal("1.0000"));
        when(aliquotaRepo.findByTomadorIdIn(any())).thenReturn(List.of(irDoTomador));

        // ISS não é retido (sem indicador); IR do tomador 1% + CSLL 1% + PIS 0,65% + COFINS 3% = 5,65%
        assertThat(service.liquidosEsperados(List.of(p))).containsEntry(p.getId(), 943_500L);
    }

    @Test
    void cbs31_producaoAntigaDeTomadorSemRetencao_semEstimativa() {
        Producao p = producao(1_000_000L, "2026-09");
        p.getTomador().setTipo(TipoTomador.CLINICA);

        assertThat(service.liquidosEsperados(List.of(p))).isEmpty();
        verifyNoInteractions(aliquotaRepo);
    }

    @Test
    void cbs31_sugestoesTrazemOLiquidoEsperado() {
        UUID lancId = UUID.randomUUID();
        UUID extratoId = UUID.randomUUID();
        LancamentoExtrato l = lancamento(938_500L, LocalDate.of(2026, 12, 1), "TED");
        l.setExtratoId(extratoId);
        ExtratoBancario e = new ExtratoBancario();
        e.setCnpjIdTenant("12345678000190");
        Producao p = producao(1_000_000L, "2026-10");
        p.setValorLiquidoEsperado(938_500L);
        p.setNotaEmitidaEm(LocalDate.of(2026, 11, 5));
        when(lancamentoRepo.findById(lancId)).thenReturn(java.util.Optional.of(l));
        when(extratoRepo.findById(extratoId)).thenReturn(java.util.Optional.of(e));
        when(producaoRepo.findCandidatasParaMatch(eq("12345678000190"), anyList()))
                .thenReturn(new ArrayList<>(List.of(p)));

        var sugestoes = service.getSugestoes(lancId);

        assertThat(sugestoes).singleElement().satisfies(c -> {
            assertThat(c.valorBruto()).isEqualTo(1_000_000L);
            assertThat(c.valorLiquidoEsperado()).isEqualTo(938_500L);
            assertThat(c.score()).isEqualTo(60);
        });
    }

    private void stubProcessar(UUID extratoId, String tenant, LancamentoExtrato l, Producao p) {
        when(lancamentoRepo.findByExtratoIdAndTipoAndStatusConciliacao(
                extratoId, TipoLancamentoExtrato.CREDITO.name(), StatusConciliacao.PENDENTE.name()))
                .thenReturn(List.of(l));
        when(producaoRepo.findCandidatasParaMatch(tenant, List.of(StatusProducao.EMITIDA.name())))
                .thenReturn(new ArrayList<>(List.of(p)));
        when(conciliacaoRepo.existsByLancamentoExtratoId(any())).thenReturn(false);
        when(cryptoService.decrypt(any())).thenReturn("12345678000190");
        when(conciliacaoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lancamentoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
