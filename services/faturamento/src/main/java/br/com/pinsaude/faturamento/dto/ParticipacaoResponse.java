package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.ParticipacaoProducao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ParticipacaoResponse(
    UUID id,
    UUID medicoId,
    long valorBruto,
    BigDecimal taxaPinPct,
    long taxaPin,
    long valorLiquido
) {
    public static ParticipacaoResponse from(ParticipacaoProducao p) {
        BigDecimal pct   = p.getTaxaPinPct() != null ? p.getTaxaPinPct() : new BigDecimal("0.1500");
        long taxaPin     = BigDecimal.valueOf(p.getValorBruto())
                              .multiply(pct)
                              .setScale(0, RoundingMode.HALF_UP)
                              .longValue();
        return new ParticipacaoResponse(
            p.getId(),
            p.getMedicoId(),
            p.getValorBruto(),
            pct,
            taxaPin,
            p.getValorBruto() - taxaPin
        );
    }
}
