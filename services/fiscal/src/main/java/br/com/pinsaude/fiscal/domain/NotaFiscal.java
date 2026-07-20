package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notas_fiscais", schema = "fiscal")
public class NotaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id_tenant", nullable = false, length = 14)
    private String cnpjIdTenant;

    @Column(name = "producao_id", nullable = false)
    private UUID producaoId;

    @Column(name = "medico_id")
    private UUID medicoId;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "tomador_nome", length = 200)
    private String tomadorNome;

    @Column(name = "numero_nota", length = 20)
    private String numeroNota;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;     // YYYY-MM

    // Valores monetários em centavos
    @Column(name = "valor_bruto", nullable = false)
    private Long valorBruto;

    @Column(name = "taxa_pin", nullable = false)
    private Long taxaPin;           // 15% do bruto

    @Column(name = "valor_iss", nullable = false)
    private Long valorIss = 0L;

    @Column(name = "valor_ir", nullable = false)
    private Long valorIr = 0L;

    @Column(name = "valor_csll", nullable = false)
    private Long valorCsll = 0L;

    @Column(name = "valor_pis", nullable = false)
    private Long valorPis = 0L;

    @Column(name = "valor_cofins", nullable = false)
    private Long valorCofins = 0L;

    // CHECK constraint no DB garante: valor_liquido_medico = valor_bruto - taxa_pin (sempre 85%)
    @Column(name = "valor_liquido_medico", nullable = false)
    private Long valorLiquidoMedico;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StatusNota status = StatusNota.PENDENTE;

    @Column(name = "xml_nota", columnDefinition = "text")
    private String xmlNota;

    @Column(name = "pdf_nota", columnDefinition = "bytea")
    private byte[] pdfNota;

    @Column(name = "protocolo_emissao", length = 100)
    private String protocoloEmissao;

    @Column(name = "cnae_codigo", length = 20)
    private String cnaeCodigo;

    @Column(name = "discriminacao", columnDefinition = "text")
    private String discriminacao;

    @Column(name = "observacoes")
    private String observacoes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "emitida_at")
    private OffsetDateTime emitidaAt;

    @OneToMany(mappedBy = "nota", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RetencaoSofrida> retencoes = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = StatusNota.PENDENTE;
    }

    public UUID getId()                              { return id; }
    public String getCnpjIdTenant()                  { return cnpjIdTenant; }
    public void setCnpjIdTenant(String v)            { this.cnpjIdTenant = v; }
    public UUID getProducaoId()                      { return producaoId; }
    public void setProducaoId(UUID v)                { this.producaoId = v; }
    public UUID getMedicoId()                        { return medicoId; }
    public void setMedicoId(UUID v)                  { this.medicoId = v; }
    public UUID getTomadorId()                       { return tomadorId; }
    public void setTomadorId(UUID v)                 { this.tomadorId = v; }
    public String getTomadorNome()                   { return tomadorNome; }
    public void setTomadorNome(String v)             { this.tomadorNome = v; }
    public String getNumeroNota()                    { return numeroNota; }
    public void setNumeroNota(String v)              { this.numeroNota = v; }
    public String getCompetencia()                   { return competencia; }
    public void setCompetencia(String v)             { this.competencia = v; }
    public Long getValorBruto()                      { return valorBruto; }
    public void setValorBruto(Long v)                { this.valorBruto = v; }
    public Long getTaxaPin()                         { return taxaPin; }
    public void setTaxaPin(Long v)                   { this.taxaPin = v; }
    public Long getValorIss()                        { return valorIss; }
    public void setValorIss(Long v)                  { this.valorIss = v; }
    public Long getValorIr()                         { return valorIr; }
    public void setValorIr(Long v)                   { this.valorIr = v; }
    public Long getValorCsll()                       { return valorCsll; }
    public void setValorCsll(Long v)                 { this.valorCsll = v; }
    public Long getValorPis()                        { return valorPis; }
    public void setValorPis(Long v)                  { this.valorPis = v; }
    public Long getValorCofins()                     { return valorCofins; }
    public void setValorCofins(Long v)               { this.valorCofins = v; }
    public Long getValorLiquidoMedico()              { return valorLiquidoMedico; }
    public void setValorLiquidoMedico(Long v)        { this.valorLiquidoMedico = v; }
    public StatusNota getStatus()                    { return status; }
    public void setStatus(StatusNota v)              { this.status = v; }
    public String getXmlNota()                       { return xmlNota; }
    public void setXmlNota(String v)                 { this.xmlNota = v; }
    public byte[] getPdfNota()                       { return pdfNota; }
    public void setPdfNota(byte[] v)                 { this.pdfNota = v; }
    public String getProtocoloEmissao()              { return protocoloEmissao; }
    public void setProtocoloEmissao(String v)        { this.protocoloEmissao = v; }
    public String getCnaeCodigo()                    { return cnaeCodigo; }
    public void setCnaeCodigo(String v)              { this.cnaeCodigo = v; }
    public String getDiscriminacao()                 { return discriminacao; }
    public void setDiscriminacao(String v)           { this.discriminacao = v; }
    public String getObservacoes()                   { return observacoes; }
    public void setObservacoes(String v)             { this.observacoes = v; }
    public OffsetDateTime getCreatedAt()             { return createdAt; }
    public OffsetDateTime getEmitidaAt()             { return emitidaAt; }
    public void setEmitidaAt(OffsetDateTime v)       { this.emitidaAt = v; }
    public List<RetencaoSofrida> getRetencoes()      { return retencoes; }
}
