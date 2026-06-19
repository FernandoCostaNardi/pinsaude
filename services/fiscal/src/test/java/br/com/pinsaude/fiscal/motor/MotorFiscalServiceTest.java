package br.com.pinsaude.fiscal.motor;

import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.domain.ParametrosFiscais;
import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.CalculoFiscalRequest;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import br.com.pinsaude.fiscal.service.AuditoriaFiscalService;
import br.com.pinsaude.fiscal.service.MotorFiscalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do MotorFiscalService — cobre os 4 cenários e validações de negócio.
 *
 * Valores com valorBruto=100_000 centavos (R$1.000,00):
 *   taxaPin           = 15.000 (15%)
 *   valorLiquido      = 85.000 (85% — invariante)
 *   ISS 2%            =  2.000
 *   IR  1,5%          =  1.500
 *   CSLL 1%           =  1.000
 *   PIS  0,65%        =    650
 *   COFINS 3%         =  3.000
 */
@ExtendWith(MockitoExtension.class)
class MotorFiscalServiceTest {

    private static final String CNPJ = "12345678000195";
    private static final String COMP  = "2024-06";
    private static final long   BRUTO = 100_000L;  // R$1.000,00

    @Mock ParametrosFiscaisRepository pfRepo;
    @Mock ParametroFiscalRepository   p1Repo;

    MotorFiscalService motor;

