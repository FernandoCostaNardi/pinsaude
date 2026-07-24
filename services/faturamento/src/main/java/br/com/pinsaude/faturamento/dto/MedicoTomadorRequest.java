package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MedicoTomadorRequest(
    @NotNull UUID medicoId
) {}
