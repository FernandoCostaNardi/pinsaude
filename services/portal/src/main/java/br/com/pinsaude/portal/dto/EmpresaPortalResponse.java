package br.com.pinsaude.portal.dto;

import java.util.UUID;

public record EmpresaPortalResponse(
    UUID id,
    String razaoSocial,
    String cnpj,
    String municipio,
    String inscricaoMunicipal
) {}
