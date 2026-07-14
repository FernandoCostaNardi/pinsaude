package br.com.pinsaude.faturamento.conciliacao.messaging;

import java.util.UUID;

public record MatchingMessage(UUID extratoId, String cnpjTenant) {}
