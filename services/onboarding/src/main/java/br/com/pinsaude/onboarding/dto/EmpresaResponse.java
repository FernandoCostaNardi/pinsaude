package br.com.pinsaude.onboarding.dto;

import br.com.pinsaude.onboarding.domain.Empresa;
import br.com.pinsaude.onboarding.domain.RegimeTributario;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EmpresaResponse(
    UUID id,
    String cnpj,
    String razaoSocial,
    String inscricaoMunicipal,
    String municipio,
    String codigoMunicipioIbge,
    RegimeTributario regimeTributario,
    boolean ativo,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static EmpresaResponse from(Empresa e) {
        return new EmpresaResponse(
            e.getId(), e.getCnpj(), e.getRazaoSocial(),
            e.getInscricaoMunicipal(), e.getMunicipio(), e.getCodigoMunicipioIbge(),
            e.getRegimeTributario(),
            e.isAtivo(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
