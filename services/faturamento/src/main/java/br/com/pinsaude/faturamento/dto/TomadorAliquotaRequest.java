package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record TomadorAliquotaRequest(
    @NotBlank @Pattern(regexp = "ISS|IR|CSLL|PIS|COFINS", message = "tipoTributo inválido")
    String tipoTributo,

    @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000")
    BigDecimal valorAliquota  // percentual: 5.0000 = 5%
) {}
