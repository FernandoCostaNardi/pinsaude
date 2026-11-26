package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.apuracao.TributoApurado;
import br.com.pinsaude.fiscal.domain.*;
import br.com.pinsaude.fiscal.dto.ApuracaoMensalResponse;
import br.com.pinsaude.fiscal.dto.RegimeCompetenciaResponse;
import br.com.pinsaude.fiscal.port.RecebimentosPort;
import br.com.pinsaude.fiscal.port.RecebimentosPort.Recebimento;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import br.com.pinsaude.fiscal.repository.RegraEquiparacaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * APU-01 — o motor monta os meses com as notas e recebimentos reais e usa a calculadora da APU-00.
 * Os 4 cenários do motor fiscal (PJ com/sem retenção, PF com/sem equiparação) numa só competência.
 */
@ExtendWith(MockitoExtension.class)
class ApuracaoMensalServiceTest {

    private static final String CNPJ = "12345678000190";

    @Mock RegraApuracaoService regras;
    @Mock RegimeFiscalService regimeFiscal;
    @Mock CronogramaReformaRepository cronogramaRepo;
    @Mock NotaFiscalRepository notaRepo;
    @Mock RegraEquiparacaoRepository equiparacaoRepo;
    @Mock RecebimentosPort recebimentos;

    private ApuracaoMensalService service;

