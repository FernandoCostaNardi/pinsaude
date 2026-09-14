package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

// Drag-and-drop na aba Modalidades do modal de Faturamento por Grupo: o frontend manda a
// lista COMPLETA de ids na nova ordem (posição no array = nova ordem), nunca um delta —
// TomadorService.reordenarModalidades valida que o conjunto bate exatamente com as modalidades
// já cadastradas para o tomador antes de gravar.
public record TomadorModalidadesReordenarRequest(
    @NotEmpty List<UUID> modalidadeIds
) {}
