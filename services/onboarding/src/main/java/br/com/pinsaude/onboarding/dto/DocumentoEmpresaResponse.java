package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.DocumentoEmpresa;
import br.com.pinsaude.onboarding.domain.StatusValidacaoDocumento;
import br.com.pinsaude.onboarding.domain.TipoDocumentoEmpresa;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentoEmpresaResponse(
    UUID id,
    TipoDocumentoEmpresa tipo,
    String nomeArquivo,
    String caminhoStorage,
    StatusValidacaoDocumento statusValidacao,
    String motivoReprovacao,
    boolean versaoAtual,
    OffsetDateTime createdAt
) {
    public static DocumentoEmpresaResponse from(DocumentoEmpresa d) {
        return new DocumentoEmpresaResponse(
            d.getId(), d.getTipo(), d.getNomeArquivo(),
            d.getCaminhoStorage(), d.getStatusValidacao(),
            d.getMotivoReprovacao(), d.isVersaoAtual(), d.getCreatedAt()
        );
    }
}
