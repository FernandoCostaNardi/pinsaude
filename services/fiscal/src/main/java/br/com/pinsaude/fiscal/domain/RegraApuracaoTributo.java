package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/** APU-00 — como um tributo é apurado dentro de uma {@link RegraApuracao}. */
@Entity
@Table(name = "regra_apuracao_tributo", schema = "fiscal")
public class RegraApuracaoTributo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regra_id", nullable = false)
    private RegraApuracao regra;

    @Enumerated(EnumType.STRING)
    @Column(name = "tributo", nullable = false, length = 10)
    private TributoApuracao tributo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "aplica_em", nullable = false, length = 10)
    private AplicacaoRegime aplicaEm = AplicacaoRegime.AMBOS;

    /** Fração decimal; nula só para CBS/IBS (usa o cronograma da reforma). */
    @Column(name = "aliquota", precision = 8, scale = 4)
    private BigDecimal aliquota;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_calculo", nullable = false, length = 20)
    private BaseCalculoApuracao baseCalculo;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_reconhecimento", nullable = false, length = 12)
    private RegimeReconhecimento regimeReconhecimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidade", nullable = false, length = 12)
    private PeriodicidadeApuracao periodicidade;

    @Column(name = "deduz_retencao", nullable = false)
    private boolean deduzRetencao = true;

    @Column(name = "codigo_receita", length = 10)
    private String codigoReceita;

    @Column(name = "guia", length = 40)
    private String guia;

    @Column(name = "vencimento_dia")
    private Short vencimentoDia;

    @Column(name = "vencimento_meses_apos")
    private Short vencimentoMesesApos;

    @Enumerated(EnumType.STRING)
    @Column(name = "vencimento_ajuste", nullable = false, length = 10)
    private AjusteVencimento vencimentoAjuste = AjusteVencimento.NENHUM;

    public UUID getId()                                        { return id; }
    public RegraApuracao getRegra()                            { return regra; }
    public void setRegra(RegraApuracao v)                      { this.regra = v; }
    public TributoApuracao getTributo()                        { return tributo; }
    public void setTributo(TributoApuracao v)                  { this.tributo = v; }
    public boolean isAtivo()                                   { return ativo; }
    public void setAtivo(boolean v)                            { this.ativo = v; }
    public AplicacaoRegime getAplicaEm()                       { return aplicaEm; }
    public void setAplicaEm(AplicacaoRegime v)                 { this.aplicaEm = v; }
    public BigDecimal getAliquota()                            { return aliquota; }
    public void setAliquota(BigDecimal v)                      { this.aliquota = v; }
    public BaseCalculoApuracao getBaseCalculo()                { return baseCalculo; }
    public void setBaseCalculo(BaseCalculoApuracao v)          { this.baseCalculo = v; }
    public RegimeReconhecimento getRegimeReconhecimento()      { return regimeReconhecimento; }
    public void setRegimeReconhecimento(RegimeReconhecimento v){ this.regimeReconhecimento = v; }
    public PeriodicidadeApuracao getPeriodicidade()            { return periodicidade; }
    public void setPeriodicidade(PeriodicidadeApuracao v)      { this.periodicidade = v; }
    public boolean isDeduzRetencao()                           { return deduzRetencao; }
    public void setDeduzRetencao(boolean v)                    { this.deduzRetencao = v; }
    public String getCodigoReceita()                           { return codigoReceita; }
    public void setCodigoReceita(String v)                     { this.codigoReceita = v; }
    public String getGuia()                                    { return guia; }
    public void setGuia(String v)                              { this.guia = v; }
    public Short getVencimentoDia()                            { return vencimentoDia; }
    public void setVencimentoDia(Short v)                      { this.vencimentoDia = v; }
    public Short getVencimentoMesesApos()                      { return vencimentoMesesApos; }
    public void setVencimentoMesesApos(Short v)                { this.vencimentoMesesApos = v; }
    public AjusteVencimento getVencimentoAjuste()              { return vencimentoAjuste; }
    public void setVencimentoAjuste(AjusteVencimento v)        { this.vencimentoAjuste = v; }
}
