package br.com.pinsaude.faturamento.conciliacao.messaging;

import java.util.UUID;

public record ProducaoEmitidaMessage(UUID producaoId, String cnpjTenant) {}
