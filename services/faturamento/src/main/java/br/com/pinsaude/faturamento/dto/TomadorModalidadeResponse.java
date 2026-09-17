package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorModalidade;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TomadorModalidadeResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    List<String> tipos,
    String turno,
    String horario,
    BigDecimal horas,
    long valorCentavos,
    long deslocamentoCentavos,
    boolean ativo,
    BigDecimal horasSemanais,
    int ordem,
    // Dias da semana em que este turno pode ser lançado — vazio = sem restrição.
    List<String> diasSemana
) {
    public static TomadorModalidadeResponse from(TomadorModalidade m) {
        return new TomadorModalidadeResponse(
            m.getId(),
            m.getTomadorId(),
            m.getNome(),
            List.of(m.getTipos()),
            m.getTurno(),
            m.getHorario(),
            m.getHoras(),
            m.getValorCentavos(),
            m.getDeslocamentoCentavos(),
            m.isAtivo(),
            m.getHorasSemanais(),
            m.getOrdem(),
            m.getDiasSemana() != null ? List.of(m.getDiasSemana()) : List.of()
        );
    }
}
