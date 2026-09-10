package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FechamentoMedicoStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FechamentoMedicoStatusResponse(
    UUID medicoId,
    String competencia,
    String status,
    String atualizadoPor,
    OffsetDateTime atualizadoEm
) {
    public static FechamentoMedicoStatusResponse from(FechamentoMedicoStatus s) {
        return new FechamentoMedicoStatusResponse(
            s.getMedicoId(),
            s.getCompetencia(),
            s.getStatus(),
            s.getAtualizadoPor(),
            s.getAtualizadoEm()
        );
    }
}
