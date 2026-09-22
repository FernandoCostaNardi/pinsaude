package br.com.pinsaude.onboarding.dto;

import java.util.List;

/**
 * Corpo opcional do reenvio do e-mail de boas-vindas. copiaPara permite acompanhar o envio
 * (útil para o time conferir que o e-mail saiu quando o médico diz que não recebeu).
 */
public record ReenviarBoasVindasRequest(List<String> copiaPara) {

    public List<String> copiasNormalizadas() {
        if (copiaPara == null) return List.of();
        return copiaPara.stream()
            .filter(c -> c != null && !c.isBlank())
            .map(String::trim)
            .toList();
    }
}
