package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.MedicoTomador;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicoTomadorResponse(
    UUID medicoId,
    UUID tomadorId,
    OffsetDateTime createdAt
) {
    public static MedicoTomadorResponse from(MedicoTomador mt) {
        return new MedicoTomadorResponse(
            mt.getMedicoId(),
            mt.getTomadorId(),
            mt.getCreatedAt()
        );
    }
}
