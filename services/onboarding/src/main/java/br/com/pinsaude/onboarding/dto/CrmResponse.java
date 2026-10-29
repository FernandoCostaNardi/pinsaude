package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.MedicoCrm;

import java.util.UUID;

public record CrmResponse(UUID id, String numero, String uf, boolean principal) {
    public static CrmResponse from(MedicoCrm c) {
        return new CrmResponse(c.getId(), c.getNumero(), c.getUf(), c.isPrincipal());
    }
}
