package br.com.pinsaude.faturamento.dto;

// Todos os valores em centavos (long).
// taxaPin = 15% do valorBruto (comissão da plataforma Pin Saúde).
// Retenções apenas quando o tomador tem indicadorRetencao* = true.
public record PreviewCalculoResponse(
    long valorBruto,
    long taxaPin,
    long issRetido,
    long irRetido,
    long csllRetido,
    long pisRetido,
    long cofinsRetido,
    long totalRetencoes,
    long valorLiquidoMedico
) {}
