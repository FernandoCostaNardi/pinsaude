package br.com.pinsaude.faturamento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record ProducaoRequest(
    @NotNull(message = "tomadorId é obrigatório")
    UUID tomadorId,

    @NotNull(message = "servicoId é obrigatório")
    UUID servicoId,

    @NotBlank(message = "competencia é obrigatória")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "competencia deve estar no formato YYYY-MM")
    String competencia,

    String descricaoComplementar,

    UUID empresaId,

    String cnaeCodigo,

    @NotEmpty(message = "Ao menos um participante é obrigatório")
    List<@Valid ParticipacaoRequest> participantes
) {}
