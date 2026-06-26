package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ParticipacaoRequest(
    @NotNull(message = "medicoId é obrigatório")
    UUID medicoId,

    @NotNull(message = "valorBruto é obrigatório")
    @Min(value = 1, message = "Valor bruto por participante deve ser maior que zero")
    Long valorBruto
) {}
