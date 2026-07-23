package br.com.pinsaude.onboarding.dto;

import java.util.UUID;

public record FinalizarCandidaturaResponse(UUID id, String status, String mensagem) {}
