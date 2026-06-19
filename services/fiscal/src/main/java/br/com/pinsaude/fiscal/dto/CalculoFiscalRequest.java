package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

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
    boolean equiparacaoHospitalar
) {}
