package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TomadorServico;

import java.util.UUID;

public record TomadorServicoResponse(
    UUID id,
    UUID servicoId,
    String codigoLc116,
    String descricaoPadrao
) {
    public static TomadorServicoResponse from(TomadorServico vinculo, Servico servico) {
        return new TomadorServicoResponse(
            vinculo.getId(),
            vinculo.getServicoId(),
            servico != null ? servico.getCodigoLc116() : null,
            servico != null ? servico.getDescricaoPadrao() : null
        );
    }
}
