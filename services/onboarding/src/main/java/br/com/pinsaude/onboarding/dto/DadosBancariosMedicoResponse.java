package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.DadosBancariosMedico;
import br.com.pinsaude.onboarding.domain.TipoPix;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DadosBancariosMedicoResponse(
    UUID id,
    String tipoRecebimento,
    // PIX
    TipoPix tipoPix,
    String chavePix,
    String cpfsAdicionaisSplit,
    // TED
    String bancoCodigo,
    String bancoNome,
    String agencia,
    String conta,
    String tipoConta,
    OffsetDateTime updatedAt
) {
    public static DadosBancariosMedicoResponse from(DadosBancariosMedico d, String chavePIXDecriptografada) {
        return new DadosBancariosMedicoResponse(
            d.getId(),
            d.getTipoRecebimento() != null ? d.getTipoRecebimento() : "PIX",
            d.getTipoPix(),
            chavePIXDecriptografada,
            d.getCpfsAdicionaisSplit(),
            d.getBancoCodigo(),
            d.getBancoNome(),
            d.getAgencia(),
            d.getConta(),
            d.getTipoConta(),
            d.getUpdatedAt()
        );
    }
}
