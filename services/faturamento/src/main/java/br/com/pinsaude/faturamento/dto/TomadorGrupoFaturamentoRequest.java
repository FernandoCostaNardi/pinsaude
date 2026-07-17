package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TomadorGrupoFaturamentoRequest(
    @NotNull UUID servicoLc116Id,
    @NotBlank @Size(max = 120) String nome,
    @NotBlank String descricaoNota,
    int ordem,
    boolean ativo
) {}
