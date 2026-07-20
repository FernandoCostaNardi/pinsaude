package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FechamentoRequest(
    @NotNull UUID tomadorId,
    @NotBlank String competencia
) {}
