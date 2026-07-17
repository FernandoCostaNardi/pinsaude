package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.TipoOrigem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Item do extrato do médico, com saldo running após o lançamento.
 * valor = efeito líquido do lançamento na conta de repasse (positivo = crédito ao médico).
 */
public record ExtratoItemResponse(
    UUID lancamentoId,
    LocalDate dataLancamento,
    String competencia,
    TipoOrigem tipoOrigem,
    UUID origemId,
    String descricao,
    BigDecimal valor,
    BigDecimal saldoApos
) {
    public static ExtratoItemResponse of(ExtratoLinha linha, long saldoAposCentavos) {
        return new ExtratoItemResponse(
            linha.lancamentoId(), linha.dataLancamento(), linha.competencia(),
            linha.tipoOrigem(), linha.origemId(), linha.descricao(),
            Money.reais(linha.netCentavos()), Money.reais(saldoAposCentavos)
        );
    }
}
