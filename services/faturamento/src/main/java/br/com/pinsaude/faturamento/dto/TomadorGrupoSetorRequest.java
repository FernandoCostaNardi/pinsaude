package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Vincula um Serviço Operacional (setor) já existente no catálogo do tomador a um Grupo de
// Faturamento — permite reutilizar o mesmo setor em quantos grupos forem necessários.
public record TomadorGrupoSetorRequest(
    @NotNull UUID setorId
) {}
