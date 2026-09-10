package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record FechamentoMedicoStatusRequest(
    @NotNull UUID tomadorId,
    @NotNull UUID medicoId,
    @NotNull @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Competência deve estar no formato AAAA-MM") String competencia,
    @NotNull @Pattern(regexp = "OK|SEM_FATURAR|NAO_TEVE", message = "Status inválido") String status
) {}
