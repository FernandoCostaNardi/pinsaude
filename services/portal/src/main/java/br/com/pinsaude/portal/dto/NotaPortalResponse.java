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
    long valorIss,
    long valorIr,
    long valorCsll,
    long valorPis,
    long valorCofins,
    String status,
    String numeroNota,
    boolean temXml,
    boolean temPdf,
    String protocolo,
    OffsetDateTime emitidaAt,
    OffsetDateTime createdAt
) {}
