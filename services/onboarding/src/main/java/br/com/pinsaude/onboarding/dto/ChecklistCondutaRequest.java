package br.com.pinsaude.onboarding.dto;

public record ChecklistCondutaRequest(
    boolean numeroConselhoVerificado,
    boolean registrosDisciplinares,
    boolean processosMedicos,
    String verificadoPor
) {}
