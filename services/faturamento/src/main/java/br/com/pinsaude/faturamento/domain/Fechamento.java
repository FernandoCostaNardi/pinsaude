package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fechamentos", schema = "faturamento")
public class Fechamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id_tenant", nullable = false, length = 20)
    private String cnpjIdTenant;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "ABERTO";

    @Column(name = "total_centavos", nullable = false)
    private long totalCentavos;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "fechado_em")
    private OffsetDateTime fechadoEm;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                            { return id; }
    public String getCnpjIdTenant()               { return cnpjIdTenant; }
    public void setCnpjIdTenant(String v)         { this.cnpjIdTenant = v; }
    public UUID getTomadorId()                     { return tomadorId; }
    public void setTomadorId(UUID v)              { this.tomadorId = v; }
    public String getCompetencia()                 { return competencia; }
    public void setCompetencia(String v)          { this.competencia = v; }
    public String getStatus()                      { return status; }
    public void setStatus(String v)               { this.status = v; }
    public long getTotalCentavos()                 { return totalCentavos; }
    public void setTotalCentavos(long v)          { this.totalCentavos = v; }
    public OffsetDateTime getCreatedAt()           { return createdAt; }
    public OffsetDateTime getFechadoEm()           { return fechadoEm; }
    public void setFechadoEm(OffsetDateTime v)    { this.fechadoEm = v; }
}
