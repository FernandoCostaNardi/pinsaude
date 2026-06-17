package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.ContratoAssinatura;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContratoAssinaturaResponse(
    UUID id,
    String status,
    String linkAssinatura,
    OffsetDateTime enviadoEm,
    OffsetDateTime assinadoEm
) {
    public static ContratoAssinaturaResponse from(ContratoAssinatura c) {
        return new ContratoAssinaturaResponse(
            c.getId(), c.getStatus(), c.getLinkAssinatura(),
            c.getEnviadoEm(), c.getAssinadoEm()
        );
    }
}
