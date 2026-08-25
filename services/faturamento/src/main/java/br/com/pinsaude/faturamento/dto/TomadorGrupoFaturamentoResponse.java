package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
            List<TomadorServicoOperacional> setores,
            Map<UUID, List<TomadorModalidade>> modalidadesPorSetor) {
        List<TomadorServicoOperacionalResponse> setoresResp = setores.stream()
            .map(s -> TomadorServicoOperacionalResponse.from(s, modalidadesPorSetor.getOrDefault(s.getId(), List.of())))
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

    // Collections.emptyMap() — nunca Map.of() aqui: mesma armadilha já documentada (ver
    // CLAUDE.md) — evita NPE em .getOrDefault caso algum chamador futuro passe uma chave que o
    // Map.of() não tolere.
    public static TomadorGrupoFaturamentoResponse from(
            TomadorGrupoFaturamento g, Servico servico, List<TomadorServicoOperacional> setores) {
        return from(g, servico, setores, Collections.emptyMap());
    }

    public static TomadorGrupoFaturamentoResponse from(TomadorGrupoFaturamento g, Servico servico) {
        return from(g, servico, List.of(), Collections.emptyMap());
    }
}
