package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PreviewCalculoRequest(
    @NotNull(message = "servicoId é obrigatório")
    UUID servicoId,

    @NotNull(message = "tomadorId é obrigatório")
    UUID tomadorId,

    @NotNull(message = "valorBruto é obrigatório")
    @Min(value = 1, message = "Valor bruto deve ser maior que zero")
    Long valorBruto,

    @DecimalMin(value = "0.0100", message = "Taxa Pin mínima é 1%")
    @DecimalMax(value = "0.5000", message = "Taxa Pin máxima é 50%")
    BigDecimal taxaPinPct
) {}
