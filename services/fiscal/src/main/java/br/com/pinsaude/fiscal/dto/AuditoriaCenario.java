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
    BigDecimal aliqIbsCbsEfetivaUsada
) {}
