package br.com.pinsaude.ledger.messaging;

import java.util.UUID;

/**
 * Evento publicado quando uma NFS-e é emitida (origem: serviço fiscal).
 * Gera o lançamento de reconhecimento: débito em "Honorários a Receber",
 * crédito no repasse ao médico (85%), crédito na receita da Pin e crédito de
 * cada retenção de imposto. Valores em centavos.
 */
public record NotaEmitidaEvent(
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
    long valorCofinsCentavos
) {}
