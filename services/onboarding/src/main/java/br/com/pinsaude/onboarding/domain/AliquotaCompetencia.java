package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "aliquotas_competencia", schema = "onboarding")
public class AliquotaCompetencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;  // formato YYYY-MM

    @Column(name = "aliquota_iss", nullable = false, precision = 5, scale = 2)
    private BigDecimal aliquotaIss = BigDecimal.ZERO;

    @Column(name = "aliquota_ir", nullable = false, precision = 5, scale = 2)
    private BigDecimal aliquotaIr = BigDecimal.ZERO;

    @Column(name = "aliquota_csll", nullable = false, precision = 5, scale = 2)
    private BigDecimal aliquotaCsll = BigDecimal.ZERO;

    @Column(name = "aliquota_pis", nullable = false, precision = 5, scale = 2)
    private BigDecimal aliquotaPis = BigDecimal.ZERO;

    @Column(name = "aliquota_cofins", nullable = false, precision = 5, scale = 2)
    private BigDecimal aliquotaCofins = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_presuncao", nullable = false, length = 10)
    private RegimePresuncao regimePresuncao = RegimePresuncao.CHEIA;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public BigDecimal getAliquotaIss() { return aliquotaIss; }
    public void setAliquotaIss(BigDecimal aliquotaIss) { this.aliquotaIss = aliquotaIss; }

    public BigDecimal getAliquotaIr() { return aliquotaIr; }
    public void setAliquotaIr(BigDecimal aliquotaIr) { this.aliquotaIr = aliquotaIr; }

    public BigDecimal getAliquotaCsll() { return aliquotaCsll; }
    public void setAliquotaCsll(BigDecimal aliquotaCsll) { this.aliquotaCsll = aliquotaCsll; }

    public BigDecimal getAliquotaPis() { return aliquotaPis; }
    public void setAliquotaPis(BigDecimal aliquotaPis) { this.aliquotaPis = aliquotaPis; }

    public BigDecimal getAliquotaCofins() { return aliquotaCofins; }
    public void setAliquotaCofins(BigDecimal aliquotaCofins) { this.aliquotaCofins = aliquotaCofins; }

    public RegimePresuncao getRegimePresuncao() { return regimePresuncao; }
    public void setRegimePresuncao(RegimePresuncao regimePresuncao) { this.regimePresuncao = regimePresuncao; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
