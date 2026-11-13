package br.com.pinsaude.fiscal.dto;

import java.math.BigDecimal;

public record CalculoFiscalResponse(

    long valorBruto,

    /** 15% do valorBruto — custo fiscal da Pin Saúde. */
    long taxaPin,

    long valorIss,
    long valorIr,
    long valorCsll,
    long valorPis,
    long valorCofins,

    /** Soma dos tributos retidos pelo tomador (deduzidos do repasse ao prestador). CBS/IBS nunca entram. */
    long totalRetencoes,

    /** Soma de todos os tributos calculados (ISS + federais + CBS + IBS). */
    long totalTributos,

    /**
     * Repasse ao médico — SEMPRE = valorBruto - taxaPin (85%).
     * Tributos saem da cota da Pin Saúde, nunca do médico.
     */
    long valorLiquidoMedico,

    /**
     * Resultado líquido da Pin: taxaPin − tributos que saem da cota da Pin.
     * Com CBS/IBS por fora, CBS/IBS são pagos pelo tomador por cima do bruto e não reduzem o resultado.
     */
    long resultadoPin,

    /** Cenário fiscal aplicado: "A", "B", "C" ou "D". */
    String cenario,

    String competencia,

    AuditoriaCenario auditoria,

    // ─── Reforma tributária (CBS-13) ───

    /** LEGADO ou IBS_CBS. */
    String regime,

    long valorCbs,
    long valorIbs,

    /** Alíquotas efetivas (fração, já com a redução do serviço). Zero no LEGADO. */
    BigDecimal aliqCbs,
    BigDecimal aliqIbs,

    /** Fator do ISS no ano (1 até 2028, 0 em 2033). 1 no LEGADO. */
    BigDecimal fatorIss,

    String cstIbsCbs,
    String cClassTrib,

    /** true = CBS/IBS somados ao preço; false = dentro do bruto. */
    boolean cbsIbsPorFora,

    /** Base de cálculo de CBS/IBS (bruto sem CBS/IBS quando estão dentro do preço). */
    long baseCalculoIbsCbs,

    /** Valor total da nota: bruto + CBS + IBS quando por fora; senão igual ao bruto. */
    long valorTotalNota
) {}
