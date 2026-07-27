package br.com.pinsaude.portal.dto;

import java.util.UUID;

public record TomadorPortalResponse(
    UUID id,
    String razaoSocial,
    String municipio
) {}
