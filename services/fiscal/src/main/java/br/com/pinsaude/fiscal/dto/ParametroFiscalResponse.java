package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.ParametroFiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

public record ParametroFiscalResponse(
    UUID id,
    String cnpjId,
    LocalDate vigenciaInicio,
    String competenciaInicio,
    // Regime atual
    BigDecimal aliqIss,
    BigDecimal aliqIr,
    BigDecimal aliqCsll,
    BigDecimal aliqPis,
    BigDecimal aliqCofins,
    // IBS/CBS
    boolean ibsCbsAtivo,
    BigDecimal aliqIbsCbs,
    BigDecimal reducaoIbsCbsSaude,
    BigDecimal aliqIbsCbsEfetiva,
    // Controle
    boolean homologado,
    String observacoes,
    OffsetDateTime createdAt
) {
    public static ParametroFiscalResponse from(ParametroFiscal p) {
        BigDecimal efetiva = null;
        if (p.isIbsCbsAtivo() && p.getAliqIbsCbs() != null && p.getReducaoIbsCbsSaude() != null) {
            efetiva = p.getAliqIbsCbs()
                .multiply(BigDecimal.ONE.subtract(p.getReducaoIbsCbsSaude()))
                .setScale(6, RoundingMode.HALF_UP);
        }
        String comp = p.getVigenciaInicio().getYear() + "-"
                    + String.format("%02d", p.getVigenciaInicio().getMonthValue());
        return new ParametroFiscalResponse(
            p.getId(), p.getCnpjId(), p.getVigenciaInicio(), comp,
            p.getAliqIss(), p.getAliqIr(), p.getAliqCsll(), p.getAliqPis(), p.getAliqCofins(),
            p.isIbsCbsAtivo(), p.getAliqIbsCbs(), p.getReducaoIbsCbsSaude(), efetiva,
            p.isHomologado(), p.getObservacoes(), p.getCreatedAt()
        );
    }
}
