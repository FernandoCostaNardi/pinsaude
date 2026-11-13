package br.com.pinsaude.fiscal.motor;

import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import br.com.pinsaude.fiscal.domain.ParametroFiscal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cronograma completo para testes (2026–2028 iguais à V15; 2029–2033 com alíquotas fictícias,
 * já que na V15 ainda estão nulas) e parâmetros fiscais da empresa.
 */
final class CronogramaFixture {

    private CronogramaFixture() {}

    static final LocalDate VIRADA = LocalDate.of(2027, 1, 1);

    static Optional<CronogramaReforma> ano(short ano) {
        return switch (ano) {
            case 2026 -> Optional.of(cron(ano, "0.0090", "0.0010", "0.0000", "1.0000"));
            case 2027, 2028 -> Optional.of(cron(ano, "0.1120", "0.0005", "0.0005", "1.0000"));
            case 2029 -> Optional.of(cron(ano, "0.0880", "0.0200", "0.0100", "0.9000"));
            case 2030 -> Optional.of(cron(ano, "0.0880", "0.0400", "0.0200", "0.8000"));
            case 2031 -> Optional.of(cron(ano, "0.0880", "0.0600", "0.0300", "0.7000"));
            case 2032 -> Optional.of(cron(ano, "0.0880", "0.0800", "0.0400", "0.6000"));
            case 2033 -> Optional.of(cron(ano, "0.0880", "0.1000", "0.0700", "0.0000"));
            default -> Optional.empty();
        };
    }

    static CronogramaReforma cron(short ano, String cbs, String ibsUf, String ibsMun, String fatorIss) {
        CronogramaReforma c = new CronogramaReforma();
        c.setAno(ano);
        c.setAliqCbs(cbs == null ? null : new BigDecimal(cbs));
        c.setAliqIbsUf(ibsUf == null ? null : new BigDecimal(ibsUf));
        c.setAliqIbsMun(ibsMun == null ? null : new BigDecimal(ibsMun));
        c.setFatorIss(new BigDecimal(fatorIss));
        return c;
    }

    /** ISS 2%, IR 1,5%, CSLL 1%, PIS 0,65%, COFINS 3% — regime anterior. */
    static ParametroFiscal legado() {
        ParametroFiscal pf = new ParametroFiscal();
        pf.setAliqIss(new BigDecimal("0.0200"));
        pf.setAliqIr(new BigDecimal("0.0150"));
        pf.setAliqCsll(new BigDecimal("0.0100"));
        pf.setAliqPis(new BigDecimal("0.0065"));
        pf.setAliqCofins(new BigDecimal("0.0300"));
        pf.setIbsCbsAtivo(false);
        pf.setVigenciaInicio(LocalDate.of(2024, 1, 1));
        return pf;
    }

    /** Mesmas alíquotas, com a chave ligada a partir de 2027-01. */
    static ParametroFiscal chaveLigada(boolean homologado) {
        ParametroFiscal pf = legado();
        pf.setIbsCbsAtivo(true);
        pf.setHomologado(homologado);
        pf.setVigenciaInicio(VIRADA);
        return pf;
    }

    /** Simula o findFirst...LessThanEqual...Desc: antes da virada vale o legado. */
    static Optional<ParametroFiscal> vigente(LocalDate dataRef, boolean homologado) {
        return Optional.of(dataRef.isBefore(VIRADA) ? legado() : chaveLigada(homologado));
    }
}
