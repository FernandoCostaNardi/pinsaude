package br.com.pinsaude.portal.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PortalServiceExtratoTest {

    @Test
    void semNotaEmitida_ficaProvisionado_mesmoComRepasseNaCompetencia() {
        assertThat(PortalService.resolverStatus(false, "2026-10", Set.of("2026-10")))
                .isEqualTo(PortalService.STATUS_PROVISIONADO);
    }

    @Test
    void comNotaEmitida_semRepasse_ficaFaturado() {
        assertThat(PortalService.resolverStatus(true, "2026-10", Set.of("2026-09")))
                .isEqualTo(PortalService.STATUS_FATURADO);
    }

    @Test
    void comNotaEmitida_eRepasseNaCompetencia_ficaPago() {
        assertThat(PortalService.resolverStatus(true, "2026-10", Set.of("2026-10")))
                .isEqualTo(PortalService.STATUS_PAGO);
    }

    @Test
    void taxaPin_usaPercentualDoMedico_arredondandoHalfUp() {
        // 12% de R$ 1.000,05 = 12000,6 centavos → 12001
        assertThat(PortalService.calcularTaxaPin(100005L, new BigDecimal("0.1200"))).isEqualTo(12001L);
    }

    @Test
    void taxaPin_semPercentual_usaPadraoDe15() {
        assertThat(PortalService.calcularTaxaPin(100000L, null)).isEqualTo(15000L);
    }
}
