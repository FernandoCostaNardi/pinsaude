package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.HistoricoTaxaPin;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoricoTaxaPinResponse(
    UUID id,
    BigDecimal taxaAnterior,
    BigDecimal taxaNova,
    String alteradoPor,
    OffsetDateTime alteradoEm
) {
    public static HistoricoTaxaPinResponse from(HistoricoTaxaPin h) {
        return new HistoricoTaxaPinResponse(
            h.getId(),
            h.getTaxaAnterior(),
            h.getTaxaNova(),
            h.getAlteradoPor(),
            h.getAlteradoEm()
        );
    }
}
