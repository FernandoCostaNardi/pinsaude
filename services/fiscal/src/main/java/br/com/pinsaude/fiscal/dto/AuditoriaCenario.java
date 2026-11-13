package br.com.pinsaude.fiscal.dto;

import java.math.BigDecimal;

public record AuditoriaCenario(
    String competencia,
    String cenario,
    BigDecimal aliqIssUsada,
    BigDecimal aliqIrUsada,
    BigDecimal aliqCsllUsada,
    BigDecimal aliqPisUsada,
    BigDecimal aliqCofinsUsada,
    boolean ibsCbsAtivo,
    /** CBS + IBS efetivos (com a redução do serviço). Null no regime LEGADO. */
    BigDecimal aliqIbsCbsEfetivaUsada,
    /** LEGADO ou IBS_CBS. */
    String regime,
    BigDecimal aliqCbsUsada,
    BigDecimal aliqIbsUsada,
    BigDecimal fatorIssUsado
) {}
