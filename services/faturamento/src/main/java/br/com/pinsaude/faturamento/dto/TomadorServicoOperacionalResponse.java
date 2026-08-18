package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.util.UUID;

public record TomadorServicoOperacionalResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    String categoria,
    boolean ativo
) {
    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s) {
        return new TomadorServicoOperacionalResponse(
            s.getId(),
            s.getTomadorId(),
            s.getNome(),
            s.getCategoria(),
            s.isAtivo()
        );
    }
}
