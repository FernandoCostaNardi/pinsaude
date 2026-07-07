package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_cnaes", schema = "faturamento",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tomador_id", "codigo_cnae"}))
public class TomadorCnae {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "codigo_cnae", nullable = false, length = 20)
    private String codigoCnae;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                        { return id; }
    public UUID getTomadorId()                 { return tomadorId; }
    public void setTomadorId(UUID v)           { this.tomadorId = v; }
    public String getCodigoCnae()              { return codigoCnae; }
    public void setCodigoCnae(String v)        { this.codigoCnae = v; }
    public String getDescricao()               { return descricao; }
    public void setDescricao(String v)         { this.descricao = v; }
    public OffsetDateTime getCreatedAt()       { return createdAt; }
}
