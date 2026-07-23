package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FrequenciaMedicaResponse(
    UUID id,
    UUID tomadorId,
    UUID medicoId,
    UUID servicoOperacionalId,
    String servicoOperacionalNome,
    String competencia,
    String especialidade,
    String tipoMedico,
    String status,
    boolean documentoAssinado,
    OffsetDateTime enviadaTomadorEm,
    UUID fechamentoId,
    UUID producaoId,
    OffsetDateTime createdAt,
    List<FrequenciaItemResponse> itens,
    long totalValorCentavos
) {
    public static FrequenciaMedicaResponse from(FrequenciaMedica f,
                                                TomadorServicoOperacional setor,
                                                List<FrequenciaItemResponse> itens) {
        long total = itens.stream()
            .mapToLong(FrequenciaItemResponse::totalItemCentavos)
            .sum();
        return new FrequenciaMedicaResponse(
            f.getId(),
            f.getTomadorId(),
            f.getMedicoId(),
            f.getServicoOperacionalId(),
            setor != null ? setor.getNome() : null,
            f.getCompetencia(),
            f.getEspecialidade(),
            f.getTipoMedico(),
            f.getStatus(),
            f.getDocumentoAssinadoKey() != null,
            f.getEnviadaTomadorEm(),
            f.getFechamentoId(),
            f.getProducaoId(),
            f.getCreatedAt(),
            itens,
            total
        );
    }
}
