package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edição de um ano do cronograma. Alíquotas em fração decimal (0.1120 = 11,2%); nulas = não fixadas. */
public record CronogramaReformaRequest(
    @DecimalMin("0") @DecimalMax("1") BigDecimal aliqCbs,
    @DecimalMin("0") @DecimalMax("1") BigDecimal aliqIbsUf,
    @DecimalMin("0") @DecimalMax("1") BigDecimal aliqIbsMun,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal fatorIss,
    boolean aliquotasDefinitivas,
    @Size(max = 2000) String fonte
) {}
