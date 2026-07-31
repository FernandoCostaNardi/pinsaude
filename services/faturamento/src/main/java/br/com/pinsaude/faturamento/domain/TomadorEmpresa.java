package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_empresas", schema = "faturamento")
public class TomadorEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                        { return id; }
    public UUID getTomadorId()                  { return tomadorId; }
    public void setTomadorId(UUID tomadorId)    { this.tomadorId = tomadorId; }
    public UUID getEmpresaId()                  { return empresaId; }
    public void setEmpresaId(UUID empresaId)    { this.empresaId = empresaId; }
    public OffsetDateTime getCreatedAt()        { return createdAt; }
}
