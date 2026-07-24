package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medico_tomadores", schema = "faturamento")
public class MedicoTomador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                        { return id; }
    public UUID getTomadorId()                  { return tomadorId; }
    public void setTomadorId(UUID tomadorId)    { this.tomadorId = tomadorId; }
    public UUID getMedicoId()                   { return medicoId; }
    public void setMedicoId(UUID medicoId)      { this.medicoId = medicoId; }
    public OffsetDateTime getCreatedAt()        { return createdAt; }
}
