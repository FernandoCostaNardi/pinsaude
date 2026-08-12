package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    List<FrequenciaModalidadeProgressoResponse> progressoMetas,
    List<FrequenciaSemanaProgressoResponse> progressoSemanal
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
        long totalItens = itens.stream()
            .mapToLong(FrequenciaItemResponse::totalItemCentavos)
            .sum();
        long totalMensalDiarista = valorMensalDiaristaUnico(itens, modalidadesMap);
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
            totalItens + totalMensalDiarista,
            List.of(),
            calcularProgressoSemanal(itens, modalidadesMap)
        );
    }

    // PINSAUDE-13.23: Diarista não paga por lançamento — cada item vale R$0 (ver
    // FrequenciaService.calcularValorItem), servindo só pra registrar presença/horas. O valor da
    // frequência é o valor mensal fixo cadastrado na modalidade, somado UMA ÚNICA VEZ por
    // modalidade Diarista distinta usada, independente de quantos dias foram lançados no mês.
    private static long valorMensalDiaristaUnico(
            List<FrequenciaItemResponse> itens, Map<UUID, TomadorModalidade> modalidadesMap) {
        return itens.stream()
            .map(FrequenciaItemResponse::modalidadeId)
            .distinct()
            .map(modalidadesMap::get)
            .filter(m -> m != null && "DIARISTA".equals(m.getTipo()))
            .mapToLong(TomadorModalidade::getValorCentavos)
            .sum();
    }

    // PINSAUDE-13.23: acompanhamento semanal do tipo DIARISTA — agrupa horasTrabalhadas por
    // semana ISO (segunda a domingo) para cada modalidade DIARISTA usada na frequência,
    // comparando à meta semanal cadastrada (horasSemanais). Puramente informativo, nunca altera
    // o valor pago. Semanas sem nenhum item lançado não aparecem no resultado.
    private static List<FrequenciaSemanaProgressoResponse> calcularProgressoSemanal(
            List<FrequenciaItemResponse> itens, Map<UUID, TomadorModalidade> modalidadesMap) {
        Map<UUID, List<FrequenciaItemResponse>> porModalidade = itens.stream()
            .filter(i -> {
                TomadorModalidade m = modalidadesMap.get(i.modalidadeId());
                return m != null && "DIARISTA".equals(m.getTipo());
            })
            .collect(Collectors.groupingBy(FrequenciaItemResponse::modalidadeId));

        List<FrequenciaSemanaProgressoResponse> resultado = new ArrayList<>();
        for (Map.Entry<UUID, List<FrequenciaItemResponse>> entry : porModalidade.entrySet()) {
            TomadorModalidade m = modalidadesMap.get(entry.getKey());
            BigDecimal meta = m.getHorasSemanais();

            Map<LocalDate, BigDecimal> horasPorSemana = new TreeMap<>();
            for (FrequenciaItemResponse item : entry.getValue()) {
                LocalDate inicioSemana = item.dataExecucao().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                BigDecimal horas = item.horasTrabalhadas() != null ? item.horasTrabalhadas() : BigDecimal.ZERO;
                horasPorSemana.merge(inicioSemana, horas, BigDecimal::add);
            }

            horasPorSemana.forEach((inicio, horas) -> resultado.add(new FrequenciaSemanaProgressoResponse(
                inicio, inicio.plusDays(6), horas, meta,
                meta != null && horas.compareTo(meta) >= 0
            )));
        }
        resultado.sort(Comparator.comparing(FrequenciaSemanaProgressoResponse::semanaInicio));
        return resultado;
    }
}
