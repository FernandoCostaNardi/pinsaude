package br.com.pinsaude.fiscal.messaging;

import java.util.UUID;

public record ProducaoEmitidaMessage(UUID producaoId, String cnpjTenant) {}
