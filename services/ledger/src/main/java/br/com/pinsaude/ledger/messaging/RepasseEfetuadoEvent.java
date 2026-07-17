package br.com.pinsaude.ledger.messaging;

import java.util.UUID;

/**
 * Evento publicado quando um repasse ao médico é efetuado (origem: serviço repasse).
 * Gera: débito em "Repasses a Médicos a Pagar" (baixa da obrigação) + crédito em
 * "Caixa e Bancos" (saída de caixa). Valores em centavos.
 */
public record RepasseEfetuadoEvent(
    UUID repasseId,
    UUID medicoId,
    String cnpjTenant,
    String competencia,
    long valorCentavos
) {}
