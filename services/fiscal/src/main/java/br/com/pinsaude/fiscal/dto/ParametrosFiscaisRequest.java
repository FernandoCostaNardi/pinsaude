package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.TipoTributo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ParametrosFiscaisRequest(

    @NotNull
    TipoTributo tipoTributo,

    /** Competência de início de vigência — YYYY-MM. */
    @NotNull
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "competenciaInicio deve estar no formato YYYY-MM")
    String competenciaInicio,

    /** Competência de fim de vigência — YYYY-MM (opcional; null = sem fim). */
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "competenciaFim deve estar no formato YYYY-MM")
    String competenciaFim,

    /** Alíquota em fração decimal: 0.0200 = 2%. */
    @NotNull
    @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000")
    BigDecimal valorAliquota,

    String descricao
) {}
