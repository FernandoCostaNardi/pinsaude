package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "extratos_bancarios", schema = "faturamento")
public class ExtratoBancario {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "cnpj_id_tenant", length = 14, nullable = false)
    private String cnpjIdTenant;

    @Column(name = "data_upload", nullable = false)
    private OffsetDateTime dataUpload = OffsetDateTime.now();

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "banco", length = 20)
    private BancoEnum banco;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim", nullable = false)
    private LocalDate periodoFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_importacao", nullable = false)
    @ColumnTransformer(write = "?::faturamento.status_importacao_enum")
    private StatusImportacao statusImportacao = StatusImportacao.PROCESSANDO;

    @Column(name = "total_lancamentos")
    private int totalLancamentos = 0;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public String getCnpjIdTenant() { return cnpjIdTenant; }
    public void setCnpjIdTenant(String cnpjIdTenant) { this.cnpjIdTenant = cnpjIdTenant; }
    public OffsetDateTime getDataUpload() { return dataUpload; }
    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }
    public BancoEnum getBanco() { return banco; }
    public void setBanco(BancoEnum banco) { this.banco = banco; }
    public LocalDate getPeriodoInicio() { return periodoInicio; }
    public void setPeriodoInicio(LocalDate periodoInicio) { this.periodoInicio = periodoInicio; }
    public LocalDate getPeriodoFim() { return periodoFim; }
    public void setPeriodoFim(LocalDate periodoFim) { this.periodoFim = periodoFim; }
    public StatusImportacao getStatusImportacao() { return statusImportacao; }
    public void setStatusImportacao(StatusImportacao statusImportacao) { this.statusImportacao = statusImportacao; }
    public int getTotalLancamentos() { return totalLancamentos; }
    public void setTotalLancamentos(int totalLancamentos) { this.totalLancamentos = totalLancamentos; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
