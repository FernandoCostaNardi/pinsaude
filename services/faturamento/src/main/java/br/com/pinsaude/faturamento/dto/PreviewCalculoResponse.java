package br.com.pinsaude.faturamento.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Prévia dos tributos de uma produção, calculada pelo motor fiscal (CBS-30) — a mesma conta da
 * emissão da nota. Valores em centavos (long); alíquotas em FRAÇÃO (0.0352 = 3,52%).
 *
 * valorLiquidoMedico = valorBruto − taxaPin (o médico nunca absorve tributo).
 * resultadoPin = taxaPin − tributos que saem da cota da Pin (todos, retidos ou pagos por guia;
 * CBS/IBS só quando estão dentro do preço).
 */
public record PreviewCalculoResponse(
    long valorBruto,
    long taxaPin,

    // Só o que o tomador retém na fonte (0 quando o tributo é pago pela Pin).
    long issRetido,
    long irRetido,
    long csllRetido,
    long pisRetido,
    long cofinsRetido,
    long totalRetencoes,

    long valorLiquidoMedico,
    long resultadoPin,

    // ─── CBS-30: o que o motor fiscal devolve ───
    String competencia,
    /** LEGADO ou IBS_CBS. */
    String regime,
    /** Cenário fiscal: A, B, C ou D. */
    String cenario,

    // Valor apurado de cada tributo (retido ou não).
    long valorIss,
    long valorIr,
    long valorCsll,
    long valorPis,
    long valorCofins,
    long valorCbs,
    long valorIbs,
    long totalTributos,

    BigDecimal aliqCbs,
    BigDecimal aliqIbs,
    BigDecimal fatorIss,
    String cstIbsCbs,
    String cClassTrib,
    boolean cbsIbsPorFora,
    long baseCalculoIbsCbs,
    /** Bruto + CBS + IBS quando por fora; senão igual ao bruto. */
    long valorTotalNota,
    /** Tributos retidos pelo tomador (ISS, IR, CSLL, PIS, COFINS), só os com valor. */
    List<String> tributosRetidos
) {}
