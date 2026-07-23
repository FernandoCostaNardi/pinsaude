package br.com.pinsaude.onboarding.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Os 4 aceites são obrigatoriamente `true` (@AssertTrue) — são declarações legais de
 * consentimento, não preferências opcionais. assinaturaNome é a assinatura eletrônica
 * simples (nome digitado pelo próprio médico).
 */
public record DeclaracaoLgpdRequest(
    @AssertTrue(message = "É necessário confirmar que as informações prestadas são verdadeiras")
    boolean aceiteDeclaracaoVeracidade,

    @AssertTrue(message = "É necessário autorizar o uso dos dados para contratação/credenciamento")
    boolean autorizacaoUsoDados,

    @AssertTrue(message = "É necessário autorizar o compartilhamento dos dados para credenciamento")
    boolean autorizacaoCompartilhamento,

    @AssertTrue(message = "É necessário confirmar a leitura do Aviso de Privacidade (LGPD)")
    boolean avisoPrivacidadeLido,

    @NotBlank(message = "Assinatura eletrônica (nome completo) é obrigatória")
    @Size(max = 200)
    String assinaturaNome
) {}
