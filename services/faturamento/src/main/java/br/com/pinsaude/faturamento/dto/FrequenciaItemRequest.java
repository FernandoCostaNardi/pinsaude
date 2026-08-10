package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FrequenciaItemRequest(
    @NotNull UUID modalidadeId,
    @NotNull LocalDate dataExecucao,
    @Size(max = 120) String ocorrencia,
    @DecimalMin(value = "0.01", message = "horas trabalhadas deve ser maior que zero") BigDecimal horasTrabalhadas
) {}
