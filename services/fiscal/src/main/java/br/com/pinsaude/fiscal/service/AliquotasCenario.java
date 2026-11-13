package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.RegimeTributario;

import java.math.BigDecimal;

/**
 * Alíquotas resolvidas para uma competência (todas em fração: 0.02 = 2%).
 *
 * No regime IBS_CBS, {@code pis}/{@code cofins} vêm zeradas, {@code aliqCbs}/{@code aliqIbs}
 * já incluem a redução do serviço e {@code fatorIss} é o fator do ano no cronograma.
 * No regime LEGADO, CBS/IBS são zero e {@code fatorIss} = 1.
 */
record AliquotasCenario(
    BigDecimal iss,
    BigDecimal ir,
    BigDecimal csll,
    BigDecimal pis,
    BigDecimal cofins,
    RegimeTributario regime,
    BigDecimal aliqCbs,
    BigDecimal aliqIbs,
    BigDecimal fatorIss
) {
    boolean ibsCbsAtivo() { return regime == RegimeTributario.IBS_CBS; }
}
