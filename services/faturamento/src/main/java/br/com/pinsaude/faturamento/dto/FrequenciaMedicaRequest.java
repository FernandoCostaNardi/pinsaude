package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

// modalidadeId/ocorrenciaId (PINSAUDE-13.26): escolhidos uma única vez na criação da
// frequência — nenhum lançamento de plantão pergunta mais isso (ver FrequenciaService).
// modalidadeId é obrigatório; ocorrenciaId é opcional (mesma semântica de sempre).
public record FrequenciaMedicaRequest(
    @NotNull UUID tomadorId,
    @NotNull UUID medicoId,
    @NotNull UUID servicoOperacionalId,
    @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String competencia,       // YYYY-MM
    @NotBlank @Pattern(regexp = "PLANTONISTA|DIARISTA") String tipoMedico, // tipo de escala
    @NotNull UUID modalidadeId,
    UUID ocorrenciaId
) {}
