package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorHorarioPadrao;

import java.math.BigDecimal;
import java.util.UUID;

public record TomadorHorarioPadraoResponse(
    UUID id,
    UUID tomadorId,
    String turno,
    BigDecimal horas,
    String horario,
    int ordem,
    boolean ativo
) {
    public static TomadorHorarioPadraoResponse from(TomadorHorarioPadrao h) {
        return new TomadorHorarioPadraoResponse(
            h.getId(),
            h.getTomadorId(),
            h.getTurno(),
            h.getHoras(),
            h.getHorario(),
            h.getOrdem(),
            h.isAtivo()
        );
    }
}
