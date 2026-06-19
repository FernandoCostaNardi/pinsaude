package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ProducaoRequest(
    @NotNull(message = "medicoId é obrigatório")
    UUID medicoId,

    @NotNull(message = "tomadorId é obrigatório")
    UUID tomadorId,

    @NotNull(message = "servicoId é obrigatório")
    UUID servicoId,

    // valor em centavos: R$ 1.500,00 → 150000
    @NotNull(message = "valorBruto é obrigatório")
    @Min(value = 1, message = "Valor bruto deve ser maior que zero")
    Long valorBruto,

    // formato YYYY-MM
    @NotBlank(message = "competencia é obrigatória")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "competencia deve estar no formato YYYY-MM")
    String competencia,

    String descricaoComplementar
) {}
