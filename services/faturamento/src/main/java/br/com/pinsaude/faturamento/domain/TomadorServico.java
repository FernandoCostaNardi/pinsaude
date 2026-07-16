package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_servicos", schema = "faturamento",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tomador_id", "servico_id"}))
public class TomadorServico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "servico_id", nullable = false)
    private UUID servicoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                  { return id; }
    public UUID getTomadorId()           { return tomadorId; }
    public void setTomadorId(UUID v)     { this.tomadorId = v; }
    public UUID getServicoId()           { return servicoId; }
    public void setServicoId(UUID v)     { this.servicoId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
