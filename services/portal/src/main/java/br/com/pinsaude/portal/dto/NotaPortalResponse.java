package br.com.pinsaude.portal.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotaPortalResponse(
    UUID id,
    UUID producaoId,
    String competencia,
    String tomadorNome,
    long valorBrutoCentavos,
    long valorLiquidoMedicoCentavos,
    long taxaPinCentavos,
    String status,
    String numeroNota,
    OffsetDateTime emitidaAt,
    OffsetDateTime createdAt
) {}
