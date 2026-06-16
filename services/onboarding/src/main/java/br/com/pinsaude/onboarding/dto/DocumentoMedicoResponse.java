package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.DocumentoMedico;
import br.com.pinsaude.onboarding.domain.StatusValidacaoDocumento;
import br.com.pinsaude.onboarding.domain.TipoDocumentoMedico;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentoMedicoResponse(
    UUID id,
    TipoDocumentoMedico tipo,
    String nomeArquivo,
    String caminhoStorage,
    StatusValidacaoDocumento statusValidacao,
    String motivoReprovacao,
    OffsetDateTime createdAt
) {
    public static DocumentoMedicoResponse from(DocumentoMedico d) {
        return new DocumentoMedicoResponse(
            d.getId(), d.getTipo(), d.getNomeArquivo(),
            d.getCaminhoStorage(), d.getStatusValidacao(),
            d.getMotivoReprovacao(), d.getCreatedAt()
        );
    }
}
