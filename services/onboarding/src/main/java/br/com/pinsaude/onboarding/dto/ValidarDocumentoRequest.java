package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.StatusValidacaoDocumento;
import jakarta.validation.constraints.NotNull;

public record ValidarDocumentoRequest(
    @NotNull StatusValidacaoDocumento statusValidacao,
    String motivoReprovacao
) {}
