package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.DeclaracoesLgpdMedico;

import java.time.OffsetDateTime;

public record DeclaracaoLgpdResponse(
    boolean aceiteDeclaracaoVeracidade,
    boolean autorizacaoUsoDados,
    boolean autorizacaoCompartilhamento,
    boolean avisoPrivacidadeLido,
    String assinaturaNome,
    OffsetDateTime assinadoEm
) {
    public static DeclaracaoLgpdResponse from(DeclaracoesLgpdMedico d) {
        return new DeclaracaoLgpdResponse(
            d.isAceiteDeclaracaoVeracidade(),
            d.isAutorizacaoUsoDados(),
            d.isAutorizacaoCompartilhamento(),
            d.isAvisoPrivacidadeLido(),
            d.getAssinaturaNome(),
            d.getAssinadoEm()
        );
    }
}
