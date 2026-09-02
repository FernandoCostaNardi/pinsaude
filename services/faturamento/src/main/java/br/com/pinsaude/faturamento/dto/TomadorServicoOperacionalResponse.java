package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.util.List;
import java.util.UUID;

public record TomadorServicoOperacionalResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    String categoria,
    boolean ativo,
    // Modalidades de referência do setor (pedido do cliente: pode ter mais de uma) — o frontend
    // usa a lista pra derivar o Tipo de Escala da Frequência automaticamente quando só há 1, ou
    // pra oferecer a escolha (Tipo de Escala e/ou Modalidade específica) quando há mais de 1.
    List<ModalidadeResumo> modalidades
) {
    public record ModalidadeResumo(UUID id, String nome, String tipo) {
        public static ModalidadeResumo from(TomadorModalidade m) {
            return new ModalidadeResumo(m.getId(), m.getNome(), m.getTipo());
        }
    }

    // modalidades pode vir vazia pra setores legados sem nenhuma modalidade resolvida (nunca
    // editados desde a criação deste campo, ou todas as modalidades vinculadas removidas do
    // catálogo) — campos derivados no frontend ficam null/bloqueados nesse caso.
    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s, List<TomadorModalidade> modalidades) {
        return new TomadorServicoOperacionalResponse(
            s.getId(),
            s.getTomadorId(),
            s.getNome(),
            s.getCategoria(),
            s.isAtivo(),
            modalidades.stream().map(ModalidadeResumo::from).toList()
        );
    }

    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s) {
        return from(s, List.of());
    }
}
