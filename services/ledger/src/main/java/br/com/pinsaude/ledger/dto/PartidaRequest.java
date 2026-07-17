package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.TipoPartida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PartidaRequest(
    @NotBlank String contaCodigo,
    @NotNull TipoPartida tipo,
    @Positive long valorCentavos
) {}
