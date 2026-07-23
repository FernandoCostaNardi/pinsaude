package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.TipoPix;
import jakarta.validation.constraints.NotNull;

/**
 * Igual a DadosBancariosMedicoRequest, mas sem confirmarAlteracao — esse campo é
 * pensado para alteração posterior por operação (EPIC-13.x), não para o cadastro
 * inicial feito pelo próprio médico no auto-cadastro público (EPIC-14.3).
 */
public record CandidaturaDadosBancariosRequest(
    @NotNull String tipoRecebimento,   // "PIX" ou "TED"
    // PIX
    TipoPix tipoPix,
    String chavePix,
    String cpfsAdicionaisSplit,
    // TED
    String bancoCodigo,
    String bancoNome,
    String agencia,
    String conta,
    String tipoConta                   // "CORRENTE" ou "POUPANCA"
) {}
