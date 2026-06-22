package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.NotBlank;

public record MotivoRequest(@NotBlank String motivo) {}
