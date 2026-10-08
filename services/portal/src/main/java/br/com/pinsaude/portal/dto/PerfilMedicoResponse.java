package br.com.pinsaude.portal.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PerfilMedicoResponse(
    UUID id,
    String nome,
    String email,
    String crm,
    String crmUf,
    String especialidade,
    String status,
    // Percentual da Taxa Pin acordado no cadastro do médico (ex: 0.1200 = 12%)
    BigDecimal taxaPinPct
) {}
