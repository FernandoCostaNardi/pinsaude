package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorCnae;

import java.util.UUID;

public record TomadorCnaeResponse(
    UUID id,
    String codigoCnae,
    String descricao
) {
    public static TomadorCnaeResponse from(TomadorCnae c) {
        return new TomadorCnaeResponse(c.getId(), c.getCodigoCnae(), c.getDescricao());
    }
}
