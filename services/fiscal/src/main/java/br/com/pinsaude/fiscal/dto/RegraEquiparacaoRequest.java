package br.com.pinsaude.fiscal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegraEquiparacaoRequest(

    @NotBlank String cnae,

    @NotBlank String codigoLc116,

    boolean indicadorEquiparacao,

    /** "REDUZIDA" ou "CHEIA" — obrigatório quando indicadorEquiparacao=true. */
    @Pattern(regexp = "^(REDUZIDA|CHEIA)$", message = "regimePresuncao deve ser REDUZIDA ou CHEIA")
    String regimePresuncao
) {}
