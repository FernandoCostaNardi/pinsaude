package br.com.pinsaude.portal.dto;

import java.util.UUID;

public record PerfilMedicoResponse(
    UUID id,
    String nome,
    String email,
    String crm,
    String crmUf,
    String especialidade,
    String status
) {}
