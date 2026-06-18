package br.com.pinsaude.faturamento.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceitaFederalResponse(
    String cnpj,
    @JsonProperty("razao_social")   String razaoSocial,
    @JsonProperty("nome_fantasia")  String nomeFantasia,
    String logradouro,
    String numero,
    String bairro,
    String municipio,
    String uf,
    String cep,
    String email,
    String telefone
) {}
