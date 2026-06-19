package br.com.pinsaude.fiscal.service;

import java.math.BigDecimal;

record AliquotasCenario(
    BigDecimal iss,
    BigDecimal ir,
    BigDecimal csll,
    BigDecimal pis,
    BigDecimal cofins,
    boolean ibsCbsAtivo,
    BigDecimal aliqIbsCbsEfetiva
) {}
