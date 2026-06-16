package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.DadosBancariosMedico;
import br.com.pinsaude.onboarding.domain.TipoPix;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DadosBancariosMedicoResponse(
    UUID id,
    TipoPix tipoPix,
    String chavePix,
    String cpfsAdicionaisSplit,
    OffsetDateTime updatedAt
) {
    public static DadosBancariosMedicoResponse from(DadosBancariosMedico d, String chavePIXDecriptografada) {
        return new DadosBancariosMedicoResponse(
            d.getId(), d.getTipoPix(),
            chavePIXDecriptografada,
            d.getCpfsAdicionaisSplit(),
            d.getUpdatedAt()
        );
    }
}
