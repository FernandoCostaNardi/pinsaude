package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_aliquotas", schema = "faturamento",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tomador_id", "tipo_tributo"}))
public class TomadorAliquota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "tipo_tributo", nullable = false, length = 10)
    private String tipoTributo;  // ISS, IR, CSLL, PIS, COFINS

    @Column(name = "valor_aliquota", nullable = false, precision = 8, scale = 4)
    private BigDecimal valorAliquota;  // percentual: 5.0000 = 5%

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                              { return id; }
    public UUID getTomadorId()                       { return tomadorId; }
    public void setTomadorId(UUID v)                 { this.tomadorId = v; }
    public String getTipoTributo()                   { return tipoTributo; }
    public void setTipoTributo(String v)             { this.tipoTributo = v; }
    public BigDecimal getValorAliquota()             { return valorAliquota; }
    public void setValorAliquota(BigDecimal v)       { this.valorAliquota = v; }
    public OffsetDateTime getCreatedAt()             { return createdAt; }
}
