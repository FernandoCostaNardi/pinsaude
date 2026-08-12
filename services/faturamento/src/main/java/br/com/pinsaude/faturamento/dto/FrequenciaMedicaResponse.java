package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    long totalValorCentavos,
    List<FrequenciaModalidadeProgressoResponse> progressoMetas
) {
    public static FrequenciaMedicaResponse from(FrequenciaMedica f,
                                                TomadorServicoOperacional setor,
                                                List<FrequenciaItemResponse> itens) {
        return from(f, setor, itens, Map.of());
    }

    public static FrequenciaMedicaResponse from(FrequenciaMedica f,
                                                TomadorServicoOperacional setor,
                                                List<FrequenciaItemResponse> itens,
                                                Map<UUID, TomadorModalidade> modalidadesMap) {
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
            total,
            calcularProgressoMetas(itens, modalidadesMap)
        );
    }

    // PINSAUDE-13.22: a modalidade META (única que alimentava este acompanhamento por "bloco")
    // foi removida — ver TomadorService.aplicarCamposPorTipo. O acompanhamento semanal do tipo
    // DIARISTA (que substitui este conceito) é implementado em PINSAUDE-13.23; até lá, nenhuma
    // modalidade tem progresso para exibir.
    private static List<FrequenciaModalidadeProgressoResponse> calcularProgressoMetas(
            List<FrequenciaItemResponse> itens, Map<UUID, TomadorModalidade> modalidadesMap) {
        return List.of();
    }
}
