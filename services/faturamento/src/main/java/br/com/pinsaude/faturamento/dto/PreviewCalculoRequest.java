package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PreviewCalculoRequest(
    @NotNull(message = "servicoId é obrigatório")
    UUID servicoId,

    @NotNull(message = "tomadorId é obrigatório")
    UUID tomadorId,

    @NotNull(message = "valorBruto é obrigatório")
    @Min(value = 1, message = "Valor bruto deve ser maior que zero")
    Long valorBruto
) {}
