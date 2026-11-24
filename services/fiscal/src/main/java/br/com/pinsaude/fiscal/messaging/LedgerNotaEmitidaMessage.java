package br.com.pinsaude.fiscal.messaging;

import java.util.UUID;

/**
 * Evento da NFS-e emitida para o ledger (fila ledger.nota.emitida, CBS-40). Mesmo contrato do
 * {@code NotaEmitidaEvent} do serviço ledger. Valores em centavos; regime e CBS/IBS vêm do
 * snapshot gravado na nota (CBS-11).
 */
public record LedgerNotaEmitidaMessage(
    UUID notaId,
    UUID medicoId,
    String cnpjTenant,
    String competencia,
    long valorBrutoCentavos,
    long valorLiquidoMedicoCentavos,
    long valorIssCentavos,
    long valorIrCentavos,
    long valorCsllCentavos,
    long valorPisCentavos,
    long valorCofinsCentavos,
    String regime,
    long valorCbsCentavos,
    long valorIbsCentavos,
    Boolean cbsIbsPorFora
) {}
