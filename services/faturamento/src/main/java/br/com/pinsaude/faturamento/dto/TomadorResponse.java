package br.com.pinsaude.faturamento.dto;

import java.util.UUID;

public record TomadorResponse(
    UUID id,
    String tipo,
    String cnpjCpf,
    String razaoSocialNome,
    String nomeFantasia,
    String municipio,
    String inscricaoMunicipal,
    boolean indicadorRetencaoFederal,
    boolean indicadorRetencaoIss,
    String email,
    String telefone,
    String logradouro,
    String bairro,
    String cep,
    String uf,
    String pais
) {}
