package br.com.pinsaude.portal.dto;

import java.util.List;
import java.util.UUID;

// modalidades: Modalidade(s) de referência configuradas no cadastro do setor (pedido do cliente:
// um setor pode ter mais de uma) — o Portal usa a lista pra (1) derivar o Tipo de Escala da
// frequência automaticamente quando há só 1, ou pra oferecer a escolha quando há mais de 1, e
// (2), pra Diarista, usar a modalidade direto sem perguntar de novo (quando só há 1 opção
// Diarista) ou oferecer a escolha entre as opções Diarista do setor (quando há mais de 1).
// Lista vazia pra setores sem nenhuma modalidade configurada (legados).
public record SetorOperacionalPortalResponse(
    UUID id,
    String nome,
    String categoria,
    List<ModalidadeResumo> modalidades
) {
    public record ModalidadeResumo(UUID id, String nome, String tipo) {}
}
