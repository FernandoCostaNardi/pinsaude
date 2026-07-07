package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "producoes", schema = "faturamento")
public class Producao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id_tenant", nullable = false, length = 14)
    private String cnpjIdTenant;

    @Column(name = "medico_id")
    private UUID medicoId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tomador_id", nullable = false)
    private Tomador tomador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "valor_bruto", nullable = false)
    private long valorBruto;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    @Column(name = "descricao_complementar")
    private String descricaoComplementar;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnTransformer(write = "?::faturamento.status_producao_enum")
    private StatusProducao status;

    @Column(name = "cnae_codigo", length = 10)
    private String cnaeCodigo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = StatusProducao.CONFIRMADA;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCnpjIdTenant() { return cnpjIdTenant; }
    public void setCnpjIdTenant(String cnpjIdTenant) { this.cnpjIdTenant = cnpjIdTenant; }
    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }
    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }
    public Tomador getTomador() { return tomador; }
    public void setTomador(Tomador tomador) { this.tomador = tomador; }
    public Servico getServico() { return servico; }
    public void setServico(Servico servico) { this.servico = servico; }
    public long getValorBruto() { return valorBruto; }
    public void setValorBruto(long valorBruto) { this.valorBruto = valorBruto; }
    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }
    public String getDescricaoComplementar() { return descricaoComplementar; }
    public void setDescricaoComplementar(String descricaoComplementar) { this.descricaoComplementar = descricaoComplementar; }
    public StatusProducao getStatus() { return status; }
    public void setStatus(StatusProducao status) { this.status = status; }
    public String getCnaeCodigo() { return cnaeCodigo; }
    public void setCnaeCodigo(String cnaeCodigo) { this.cnaeCodigo = cnaeCodigo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
