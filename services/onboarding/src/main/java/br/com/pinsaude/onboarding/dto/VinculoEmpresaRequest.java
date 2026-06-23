package br.com.pinsaude.onboarding.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VinculoEmpresaRequest(@NotNull UUID empresaId) {}
