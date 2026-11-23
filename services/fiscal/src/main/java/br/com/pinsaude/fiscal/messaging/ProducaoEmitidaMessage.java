package br.com.pinsaude.fiscal.messaging;

import java.util.UUID;

/**
 * Evento para o Faturamento: a NFS-e da produção foi emitida.
 * CBS-31: leva o líquido esperado do depósito (bruto − tributos retidos pelo tomador, em centavos)
 * e a data de emissão (AAAA-MM-DD) para a conciliação bancária.
 */
public record ProducaoEmitidaMessage(UUID producaoId, String cnpjTenant,
                                     Long valorLiquidoEsperado, String emitidaEm) {}
