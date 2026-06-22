package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;

import java.util.UUID;

public record EmitirNfseResponse(
    UUID notaId,
    UUID producaoId,
    StatusNota status,
    String numeroNota,
    String competencia
) {
    public static EmitirNfseResponse from(NotaFiscal n) {
        return new EmitirNfseResponse(n.getId(), n.getProducaoId(), n.getStatus(),
                                      n.getNumeroNota(), n.getCompetencia());
    }
}
