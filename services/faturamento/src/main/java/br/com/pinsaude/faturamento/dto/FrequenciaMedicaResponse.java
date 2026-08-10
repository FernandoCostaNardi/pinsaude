package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    // Agrupa os itens por modalidade META e resume o acumulado/quanto falta pro bloco atual.
    // Modalidades PLANTAO/MENSAL (ou META sem itens lançados) não entram no resultado.
    private static List<FrequenciaModalidadeProgressoResponse> calcularProgressoMetas(
            List<FrequenciaItemResponse> itens, Map<UUID, TomadorModalidade> modalidadesMap) {
        Map<UUID, List<FrequenciaItemResponse>> porModalidade = itens.stream()
            .filter(i -> {
                TomadorModalidade m = modalidadesMap.get(i.modalidadeId());
                return m != null && "META".equals(m.getTipo());
            })
            .collect(Collectors.groupingBy(FrequenciaItemResponse::modalidadeId));

        return porModalidade.entrySet().stream()
            .map(e -> {
                TomadorModalidade m = modalidadesMap.get(e.getKey());
                List<FrequenciaItemResponse> itensModalidade = e.getValue();
                BigDecimal acumuladoHoras = itensModalidade.stream()
                    .map(FrequenciaItemResponse::horasTrabalhadas)
                    .filter(h -> h != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                int acumuladoDias = itensModalidade.size();
                return FrequenciaModalidadeProgressoResponse.calcular(
                    m.getId(), m.getNome(), m.getUnidadeCalculo(),
                    m.getMetaHoras(), m.getMetaDias(),
                    acumuladoHoras, acumuladoDias
                );
            })
            .sorted((a, b) -> a.modalidadeNome().compareToIgnoreCase(b.modalidadeNome()))
            .toList();
    }
}
