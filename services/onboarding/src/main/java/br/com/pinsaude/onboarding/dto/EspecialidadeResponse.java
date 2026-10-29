package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.Especialidade;

import java.util.UUID;

public record EspecialidadeResponse(UUID id, String nome) {
    public static EspecialidadeResponse from(Especialidade e) {
        return new EspecialidadeResponse(e.getId(), e.getNome());
    }
}
