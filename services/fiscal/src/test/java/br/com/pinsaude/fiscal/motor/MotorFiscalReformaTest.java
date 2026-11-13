package br.com.pinsaude.fiscal.motor;

import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.CalculoFiscalRequest;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import br.com.pinsaude.fiscal.service.AuditoriaFiscalService;
import br.com.pinsaude.fiscal.service.MotorFiscalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Motor fiscal no regime IBS_CBS (CBS-13).
 *
 * Bruto = 100.000 centavos. Empresa: ISS 2%, IR 1,5%, CSLL 1%, PIS 0,65%, COFINS 3%,
 * com a chave ligada a partir de 2027-01 (CronogramaFixture).
 */
class MotorFiscalReformaTest {

    private static final String CNPJ  = "12345678000195";
    private static final long   BRUTO = 100_000L;
    private static final BigDecimal RED_SAUDE = new BigDecimal("0.6000");

    private ParametroFiscalRepository p1Repo;
    private CronogramaReformaRepository cronRepo;
    private MotorFiscalService motor;
    private boolean homologado;

    @BeforeEach
    void setUp() {
        ParametrosFiscaisRepository pfRepo = mock(ParametrosFiscaisRepository.class);
        p1Repo = mock(ParametroFiscalRepository.class);
        cronRepo = mock(CronogramaReformaRepository.class);
        homologado = true;

        when(pfRepo.findVigenteParaTributo(anyString(), any(TipoTributo.class), anyString(), any(Pageable.class)))
            .thenReturn(java.util.List.of());
        when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(anyString(), any(LocalDate.class)))
            .thenAnswer(inv -> CronogramaFixture.vigente(inv.getArgument(1), homologado));
        when(cronRepo.findById(any(Short.class)))
            .thenAnswer(inv -> CronogramaFixture.ano(inv.getArgument(0)));

