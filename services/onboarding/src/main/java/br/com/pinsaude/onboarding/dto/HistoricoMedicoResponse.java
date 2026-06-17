package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.HistoricoMedico;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoricoMedicoResponse(
    UUID id,
    String tipoAcao,
    String descricao,
    String usuario,
    OffsetDateTime createdAt
) {
    public static HistoricoMedicoResponse from(HistoricoMedico h) {
        return new HistoricoMedicoResponse(
            h.getId(), h.getTipoAcao(), h.getDescricao(),
            h.getUsuario(), h.getCreatedAt()
        );
    }
}
