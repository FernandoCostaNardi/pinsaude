package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EmitirLoteRequest(
    @NotNull
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Competência deve estar no formato YYYY-MM")
    String competencia
) {}
