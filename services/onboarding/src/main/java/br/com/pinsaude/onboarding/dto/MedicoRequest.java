package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.service.ValidCpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MedicoRequest(
    @NotBlank @ValidCpf String cpf,
    @NotBlank @Size(max = 200) String nome,
    @NotBlank @Size(max = 20) String crm,
    @NotBlank @Size(min = 2, max = 2) String crmUf,
    @Size(max = 100) String especialidade,
    @Size(max = 200) String email,
    @Size(max = 20) String telefone,
    @NotNull UUID empresaId
) {}
