package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.ConviteMedico;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnviarConviteResponse(
    UUID id,
    String emailDestino,
    String status,
    OffsetDateTime enviadoEm,
    OffsetDateTime expiraEm
) {
    public static EnviarConviteResponse from(ConviteMedico c) {
        return new EnviarConviteResponse(
            c.getId(), c.getEmailDestino(), c.getStatus(),
            c.getEnviadoEm(), c.getExpiraEm()
        );
    }
}
