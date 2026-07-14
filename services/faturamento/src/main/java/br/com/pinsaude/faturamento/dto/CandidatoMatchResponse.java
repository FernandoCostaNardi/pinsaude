package br.com.pinsaude.faturamento.dto;

import java.util.UUID;

public record CandidatoMatchResponse(
        UUID producaoId,
        String tomadorNome,
        long valorBruto,
        String competencia,
        int score
) {}
