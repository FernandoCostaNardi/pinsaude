package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record FrequenciaItemRequest(
    @NotNull UUID modalidadeId,
    @NotNull LocalDate dataExecucao,
    @Size(max = 120) String ocorrencia
) {}
