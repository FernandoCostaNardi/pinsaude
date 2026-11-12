package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Alíquotas de referência da reforma tributária (EC 132/2023, LC 214/2025) por ano.
 * Catálogo global (sem tenant). Frações decimais: 0.0090 = 0,9%. Nulo = ainda não fixado.
 */
@Entity
@Table(name = "cronograma_reforma", schema = "fiscal")
public class CronogramaReforma {

    @Id
    @Column(name = "ano")
    private Short ano;

    @Column(name = "aliq_cbs", precision = 8, scale = 4)
    private BigDecimal aliqCbs;

    @Column(name = "aliq_ibs_uf", precision = 8, scale = 4)
    private BigDecimal aliqIbsUf;

    @Column(name = "aliq_ibs_mun", precision = 8, scale = 4)
    private BigDecimal aliqIbsMun;

    @Column(name = "fator_iss", nullable = false, precision = 6, scale = 4)
    private BigDecimal fatorIss;

    @Column(name = "aliquotas_definitivas", nullable = false)
    private boolean aliquotasDefinitivas;

    @Column(name = "fonte")
    private String fonte;

    @Column(name = "atualizado_por", length = 100)
    private String atualizadoPor;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    void touch() {
        atualizadoEm = OffsetDateTime.now();
    }

    public Short getAno()                         { return ano; }
    public void setAno(Short ano)                 { this.ano = ano; }
    public BigDecimal getAliqCbs()                { return aliqCbs; }
    public void setAliqCbs(BigDecimal v)          { this.aliqCbs = v; }
    public BigDecimal getAliqIbsUf()              { return aliqIbsUf; }
    public void setAliqIbsUf(BigDecimal v)        { this.aliqIbsUf = v; }
    public BigDecimal getAliqIbsMun()             { return aliqIbsMun; }
    public void setAliqIbsMun(BigDecimal v)       { this.aliqIbsMun = v; }
    public BigDecimal getFatorIss()               { return fatorIss; }
    public void setFatorIss(BigDecimal v)         { this.fatorIss = v; }
    public boolean isAliquotasDefinitivas()       { return aliquotasDefinitivas; }
    public void setAliquotasDefinitivas(boolean v){ this.aliquotasDefinitivas = v; }
    public String getFonte()                      { return fonte; }
    public void setFonte(String v)                { this.fonte = v; }
    public String getAtualizadoPor()              { return atualizadoPor; }
    public void setAtualizadoPor(String v)        { this.atualizadoPor = v; }
    public OffsetDateTime getAtualizadoEm()       { return atualizadoEm; }
}
