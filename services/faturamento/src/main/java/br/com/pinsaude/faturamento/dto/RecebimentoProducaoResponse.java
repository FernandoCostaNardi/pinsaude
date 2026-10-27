package br.com.pinsaude.faturamento.dto;

import java.util.UUID;

/** Uma produção recebida do tomador: conciliada com um crédito do extrato bancário. */
public record RecebimentoProducaoResponse(
        UUID producaoId,
        String recebidoEm,   // data do lançamento no extrato (YYYY-MM-DD)
        long valorRecebido,  // centavos
        String tipoMatch     // AUTOMATICO | MANUAL
) {}
