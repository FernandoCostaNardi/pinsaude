package br.com.pinsaude.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saldo do médico em reais. saldo = SUM(créditos) - SUM(débitos) na conta de repasse do médico
 * (quanto a Pin ainda deve ao médico).
 */
public record SaldoResponse(
    UUID medicoId,
    BigDecimal saldo
) {
    public static SaldoResponse of(UUID medicoId, long saldoCentavos) {
        return new SaldoResponse(medicoId, Money.reais(saldoCentavos));
    }
}
