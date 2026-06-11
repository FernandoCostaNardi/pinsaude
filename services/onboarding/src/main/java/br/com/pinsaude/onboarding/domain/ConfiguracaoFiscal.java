package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "configuracoes_fiscais", schema = "onboarding")
public class ConfiguracaoFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "cnae_codigo", length = 10)
    private String cnaeCodigo;

    @Column(name = "cnae_descricao", length = 500)
    private String cnaeDescricao;

    @Column(name = "codigo_lc116", length = 10)
    private String codigoLc116;

    @Column(name = "indicador_equiparacao_hospitalar", nullable = false)
    private boolean indicadorEquiparacaoHospitalar = false;

    @Column(name = "vencimento_certificado_a1")
    private LocalDate vencimentoCertificadoA1;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

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

    public String getCnaeCodigo() { return cnaeCodigo; }
    public void setCnaeCodigo(String cnaeCodigo) { this.cnaeCodigo = cnaeCodigo; }

    public String getCnaeDescricao() { return cnaeDescricao; }
    public void setCnaeDescricao(String cnaeDescricao) { this.cnaeDescricao = cnaeDescricao; }

    public String getCodigoLc116() { return codigoLc116; }
    public void setCodigoLc116(String codigoLc116) { this.codigoLc116 = codigoLc116; }

    public boolean isIndicadorEquiparacaoHospitalar() { return indicadorEquiparacaoHospitalar; }
    public void setIndicadorEquiparacaoHospitalar(boolean indicadorEquiparacaoHospitalar) {
        this.indicadorEquiparacaoHospitalar = indicadorEquiparacaoHospitalar;
    }

    public LocalDate getVencimentoCertificadoA1() { return vencimentoCertificadoA1; }
    public void setVencimentoCertificadoA1(LocalDate vencimentoCertificadoA1) {
        this.vencimentoCertificadoA1 = vencimentoCertificadoA1;
    }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
