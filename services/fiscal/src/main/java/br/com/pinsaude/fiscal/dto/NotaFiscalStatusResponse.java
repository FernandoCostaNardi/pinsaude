package br.com.pinsaude.fiscal.dto;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotaFiscalStatusResponse(
    UUID notaId,
    UUID producaoId,
    UUID medicoId,
    UUID tomadorId,
    String tomadorNome,
    String competencia,
    StatusNota status,
    String numeroNota,
    String protocolo,
    String observacoes,
    Long valorBruto,
    Long taxaPin,
    Long valorLiquidoMedico,
    OffsetDateTime emitidaAt,
    OffsetDateTime createdAt
) {
    public static NotaFiscalStatusResponse from(NotaFiscal n) {
        return new NotaFiscalStatusResponse(
            n.getId(), n.getProducaoId(), n.getMedicoId(), n.getTomadorId(),
            n.getTomadorNome(), n.getCompetencia(),
            n.getStatus(), n.getNumeroNota(), n.getProtocoloEmissao(),
            n.getObservacoes(), n.getValorBruto(), n.getTaxaPin(),
            n.getValorLiquidoMedico(), n.getEmitidaAt(), n.getCreatedAt()
        );
    }
}
