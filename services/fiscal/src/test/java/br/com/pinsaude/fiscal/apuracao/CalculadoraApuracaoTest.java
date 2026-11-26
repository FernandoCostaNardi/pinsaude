package br.com.pinsaude.fiscal.apuracao;

import br.com.pinsaude.fiscal.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** APU-00 — fórmulas da apuração com a regra padrão do PRD (V18). Valores em centavos. */
class CalculadoraApuracaoTest {

    private static final YearMonth JAN = YearMonth.of(2026, 1);
    private static final YearMonth FEV = YearMonth.of(2026, 2);
    private static final YearMonth MAR = YearMonth.of(2026, 3);

    /** Mesma regra semeada pela V18 (padrão global, rascunho). */
    static RegraApuracao regraPadrao() {
        RegraApuracao r = new RegraApuracao();
        r.setCompetenciaInicio("2026-01");
        r.setPresuncaoIrpjEquiparado(new BigDecimal("0.0800"));
        r.setPresuncaoCsllEquiparado(new BigDecimal("0.1200"));
        r.setPresuncaoIrpjPadrao(new BigDecimal("0.3200"));
        r.setPresuncaoCsllPadrao(new BigDecimal("0.3200"));
        r.setAliqAdicionalIrpj(new BigDecimal("0.1000"));
        r.setLimiteAdicionalIrpjMensal(2_000_000L);
        r.setTetoReceitaAnual(7_800_000_000L);
        r.setAlertaTetoPercentual(new BigDecimal("0.8"));
        r.setValorMinimoGuia(1_000L);
        r.substituirTributos(List.of(
            t(TributoApuracao.IRPJ, AplicacaoRegime.AMBOS, "0.15", BaseCalculoApuracao.BASE_PRESUMIDA,
                RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.TRIMESTRAL, "2089", 31, 1, AjusteVencimento.ANTECIPA),
            t(TributoApuracao.CSLL, AplicacaoRegime.AMBOS, "0.09", BaseCalculoApuracao.BASE_PRESUMIDA,
                RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.TRIMESTRAL, "2372", 31, 1, AjusteVencimento.ANTECIPA),
            t(TributoApuracao.PIS, AplicacaoRegime.LEGADO, "0.0065", BaseCalculoApuracao.RECEITA_BRUTA,
                RegimeReconhecimento.CAIXA, PeriodicidadeApuracao.MENSAL, "8109", 25, 1, AjusteVencimento.ANTECIPA),
            t(TributoApuracao.COFINS, AplicacaoRegime.LEGADO, "0.03", BaseCalculoApuracao.RECEITA_BRUTA,
                RegimeReconhecimento.CAIXA, PeriodicidadeApuracao.MENSAL, "2172", 25, 1, AjusteVencimento.ANTECIPA),
            t(TributoApuracao.ISS, AplicacaoRegime.AMBOS, "0.02", BaseCalculoApuracao.RECEITA_BRUTA,
                RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.MENSAL, null, null, null, AjusteVencimento.NENHUM),
            t(TributoApuracao.CBS, AplicacaoRegime.IBS_CBS, null, BaseCalculoApuracao.SOMA_NOTAS,
                RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.MENSAL, null, null, null, AjusteVencimento.NENHUM),
            t(TributoApuracao.IBS, AplicacaoRegime.IBS_CBS, null, BaseCalculoApuracao.SOMA_NOTAS,
                RegimeReconhecimento.COMPETENCIA, PeriodicidadeApuracao.MENSAL, null, null, null, AjusteVencimento.NENHUM)
        ));
        return r;
    }

    static RegraApuracaoTributo t(TributoApuracao trib, AplicacaoRegime aplica, String aliq, BaseCalculoApuracao base,
                                  RegimeReconhecimento rr, PeriodicidadeApuracao per, String codigo,
                                  Integer dia, Integer meses, AjusteVencimento ajuste) {
        RegraApuracaoTributo x = new RegraApuracaoTributo();
        x.setTributo(trib);
        x.setAtivo(true);
        x.setAplicaEm(aplica);
        x.setAliquota(aliq == null ? null : new BigDecimal(aliq));
        x.setBaseCalculo(base);
        x.setRegimeReconhecimento(rr);
        x.setPeriodicidade(per);
        x.setDeduzRetencao(true);
        x.setCodigoReceita(codigo);
        x.setVencimentoDia(dia == null ? null : dia.shortValue());
        x.setVencimentoMesesApos(meses == null ? null : meses.shortValue());
        x.setVencimentoAjuste(ajuste);
        return x;
    }

    private static MesApuracao mes(YearMonth ym, long eq, long pad) {
        return new MesApuracao(ym, eq, pad, eq, pad, Map.of(), 0, 0);
    }

