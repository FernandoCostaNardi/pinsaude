package br.com.pinsaude.fiscal.dto;

public record CalculoFiscalResponse(

    long valorBruto,

    /** 15% do valorBruto — custo fiscal da Pin Saúde. */
    long taxaPin,

    long valorIss,
    long valorIr,
    long valorCsll,
    long valorPis,
    long valorCofins,

    /** Soma dos tributos retidos pelo tomador (deduzidos do repasse ao prestador). */
    long totalRetencoes,

    /** Soma de todos os tributos calculados (ISS + federais). */
    long totalTributos,

    /**
     * Repasse ao médico — SEMPRE = valorBruto - taxaPin (85%).
     * Tributos saem da cota da Pin Saúde, nunca do médico.
     */
    long valorLiquidoMedico,

    /** Resultado líquido da Pin: taxaPin − totalTributos. Pode ser negativo. */
    long resultadoPin,

    /** Cenário fiscal aplicado: "A", "B", "C" ou "D". */
    String cenario,

    String competencia,

    AuditoriaCenario auditoria
) {}
