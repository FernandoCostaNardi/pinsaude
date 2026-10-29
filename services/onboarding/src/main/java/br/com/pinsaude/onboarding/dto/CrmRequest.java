package br.com.pinsaude.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrmRequest(
    @NotBlank @Size(max = 20) String numero,
    @NotBlank @Size(min = 2, max = 2) String uf,
    boolean principal
) {}