    @BeforeEach
    void setUp() {
        service = new ApuracaoMensalService(regras, regimeFiscal, cronogramaRepo, notaRepo, equiparacaoRepo, recebimentos);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String cnpj) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("sub")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (cnpj != null) b.claim("cnpj_id", cnpj);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(b.build()));
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private static RegraApuracaoTributo trib(TributoApuracao t, String aliq, BaseCalculoApuracao base,
                                             RegimeReconhecimento reg, PeriodicidadeApuracao per) {
        RegraApuracaoTributo x = new RegraApuracaoTributo();
        x.setTributo(t);
        x.setAtivo(true);
        x.setAplicaEm(AplicacaoRegime.AMBOS);
        x.setAliquota(aliq == null ? null : new BigDecimal(aliq));
        x.setBaseCalculo(base);
        x.setRegimeReconhecimento(reg);
        x.setPeriodicidade(per);
        x.setDeduzRetencao(true);
        x.setGuia("DARF");
        x.setVencimentoDia((short) 25);
        x.setVencimentoMesesApos((short) 1);
        x.setVencimentoAjuste(AjusteVencimento.ANTECIPA);
        return x;
    }

    /** Padrão do PRD: IRPJ/CSLL trimestrais por competência, PIS/COFINS mensais por caixa, ISS mensal. */
    private static RegraApuracao regra(boolean comCaixa) {
        RegraApuracao r = new RegraApuracao();
        r.setCompetenciaInicio("2026-01");
        r.setPresuncaoIrpjEquiparado(new BigDecimal("0.08"));
        r.setPresuncaoCsllEquiparado(new BigDecimal("0.12"));
        r.setPresuncaoIrpjPadrao(new BigDecimal("0.32"));
        r.setPresuncaoCsllPadrao(new BigDecimal("0.32"));
        r.setAliqAdicionalIrpj(new BigDecimal("0.10"));
        r.setLimiteAdicionalIrpjMensal(2_000_000L);
        r.setTetoReceitaAnual(7_800_000_000L);
        r.setAlertaTetoPercentual(new BigDecimal("0.8"));
        r.setValorMinimoGuia(1_000L);
        RegimeReconhecimento pisCofins = comCaixa ? RegimeReconhecimento.CAIXA : RegimeReconhecimento.COMPETENCIA;
        r.substituirTributos(List.of(
            trib(TributoApuracao.IRPJ, "0.15", BaseCalculoApuracao.BASE_PRESUMIDA, RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.TRIMESTRAL),
            trib(TributoApuracao.CSLL, "0.09", BaseCalculoApuracao.BASE_PRESUMIDA, RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.TRIMESTRAL),
            trib(TributoApuracao.PIS, "0.0065", BaseCalculoApuracao.RECEITA_BRUTA, pisCofins, PeriodicidadeApuracao.MENSAL),
            trib(TributoApuracao.COFINS, "0.03", BaseCalculoApuracao.RECEITA_BRUTA, pisCofins, PeriodicidadeApuracao.MENSAL),
            trib(TributoApuracao.ISS, "0.02", BaseCalculoApuracao.RECEITA_BRUTA, RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.MENSAL)));
        return r;
    }

    private static NotaFiscal nota(String competencia, long bruto, String cnae, long ir, long csll, long pis, long cofins) {
        NotaFiscal n = new NotaFiscal();
        try {
            var f = NotaFiscal.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(n, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        n.setCnpjIdTenant(CNPJ);
        n.setProducaoId(UUID.randomUUID());
        n.setTomadorId(UUID.randomUUID());
        n.setCompetencia(competencia);
        n.setValorBruto(bruto);
        n.setCnaeCodigo(cnae);
        n.setValorIr(ir);
        n.setValorCsll(csll);
        n.setValorPis(pis);
        n.setValorCofins(cofins);
        n.setStatus(StatusNota.EMITIDA);
        n.setEmitidaAt(OffsetDateTime.parse(competencia + "-28T10:00:00-03:00"));
        return n;
    }

    private static RegraEquiparacao equiparacao(String cnae, boolean eq) {
        RegraEquiparacao r = new RegraEquiparacao();
        r.setCnpjId(CNPJ);
        r.setCnae(cnae);
        r.setCodigoLc116("4.03");
        r.setIndicadorEquiparacao(eq);
        return r;
    }

    private void regimeLegado() {
        when(regimeFiscal.regime(eq(CNPJ), anyString()))
            .thenReturn(new RegimeCompetenciaResponse("2026-12", "LEGADO", List.of(), false, false, null, null));
        lenient().when(cronogramaRepo.findById(any())).thenReturn(Optional.empty());
    }

    private static TributoApurado tributo(ApuracaoMensalResponse r, String nome) {
        return r.apuracao().tributos().stream().filter(t -> t.tributo().equals(nome)).findFirst().orElseThrow();
    }

    // ─── Testes ───────────────────────────────────────────────────────────────

    @Test
    void apurar_quatroCenarios_competenciaCaixaEquiparacaoERetencoes() {
        login(CNPJ);
        when(regras.regraVigente(CNPJ, "2026-12")).thenReturn(regra(true));
        regimeLegado();

        // A: PJ com retenção, equiparada (CNAE 8610-1/01), recebida em dezembro
        NotaFiscal a = nota("2026-12", 10_000_000, "8610-1/01", 150_000, 100_000, 65_000, 300_000);
        // B: PJ sem retenção, sem equiparação (CNAE 8630-5/01), de novembro, recebida em dezembro
        NotaFiscal b = nota("2026-11", 5_000_000, "8630-5/01", 0, 0, 0, 0);
        // C: PF equiparado: nota com tributos zerados, não recebida
        NotaFiscal c = nota("2026-12", 1_000_000, "8610101", 0, 0, 0, 0);
        // D: PF sem equiparação: IR na fonte pelo tomador, sem CNAE, recebida em outubro
        NotaFiscal d = nota("2026-10", 2_000_000, null, 30_000, 0, 0, 0);

        when(notaRepo.findDaEmpresaPorCompetencia(CNPJ, StatusNota.EMITIDA, "2026-10", "2026-12"))
            .thenReturn(List.of(a, b, c, d));
        when(recebimentos.recebidosEntre(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31))).thenReturn(List.of(
            new Recebimento(a.getProducaoId(), LocalDate.of(2026, 12, 15), 9_385_000),
            new Recebimento(b.getProducaoId(), LocalDate.of(2026, 12, 20), 5_000_000),
            new Recebimento(d.getProducaoId(), LocalDate.of(2026, 10, 5), 1_970_000)));
        when(notaRepo.findDaEmpresaPorProducoes(eq(CNPJ), eq(StatusNota.EMITIDA), anyCollection()))
            .thenReturn(List.of(a, b, d));
        when(equiparacaoRepo.findByCnpjIdOrderByCnaeAsc(CNPJ))
            .thenReturn(List.of(equiparacao("8610101", true), equiparacao("8630-5/01", false)));

        ApuracaoMensalResponse r = service.apurar("2026-12", null);

        // IRPJ trimestral por competência: equiparada 11M × 8% + padrão 7M × 32% = 3.120.000
        TributoApurado irpj = tributo(r, "IRPJ");
        assertThat(irpj.fechaNesteMes()).isTrue();
        assertThat(irpj.receita()).isEqualTo(18_000_000);
        assertThat(irpj.base()).isEqualTo(3_120_000);
        assertThat(irpj.valorDevido()).isEqualTo(468_000);
        assertThat(irpj.adicional()).isZero();                 // base abaixo de 3 × R$ 20.000
        assertThat(irpj.retencoes()).isEqualTo(180_000);       // IR retido de A (150.000) e D (30.000)
        assertThat(irpj.valorARecolher()).isEqualTo(288_000);
        assertThat(irpj.vencimento()).isEqualTo(LocalDate.of(2027, 1, 25));

        // CSLL: equiparada 11M × 12% + padrão 7M × 32% = 3.560.000 × 9% = 320.400 − 100.000 retida
        TributoApurado csll = tributo(r, "CSLL");
        assertThat(csll.base()).isEqualTo(3_560_000);
        assertThat(csll.valorARecolher()).isEqualTo(220_400);

        // PIS mensal por caixa: recebidas em dezembro A + B = 15M × 0,65% − 65.000 retido de A
        TributoApurado pis = tributo(r, "PIS");
        assertThat(pis.receita()).isEqualTo(15_000_000);
        assertThat(pis.valorDevido()).isEqualTo(97_500);
        assertThat(pis.retencoes()).isEqualTo(65_000);
        assertThat(pis.valorARecolher()).isEqualTo(32_500);

        // ISS mensal por competência: notas de dezembro A + C = 11M × 2%
        assertThat(tributo(r, "ISS").valorDevido()).isEqualTo(220_000);

        assertThat(r.apuracao().aHomologar()).isTrue();
        assertThat(r.usouCaixa()).isTrue();
        assertThat(r.referenciaCompetencia()).isEqualTo("COMPETENCIA_NOTA");
        assertThat(r.apuracao().avisos()).anyMatch(s -> s.contains("1 nota(s) sem CNAE"));
        assertThat(r.meses()).hasSize(3);
        assertThat(r.meses().get(2).notasRecebidas()).isEqualTo(2);
        assertThat(r.meses().get(2).retencoesCaixa()).containsEntry("PIS", 65_000L);
        assertThat(r.notas()).hasSize(4);
        assertThat(r.notas()).filteredOn(n -> n.notaId().equals(a.getId()))
            .singleElement().satisfies(n -> {
                assertThat(n.equiparada()).isTrue();
                assertThat(n.recebidoEm()).isEqualTo(LocalDate.of(2026, 12, 15));
                assertThat(n.retencoes()).isEqualTo(615_000);
            });
    }

    @Test
    void apurar_regraHomologada_naoMarcaAHomologar() {
        login(CNPJ);
        RegraApuracao regra = regra(false);
        regra.setHomologado(true);
        when(regras.regraVigente(CNPJ, "2026-11")).thenReturn(regra);
        regimeLegado();
        when(notaRepo.findDaEmpresaPorCompetencia(CNPJ, StatusNota.EMITIDA, "2026-10", "2026-11")).thenReturn(List.of());

        ApuracaoMensalResponse r = service.apurar("2026-11", null);

        assertThat(r.apuracao().aHomologar()).isFalse();
        assertThat(r.apuracao().homologado()).isTrue();
        assertThat(r.usouCaixa()).isFalse();
        verifyNoInteractions(recebimentos);                 // nenhum tributo por caixa: não chama o Faturamento
        assertThat(tributo(r, "IRPJ").fechaNesteMes()).isFalse();
    }

    @Test
    void apurar_referenciaDataEmissao_usaDataDeEmissaoNoFusoDeSaoPaulo() {
        login(CNPJ);
        RegraApuracao regra = regra(false);
        regra.setReferenciaCompetencia(ReferenciaCompetencia.DATA_EMISSAO);
        when(regras.regraVigente(CNPJ, "2026-12")).thenReturn(regra);
        regimeLegado();

        // Competência de novembro, emitida em 01/12 00:30 de Brasília (03:30 UTC) → entra em dezembro
        NotaFiscal n = nota("2026-11", 1_000_000, null, 0, 0, 0, 0);
        n.setEmitidaAt(OffsetDateTime.parse("2026-12-01T03:30:00Z"));
        when(notaRepo.findDaEmpresaPorEmissao(eq(CNPJ), eq(StatusNota.EMITIDA), any(), any())).thenReturn(List.of(n));

        ApuracaoMensalResponse r = service.apurar("2026-12", null);

        assertThat(r.referenciaCompetencia()).isEqualTo("DATA_EMISSAO");
        assertThat(r.meses().get(2).receitaCompetenciaPadrao()).isEqualTo(1_000_000);
        assertThat(r.meses().get(1).receitaCompetenciaPadrao()).isZero();
        assertThat(tributo(r, "ISS").valorDevido()).isEqualTo(20_000);
        verify(notaRepo, never()).findDaEmpresaPorCompetencia(any(), any(), any(), any());
    }

    @Test
    void apurar_faturamentoIndisponivel_retorna503() {
        login(CNPJ);
        when(regras.regraVigente(CNPJ, "2026-12")).thenReturn(regra(true));
        regimeLegado();
        when(recebimentos.recebidosEntre(any(), any()))
            .thenThrow(new RecebimentosPort.RecebimentosIndisponiveisException("fora", null));

        assertThatThrownBy(() -> service.apurar("2026-12", null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void apurar_gestaoSemCnpj_retorna400() {
        login(null);
        assertThatThrownBy(() -> service.apurar("2026-12", null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void apurar_gestaoComCnpj_usaEmpresaInformadaEIgnoraRecebimentosDeOutras() {
        login(null);
        when(regras.regraVigente(CNPJ, "2026-12")).thenReturn(regra(true));
        regimeLegado();
        NotaFiscal a = nota("2026-12", 1_000_000, null, 0, 0, 0, 0);
        when(notaRepo.findDaEmpresaPorCompetencia(CNPJ, StatusNota.EMITIDA, "2026-10", "2026-12")).thenReturn(List.of(a));
        // A gestão recebe recebimentos de todas as empresas; o de outra empresa não tem nota desta
        when(recebimentos.recebidosEntre(any(), any())).thenReturn(List.of(
            new Recebimento(a.getProducaoId(), LocalDate.of(2026, 12, 10), 1_000_000),
            new Recebimento(UUID.randomUUID(), LocalDate.of(2026, 12, 11), 7_000_000)));
        when(notaRepo.findDaEmpresaPorProducoes(eq(CNPJ), eq(StatusNota.EMITIDA), anyCollection())).thenReturn(List.of(a));

        ApuracaoMensalResponse r = service.apurar("2026-12", "12.345.678/0001-90");

        assertThat(r.apuracao().cnpj()).isEqualTo(CNPJ);
        assertThat(tributo(r, "PIS").receita()).isEqualTo(1_000_000);
        assertThat(r.apuracao().avisos()).noneMatch(s -> s.contains("recebimento(s) sem nota"));
    }

    @Test
    void apurar_cnaeComRegrasDivergentes_consideraSemEquiparacaoEAvisa() {
        login(CNPJ);
        when(regras.regraVigente(CNPJ, "2026-12")).thenReturn(regra(false));
        regimeLegado();
        NotaFiscal a = nota("2026-12", 1_000_000, "8610-1/01", 0, 0, 0, 0);
        when(notaRepo.findDaEmpresaPorCompetencia(CNPJ, StatusNota.EMITIDA, "2026-10", "2026-12")).thenReturn(List.of(a));
        RegraEquiparacao outra = equiparacao("8610101", false);
        outra.setCodigoLc116("4.01");
        when(equiparacaoRepo.findByCnpjIdOrderByCnaeAsc(CNPJ)).thenReturn(List.of(equiparacao("8610101", true), outra));

        ApuracaoMensalResponse r = service.apurar("2026-12", null);

        assertThat(r.notas().get(0).equiparada()).isFalse();
        assertThat(tributo(r, "IRPJ").base()).isEqualTo(320_000);   // 1M × 32%
        assertThat(r.apuracao().avisos()).anyMatch(s -> s.contains("regras de equiparação divergentes"));
    }
}
