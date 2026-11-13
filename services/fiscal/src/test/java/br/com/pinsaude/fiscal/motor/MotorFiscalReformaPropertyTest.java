package br.com.pinsaude.fiscal.motor;

import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.CalculoFiscalRequest;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import br.com.pinsaude.fiscal.service.AuditoriaFiscalService;
import br.com.pinsaude.fiscal.service.MotorFiscalService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriedades do motor no regime IBS_CBS (CBS-13), para qualquer bruto, ano 2026–2033 e cenário.
 */
class MotorFiscalReformaPropertyTest {

    private static final String CNPJ = "12345678000195";

    private MotorFiscalService motor;

    @BeforeProperty
    void setUp() {
        ParametrosFiscaisRepository pfRepo = mock(ParametrosFiscaisRepository.class);
        ParametroFiscalRepository p1Repo = mock(ParametroFiscalRepository.class);
        CronogramaReformaRepository cronRepo = mock(CronogramaReformaRepository.class);

        when(pfRepo.findVigenteParaTributo(anyString(), any(TipoTributo.class), anyString(), any(Pageable.class)))
            .thenReturn(List.of());
        when(p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(anyString(), any(LocalDate.class)))
            .thenAnswer(inv -> CronogramaFixture.vigente(inv.getArgument(1), true));
        when(cronRepo.findById(any(Short.class)))
            .thenAnswer(inv -> CronogramaFixture.ano(inv.getArgument(0)));

        motor = new MotorFiscalService(pfRepo, p1Repo, cronRepo, new AuditoriaFiscalService());
    }

    @Property(tries = 1000)
    void invariantes(
            @ForAll @LongRange(min = 1, max = 100_000_000) long bruto,
            @ForAll @IntRange(min = 2026, max = 2033) int ano,
            @ForAll @IntRange(min = 1, max = 12) int mes,
            @ForAll boolean pj, @ForAll boolean retFed, @ForAll boolean retIss, @ForAll boolean equip,
            @ForAll boolean comReducao, @ForAll boolean porFora) {

        String comp = String.format("%d-%02d", ano, mes);
        BigDecimal red = comReducao ? new BigDecimal("0.6") : null;
        CalculoFiscalResponse r = motor.calcular(CNPJ,
            new CalculoFiscalRequest(bruto, comp, pj, retFed, retIss, equip, null, red, "200", "200029", porFora));

        // Médico sempre 85%
        assertThat(r.taxaPin() + r.valorLiquidoMedico()).isEqualTo(bruto);

        if (ano == 2026) {
            assertThat(r.regime()).isEqualTo("LEGADO");
            assertThat(r.valorCbs() + r.valorIbs()).isZero();
            return;
        }

        assertThat(r.regime()).isEqualTo("IBS_CBS");
        assertThat(r.valorPis() + r.valorCofins()).isZero();

        // CBS/IBS nunca entram nas retenções
        long retIssEsperado = (r.cenario().equals("A") && retIss) ? r.valorIss() : 0;
        long retEsperada = switch (r.cenario()) {
            case "A" -> r.valorIr() + r.valorCsll() + retIssEsperado;
            case "D" -> r.valorIr();
            default  -> 0L;
        };
        assertThat(r.totalRetencoes()).isEqualTo(retEsperada);

        long cbsIbs = r.valorCbs() + r.valorIbs();
        if (porFora) {
            assertThat(r.valorTotalNota()).isEqualTo(bruto + cbsIbs);
            assertThat(r.resultadoPin()).isEqualTo(r.taxaPin() - (r.totalTributos() - cbsIbs));
        } else {
            assertThat(r.valorTotalNota()).isEqualTo(bruto);
            // Dentro do preço: base + CBS + IBS reconstrói o bruto (arredondamento de até 2 centavos)
            assertThat(Math.abs(r.baseCalculoIbsCbs() + cbsIbs - bruto)).isLessThanOrEqualTo(2L);
            assertThat(r.resultadoPin()).isEqualTo(r.taxaPin() - r.totalTributos());
        }
    }
}
