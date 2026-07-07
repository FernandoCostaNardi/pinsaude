package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorAliquota;

import java.math.BigDecimal;
import java.util.UUID;

public record TomadorAliquotaResponse(
    UUID id,
    String tipoTributo,
    BigDecimal valorAliquota  // percentual: 5.0000 = 5%
) {
    public static TomadorAliquotaResponse from(TomadorAliquota a) {
        return new TomadorAliquotaResponse(a.getId(), a.getTipoTributo(), a.getValorAliquota());
    }
}
