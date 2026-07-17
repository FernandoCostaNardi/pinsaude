package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.util.UUID;

public record TomadorServicoOperacionalResponse(
    UUID id,
    UUID tomadorId,
    UUID grupoId,
    String nome,
    boolean ativo
) {
    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s) {
        return new TomadorServicoOperacionalResponse(
            s.getId(),
            s.getTomadorId(),
            s.getGrupoId(),
            s.getNome(),
            s.isAtivo()
        );
    }
}
