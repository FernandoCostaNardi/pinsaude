package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.AliquotaCompetencia;
import br.com.pinsaude.onboarding.domain.RegimePresuncao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AliquotaCompetenciaResponse(
    UUID id,
    String competencia,
    BigDecimal iss,
    BigDecimal ir,
    BigDecimal csll,
    BigDecimal pis,
    BigDecimal cofins,
    RegimePresuncao regimePresuncao,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static AliquotaCompetenciaResponse from(AliquotaCompetencia a) {
        return new AliquotaCompetenciaResponse(
            a.getId(), a.getCompetencia(),
            a.getAliquotaIss(), a.getAliquotaIr(), a.getAliquotaCsll(),
            a.getAliquotaPis(), a.getAliquotaCofins(),
            a.getRegimePresuncao(), a.getCreatedBy(),
            a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
