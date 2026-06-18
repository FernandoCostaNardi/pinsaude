package br.com.pinsaude.faturamento.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceitaFederalResponse(
    String cnpj,
    @JsonAlias("razao_social")   String razaoSocial,
    @JsonAlias("nome_fantasia")  String nomeFantasia,
    String logradouro,
    String numero,
    String bairro,
    String municipio,
    String uf,
    String cep,
    String email,
    String telefone
) {}
