package br.com.pinsaude.ledger.dto;

import br.com.pinsaude.ledger.domain.AjusteManual;
import br.com.pinsaude.ledger.domain.StatusAjuste;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AjusteResponse(
    UUID id,
    UUID medicoId,
    String competencia,
    String contaDebitoCodigo,
    String contaCreditoCodigo,
    BigDecimal valor,
    String motivo,
    String solicitanteId,
    String solicitantePerfil,
    String aprovadorId,
    String aprovadorPerfil,
    StatusAjuste status,
    UUID lancamentoId,
    String motivoRejeicao,
    OffsetDateTime createdAt,
    OffsetDateTime decidedAt
) {
    public static AjusteResponse from(AjusteManual a) {
        return new AjusteResponse(
            a.getId(), a.getMedicoId(), a.getCompetencia(),
            a.getContaDebitoCodigo(), a.getContaCreditoCodigo(),
            Money.reais(a.getValorCentavos()), a.getMotivo(),
            a.getSolicitanteId(), a.getSolicitantePerfil(),
            a.getAprovadorId(), a.getAprovadorPerfil(),
            a.getStatus(), a.getLancamentoId(), a.getMotivoRejeicao(),
            a.getCreatedAt(), a.getDecidedAt()
        );
    }
}