    private static TributoApurado tributo(ResultadoApuracao r, String nome) {
        return r.tributos().stream().filter(t -> t.tributo().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void pjComRetencao_pisCofinsMensaisAbatemRetencoesEIssSemVencimentoGeraAviso() {
        // R$ 100.000 equiparados, recebidos no mês, com CSRF retida pelo hospital (PIS 650, COFINS 3.000)
        MesApuracao m = new MesApuracao(JAN, 10_000_000, 0, 10_000_000, 0,
            Map.of(TributoApuracao.PIS, 65_000L, TributoApuracao.COFINS, 300_000L), 0, 0);

        ResultadoApuracao r = CalculadoraApuracao.calcular(regraPadrao(), null, RegimeTributario.LEGADO, JAN,
            List.of(m), AliquotasReformaAno.NENHUMA);

        TributoApurado pis = tributo(r, "PIS");
        assertThat(pis.valorDevido()).isEqualTo(65_000);
        assertThat(pis.retencoes()).isEqualTo(65_000);
        assertThat(pis.valorARecolher()).isZero();
        assertThat(pis.vencimento()).isNull();

        TributoApurado iss = tributo(r, "ISS");
        assertThat(iss.valorDevido()).isEqualTo(200_000);
        assertThat(iss.valorARecolher()).isEqualTo(200_000);
        assertThat(r.avisos()).anyMatch(a -> a.contains("ISS: vencimento não cadastrado"));
        assertThat(r.aHomologar()).isTrue();
        assertThat(r.avisos()).anyMatch(a -> a.contains("não homologada"));
    }

    @Test
    void pjSemRetencao_pisCofinsIntegraisComVencimentoDia25AntecipadoNoFimDeSemana() {
        // Fevereiro de 2026: 25/03/2026 é quarta-feira (sem ajuste)
        ResultadoApuracao r = CalculadoraApuracao.calcular(regraPadrao(), null, RegimeTributario.LEGADO, FEV,
            List.of(mes(FEV, 0, 10_000_000)), AliquotasReformaAno.NENHUMA);

        TributoApurado cofins = tributo(r, "COFINS");
        assertThat(cofins.valorARecolher()).isEqualTo(300_000);
        assertThat(cofins.vencimento()).isEqualTo(LocalDate.of(2026, 3, 25));
        assertThat(cofins.codigoReceita()).isEqualTo("2172");

        // Março de 2026: 25/04/2026 é sábado → antecipa para sexta 24/04
        ResultadoApuracao abr = CalculadoraApuracao.calcular(regraPadrao(), null, RegimeTributario.LEGADO, MAR,
            List.of(mes(MAR, 0, 10_000_000)), AliquotasReformaAno.NENHUMA);
        assertThat(tributo(abr, "PIS").vencimento()).isEqualTo(LocalDate.of(2026, 4, 24));
    }

    @Test
    void irpjTrimestral_soFechaNoUltimoMesDoTrimestre() {
        ResultadoApuracao jan = CalculadoraApuracao.calcular(regraPadrao(), null, RegimeTributario.LEGADO, JAN,
            List.of(mes(JAN, 10_000_000, 0)), AliquotasReformaAno.NENHUMA);
        TributoApurado irpjJan = tributo(jan, "IRPJ");
        assertThat(irpjJan.fechaNesteMes()).isFalse();
        assertThat(irpjJan.valorARecolher()).isZero();
        assertThat(irpjJan.periodoFim()).isEqualTo("2026-03");
        assertThat(jan.totalARecolher()).isEqualTo(tributo(jan, "PIS").valorARecolher()
            + tributo(jan, "COFINS").valorARecolher() + tributo(jan, "ISS").valorARecolher());
    }

    @Test
    void irpjTrimestral_presuncaoEquiparadaEAdicionalSobreLimiteDoTrimestre() {
        // Trimestre: R$ 1.000.000 por mês equiparado → receita R$ 3.000.000, base 8% = R$ 240.000
        List<MesApuracao> meses = List.of(mes(JAN, 100_000_000, 0), mes(FEV, 100_000_000, 0), mes(MAR, 100_000_000, 0));

        ResultadoApuracao r = CalculadoraApuracao.calcular(regraPadrao(), null, RegimeTributario.LEGADO, MAR,
            meses, AliquotasReformaAno.NENHUMA);

        TributoApurado irpj = tributo(r, "IRPJ");
        assertThat(irpj.fechaNesteMes()).isTrue();
        assertThat(irpj.base()).isEqualTo(24_000_000);            // R$ 240.000
        assertThat(irpj.valorDevido()).isEqualTo(3_600_000);      // 15% = R$ 36.000
        assertThat(irpj.adicional()).isEqualTo(1_800_000);        // (240.000 − 60.000) × 10% = R$ 18.000
        assertThat(irpj.valorARecolher()).isEqualTo(5_400_000);
        // Último dia útil de abril/2026: 30/04 é quinta
        assertThat(irpj.vencimento()).isEqualTo(LocalDate.of(2026, 4, 30));

        TributoApurado csll = tributo(r, "CSLL");
        assertThat(csll.base()).isEqualTo(36_000_000);            // 12% = R$ 360.000
        assertThat(csll.valorDevido()).isEqualTo(3_240_000);      // 9% = R$ 32.400
        assertThat(csll.adicional()).isZero();
    }

    @Test
    void irpj_adicionalSoAcimaDoLimite() {
        RegraApuracao regra = regraPadrao();
        regra.getTributos().stream().filter(x -> x.getTributo() == TributoApuracao.IRPJ)
            .forEach(x -> x.setPeriodicidade(PeriodicidadeApuracao.MENSAL));

        // Receita R$ 250.000 × 8% = R$ 20.000 → exatamente no limite: sem adicional
        ResultadoApuracao noLimite = CalculadoraApuracao.calcular(regra, null, RegimeTributario.LEGADO, JAN,
            List.of(mes(JAN, 25_000_000, 0)), AliquotasReformaAno.NENHUMA);
        assertThat(tributo(noLimite, "IRPJ").adicional()).isZero();

        // Receita R$ 251.000 → base R$ 20.080 → adicional (80,00 × 10%) = R$ 8,00
        ResultadoApuracao acima = CalculadoraApuracao.calcular(regra, null, RegimeTributario.LEGADO, JAN,
            List.of(mes(JAN, 25_100_000, 0)), AliquotasReformaAno.NENHUMA);
        assertThat(tributo(acima, "IRPJ").base()).isEqualTo(2_008_000);
        assertThat(tributo(acima, "IRPJ").adicional()).isEqualTo(800);
    }

    @Test
    void semEquiparacao_presuncaoCheia() {
        RegraApuracao regra = regraPadrao();
        regra.getTributos().forEach(x -> x.setPeriodicidade(PeriodicidadeApuracao.MENSAL));

        ResultadoApuracao r = CalculadoraApuracao.calcular(regra, null, RegimeTributario.LEGADO, JAN,
            List.of(mes(JAN, 0, 1_000_000)), AliquotasReformaAno.NENHUMA);

        assertThat(tributo(r, "IRPJ").base()).isEqualTo(320_000);  // 32%
        assertThat(tributo(r, "CSLL").base()).isEqualTo(320_000);
    }

    @Test
    void retencaoMaiorQueDevido_geraSaldoCredorSemGuia() {
        RegraApuracao regra = regraPadrao();
        regra.getTributos().forEach(x -> x.setPeriodicidade(PeriodicidadeApuracao.MENSAL));
        // IR retido 1,5% de R$ 10.000 = R$ 150; IRPJ devido = 10.000 × 8% × 15% = R$ 120
        MesApuracao m = new MesApuracao(JAN, 1_000_000, 0, 1_000_000, 0,
            Map.of(TributoApuracao.IRPJ, 15_000L), 0, 0);

        ResultadoApuracao r = CalculadoraApuracao.calcular(regra, null, RegimeTributario.LEGADO, JAN,
            List.of(m), AliquotasReformaAno.NENHUMA);

        TributoApurado irpj = tributo(r, "IRPJ");
        assertThat(irpj.valorDevido()).isEqualTo(12_000);
        assertThat(irpj.saldo()).isZero();
        assertThat(irpj.saldoCredor()).isEqualTo(3_000);
        assertThat(irpj.valorARecolher()).isZero();
    }

    @Test
    void saldoAbaixoDoMinimo_naoGeraGuia() {
        // PIS de R$ 1.000 de receita = R$ 6,50 < mínimo R$ 10
        ResultadoApuracao r = CalculadoraApuracao.calcular(regraPadrao(), null, RegimeTributario.LEGADO, JAN,
            List.of(mes(JAN, 100_000, 0)), AliquotasReformaAno.NENHUMA);

        TributoApurado pis = tributo(r, "PIS");
        assertThat(pis.saldo()).isEqualTo(650);
        assertThat(pis.abaixoMinimo()).isTrue();
        assertThat(pis.valorARecolher()).isZero();
    }

    @Test
    void pisCofinsPorCaixa_usamReceitaRecebidaNaoEmitida() {
        // Emitiu R$ 100.000 em janeiro mas só recebeu R$ 40.000
        MesApuracao m = new MesApuracao(JAN, 10_000_000, 0, 4_000_000, 0, Map.of(), 0, 0);

        ResultadoApuracao r = CalculadoraApuracao.calcular(regraPadrao(), null, RegimeTributario.LEGADO, JAN,
            List.of(m), AliquotasReformaAno.NENHUMA);

        assertThat(tributo(r, "COFINS").receita()).isEqualTo(4_000_000);
        assertThat(tributo(r, "ISS").receita()).isEqualTo(10_000_000);
    }

    @Test
    void regimeIbsCbs_trocaPisCofinsPorCbsIbsDasNotasEAplicaFatorIss() {
        YearMonth jan27 = YearMonth.of(2027, 1);
        MesApuracao m = new MesApuracao(jan27, 10_000_000, 0, 10_000_000, 0, Map.of(), 44_800, 400);

        ResultadoApuracao r = CalculadoraApuracao.calcular(regraPadrao(), "12345678000190", RegimeTributario.IBS_CBS,
            jan27, List.of(m), new AliquotasReformaAno(new BigDecimal("0.112"), new BigDecimal("0.001"), new BigDecimal("0.9")));

        assertThat(r.tributos()).extracting(TributoApurado::tributo)
            .contains("CBS", "IBS", "ISS", "IRPJ", "CSLL").doesNotContain("PIS", "COFINS");
        assertThat(tributo(r, "CBS").valorDevido()).isEqualTo(44_800);
        assertThat(tributo(r, "IBS").valorDevido()).isEqualTo(400);
        // ISS 2% × fator 0,9 = 1,8%
        assertThat(tributo(r, "ISS").valorDevido()).isEqualTo(180_000);
        assertThat(r.regime()).isEqualTo("IBS_CBS");
    }

    @Test
    void cbsSemAliquotaComBaseReceita_usaCronograma() {
        RegraApuracao regra = regraPadrao();
        regra.getTributos().stream().filter(x -> x.getTributo() == TributoApuracao.CBS)
            .forEach(x -> x.setBaseCalculo(BaseCalculoApuracao.RECEITA_BRUTA));

        ResultadoApuracao r = CalculadoraApuracao.calcular(regra, null, RegimeTributario.IBS_CBS, JAN,
            List.of(mes(JAN, 1_000_000, 0)), new AliquotasReformaAno(new BigDecimal("0.009"), null, null));

        assertThat(tributo(r, "CBS").aliquota()).isEqualByComparingTo("0.009");
        assertThat(tributo(r, "CBS").valorDevido()).isEqualTo(9_000);
    }

    @Test
    void tributoInativo_naoApareceERegraHomologadaSemAviso() {
        RegraApuracao regra = regraPadrao();
        regra.setHomologado(true);
        regra.getTributos().stream().filter(x -> x.getTributo() == TributoApuracao.ISS).forEach(x -> x.setAtivo(false));

        ResultadoApuracao r = CalculadoraApuracao.calcular(regra, null, RegimeTributario.LEGADO, FEV,
            List.of(mes(FEV, 1_000_000, 0)), AliquotasReformaAno.NENHUMA);

        assertThat(r.tributos()).extracting(TributoApurado::tributo).doesNotContain("ISS");
        assertThat(r.aHomologar()).isFalse();
        assertThat(r.avisos()).noneMatch(a -> a.contains("não homologada"));
        // Trimestre sem os números de janeiro
        assertThat(r.avisos()).anyMatch(a -> a.contains("2026-01"));
    }

    @Test
    void retencaoNaoDeduzida_quandoRegraDesliga() {
        RegraApuracao regra = regraPadrao();
        regra.getTributos().stream().filter(x -> x.getTributo() == TributoApuracao.ISS).forEach(x -> x.setDeduzRetencao(false));
        MesApuracao m = new MesApuracao(JAN, 1_000_000, 0, 1_000_000, 0, Map.of(TributoApuracao.ISS, 20_000L), 0, 0);

        ResultadoApuracao r = CalculadoraApuracao.calcular(regra, null, RegimeTributario.LEGADO, JAN,
            List.of(m), AliquotasReformaAno.NENHUMA);

        assertThat(tributo(r, "ISS").retencoes()).isZero();
        assertThat(tributo(r, "ISS").valorARecolher()).isEqualTo(20_000);
    }

    @Test
    void formatacao() {
        assertThat(CalculadoraApuracao.brl(123_456_789)).isEqualTo("R$ 1.234.567,89");
        assertThat(CalculadoraApuracao.pct(new BigDecimal("0.0065"))).isEqualTo("0,65%");
    }
}
