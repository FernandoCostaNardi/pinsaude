package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TomadorOcorrenciaResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    String tipoValor,
    BigDecimal valorPercentual,
    Long valorCentavos,
    boolean ativo,
    // Setores Operacionais em que esta ocorrência é sugerida — vazio = todos os setores do
    // tomador (bypass, ver SetorOperacionalOcorrencia).
    List<UUID> setorIds
) {
    public static TomadorOcorrenciaResponse from(TomadorOcorrencia o, List<UUID> setorIds) {
        return new TomadorOcorrenciaResponse(
            o.getId(),
            o.getTomadorId(),
            o.getNome(),
            o.getTipoValor(),
            o.getValorPercentual(),
            o.getValorCentavos(),
            o.isAtivo(),
            setorIds
        );
    }
}
