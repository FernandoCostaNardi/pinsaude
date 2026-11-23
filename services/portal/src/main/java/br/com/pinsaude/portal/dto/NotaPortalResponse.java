package br.com.pinsaude.portal.dto;

import java.math.BigDecimal;
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
    // CBS-22 — snapshot do regime gravado na nota (CBS-11). Nota antiga = LEGADO, CBS/IBS zerados.
    String regimeTributario,   // "LEGADO" | "IBS_CBS"
    long valorCbs,
    long valorIbs,
    BigDecimal fatorIss,       // fração do ISS no ano (null em nota antiga); 0 = ISS extinto
    String status,
    String numeroNota,
    boolean temXml,
    boolean temPdf,
    String protocolo,
    OffsetDateTime emitidaAt,
    OffsetDateTime createdAt
) {}
