package br.com.pinsaude.faturamento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acompanhamento (read-only) da carga horária semanal de uma modalidade DIARISTA: quantas horas
 * foram lançadas em cada semana ISO (segunda a domingo) da competência, comparado à meta semanal
 * cadastrada na modalidade (horasSemanais). Puramente informativo — nunca altera o valor pago
 * (a frequência sempre paga o valor mensal fixo da modalidade, somado uma única vez, ver
 * FrequenciaMedicaResponse). Se a meta de uma semana não for cumprida, não há nenhuma ação
 * automática — só um indicador visual pra UI (campo `cumprida`).
 */
public record FrequenciaSemanaProgressoResponse(
    LocalDate semanaInicio,
    LocalDate semanaFim,
    BigDecimal horasLancadas,
    BigDecimal metaHoras,
    boolean cumprida
) {
}
