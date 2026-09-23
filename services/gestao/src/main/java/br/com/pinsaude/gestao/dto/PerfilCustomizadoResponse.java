package br.com.pinsaude.gestao.dto;

import br.com.pinsaude.gestao.domain.PerfilCustomizado;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PerfilCustomizadoResponse(
    UUID id,
    String nome,
    String keycloakRoleName,
    List<String> permissoes,
    String criadoPor,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static PerfilCustomizadoResponse from(PerfilCustomizado p) {
        return new PerfilCustomizadoResponse(
            p.getId(),
            p.getNome(),
            p.getKeycloakRoleName(),
            List.of(p.getPermissoes()),
            p.getCriadoPor(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
