package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.RegimeTributario;

import java.math.BigDecimal;

/**
 * CBS-41 — CBS/IBS de uma nota pela regra do motor fiscal (valores em centavos, alíquotas em
 * fração já com a redução do serviço). No LEGADO tudo é zero/nulo e só o regime importa.
 * {@code valorIbsUf + valorIbsMun = valorIbs}.
 */
public record TributacaoIbsCbs(
    RegimeTributario regime,
    boolean porFora,
    long baseCalculo,
    BigDecimal aliqCbs,
    BigDecimal aliqIbs,
    BigDecimal aliqIbsUf,
    BigDecimal aliqIbsMun,
    BigDecimal fatorIss,
    long valorCbs,
    long valorIbs,
    long valorIbsUf,
    long valorIbsMun
) {
    static TributacaoIbsCbs legado(BigDecimal fatorIss) {
        return new TributacaoIbsCbs(RegimeTributario.LEGADO, false, 0L,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, fatorIss,
            0L, 0L, 0L, 0L);
    }

    public boolean ibsCbs() { return regime == RegimeTributario.IBS_CBS; }
}
