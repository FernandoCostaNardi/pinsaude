package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TomadorServicoOperacionalRequest(
    @NotNull UUID grupoId,
    @NotBlank @Size(max = 150) String nome,
    boolean ativo
) {}
