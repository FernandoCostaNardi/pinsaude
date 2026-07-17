package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.LancamentoLedger;
import br.com.pinsaude.ledger.domain.TipoOrigem;
import br.com.pinsaude.ledger.domain.TipoPartida;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LancamentoResponse(
    UUID id,
    String cnpjIdTenant,
    UUID medicoId,
    LocalDate dataLancamento,
    String competencia,
    TipoOrigem tipoOrigem,
    UUID origemId,
    String descricao,
    String correlationId,
    OffsetDateTime createdAt,
    BigDecimal valorTotal,
    List<PartidaResponse> partidas
) {
    /** Resumo para listagem (sem partidas) — valorTotal calculado em batch. */
    public static LancamentoResponse resumo(LancamentoLedger l, long valorTotalCentavos) {
        return new LancamentoResponse(
            l.getId(), l.getCnpjIdTenant(), l.getMedicoId(), l.getDataLancamento(),
            l.getCompetencia(), l.getTipoOrigem(), l.getOrigemId(), l.getDescricao(),
            l.getCorrelationId(), l.getCreatedAt(), Money.reais(valorTotalCentavos), null
        );
    }

    /** Detalhe com todas as partidas; valorTotal = soma dos créditos (= soma dos débitos). */
    public static LancamentoResponse detalhe(LancamentoLedger l) {
        long totalCreditos = l.getPartidas().stream()
            .filter(p -> p.getTipo() == TipoPartida.CREDITO)
            .mapToLong(p -> p.getValorCentavos())
            .sum();
        List<PartidaResponse> partidas = l.getPartidas().stream()
            .map(PartidaResponse::from)
            .toList();
        return new LancamentoResponse(
            l.getId(), l.getCnpjIdTenant(), l.getMedicoId(), l.getDataLancamento(),
            l.getCompetencia(), l.getTipoOrigem(), l.getOrigemId(), l.getDescricao(),
            l.getCorrelationId(), l.getCreatedAt(), Money.reais(totalCreditos), partidas
        );
    }
}
