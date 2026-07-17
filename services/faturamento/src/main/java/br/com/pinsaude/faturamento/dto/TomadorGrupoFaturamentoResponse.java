package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.util.List;
import java.util.UUID;

public record TomadorGrupoFaturamentoResponse(
    UUID id,
    UUID tomadorId,
    UUID servicoLc116Id,
    String codigoLc116,
    String descricaoServico,
    String nome,
    String descricaoNota,
    int ordem,
    boolean ativo,
    List<TomadorServicoOperacionalResponse> servicosOperacionais
) {
    public static TomadorGrupoFaturamentoResponse from(
            TomadorGrupoFaturamento g,
            Servico servico,
            List<TomadorServicoOperacional> setores) {
        List<TomadorServicoOperacionalResponse> setoresResp = setores.stream()
            .map(TomadorServicoOperacionalResponse::from)
            .toList();
        return new TomadorGrupoFaturamentoResponse(
            g.getId(),
            g.getTomadorId(),
            g.getServicoLc116Id(),
            servico != null ? servico.getCodigoLc116() : null,
            servico != null ? servico.getDescricaoPadrao() : null,
            g.getNome(),
            g.getDescricaoNota(),
            g.getOrdem(),
            g.isAtivo(),
            setoresResp
        );
    }

    public static TomadorGrupoFaturamentoResponse from(TomadorGrupoFaturamento g, Servico servico) {
        return from(g, servico, List.of());
    }
}
