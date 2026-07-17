package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorModalidade;

import java.math.BigDecimal;
import java.util.UUID;

public record TomadorModalidadeResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    String turno,
    String horario,
    BigDecimal horas,
    long valorCentavos,
    long deslocamentoCentavos,
    boolean ativo
) {
    public static TomadorModalidadeResponse from(TomadorModalidade m) {
        return new TomadorModalidadeResponse(
            m.getId(),
            m.getTomadorId(),
            m.getNome(),
            m.getTurno(),
            m.getHorario(),
            m.getHoras(),
            m.getValorCentavos(),
            m.getDeslocamentoCentavos(),
            m.isAtivo()
        );
    }
}
