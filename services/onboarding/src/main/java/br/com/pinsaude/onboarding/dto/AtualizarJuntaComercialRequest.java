package br.com.pinsaude.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AtualizarJuntaComercialRequest(
    @NotBlank
    @Pattern(regexp = "AGUARDANDO|EM_ANALISE|APROVADO|RECUSADO",
             message = "Status inválido. Use: AGUARDANDO, EM_ANALISE, APROVADO ou RECUSADO")
    String status,
    String observacao
) {}
