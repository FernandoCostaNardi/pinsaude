package br.com.pinsaude.faturamento.dto;

import java.util.List;
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
    String pais,
    List<TomadorAliquotaResponse> aliquotas,
    List<TomadorCnaeResponse> cnaes,
    List<TomadorServicoResponse> servicos,
    boolean temGrupoFaturamento,
    List<TomadorEmpresaResponse> empresas
) {}
