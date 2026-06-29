package br.com.pinsaude.portal.dto;

import java.util.List;

public record ExtratoResponse(
    long saldoPeriodo,
    long totalCreditos,
    long totalDebitos,
    long totalRetencoes,
    long totalTaxaPin,
    List<ExtratoLancamentoResponse> lancamentos
) {}
