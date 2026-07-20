package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;

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
    long valorUnitarioCentavos,
    long deslocamentoCentavos,
    long totalItemCentavos,
    OffsetDateTime createdAt
) {
    public static FrequenciaItemResponse from(FrequenciaItem item, TomadorModalidade modalidade) {
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
            item.getValorUnitarioCentavos(),
            item.getDeslocamentoCentavos(),
            item.getValorUnitarioCentavos() + item.getDeslocamentoCentavos(),
            item.getCreatedAt()
        );
    }
}
