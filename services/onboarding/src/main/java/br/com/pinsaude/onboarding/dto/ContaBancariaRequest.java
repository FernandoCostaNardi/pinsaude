package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.TipoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContaBancariaRequest(
    @NotBlank String banco,
    @NotBlank String agencia,
    @NotBlank String conta,
    @NotNull TipoConta tipoConta,
    String chavePix,
    boolean principal
) {}
