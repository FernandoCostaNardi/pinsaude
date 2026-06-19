package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.ParametrosFiscais;
import br.com.pinsaude.fiscal.domain.TipoTributo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ParametrosFiscaisResponse(
    UUID id,
    TipoTributo tipoTributo,
    String competenciaInicio,
    String competenciaFim,
    BigDecimal valorAliquota,
    /** Valor em percentual para exibição: 0.0200 → 2.00 */
    BigDecimal valorAliquotaPct,
    String descricao,
    String createdBy,
    OffsetDateTime createdAt
) {
    public static ParametrosFiscaisResponse from(ParametrosFiscais p) {
        BigDecimal pct = p.getValorAliquota() == null ? null
            : p.getValorAliquota().multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
        return new ParametrosFiscaisResponse(
            p.getId(), p.getTipoTributo(),
            p.getCompetenciaInicio(), p.getCompetenciaFim(),
            p.getValorAliquota(), pct,
            p.getDescricao(), p.getCreatedBy(), p.getCreatedAt()
        );
    }
}
