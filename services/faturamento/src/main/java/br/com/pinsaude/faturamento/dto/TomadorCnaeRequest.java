package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TomadorCnaeRequest(
    @NotBlank @Size(max = 20)
    String codigoCnae,

    @Size(max = 255)
    String descricao
) {}
