package br.com.pinsaude.faturamento.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FechamentoPreviewResponse(
    UUID tomadorId,
    String competencia,
    List<ModalidadeDetalhe> modalidades,
    List<GrupoPreview> grupos,
    long totalCentavos,
    int totalFrequencias
) {
    /** Uma linha da tabela global de tipos de serviço (top table) */
    public record ModalidadeDetalhe(
        UUID modalidadeId,
        String nome,
        String turno,
        BigDecimal horas,
        long valorUnitarioCentavos,
        long deslocamentoCentavos,
        long valorItemCentavos,
        int quantidade,
        long totalCentavos,
        boolean fds
    ) {}

    /** Setor operacional dentro de um grupo, com seu subtotal */
    public record SetorDetalhe(
        UUID setorId,
        String setorNome,
        long totalCentavos
    ) {}

    public record GrupoPreview(
        UUID grupoId,
        String nome,
        String descricaoNota,
        String descricaoInterpolada,
        UUID servicoLc116Id,
        List<SetorDetalhe> setores,
        List<MedicoParticipacao> medicos,
        long totalCentavos
    ) {}

    public record MedicoParticipacao(
        UUID medicoId,
        long totalCentavos
    ) {}
}
