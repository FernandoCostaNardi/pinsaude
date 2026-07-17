package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.ContaLedger;
import br.com.pinsaude.ledger.domain.TipoConta;

import java.util.UUID;

public record ContaResponse(
    UUID id,
    String codigo,
    String nome,
    TipoConta tipo
) {
    public static ContaResponse from(ContaLedger c) {
        return new ContaResponse(c.getId(), c.getCodigo(), c.getNome(), c.getTipo());
    }
}
