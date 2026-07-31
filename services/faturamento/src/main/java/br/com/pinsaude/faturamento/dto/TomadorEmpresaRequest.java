package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TomadorEmpresaRequest(
    @NotNull UUID empresaId
) {}
