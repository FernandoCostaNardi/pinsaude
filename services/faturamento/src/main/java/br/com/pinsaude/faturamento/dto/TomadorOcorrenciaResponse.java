package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;

import java.math.BigDecimal;
import java.util.UUID;

public record TomadorOcorrenciaResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    String tipoValor,
    BigDecimal valorPercentual,
    Long valorCentavos,
    boolean ativo
) {
    public static TomadorOcorrenciaResponse from(TomadorOcorrencia o) {
        return new TomadorOcorrenciaResponse(
            o.getId(),
            o.getTomadorId(),
            o.getNome(),
            o.getTipoValor(),
            o.getValorPercentual(),
            o.getValorCentavos(),
            o.isAtivo()
        );
    }
}
