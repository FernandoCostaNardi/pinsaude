package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "parametro_fiscal", schema = "fiscal",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cnpj_id", "vigencia_inicio"}))
public class ParametroFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id", nullable = false, length = 14)
    private String cnpjId;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDate vigenciaInicio;

    // Regime atual
    @Column(name = "aliq_iss", nullable = false, precision = 8, scale = 4)
    private BigDecimal aliqIss;

    @Column(name = "aliq_ir", nullable = false, precision = 8, scale = 4)
    private BigDecimal aliqIr;

    @Column(name = "aliq_csll", nullable = false, precision = 8, scale = 4)
    private BigDecimal aliqCsll;

    @Column(name = "aliq_pis", nullable = false, precision = 8, scale = 4)
    private BigDecimal aliqPis;

    @Column(name = "aliq_cofins", nullable = false, precision = 8, scale = 4)
    private BigDecimal aliqCofins;

    // IBS/CBS — Reforma Tributária 2027
    @Column(name = "ibs_cbs_ativo", nullable = false)
    private boolean ibsCbsAtivo;

    @Column(name = "aliq_ibs_cbs", precision = 8, scale = 4)
    private BigDecimal aliqIbsCbs;

    @Column(name = "reducao_ibs_cbs_saude", precision = 8, scale = 4)
    private BigDecimal reducaoIbsCbsSaude;

    // Controle
    @Column(name = "homologado", nullable = false)
    private boolean homologado;

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public UUID getId()                          { return id; }
    public String getCnpjId()                    { return cnpjId; }
    public void setCnpjId(String cnpjId)         { this.cnpjId = cnpjId; }
    public LocalDate getVigenciaInicio()         { return vigenciaInicio; }
    public void setVigenciaInicio(LocalDate v)   { this.vigenciaInicio = v; }
    public BigDecimal getAliqIss()               { return aliqIss; }
    public void setAliqIss(BigDecimal v)         { this.aliqIss = v; }
    public BigDecimal getAliqIr()                { return aliqIr; }
    public void setAliqIr(BigDecimal v)          { this.aliqIr = v; }
    public BigDecimal getAliqCsll()              { return aliqCsll; }
    public void setAliqCsll(BigDecimal v)        { this.aliqCsll = v; }
    public BigDecimal getAliqPis()               { return aliqPis; }
    public void setAliqPis(BigDecimal v)         { this.aliqPis = v; }
    public BigDecimal getAliqCofins()            { return aliqCofins; }
    public void setAliqCofins(BigDecimal v)      { this.aliqCofins = v; }
    public boolean isIbsCbsAtivo()               { return ibsCbsAtivo; }
    public void setIbsCbsAtivo(boolean v)        { this.ibsCbsAtivo = v; }
    public BigDecimal getAliqIbsCbs()            { return aliqIbsCbs; }
    public void setAliqIbsCbs(BigDecimal v)      { this.aliqIbsCbs = v; }
    public BigDecimal getReducaoIbsCbsSaude()    { return reducaoIbsCbsSaude; }
    public void setReducaoIbsCbsSaude(BigDecimal v) { this.reducaoIbsCbsSaude = v; }
    public boolean isHomologado()                { return homologado; }
    public void setHomologado(boolean v)         { this.homologado = v; }
    public String getObservacoes()               { return observacoes; }
    public void setObservacoes(String v)         { this.observacoes = v; }
    public OffsetDateTime getCreatedAt()         { return createdAt; }
}
