package br.com.pinsaude.faturamento.dto;

// Todos os valores em centavos (long).
// valorLiquidoMedico = valorBruto - taxaPin (sempre 85% — médico não absorve impostos).
// Os impostos saem da taxaPin (15% da Pin Saúde).
// resultadoPin = taxaPin - totalRetencoes (lucro líquido da Pin após tributos).
public record PreviewCalculoResponse(
    long valorBruto,
    long taxaPin,
    long issRetido,
    long irRetido,
    long csllRetido,
    long pisRetido,
    long cofinsRetido,
    long totalRetencoes,
    long valorLiquidoMedico,
    long resultadoPin
) {}
