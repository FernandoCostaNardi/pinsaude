package br.com.pinsaude.fiscal.motor;

import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.CalculoFiscalRequest;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import br.com.pinsaude.fiscal.service.AuditoriaFiscalService;
import br.com.pinsaude.fiscal.service.MotorFiscalService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests do MotorFiscalService com jqwik.
 *
 * Invariantes testadas:
 *   1. Médico SEMPRE recebe 85% do valorBruto (= valorBruto − taxaPin)
 *   2. taxaPin + valorLiquidoMedico = valorBruto (decomposição exata)
 *   3. Para qualquer competência, o cálculo usa as alíquotas da época (historicidade)
 */
class MotorFiscalPropertyTest {

    private static final String CNPJ = "12345678000195";

    private ParametrosFiscaisRepository pfRepo;
    private ParametroFiscalRepository   p1Repo;
    private MotorFiscalService motor;

    @BeforeProperty
    void setUp() {
        pfRepo = mock(ParametrosFiscaisRepository.class);
        p1Repo = mock(ParametroFiscalRepository.class);

        when(pfRepo.findVigenteParaTributo(anyString(), any(TipoTributo.class), anyString(), any(Pageable.class)))
            .thenReturn(List.of());
        when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
                anyString(), any(LocalDate.class)))
            .thenReturn(Optional.of(parametroPadrao()));

        motor = new MotorFiscalService(pfRepo, p1Repo, new AuditoriaFiscalService());
    }

    /**
     * Invariante 1: valorLiquidoMedico = valorBruto − taxaPin, para qualquer valor bruto.
     * O médico SEMPRE recebe exatamente 85% — tributos não afetam o repasse.
     */
    @Property(tries = 1000)
    void invariante1_medicoSempreRecebe85Pct(
            @ForAll @LongRange(min = 1, max = 100_000_000) long valorBruto) {

        CalculoFiscalResponse r = calcular(valorBruto, true, true, false, false); // cenário A

        long taxaPinEsperada = BigDecimal.valueOf(valorBruto)
            .multiply(new BigDecimal("0.15"))
            .setScale(0, RoundingMode.HALF_UP)
            .longValue();

        assertThat(r.valorLiquidoMedico())
            .as("médico deve receber valorBruto − taxaPin")
            .isEqualTo(valorBruto - taxaPinEsperada);
    }

    /**
     * Invariante 2: taxaPin + valorLiquidoMedico = valorBruto, para qualquer valor e cenário.
     * A decomposição do bruto em taxa + repasse é sempre exata.
     */
    @Property(tries = 1000)
    void invariante2_decomposicaoExata(
            @ForAll @LongRange(min = 1, max = 100_000_000) long valorBruto,
            @ForAll boolean tomadorPj,
            @ForAll boolean retFederal,
            @ForAll boolean equiparado) {

        CalculoFiscalResponse r = calcular(valorBruto, tomadorPj, retFederal, false, equiparado);

        assertThat(r.taxaPin() + r.valorLiquidoMedico())
            .as("taxaPin + valorLiquidoMedico deve ser igual ao valorBruto")
            .isEqualTo(valorBruto);
    }

    /**
     * Invariante 3: cálculo de competência passada usa alíquotas da época.
     * Configuramos ISS=2,5% para 2023 e ISS=2% para 2024 — o resultado deve refletir
     * a alíquota correta para cada competência.
     */
    @Property(tries = 500)
    void invariante3_historicidadeDeAliquotas(
            @ForAll @LongRange(min = 100, max = 100_000_000) long valorBruto) {

        // ISS 2,5% vigente em 2023; ISS 2% vigente a partir de 2024-01
        when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
                anyString(), any(LocalDate.class)))
            .thenAnswer(inv -> {
                LocalDate data = inv.getArgument(1);
                return Optional.of(data.getYear() == 2023 ? parametroComIss("0.0250") : parametroComIss("0.0200"));
            });

        CalculoFiscalResponse r2023 = motor.calcular(CNPJ,
            new CalculoFiscalRequest(valorBruto, "2023-06", true, false, false, false, null));
        CalculoFiscalResponse r2024 = motor.calcular(CNPJ,
            new CalculoFiscalRequest(valorBruto, "2024-06", true, false, false, false, null));

        long iss2023 = BigDecimal.valueOf(valorBruto)
            .multiply(new BigDecimal("0.0250")).setScale(0, RoundingMode.HALF_UP).longValue();
        long iss2024 = BigDecimal.valueOf(valorBruto)
            .multiply(new BigDecimal("0.0200")).setScale(0, RoundingMode.HALF_UP).longValue();

        assertThat(r2023.valorIss())
            .as("2023 deve usar ISS 2,5%")
            .isEqualTo(iss2023);
        assertThat(r2024.valorIss())
            .as("2024 deve usar ISS 2,0%")
            .isEqualTo(iss2024);

        // Médico recebe 85% em ambas as competências (tributos não afetam)
        assertThat(r2023.taxaPin() + r2023.valorLiquidoMedico()).isEqualTo(valorBruto);
        assertThat(r2024.taxaPin() + r2024.valorLiquidoMedico()).isEqualTo(valorBruto);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CalculoFiscalResponse calcular(long bruto, boolean pj, boolean retFederal,
                                           boolean retIss, boolean equiparado) {
        return motor.calcular(CNPJ, new CalculoFiscalRequest(bruto, "2024-06", pj, retFederal, retIss, equiparado, null));
    }

    private ParametroFiscal parametroPadrao() {
        return parametroComIss("0.0200");
    }

    private ParametroFiscal parametroComIss(String aliqIss) {
        ParametroFiscal pf = new ParametroFiscal();
        pf.setAliqIss(new BigDecimal(aliqIss));
        pf.setAliqIr(new BigDecimal("0.0150"));
        pf.setAliqCsll(new BigDecimal("0.0100"));
        pf.setAliqPis(new BigDecimal("0.0065"));
        pf.setAliqCofins(new BigDecimal("0.0300"));
        pf.setIbsCbsAtivo(false);
        return pf;
    }
}
