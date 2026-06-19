package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "regras_equiparacao", schema = "fiscal",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cnpj_id", "cnae", "codigo_lc116"}))
public class RegraEquiparacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id", nullable = false, length = 14)
    private String cnpjId;

    @Column(name = "cnae", nullable = false, length = 10)
    private String cnae;

    @Column(name = "codigo_lc116", nullable = false, length = 10)
    private String codigoLc116;

    @Column(name = "indicador_equiparacao", nullable = false)
    private Boolean indicadorEquiparacao = false;

    // VARCHAR com CHECK: 'REDUZIDA' ou 'CHEIA' — sem enum PostgreSQL, sem @ColumnTransformer
    @Column(name = "regime_presuncao", length = 10)
    private String regimePresuncao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                            { return id; }
    public String getCnpjId()                      { return cnpjId; }
    public void setCnpjId(String v)                { this.cnpjId = v; }
    public String getCnae()                        { return cnae; }
    public void setCnae(String v)                  { this.cnae = v; }
    public String getCodigoLc116()                 { return codigoLc116; }
    public void setCodigoLc116(String v)           { this.codigoLc116 = v; }
    public Boolean getIndicadorEquiparacao()       { return indicadorEquiparacao; }
    public void setIndicadorEquiparacao(Boolean v) { this.indicadorEquiparacao = v; }
    public String getRegimePresuncao()             { return regimePresuncao; }
    public void setRegimePresuncao(String v)       { this.regimePresuncao = v; }
    public OffsetDateTime getCreatedAt()           { return createdAt; }
}
