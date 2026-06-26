package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.ParticipacaoProducao;

import java.util.UUID;

public record ParticipacaoResponse(
    UUID id,
    UUID medicoId,
    long valorBruto,
    long taxaPin,
    long valorLiquido
) {
    public static ParticipacaoResponse from(ParticipacaoProducao p) {
        long taxaPin = Math.round(p.getValorBruto() * 0.15);
        return new ParticipacaoResponse(
            p.getId(),
            p.getMedicoId(),
            p.getValorBruto(),
            taxaPin,
            p.getValorBruto() - taxaPin
        );
    }
}
