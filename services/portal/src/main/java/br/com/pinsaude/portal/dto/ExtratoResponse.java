package br.com.pinsaude.portal.dto;

import java.util.List;

/**
 * Extrato do médico: tudo que ele lançou (produção e frequência), com o valor previsto a
 * receber e o status de cada lançamento. Totais em centavos, sobre os lançamentos retornados.
 */
public record ExtratoResponse(
    String competencia,                  // filtro aplicado (null = todas)
    long totalPrevisto,
    long totalProvisionado,
    long totalFaturado,
    long totalPago,
    List<String> competenciasDisponiveis, // competências com algum lançamento, mais recente primeiro
    List<ExtratoLancamentoResponse> lancamentos
) {}
