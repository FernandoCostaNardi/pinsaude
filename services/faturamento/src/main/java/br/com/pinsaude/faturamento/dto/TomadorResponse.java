package br.com.pinsaude.faturamento.dto;

import java.util.UUID;

public record TomadorResponse(
    UUID id,
    String tipo,
    String cnpjCpf,
    String razaoSocialNome,
    String inscricaoMunicipal,
    boolean indicadorRetencaoFederal,
    boolean indicadorRetencaoIss
) {}
