package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "parametros_fiscais", schema = "fiscal",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cnpj_id", "competencia_inicio", "tipo_tributo"}))
public class ParametrosFiscais {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id", nullable = false, length = 14)
    private String cnpjId;

    @Column(name = "competencia_inicio", nullable = false, length = 7)
    private String competenciaInicio;   // YYYY-MM

    @Column(name = "competencia_fim", length = 7)
    private String competenciaFim;      // nullable

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tributo", nullable = false)
    @ColumnTransformer(write = "?::fiscal.tipo_tributo_enum")
    private TipoTributo tipoTributo;

    @Column(name = "valor_aliquota", nullable = false, precision = 8, scale = 4)
    private BigDecimal valorAliquota;   // fração decimal: 0.0500 = 5%

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                           { return id; }
    public String getCnpjId()                     { return cnpjId; }
    public void setCnpjId(String v)               { this.cnpjId = v; }
    public String getCompetenciaInicio()          { return competenciaInicio; }
    public void setCompetenciaInicio(String v)    { this.competenciaInicio = v; }
    public String getCompetenciaFim()             { return competenciaFim; }
    public void setCompetenciaFim(String v)       { this.competenciaFim = v; }
    public TipoTributo getTipoTributo()           { return tipoTributo; }
    public void setTipoTributo(TipoTributo v)     { this.tipoTributo = v; }
    public BigDecimal getValorAliquota()          { return valorAliquota; }
    public void setValorAliquota(BigDecimal v)    { this.valorAliquota = v; }
    public String getDescricao()                  { return descricao; }
    public void setDescricao(String v)            { this.descricao = v; }
    public OffsetDateTime getCreatedAt()          { return createdAt; }
}
