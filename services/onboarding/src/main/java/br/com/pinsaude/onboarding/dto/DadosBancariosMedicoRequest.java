package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.TipoPix;
import jakarta.validation.constraints.NotNull;

public record DadosBancariosMedicoRequest(
    TipoPix tipoPix,
    String chavePix,
    String cpfsAdicionaisSplit,
    @NotNull Boolean confirmarAlteracao
) {}
