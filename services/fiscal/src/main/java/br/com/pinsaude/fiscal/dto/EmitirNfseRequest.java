package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record EmitirNfseRequest(
    @NotNull UUID producaoId,
    UUID medicoId,             // null para produções multi-médico
    @NotNull UUID tomadorId,
    @NotNull @Pattern(regexp = "\\d{4}-\\d{2}") String competencia,
    @NotNull @Positive Long valorBruto,
    @NotNull @Positive Long taxaPin,
    Long valorIss,
    Long valorIr,
    Long valorCsll,
    Long valorPis,
    Long valorCofins,
    String tomadorNome
) {}
