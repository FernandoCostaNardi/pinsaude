package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

// modalidadeId/ocorrenciaId: escolhidos uma única vez na criação da frequência SÓ para
// Diarista (obrigatório nesse caso — validado em FrequenciaService.criar, não aqui, porque a
// obrigatoriedade depende do valor de tipoMedico). Para Plantonista devem vir nulos — a
// modalidade (e a ocorrência) voltam a ser escolhidas a cada plantão lançado, podendo ter
// turnos/modalidades diferentes dentro da mesma frequência (ajuste pós-implantação, ver
// CLAUDE.md — reverte parte do comportamento fixo introduzido em PINSAUDE-13.26).
public record FrequenciaMedicaRequest(
    @NotNull UUID tomadorId,
    @NotNull UUID medicoId,
    @NotNull UUID servicoOperacionalId,
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String competencia,       // YYYY-MM
    @NotBlank @Pattern(regexp = "PLANTONISTA|DIARISTA") String tipoMedico, // tipo de escala
    UUID modalidadeId,
    UUID ocorrenciaId
) {}
