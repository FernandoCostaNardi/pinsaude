package br.com.pinsaude.portal.dto;

import java.util.List;

public record DashboardResponse(
    long saldoDisponivelCentavos,
    long valorAReceberCentavos,
    long totalProduzidoCentavos,
    long totalNotasEmitidas,
    long totalProducoes,
    List<NotaPortalResponse> ultimasNotas,
    List<Object> ultimosRepasses
) {}