    @BeforeEach
    void setUp() {
        motor = new MotorFiscalService(pfRepo, p1Repo, new AuditoriaFiscalService());

        // Sem alíquotas per-tributo (tabela V2): tudo via regime geral (tabela V1)
        when(pfRepo.findVigenteParaTributo(anyString(), any(TipoTributo.class), anyString(), any(Pageable.class)))
            .thenReturn(List.of());

        // Regime geral: ISS=2%, IR=1,5%, CSLL=1%, PIS=0,65%, COFINS=3%
        // lenient: testes que redefinem p1Repo individualmente tornariam esse stub desnecessário
        org.mockito.Mockito.lenient()
            .when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
                anyString(), any(LocalDate.class)))
            .thenReturn(Optional.of(parametroPadrao()));
    }

    // ─── Cenário A: PJ com retenção federal ─────────────────────────────────

    @Test
    void cenarioA_tomadorPjComRetencao_derivaCenarioCorreto() {
        CalculoFiscalResponse r = calcular(true, true, false, false);
        assertThat(r.cenario()).isEqualTo("A");
    }

    @Test
    void cenarioA_medicoRecebe85Pct() {
        CalculoFiscalResponse r = calcular(true, true, false, false);
        assertThat(r.valorLiquidoMedico()).isEqualTo(85_000L);
        assertThat(r.taxaPin()).isEqualTo(15_000L);
    }

    @Test
    void cenarioA_federaisRetidosPeloTomador() {
        CalculoFiscalResponse r = calcular(true, true, false, false);
        // IR+CSLL+PIS+COFINS retidos; ISS não retido (indicadorRetencaoIss=false)
        assertThat(r.totalRetencoes()).isEqualTo(1_500 + 1_000 + 650 + 3_000); // 6.150
        assertThat(r.valorIss()).isEqualTo(2_000L);
        assertThat(r.valorIr()).isEqualTo(1_500L);
    }

    @Test
    void cenarioA_comRetencaoIss_issEhRetidoTambem() {
        CalculoFiscalResponse r = calcular(true, true, true, false);
        // ISS também retido (substituição tributária)
        assertThat(r.totalRetencoes()).isEqualTo(2_000 + 1_500 + 1_000 + 650 + 3_000); // 8.150
    }

    @Test
    void cenarioA_resultadoPin_ehTaxaMenosTributos() {
        CalculoFiscalResponse r = calcular(true, true, false, false);
        assertThat(r.resultadoPin()).isEqualTo(r.taxaPin() - r.totalTributos());
    }

    // ─── Cenário B: PJ sem retenção ──────────────────────────────────────────

    @Test
    void cenarioB_tomadorPjSemRetencao_derivaCenario() {
        CalculoFiscalResponse r = calcular(true, false, false, false);
        assertThat(r.cenario()).isEqualTo("B");
    }

    @Test
    void cenarioB_semRetencoes_medicoRecebeIntegro85Pct() {
        CalculoFiscalResponse r = calcular(true, false, false, false);
        assertThat(r.totalRetencoes()).isZero();
        assertThat(r.valorLiquidoMedico()).isEqualTo(85_000L);
    }

    @Test
    void cenarioB_tributosCalculados_pinPagaPorGuia() {
        CalculoFiscalResponse r = calcular(true, false, false, false);
        assertThat(r.totalTributos()).isEqualTo(2_000 + 1_500 + 1_000 + 650 + 3_000); // 8.150
    }

    // ─── Cenário C: PF com equiparação hospitalar ────────────────────────────

    @Test
    void cenarioC_pfEquiparado_derivaCenario() {
        CalculoFiscalResponse r = calcular(false, false, false, true);
        assertThat(r.cenario()).isEqualTo("C");
    }

    @Test
    void cenarioC_notaComTributosZerados() {
        CalculoFiscalResponse r = calcular(false, false, false, true);
        assertThat(r.valorIss()).isZero();
        assertThat(r.valorIr()).isZero();
        assertThat(r.valorCsll()).isZero();
        assertThat(r.valorPis()).isZero();
        assertThat(r.valorCofins()).isZero();
        assertThat(r.totalTributos()).isZero();
        assertThat(r.totalRetencoes()).isZero();
    }

    @Test
    void cenarioC_resultadoPin_ehTaxaIntegra() {
        CalculoFiscalResponse r = calcular(false, false, false, true);
        // Sem tributos: resultado da Pin é a taxa integral (15%)
        assertThat(r.resultadoPin()).isEqualTo(15_000L);
    }

    // ─── Cenário D: PF sem equiparação ───────────────────────────────────────

    @Test
    void cenarioD_pfSemEquiparacao_derivaCenario() {
        CalculoFiscalResponse r = calcular(false, false, false, false);
        assertThat(r.cenario()).isEqualTo("D");
    }

    @Test
    void cenarioD_irNaFontePeloTomador_apenasIrRetido() {
        CalculoFiscalResponse r = calcular(false, false, false, false);
        assertThat(r.valorIr()).isEqualTo(1_500L);
        assertThat(r.totalRetencoes()).isEqualTo(1_500L);
        // ISS/CSLL/PIS/COFINS não se aplicam a PF sem equiparação
        assertThat(r.valorIss()).isZero();
        assertThat(r.valorCsll()).isZero();
        assertThat(r.valorPis()).isZero();
        assertThat(r.valorCofins()).isZero();
    }

    // ─── Alíquotas per-tributo substituem regime geral ────────────────────────

    @Test
    void aliquotaPerTributo_sobrescreveRegimeGeral() {
        // ISS per-tributo: 3% em vez de 2%
        ParametrosFiscais pfIss = new ParametrosFiscais();
        pfIss.setValorAliquota(new BigDecimal("0.0300"));
        when(pfRepo.findVigenteParaTributo(anyString(), org.mockito.ArgumentMatchers.eq(TipoTributo.ISS),
                anyString(), any(Pageable.class)))
            .thenReturn(List.of(pfIss));

        CalculoFiscalResponse r = calcular(true, false, false, false);
        // ISS = 3% de 100.000 = 3.000 (em vez dos 2.000 do regime geral)
        assertThat(r.valorIss()).isEqualTo(3_000L);
    }

    // ─── Competência passada usa alíquotas da época ───────────────────────────

    @Test
    void competenciaPassada_usaAliquotasDaEpoca() {
        // Configura alíquotas diferentes para 2023 vs 2024
        ParametroFiscal params2023 = parametroComIss(new BigDecimal("0.0250")); // ISS 2,5%
        ParametroFiscal params2024 = parametroComIss(new BigDecimal("0.0200")); // ISS 2,0%

        when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
                anyString(), org.mockito.ArgumentMatchers.eq(LocalDate.of(2023, 6, 1))))
            .thenReturn(Optional.of(params2023));
        when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
                anyString(), org.mockito.ArgumentMatchers.eq(LocalDate.of(2024, 6, 1))))
            .thenReturn(Optional.of(params2024));

        CalculoFiscalRequest req2023 = req("2023-06", true, false, false, false);
        CalculoFiscalRequest req2024 = req("2024-06", true, false, false, false);

        CalculoFiscalResponse r2023 = motor.calcular(CNPJ, req2023);
        CalculoFiscalResponse r2024 = motor.calcular(CNPJ, req2024);

        assertThat(r2023.valorIss()).isEqualTo(2_500L); // ISS 2,5%
        assertThat(r2024.valorIss()).isEqualTo(2_000L); // ISS 2,0%
    }

    // ─── IBS/CBS 2027 ────────────────────────────────────────────────────────

    @Test
    void ibsCbs_ativo_substituiFederaisComAliquotaEfetiva() {
        // IBS/CBS 1% com redução 60% saúde → efetiva = 0,4%
        ParametroFiscal pfIbs = new ParametroFiscal();
        pfIbs.setAliqIss(new BigDecimal("0.0200"));
        pfIbs.setAliqIr(BigDecimal.ZERO);
        pfIbs.setAliqCsll(BigDecimal.ZERO);
        pfIbs.setAliqPis(BigDecimal.ZERO);
        pfIbs.setAliqCofins(BigDecimal.ZERO);
        pfIbs.setIbsCbsAtivo(true);
        pfIbs.setAliqIbsCbs(new BigDecimal("0.0100"));
        pfIbs.setReducaoIbsCbsSaude(new BigDecimal("0.6000"));

        when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
                anyString(), any(LocalDate.class)))
            .thenReturn(Optional.of(pfIbs));

        CalculoFiscalResponse r = motor.calcular(CNPJ,
            req(COMP, true, true, false, false));  // cenário A

        // 0,4% de 100.000 = 400 (IBS/CBS efetivo no lugar de IR)
        assertThat(r.valorIr()).isEqualTo(400L);
        assertThat(r.valorCsll()).isZero();
        assertThat(r.valorPis()).isZero();
        assertThat(r.valorCofins()).isZero();
        assertThat(r.auditoria().ibsCbsAtivo()).isTrue();
        // Médico ainda recebe 85%
        assertThat(r.valorLiquidoMedico()).isEqualTo(85_000L);
    }

    // ─── Auditoria ────────────────────────────────────────────────────────────

    @Test
    void auditoria_registraCompetenciaECenario() {
        CalculoFiscalResponse r = calcular(true, true, false, false);
        assertThat(r.auditoria().competencia()).isEqualTo(COMP);
        assertThat(r.auditoria().cenario()).isEqualTo("A");
        assertThat(r.auditoria().ibsCbsAtivo()).isFalse();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CalculoFiscalResponse calcular(boolean pj, boolean retFederal,
                                           boolean retIss, boolean equiparado) {
        return motor.calcular(CNPJ, req(COMP, pj, retFederal, retIss, equiparado));
    }

    private CalculoFiscalRequest req(String comp, boolean pj, boolean retFederal,
                                     boolean retIss, boolean equiparado) {
        return new CalculoFiscalRequest(BRUTO, comp, pj, retFederal, retIss, equiparado);
    }

    private ParametroFiscal parametroPadrao() {
        return parametroComIss(new BigDecimal("0.0200"));
    }

    private ParametroFiscal parametroComIss(BigDecimal aliqIss) {
        ParametroFiscal pf = new ParametroFiscal();
        pf.setAliqIss(aliqIss);
        pf.setAliqIr(new BigDecimal("0.0150"));
        pf.setAliqCsll(new BigDecimal("0.0100"));
        pf.setAliqPis(new BigDecimal("0.0065"));
        pf.setAliqCofins(new BigDecimal("0.0300"));
        pf.setIbsCbsAtivo(false);
        return pf;
    }
}
