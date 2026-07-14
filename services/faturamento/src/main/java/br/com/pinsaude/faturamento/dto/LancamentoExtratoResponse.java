package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.LancamentoExtrato;
import br.com.pinsaude.faturamento.domain.StatusConciliacao;
import br.com.pinsaude.faturamento.domain.TipoLancamentoExtrato;

import java.time.LocalDate;
import java.util.UUID;

public record LancamentoExtratoResponse(
        UUID id,
        UUID extratoId,
        LocalDate dataLancamento,
        String descricao,
        long valorCentavos,
        TipoLancamentoExtrato tipo,
        String identificadorExterno,
        StatusConciliacao statusConciliacao,
        int scoreMatch
) {
    public static LancamentoExtratoResponse from(LancamentoExtrato l) {
        return new LancamentoExtratoResponse(
                l.getId(),
                l.getExtratoId(),
                l.getDataLancamento(),
                l.getDescricao(),
                l.getValor(),
                l.getTipo(),
                l.getIdentificadorExterno(),
                l.getStatusConciliacao(),
                l.getScoreMatch()
        );
    }
}
