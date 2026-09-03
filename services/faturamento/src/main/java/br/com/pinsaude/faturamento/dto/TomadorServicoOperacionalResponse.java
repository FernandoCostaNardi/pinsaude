package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.util.List;
import java.util.UUID;

public record TomadorServicoOperacionalResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    String categoria,
    boolean ativo,
    // Modalidades de referência do setor (pedido do cliente: pode ter mais de uma, e uma mesma
    // modalidade pode aparecer 2x com tipo diferente quando ela suporta mais de um Tipo de
    // Escala — ver TomadorModalidade.tipos) — o frontend usa a lista pra derivar o Tipo de Escala
    // da Frequência automaticamente quando só há 1, ou pra oferecer a escolha (Tipo de Escala
    // e/ou Modalidade específica) quando há mais de 1.
    List<ModalidadeResumo> modalidades
) {
    public record ModalidadeResumo(UUID id, String nome, String tipo) {
        public static ModalidadeResumo from(ModalidadeVinculoResolvido v) {
            return new ModalidadeResumo(v.modalidade().getId(), v.modalidade().getNome(), v.tipo());
        }
    }

    // modalidades pode vir vazia pra setores legados sem nenhuma modalidade resolvida (nunca
    // editados desde a criação deste campo, ou todas as modalidades vinculadas removidas do
    // catálogo) — campos derivados no frontend ficam null/bloqueados nesse caso.
    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s, List<ModalidadeVinculoResolvido> vinculos) {
        return new TomadorServicoOperacionalResponse(
            s.getId(),
            s.getTomadorId(),
            s.getNome(),
            s.getCategoria(),
            s.isAtivo(),
            vinculos.stream().map(ModalidadeResumo::from).toList()
        );
    }

    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s) {
        return from(s, List.of());
    }
}
