package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Endpoint simplificado para parametrizar IBS/CBS (Reforma Tributária 2027).
 * Cria um parametro_fiscal com ibsCbsAtivo=true e IR/CSLL/PIS/COFINS zerados.
 * Alíquotas como fração decimal: 0.01 = 1%, 0.60 = 60%.
 */
public record IbsCbsRequest(

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Formato: YYYY-MM")
    String competenciaInicio,

    @NotNull @Positive
    BigDecimal aliqIbsCbs,

    @NotNull @DecimalMin("0") @DecimalMax("1")
    BigDecimal reducaoSaude,

    /** ISS ainda pode incidir no regime IBS/CBS — padrão 2% se não informado. */
    BigDecimal aliqIss,

    String observacoes
) {}
