package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Liga a chave CBS/IBS da empresa a partir de uma competência (CBS-14).
 * ISS/IR/CSLL/PIS/COFINS são copiados do parâmetro vigente; aliqIss opcional sobrescreve o ISS.
 * aliqIbsCbs e reducaoSaude estão deprecados (alíquotas vêm do cronograma; redução vem do serviço LC116)
 * e só são gravados se informados. Frações decimais: 0.01 = 1%, 0.60 = 60%.
 */
public record IbsCbsRequest(

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Formato: YYYY-MM")
    String competenciaInicio,

    /** Deprecado. */
    @Positive
    BigDecimal aliqIbsCbs,

    /** Deprecado. */
    @DecimalMin("0") @DecimalMax("1")
    BigDecimal reducaoSaude,

    /** Opcional: sobrescreve o ISS copiado do parâmetro vigente. */
    BigDecimal aliqIss,

    String observacoes
) {}
