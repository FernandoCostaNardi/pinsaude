package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Conciliacao;
import br.com.pinsaude.faturamento.domain.LancamentoExtrato;
import br.com.pinsaude.faturamento.domain.StatusConciliacao;
import br.com.pinsaude.faturamento.domain.TipoLancamentoExtrato;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
        int scoreMatch,
        ConciliacaoResumo conciliacao
) {

    public record ConciliacaoResumo(
            UUID conciliacaoId,
            UUID producaoId,
            String tomadorNome,
            long valorBruto,
            String competencia,
            String tipoMatch,
            int scoreConfianca,
            OffsetDateTime dataConciliacao
    ) {}

    public static LancamentoExtratoResponse from(LancamentoExtrato l) {
        return from(l, null, null, 0L, null);
    }

    public static LancamentoExtratoResponse from(LancamentoExtrato l, Conciliacao c,
                                                  String tomadorNome, long valorBruto,
                                                  String competencia) {
        ConciliacaoResumo resumo = null;
        if (c != null) {
            resumo = new ConciliacaoResumo(
                    c.getId(),
                    c.getNotaId(),
                    tomadorNome,
                    valorBruto,
                    competencia,
                    c.getTipoMatch() != null ? c.getTipoMatch().name() : null,
                    c.getScoreConfianca(),
                    c.getDataConciliacao()
            );
        }
        return new LancamentoExtratoResponse(
                l.getId(),
                l.getExtratoId(),
                l.getDataLancamento(),
                l.getDescricao(),
                l.getValor(),
                l.getTipo(),
                l.getIdentificadorExterno(),
                l.getStatusConciliacao(),
                l.getScoreMatch(),
                resumo
        );
    }
}
