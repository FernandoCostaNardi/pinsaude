package br.com.pinsaude.portal.dto;

import java.util.UUID;

// modalidadeId/modalidadeNome/modalidadeTipo: Modalidade de referência configurada no cadastro do
// setor (faturamento.tomador_servicos_operacionais.modalidade_id) — o Portal usa esses campos pra
// (1) derivar o Tipo de Escala da frequência automaticamente ao selecionar o setor e (2), pra
// Diarista, usar direto essa modalidade sem perguntar de novo na tela de Nova Frequência (pedido
// do cliente). Todos null pra setores sem modalidade configurada (legados).
public record SetorOperacionalPortalResponse(
    UUID id,
    String nome,
    String categoria,
    UUID modalidadeId,
    String modalidadeNome,
    String modalidadeTipo
) {}
