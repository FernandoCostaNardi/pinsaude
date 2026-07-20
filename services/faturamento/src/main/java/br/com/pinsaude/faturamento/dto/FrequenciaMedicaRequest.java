package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FrequenciaMedicaRequest(
    @NotNull UUID tomadorId,
    @NotNull UUID medicoId,
    @NotNull UUID servicoOperacionalId,
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String competencia,     // YYYY-MM
    @NotBlank @Size(max = 120) String especialidade
) {}
