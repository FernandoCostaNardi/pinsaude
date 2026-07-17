package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.PartidaLedger;
import br.com.pinsaude.ledger.domain.TipoPartida;

import java.math.BigDecimal;
import java.util.UUID;

public record PartidaResponse(
    UUID id,
    String contaCodigo,
    String contaNome,
    TipoPartida tipo,
    BigDecimal valor
) {
    public static PartidaResponse from(PartidaLedger p) {
        return new PartidaResponse(
            p.getId(),
            p.getConta().getCodigo(),
            p.getConta().getNome(),
            p.getTipo(),
            Money.reais(p.getValorCentavos())
        );
    }
}
