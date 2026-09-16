package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AtualizarServicoProducaoRequest(
    @NotNull(message = "servicoId é obrigatório") UUID servicoId
) {}
