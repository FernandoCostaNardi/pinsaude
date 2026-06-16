package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.ChecklistConduta;

import java.time.OffsetDateTime;

public record ChecklistCondutaResponse(
    boolean numeroConselhoVerificado,
    boolean registrosDisciplinares,
    boolean processosMedicos,
    boolean completo,
    String verificadoPor,
    OffsetDateTime verificadoEm
) {
    public static ChecklistCondutaResponse from(ChecklistConduta c) {
        return new ChecklistCondutaResponse(
            c.isNumeroConselhoVerificado(),
            c.isRegistrosDisciplinares(),
            c.isProcessosMedicos(),
            c.isCompleto(),
            c.getVerificadoPor(),
            c.getVerificadoEm()
        );
    }
}
