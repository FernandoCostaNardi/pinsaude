package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.TipoPix;
import jakarta.validation.constraints.NotNull;

public record DadosBancariosMedicoRequest(
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
    String tipoConta,                  // "CORRENTE" ou "POUPANCA"
    @NotNull Boolean confirmarAlteracao,
    // Rótulo livre para diferenciar contas quando o médico tem mais de uma
    // (ex: "PIX principal", "TED Itaú salário"). Opcional.
    String apelido
) {}
