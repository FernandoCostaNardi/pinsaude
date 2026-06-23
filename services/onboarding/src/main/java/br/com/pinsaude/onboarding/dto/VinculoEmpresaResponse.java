package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.Empresa;
import br.com.pinsaude.onboarding.domain.VinculoMedicoEmpresa;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VinculoEmpresaResponse(
    UUID empresaId,
    String cnpj,
    String razaoSocial,
    String statusSocietario,
    LocalDate dataAtivacao,
    OffsetDateTime createdAt
) {
    public static VinculoEmpresaResponse from(VinculoMedicoEmpresa v, Empresa e) {
        return new VinculoEmpresaResponse(
            e.getId(), e.getCnpj(), e.getRazaoSocial(),
            v.getStatusSocietario() != null ? v.getStatusSocietario().name() : null,
            v.getDataAtivacao(), v.getCreatedAt()
        );
    }
}