        motor = new MotorFiscalService(pfRepo, p1Repo, cronRepo, new AuditoriaFiscalService());
    }

    // ─── Virada ─────────────────────────────────────────────────────────────

    @Test
    void dezembro2026_calculaIgualAoRegimeAnterior() {
        CalculoFiscalResponse r = motor.calcular(CNPJ, req("2026-12", true, true, false, false, null, null));

        assertThat(r.regime()).isEqualTo("LEGADO");
        assertThat(r.valorIss()).isEqualTo(2_000L);
        assertThat(r.valorIr()).isEqualTo(1_500L);
        assertThat(r.valorCsll()).isEqualTo(1_000L);
        assertThat(r.valorPis()).isEqualTo(650L);
        assertThat(r.valorCofins()).isEqualTo(3_000L);
        assertThat(r.valorCbs()).isZero();
        assertThat(r.valorIbs()).isZero();
        assertThat(r.totalRetencoes()).isEqualTo(6_150L);
        assertThat(r.totalTributos()).isEqualTo(8_150L);
        assertThat(r.resultadoPin()).isEqualTo(6_850L);
        assertThat(r.valorTotalNota()).isEqualTo(BRUTO);
        assertThat(r.fatorIss()).isEqualByComparingTo("1");
        assertThat(r.cstIbsCbs()).isNull();
    }

    @Test
    void janeiro2027_semPisCofins_comCbsIbs_irCsllIguais() {
        // Sem redução, dentro do preço: base = 100000 / 1,113 = 89.847
        CalculoFiscalResponse r = motor.calcular(CNPJ, req("2027-01", true, true, false, false, null, null));

        assertThat(r.regime()).isEqualTo("IBS_CBS");
        assertThat(r.valorPis()).isZero();
        assertThat(r.valorCofins()).isZero();
        assertThat(r.valorIr()).isEqualTo(1_500L);
        assertThat(r.valorCsll()).isEqualTo(1_000L);
        assertThat(r.valorIss()).isEqualTo(2_000L);
        assertThat(r.baseCalculoIbsCbs()).isEqualTo(89_847L);
        assertThat(r.valorCbs()).isEqualTo(10_063L);   // 89.847 × 11,2%
        assertThat(r.valorIbs()).isEqualTo(90L);       // 89.847 × 0,1%
        assertThat(r.totalRetencoes()).isEqualTo(2_500L);   // só IR + CSLL
        assertThat(r.totalTributos()).isEqualTo(14_653L);
        assertThat(r.resultadoPin()).isEqualTo(347L);
        assertThat(r.valorLiquidoMedico()).isEqualTo(85_000L);
        assertThat(r.valorTotalNota()).isEqualTo(BRUTO);
        assertThat(r.auditoria().regime()).isEqualTo("IBS_CBS");
        assertThat(r.auditoria().ibsCbsAtivo()).isTrue();
    }

    @Test
    void reducaoDaSaude_aplicadaNasDuasAliquotas() {
        CalculoFiscalResponse r = motor.calcular(CNPJ, req("2027-03", true, false, false, false, RED_SAUDE, null));

        assertThat(r.aliqCbs()).isEqualByComparingTo("0.0448");
        assertThat(r.aliqIbs()).isEqualByComparingTo("0.0004");
        assertThat(r.baseCalculoIbsCbs()).isEqualTo(95_675L);   // 100000 / 1,0452
        assertThat(r.valorCbs()).isEqualTo(4_286L);
        assertThat(r.valorIbs()).isEqualTo(38L);
        assertThat(r.cstIbsCbs()).isEqualTo("200");
        assertThat(r.cClassTrib()).isEqualTo("200029");
    }

    @Test
    void porFora_somaAoPrecoEPinNaoPaga() {
        CalculoFiscalResponse r = motor.calcular(CNPJ, req("2027-03", true, false, false, false, RED_SAUDE, true));

        assertThat(r.cbsIbsPorFora()).isTrue();
        assertThat(r.baseCalculoIbsCbs()).isEqualTo(BRUTO);
        assertThat(r.valorCbs()).isEqualTo(4_480L);
        assertThat(r.valorIbs()).isEqualTo(40L);
        assertThat(r.valorTotalNota()).isEqualTo(104_520L);
        assertThat(r.totalTributos()).isEqualTo(9_020L);
        // CBS/IBS por fora não saem dos 15% da Pin
        assertThat(r.resultadoPin()).isEqualTo(15_000L - 4_500L);
        assertThat(r.valorLiquidoMedico()).isEqualTo(85_000L);
    }

    @Test
    void chaveLigadaSemHomologacao_continuaNoLegado() {
        homologado = false;
        CalculoFiscalResponse r = motor.calcular(CNPJ, req("2027-01", true, true, false, false, null, null));

        assertThat(r.regime()).isEqualTo("LEGADO");
        assertThat(r.valorPis()).isEqualTo(650L);
        assertThat(r.valorCbs()).isZero();
    }

    // ─── ISS na transição ───────────────────────────────────────────────────

    @Test
    void iss2029_fator09() {
        CalculoFiscalResponse r = motor.calcular(CNPJ, req("2029-05", true, false, false, false, null, null));
        assertThat(r.fatorIss()).isEqualByComparingTo("0.9");
        assertThat(r.valorIss()).isEqualTo(1_800L);
    }

    @Test
    void iss2033_extinto() {
        CalculoFiscalResponse r = motor.calcular(CNPJ, req("2033-01", true, true, true, false, null, null));
        assertThat(r.valorIss()).isZero();
        assertThat(r.totalRetencoes()).isEqualTo(2_500L);
    }

    // ─── Erros de configuração ──────────────────────────────────────────────

    @Test
    void anoSemCronograma_retorna422() {
        assertThatThrownBy(() -> motor.calcular(CNPJ, req("2034-01", true, true, false, false, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void anoComAliquotasNaoFixadas_retorna422() {
        when(cronRepo.findById((short) 2029))
            .thenReturn(Optional.of(CronogramaFixture.cron((short) 2029, null, null, null, "0.9000")));

        assertThatThrownBy(() -> motor.calcular(CNPJ, req("2029-01", true, true, false, false, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("2029");
    }

    // ─── Todos os anos × cenários ───────────────────────────────────────────

    static Stream<Arguments> anosECenarios() {
        String[][] cenarios = { {"A","true","true","false"}, {"B","true","false","false"},
                                {"C","false","false","true"}, {"D","false","false","false"} };
        return IntStream.rangeClosed(2027, 2033).boxed().flatMap(ano ->
            Stream.of(cenarios).map(c -> Arguments.of(ano, c[0],
                Boolean.parseBoolean(c[1]), Boolean.parseBoolean(c[2]), Boolean.parseBoolean(c[3]))));
    }

    @ParameterizedTest(name = "{0} cenário {1}")
    @MethodSource("anosECenarios")
    void regimeNovo_invariantes(int ano, String cenario, boolean pj, boolean retFed, boolean equip) {
        CalculoFiscalResponse r = motor.calcular(CNPJ, req(ano + "-06", pj, retFed, false, equip, RED_SAUDE, null));

        assertThat(r.cenario()).isEqualTo(cenario);
        assertThat(r.regime()).isEqualTo("IBS_CBS");
        assertThat(r.valorPis()).isZero();
        assertThat(r.valorCofins()).isZero();
        assertThat(r.valorCbs()).isPositive();
        assertThat(r.valorLiquidoMedico()).isEqualTo(85_000L);

        // IR/CSLL como no regime anterior
        long irEsperado   = switch (cenario) { case "A", "B", "D" -> 1_500L; default -> 0L; };
        long csllEsperado = switch (cenario) { case "A", "B" -> 1_000L; default -> 0L; };
        assertThat(r.valorIr()).isEqualTo(irEsperado);
        assertThat(r.valorCsll()).isEqualTo(csllEsperado);

        // CBS/IBS nunca são retenção
        long retEsperada = switch (cenario) { case "A" -> 2_500L; case "D" -> 1_500L; default -> 0L; };
        assertThat(r.totalRetencoes()).isEqualTo(retEsperada);

        // ISS segue o fator do ano
        BigDecimal fator = CronogramaFixture.ano((short) ano).orElseThrow().getFatorIss();
        long issEsperado = (cenario.equals("A") || cenario.equals("B"))
            ? new BigDecimal("2000").multiply(fator).setScale(0, java.math.RoundingMode.HALF_UP).longValue() : 0L;
        assertThat(r.valorIss()).isEqualTo(issEsperado);

        assertThat(r.totalTributos()).isEqualTo(r.valorIss() + r.valorIr() + r.valorCsll() + r.valorCbs() + r.valorIbs());
        assertThat(r.resultadoPin()).isEqualTo(r.taxaPin() - r.totalTributos());
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private CalculoFiscalRequest req(String comp, boolean pj, boolean retFed, boolean retIss, boolean equip,
                                     BigDecimal reducao, Boolean porFora) {
        return new CalculoFiscalRequest(BRUTO, comp, pj, retFed, retIss, equip, null,
            reducao, "200", "200029", porFora);
    }
}
