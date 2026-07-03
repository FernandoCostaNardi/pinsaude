package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TomadorRequest(
    @NotBlank String tipo,
    @NotBlank String cnpjCpf,
    @NotBlank String razaoSocialNome,
    String nomeFantasia,
    String municipio,
    String inscricaoMunicipal,
    @NotNull Boolean indicadorRetencaoFederal,
    @NotNull Boolean indicadorRetencaoIss,
    String email,
    String telefone,
    String logradouro,
    String bairro,
    String cep,
    String uf,
    String pais
) {}
