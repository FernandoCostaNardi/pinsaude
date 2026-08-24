package br.com.pinsaude.faturamento.dto;

import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;

import java.util.UUID;

public record TomadorServicoOperacionalResponse(
    UUID id,
    UUID tomadorId,
    String nome,
    String categoria,
    boolean ativo,
    // Modalidade de referência do setor (pedido do cliente) — modalidadeTipo é o que o frontend
    // usa pra derivar o Tipo de Escala da Frequência assim que o setor é selecionado, sem
    // precisar mais de um seletor próprio na tela de Nova Frequência.
    UUID modalidadeId,
    String modalidadeNome,
    String modalidadeTipo,
    String tipoEscalaLabel
) {
    // modalidade pode vir null pra setores legados sem modalidade resolvida (nunca editados
    // desde a criação deste campo, ou modalidade removida do catálogo) — campos derivados ficam
    // null nesse caso, mesma convenção já usada em outros DTOs deste service.
    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s, TomadorModalidade modalidade) {
        return new TomadorServicoOperacionalResponse(
            s.getId(),
            s.getTomadorId(),
            s.getNome(),
            s.getCategoria(),
            s.isAtivo(),
            s.getModalidadeId(),
            modalidade != null ? modalidade.getNome() : null,
            modalidade != null ? modalidade.getTipo() : null,
            s.getTipoEscalaLabel()
        );
    }

    public static TomadorServicoOperacionalResponse from(TomadorServicoOperacional s) {
        return from(s, null);
    }
}
