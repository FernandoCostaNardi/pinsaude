package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Fechamento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FechamentoResponse(
    UUID id,
    UUID tomadorId,
    String competencia,
    String status,
    long totalCentavos,
    List<ProducaoRef> producoes,
    OffsetDateTime createdAt,
    OffsetDateTime fechadoEm
) {
    public record ProducaoRef(
        UUID grupoId,
        String grupoNome,
        UUID producaoId,
        long totalCentavos
    ) {}

    public static FechamentoResponse from(Fechamento f, List<ProducaoRef> producoes) {
        return new FechamentoResponse(
            f.getId(),
            f.getTomadorId(),
            f.getCompetencia(),
            f.getStatus(),
            f.getTotalCentavos(),
            producoes,
            f.getCreatedAt(),
            f.getFechadoEm()
        );
    }
}
