package br.com.pinsaude.faturamento.dto;

import java.util.List;
import java.util.UUID;

public record FechamentoPreviewResponse(
    UUID tomadorId,
    String competencia,
    List<GrupoPreview> grupos,
    long totalCentavos,
    int totalFrequencias
) {
    public record GrupoPreview(
        UUID grupoId,
        String nome,
        String descricaoNota,
        String descricaoInterpolada,
        UUID servicoLc116Id,
        List<MedicoParticipacao> medicos,
        long totalCentavos
    ) {}

    public record MedicoParticipacao(
        UUID medicoId,
        long totalCentavos
    ) {}
}
