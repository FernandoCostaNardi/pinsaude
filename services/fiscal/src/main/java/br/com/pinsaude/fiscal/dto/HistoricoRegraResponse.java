package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.HistoricoRegraEquiparacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoricoRegraResponse(
    UUID id,
    String campoAlterado,
    String valorAnterior,
    String valorNovo,
    String alteradoPor,
    OffsetDateTime alteradoEm
) {
    public static HistoricoRegraResponse from(HistoricoRegraEquiparacao h) {
        return new HistoricoRegraResponse(
            h.getId(), h.getCampoAlterado(),
            h.getValorAnterior(), h.getValorNovo(),
            h.getAlteradoPor(), h.getAlteradoEm()
        );
    }
}
