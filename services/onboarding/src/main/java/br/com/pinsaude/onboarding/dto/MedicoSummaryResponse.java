package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.domain.StatusMedico;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicoSummaryResponse(
    UUID id,
    String nome,
    String crm,
    String crmUf,
    String especialidade,
    String email,
    StatusMedico status,
    UUID empresaId,
    OffsetDateTime createdAt
) {
    public static MedicoSummaryResponse from(Medico m, UUID empresaId) {
        return new MedicoSummaryResponse(
            m.getId(), m.getNome(), m.getCrm(), m.getCrmUf(),
            m.getEspecialidade(), m.getEmail(), m.getStatus(), empresaId, m.getCreatedAt()
        );
    }
}
