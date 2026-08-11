package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FrequenciaItemResponse(
    UUID id,
    UUID frequenciaId,
    UUID modalidadeId,
    String modalidadeNome,
    String modalidadeTurno,
    String modalidadeHorario,
    BigDecimal modalidadeHoras,
    LocalDate dataExecucao,
    String ocorrencia,
    UUID ocorrenciaId,
    String ocorrenciaNome,
    Long ocorrenciaValorCentavos,
    BigDecimal horasTrabalhadas,
    long valorUnitarioCentavos,
    long deslocamentoCentavos,
    long totalItemCentavos,
    OffsetDateTime createdAt
) {
    public static FrequenciaItemResponse from(FrequenciaItem item, TomadorModalidade modalidade,
                                              TomadorOcorrencia ocorrencia) {
        long ocorrenciaValor = item.getOcorrenciaValorCentavos() != null ? item.getOcorrenciaValorCentavos() : 0L;
        return new FrequenciaItemResponse(
            item.getId(),
            item.getFrequenciaId(),
            item.getModalidadeId(),
            modalidade != null ? modalidade.getNome()    : null,
            modalidade != null ? modalidade.getTurno()   : null,
            modalidade != null ? modalidade.getHorario() : null,
            modalidade != null ? modalidade.getHoras()   : null,
            item.getDataExecucao(),
            item.getOcorrencia(),
            item.getOcorrenciaId(),
            ocorrencia != null ? ocorrencia.getNome() : null,
            item.getOcorrenciaValorCentavos(),
            item.getHorasTrabalhadas(),
            item.getValorUnitarioCentavos(),
            item.getDeslocamentoCentavos(),
            item.getValorUnitarioCentavos() + item.getDeslocamentoCentavos() + ocorrenciaValor,
            item.getCreatedAt()
        );
    }
}
