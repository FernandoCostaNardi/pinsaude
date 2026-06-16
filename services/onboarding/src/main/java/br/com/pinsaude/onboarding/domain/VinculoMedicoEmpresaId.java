package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class VinculoMedicoEmpresaId implements Serializable {

    @Column(name = "medico_id")
    private UUID medicoId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    public VinculoMedicoEmpresaId() {}

    public VinculoMedicoEmpresaId(UUID medicoId, UUID empresaId) {
        this.medicoId = medicoId;
        this.empresaId = empresaId;
    }

    public UUID getMedicoId() { return medicoId; }
    public UUID getEmpresaId() { return empresaId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VinculoMedicoEmpresaId other)) return false;
        return Objects.equals(medicoId, other.medicoId) && Objects.equals(empresaId, other.empresaId);
    }

    @Override
    public int hashCode() { return Objects.hash(medicoId, empresaId); }
}
