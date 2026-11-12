package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.CronogramaReforma;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Um ano do cronograma, com as alíquotas em fração e em percentual (para exibição). */
public record CronogramaReformaResponse(
    int ano,
    BigDecimal aliqCbs,
    BigDecimal aliqIbsUf,
    BigDecimal aliqIbsMun,
    BigDecimal aliqIbs,
    BigDecimal fatorIss,
    BigDecimal aliqCbsPct,
    BigDecimal aliqIbsPct,
    BigDecimal fatorIssPct,
    boolean aliquotasDefinitivas,
    String fonte,
    String atualizadoPor,
    OffsetDateTime atualizadoEm
) {
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public static CronogramaReformaResponse from(CronogramaReforma c) {
        BigDecimal ibs = (c.getAliqIbsUf() != null && c.getAliqIbsMun() != null)
            ? c.getAliqIbsUf().add(c.getAliqIbsMun())
            : null;
        return new CronogramaReformaResponse(
            c.getAno(), c.getAliqCbs(), c.getAliqIbsUf(), c.getAliqIbsMun(), ibs, c.getFatorIss(),
            pct(c.getAliqCbs()), pct(ibs), pct(c.getFatorIss()),
            c.isAliquotasDefinitivas(), c.getFonte(), c.getAtualizadoPor(), c.getAtualizadoEm()
        );
    }

    private static BigDecimal pct(BigDecimal fracao) {
        return fracao == null ? null : fracao.multiply(CEM).stripTrailingZeros();
    }
}
