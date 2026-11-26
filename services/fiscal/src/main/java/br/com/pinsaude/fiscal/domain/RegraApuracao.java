package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * APU-00 — regras da apuração mensal (Lucro Presumido + reforma), versionadas por competência.
 * {@code cnpjId} nulo = padrão global, usado pela empresa sem regra própria na competência.
 * Frações decimais (0.0800 = 8%) e valores em centavos. Rascunho até homologada.
 */
@Entity
@Table(name = "regra_apuracao", schema = "fiscal")
public class RegraApuracao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id", length = 14)
    private String cnpjId;

    @Column(name = "competencia_inicio", nullable = false, length = 7)
    private String competenciaInicio;

    @Column(name = "presuncao_irpj_equiparado", nullable = false, precision = 8, scale = 4)
    private BigDecimal presuncaoIrpjEquiparado;

    @Column(name = "presuncao_csll_equiparado", nullable = false, precision = 8, scale = 4)
    private BigDecimal presuncaoCsllEquiparado;

    @Column(name = "presuncao_irpj_padrao", nullable = false, precision = 8, scale = 4)
    private BigDecimal presuncaoIrpjPadrao;

    @Column(name = "presuncao_csll_padrao", nullable = false, precision = 8, scale = 4)
    private BigDecimal presuncaoCsllPadrao;

    @Column(name = "aliq_adicional_irpj", nullable = false, precision = 8, scale = 4)
    private BigDecimal aliqAdicionalIrpj;

    @Column(name = "limite_adicional_irpj_mensal", nullable = false)
    private Long limiteAdicionalIrpjMensal;

    @Column(name = "teto_receita_anual", nullable = false)
    private Long tetoReceitaAnual;

    @Column(name = "alerta_teto_percentual", nullable = false, precision = 8, scale = 4)
    private BigDecimal alertaTetoPercentual;

    @Column(name = "valor_minimo_guia", nullable = false)
    private Long valorMinimoGuia = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "referencia_competencia", nullable = false, length = 20)
    private ReferenciaCompetencia referenciaCompetencia = ReferenciaCompetencia.COMPETENCIA_NOTA;

    @Column(name = "homologado", nullable = false)
    private boolean homologado;

    @Column(name = "homologado_por", length = 100)
    private String homologadoPor;

    @Column(name = "homologado_em")
    private OffsetDateTime homologadoEm;

    @Column(name = "fonte")
    private String fonte;

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "atualizado_por", length = 100)
    private String atualizadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "regra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("tributo ASC")
    private List<RegraApuracaoTributo> tributos = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isGlobal() { return cnpjId == null; }

    public ReferenciaCompetencia getReferenciaCompetencia() {
        return referenciaCompetencia == null ? ReferenciaCompetencia.COMPETENCIA_NOTA : referenciaCompetencia;
    }
    public void setReferenciaCompetencia(ReferenciaCompetencia v) {
        this.referenciaCompetencia = v == null ? ReferenciaCompetencia.COMPETENCIA_NOTA : v;
    }

    /** Substitui os tributos da regra (orphanRemoval apaga os que saíram). */
    public void substituirTributos(List<RegraApuracaoTributo> novos) {
        tributos.clear();
        for (RegraApuracaoTributo t : novos) {
            t.setRegra(this);
            tributos.add(t);
        }
    }

    public UUID getId()                                   { return id; }
    public String getCnpjId()                             { return cnpjId; }
    public void setCnpjId(String v)                       { this.cnpjId = v; }
    public String getCompetenciaInicio()                  { return competenciaInicio; }
    public void setCompetenciaInicio(String v)            { this.competenciaInicio = v; }
    public BigDecimal getPresuncaoIrpjEquiparado()        { return presuncaoIrpjEquiparado; }
    public void setPresuncaoIrpjEquiparado(BigDecimal v)  { this.presuncaoIrpjEquiparado = v; }
    public BigDecimal getPresuncaoCsllEquiparado()        { return presuncaoCsllEquiparado; }
    public void setPresuncaoCsllEquiparado(BigDecimal v)  { this.presuncaoCsllEquiparado = v; }
    public BigDecimal getPresuncaoIrpjPadrao()            { return presuncaoIrpjPadrao; }
    public void setPresuncaoIrpjPadrao(BigDecimal v)      { this.presuncaoIrpjPadrao = v; }
    public BigDecimal getPresuncaoCsllPadrao()            { return presuncaoCsllPadrao; }
    public void setPresuncaoCsllPadrao(BigDecimal v)      { this.presuncaoCsllPadrao = v; }
    public BigDecimal getAliqAdicionalIrpj()              { return aliqAdicionalIrpj; }
    public void setAliqAdicionalIrpj(BigDecimal v)        { this.aliqAdicionalIrpj = v; }
    public Long getLimiteAdicionalIrpjMensal()            { return limiteAdicionalIrpjMensal; }
    public void setLimiteAdicionalIrpjMensal(Long v)      { this.limiteAdicionalIrpjMensal = v; }
    public Long getTetoReceitaAnual()                     { return tetoReceitaAnual; }
    public void setTetoReceitaAnual(Long v)               { this.tetoReceitaAnual = v; }
    public BigDecimal getAlertaTetoPercentual()           { return alertaTetoPercentual; }
    public void setAlertaTetoPercentual(BigDecimal v)     { this.alertaTetoPercentual = v; }
    public Long getValorMinimoGuia()                      { return valorMinimoGuia; }
    public void setValorMinimoGuia(Long v)                { this.valorMinimoGuia = v; }
    public boolean isHomologado()                         { return homologado; }
    public void setHomologado(boolean v)                  { this.homologado = v; }
    public String getHomologadoPor()                      { return homologadoPor; }
    public void setHomologadoPor(String v)                { this.homologadoPor = v; }
    public OffsetDateTime getHomologadoEm()               { return homologadoEm; }
    public void setHomologadoEm(OffsetDateTime v)         { this.homologadoEm = v; }
    public String getFonte()                              { return fonte; }
    public void setFonte(String v)                        { this.fonte = v; }
    public String getObservacoes()                        { return observacoes; }
    public void setObservacoes(String v)                  { this.observacoes = v; }
    public String getAtualizadoPor()                      { return atualizadoPor; }
    public void setAtualizadoPor(String v)                { this.atualizadoPor = v; }
    public OffsetDateTime getCreatedAt()                  { return createdAt; }
    public OffsetDateTime getUpdatedAt()                  { return updatedAt; }
    public List<RegraApuracaoTributo> getTributos()       { return tributos; }
}
