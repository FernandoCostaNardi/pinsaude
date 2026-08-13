package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

// Edição pós-criação de uma Frequência Médica: só Competência e Setor Operacional são
// editáveis (Tomador, Tipo de Escala, Modalidade e Ocorrência permanecem fixos — se algum
// desses estiver errado, o caminho continua sendo excluir e criar de novo).
public record FrequenciaMedicaEditRequest(
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String competencia,       // YYYY-MM
    @NotNull UUID servicoOperacionalId
) {}
