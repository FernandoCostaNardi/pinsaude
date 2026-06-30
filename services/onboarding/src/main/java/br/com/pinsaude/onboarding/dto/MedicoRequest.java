package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.service.ValidCpf;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record MedicoRequest(
    @NotBlank @ValidCpf String cpf,
    @NotBlank @Size(max = 200) String nome,
    @NotBlank @Size(max = 20) String crm,
    @NotBlank @Size(min = 2, max = 2) String crmUf,
    @Size(max = 100) String especialidade,
    @Size(max = 200) String email,
    @Size(max = 20) String telefone,
    UUID empresaId,
    @DecimalMin(value = "0.0100", message = "Taxa Pin mínima é 1%")
    @DecimalMax(value = "0.5000", message = "Taxa Pin máxima é 50%")
    BigDecimal taxaPinPct
) {}
