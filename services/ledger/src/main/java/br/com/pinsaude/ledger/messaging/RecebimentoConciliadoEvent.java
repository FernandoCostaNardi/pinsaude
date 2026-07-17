package br.com.pinsaude.ledger.messaging;

import java.util.UUID;

/**
 * Evento publicado quando um recebimento bancário é conciliado a uma produção
 * (origem: serviço faturamento). Gera: débito em "Caixa e Bancos" + crédito em
 * "Honorários a Receber" (baixa do valor a receber). Valores em centavos.
 */
public record RecebimentoConciliadoEvent(
    UUID conciliacaoId,
    UUID producaoId,
    UUID medicoId,
    String cnpjTenant,
    String competencia,
    long valorRecebidoCentavos
) {}
