package br.com.pinsaude.faturamento.dto;

import java.util.UUID;

public record CandidatoMatchResponse(
        UUID producaoId,
        String tomadorNome,
        long valorBruto,
        long valorLiquidoEsperado, // CBS-31: bruto − retenções do tomador (o depósito esperado)
        String competencia,
        int score
) {}
