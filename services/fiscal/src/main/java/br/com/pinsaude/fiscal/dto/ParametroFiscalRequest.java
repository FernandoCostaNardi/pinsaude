package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Parâmetro fiscal genérico (regime atual ou IBS/CBS).
 * Alíquotas como fração decimal: 0.0200 = 2%, 0.0100 = 1%.
 */
public record ParametroFiscalRequest(

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Formato: YYYY-MM")
    String competenciaInicio,

    @NotNull @DecimalMin("0") @DecimalMax("1")
    BigDecimal aliqIss,

    @NotNull @DecimalMin("0") @DecimalMax("1")
    BigDecimal aliqIr,

    @NotNull @DecimalMin("0") @DecimalMax("1")
    BigDecimal aliqCsll,

    @NotNull @DecimalMin("0") @DecimalMax("1")
    BigDecimal aliqPis,

    @NotNull @DecimalMin("0") @DecimalMax("1")
    BigDecimal aliqCofins,

    boolean ibsCbsAtivo,

    BigDecimal aliqIbsCbs,

    BigDecimal reducaoIbsCbsSaude,

    String observacoes
) {}
