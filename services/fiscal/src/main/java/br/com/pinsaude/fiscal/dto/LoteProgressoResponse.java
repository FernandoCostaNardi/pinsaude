package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.LoteEmissao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoteProgressoResponse(
    UUID loteId,
    String competencia,
    int total,
    int emitidas,
    int falhas,
    int emProcessamento,
    String status,
    double percentualConcluido,
    OffsetDateTime iniciadoEm,
    OffsetDateTime concluidoEm
) {
    public static LoteProgressoResponse from(LoteEmissao l) {
        double pct = l.getTotal() > 0
            ? Math.min(100.0, (double) (l.getEmitidas() + l.getFalhas()) / l.getTotal() * 100)
            : 0.0;
        return new LoteProgressoResponse(
            l.getId(), l.getCompetencia(), l.getTotal(),
            l.getEmitidas(), l.getFalhas(), l.getEmProcessamento(),
            l.getStatus(), pct, l.getIniciadoEm(), l.getConcluidoEm()
        );
    }
}
