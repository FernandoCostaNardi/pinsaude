package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.RegimePresuncao;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record AliquotaCompetenciaRequest(
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Competência deve estar no formato YYYY-MM")
    String competencia,

    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal iss,
    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal ir,
    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal csll,
    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal pis,
    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal cofins,
    @NotNull RegimePresuncao regimePresuncao
) {}
