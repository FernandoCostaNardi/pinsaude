package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    List<FrequenciaSemanaProgressoResponse> progressoSemanal,
    // PINSAUDE-13.26: modalidade/ocorrência escolhidas na criação da frequência — null pra
    // frequências legadas sem modalidade fixa (ver FrequenciaService). Quando não-null, o
    // frontend esconde os seletores de modalidade/ocorrência no formulário de plantão e usa
    // estes campos diretamente (sem chamada extra).
    UUID modalidadeId,
    String modalidadeNome,
    String modalidadeTipo,
    String modalidadeTurno,
    String modalidadeHorario,
    BigDecimal modalidadeHoras,
    BigDecimal modalidadeHorasSemanais,
    long modalidadeValorCentavos,
    long modalidadeDeslocamentoCentavos,
    UUID ocorrenciaId,
    String ocorrenciaNome,
    // PINSAUDE-13.26 (ajuste pós-implantação): valor CADASTRADO da ocorrência fixa, calculado uma
    // única vez sobre o valor da modalidade — nunca por item. Sempre exibido (mesmo sem nenhum
    // item lançado ainda), espelhando modalidadeValorCentavos — mas só entra em
    // totalValorCentavos quando há pelo menos 1 item lançado (ver ocorrenciaAplicadaNoTotal).
    long ocorrenciaValorCentavos
) {
    public static FrequenciaMedicaResponse from(FrequenciaMedica f,
                                                TomadorServicoOperacional setor,
                                                List<FrequenciaItemResponse> itens) {
        return from(f, setor, itens, Map.of(), Map.of());
    }

    public static FrequenciaMedicaResponse from(FrequenciaMedica f,
                                                TomadorServicoOperacional setor,
                                                List<FrequenciaItemResponse> itens,
                                                Map<UUID, TomadorModalidade> modalidadesMap,
                                                Map<UUID, TomadorOcorrencia> ocorrenciasMap) {
        long totalItens = itens.stream()
            .mapToLong(FrequenciaItemResponse::totalItemCentavos)
            .sum();
        long totalMensalDiarista = valorMensalDiaristaUnico(itens, modalidadesMap);
        TomadorModalidade modalidadeFreq = f.getModalidadeId() != null ? modalidadesMap.get(f.getModalidadeId()) : null;
        TomadorOcorrencia ocorrenciaFreq = f.getOcorrenciaId() != null ? ocorrenciasMap.get(f.getOcorrenciaId()) : null;
        long ocorrenciaValorCadastrado = calcularValorOcorrenciaUnico(modalidadeFreq, ocorrenciaFreq);
        // só soma no total quando já existe pelo menos 1 plantão lançado — sem lançamento, não
        // faz sentido aplicar o bônus da ocorrência (mesmo critério de valorMensalDiaristaUnico).
        long ocorrenciaAplicadaNoTotal = itens.isEmpty() ? 0L : ocorrenciaValorCadastrado;
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
            totalItens + totalMensalDiarista + ocorrenciaAplicadaNoTotal,
            List.of(),
            calcularProgressoSemanal(itens, modalidadesMap),
            f.getModalidadeId(),
            modalidadeFreq != null ? modalidadeFreq.getNome() : null,
            modalidadeFreq != null ? modalidadeFreq.getTipo() : null,
            modalidadeFreq != null ? modalidadeFreq.getTurno() : null,
            modalidadeFreq != null ? modalidadeFreq.getHorario() : null,
            modalidadeFreq != null ? modalidadeFreq.getHoras() : null,
            modalidadeFreq != null ? modalidadeFreq.getHorasSemanais() : null,
            modalidadeFreq != null ? modalidadeFreq.getValorCentavos() : 0L,
            modalidadeFreq != null ? modalidadeFreq.getDeslocamentoCentavos() : 0L,
            f.getOcorrenciaId(),
            ocorrenciaFreq != null ? ocorrenciaFreq.getNome() : null,
            ocorrenciaValorCadastrado
        );
    }

    // PINSAUDE-13.26 (ajuste pós-implantação): mesma fórmula de FrequenciaService.
    // calcularValorOcorrencia (percentual sobre o valor cadastrado da modalidade + valor fixo),
    // mas aplicada UMA ÚNICA VEZ por frequência — nunca por item lançado. Duplicada aqui (não
    // compartilhada com o service) porque este é um contexto estático de DTO, sem acesso a uma
    // instância de FrequenciaService.
    private static long calcularValorOcorrenciaUnico(TomadorModalidade modalidadeFreq, TomadorOcorrencia ocorrenciaFreq) {
        if (modalidadeFreq == null || ocorrenciaFreq == null) return 0L;
        long total = 0L;
        if (ocorrenciaFreq.getValorPercentual() != null) {
            total += BigDecimal.valueOf(modalidadeFreq.getValorCentavos())
                .multiply(ocorrenciaFreq.getValorPercentual())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        }
        if (ocorrenciaFreq.getValorCentavos() != null) {
            total += ocorrenciaFreq.getValorCentavos();
        }
        return total;
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
