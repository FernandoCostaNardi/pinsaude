package br.com.pinsaude.portal.dto;

import java.util.UUID;

public record SetorOperacionalPortalResponse(
    UUID id,
    String nome,
    String categoria
) {}
