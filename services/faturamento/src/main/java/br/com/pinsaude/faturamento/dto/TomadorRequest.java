package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TomadorRequest(
    @NotBlank String tipo,
    @NotBlank String cnpjCpf,
    @NotBlank String razaoSocialNome,
    String municipio,
    String inscricaoMunicipal,
    @NotNull Boolean indicadorRetencaoFederal,
    @NotNull Boolean indicadorRetencaoIss
) {}
