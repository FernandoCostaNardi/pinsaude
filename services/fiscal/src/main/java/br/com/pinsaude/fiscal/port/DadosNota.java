package br.com.pinsaude.fiscal.port;

import java.util.UUID;

public record DadosNota(
    UUID producaoId,
    UUID medicoId,
    UUID tomadorId,
    String cnpjIdTenant,
    String competencia,
    long valorBruto,
    long taxaPin,
    long valorIss,
    long valorIr,
    long valorCsll,
    long valorPis,
    long valorCofins,
    long valorLiquidoMedico,
    String discriminacao        // Descrição/discriminação (grupo de faturamento), nullable
) {}
