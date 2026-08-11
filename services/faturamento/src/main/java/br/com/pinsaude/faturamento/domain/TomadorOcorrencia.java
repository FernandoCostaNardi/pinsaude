package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_ocorrencias", schema = "faturamento")
public class TomadorOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "tipo_valor", nullable = false, length = 12)
    private String tipoValor;   // PERCENTUAL | FIXO | SEM_VALOR

    @Column(name = "valor_percentual", precision = 8, scale = 4)
    private BigDecimal valorPercentual;

    @Column(name = "valor_centavos")
    private Long valorCentavos;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                            { return id; }
    public UUID getTomadorId()                      { return tomadorId; }
    public void setTomadorId(UUID v)                { this.tomadorId = v; }
    public String getNome()                         { return nome; }
    public void setNome(String v)                   { this.nome = v; }
    public String getTipoValor()                    { return tipoValor; }
    public void setTipoValor(String v)              { this.tipoValor = v; }
    public BigDecimal getValorPercentual()          { return valorPercentual; }
    public void setValorPercentual(BigDecimal v)    { this.valorPercentual = v; }
    public Long getValorCentavos()                  { return valorCentavos; }
    public void setValorCentavos(Long v)            { this.valorCentavos = v; }
    public boolean isAtivo()                        { return ativo; }
    public void setAtivo(boolean v)                 { this.ativo = v; }
    public OffsetDateTime getCreatedAt()            { return createdAt; }
}
