package br.com.pinsaude.portal.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProducaoPortalResponse(
    UUID id,
    String competencia,
    String tomadorNome,
    String servicoDescricao,
    long valorBrutoCentavos,
    long valorLiquidoEstimadoCentavos,
    String status,
    OffsetDateTime createdAt
) {}
