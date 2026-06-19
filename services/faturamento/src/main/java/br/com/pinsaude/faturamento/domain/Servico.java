package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "servicos", schema = "faturamento")
public class Servico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "codigo_lc116", nullable = false, length = 10, unique = true)
    private String codigoLc116;

    @Column(name = "cnae", length = 10)
    private String cnae;

    @Column(name = "descricao_padrao", nullable = false)
    private String descricaoPadrao;

    @Column(name = "indicador_equiparacao", nullable = false)
    private boolean indicadorEquiparacao;

    @Column(name = "aliquota_iss", nullable = false, precision = 7, scale = 4)
    private BigDecimal aliquotaIss;

    @Column(name = "aliquota_ir", nullable = false, precision = 7, scale = 4)
    private BigDecimal aliquotaIr;

    @Column(name = "aliquota_csll", nullable = false, precision = 7, scale = 4)
    private BigDecimal aliquotaCsll;

    @Column(name = "aliquota_pis", nullable = false, precision = 7, scale = 4)
    private BigDecimal aliquotaPis;

    @Column(name = "aliquota_cofins", nullable = false, precision = 7, scale = 4)
    private BigDecimal aliquotaCofins;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCodigoLc116() { return codigoLc116; }
    public void setCodigoLc116(String codigoLc116) { this.codigoLc116 = codigoLc116; }
    public String getCnae() { return cnae; }
    public void setCnae(String cnae) { this.cnae = cnae; }
    public String getDescricaoPadrao() { return descricaoPadrao; }
    public void setDescricaoPadrao(String descricaoPadrao) { this.descricaoPadrao = descricaoPadrao; }
    public boolean isIndicadorEquiparacao() { return indicadorEquiparacao; }
    public void setIndicadorEquiparacao(boolean indicadorEquiparacao) { this.indicadorEquiparacao = indicadorEquiparacao; }
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
}
