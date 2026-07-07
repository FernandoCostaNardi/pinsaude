package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Map;

public record CalculoFiscalRequest(

    /** Valor bruto do serviço em centavos. */
    @Positive
    long valorBruto,

    /** Competência no formato YYYY-MM (ex: "2024-06"). */
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "competencia deve estar no formato YYYY-MM")
    String competencia,

    /** true = tomador é Pessoa Jurídica; false = Pessoa Física. */
    boolean tomadorPj,

    /** PJ: tomador retém IR/CSLL/PIS/COFINS na fonte. */
    boolean indicadorRetencaoFederal,

    /** PJ: tomador retém ISS (substituição tributária). */
    boolean indicadorRetencaoIss,

    /** PF: tomador é equiparado a hospital (nota com tributos zerados). */
    boolean equiparacaoHospitalar,

    /**
     * Alíquotas do tomador que sobrescrevem as da empresa (chave = ISS|IR|CSLL|PIS|COFINS, valor = fração decimal 0.05 = 5%).
     * Nullable — quando ausente o motor usa as alíquotas configuradas na empresa.
     */
    Map<String, BigDecimal> aliquotasOverride
) {}
