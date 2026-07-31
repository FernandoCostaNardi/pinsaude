package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorEmpresa;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TomadorEmpresaResponse(
    UUID tomadorId,
    UUID empresaId,
    OffsetDateTime createdAt
) {
    public static TomadorEmpresaResponse from(TomadorEmpresa te) {
        return new TomadorEmpresaResponse(
            te.getTomadorId(),
            te.getEmpresaId(),
            te.getCreatedAt()
        );
    }
}
