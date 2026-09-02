package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_modalidades", schema = "faturamento")
public class TomadorModalidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo = "PLANTONISTA";

    @Column(name = "turno", length = 10)
    private String turno;

    @Column(name = "horario", length = 30)
    private String horario;

    @Column(name = "horas", precision = 6, scale = 2)
    private BigDecimal horas;

    // ─── Campo do tipo DIARISTA (carga horária semanal obrigatória) ─────────────
    @Column(name = "horas_semanais", precision = 6, scale = 2)
    private BigDecimal horasSemanais;

    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;

    @Column(name = "deslocamento_centavos", nullable = false)
    private long deslocamentoCentavos;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                           { return id; }
    public UUID getTomadorId()                    { return tomadorId; }
    public void setTomadorId(UUID v)              { this.tomadorId = v; }
    public String getNome()                       { return nome; }
    public void setNome(String v)                 { this.nome = v; }
    public String getTipo()                       { return tipo; }
    public void setTipo(String v)                 { this.tipo = v; }
    public String getTurno()                      { return turno; }
    public void setTurno(String v)                { this.turno = v; }
    public String getHorario()                    { return horario; }
    public void setHorario(String v)              { this.horario = v; }
    public BigDecimal getHoras()                  { return horas; }
    public void setHoras(BigDecimal v)            { this.horas = v; }
    public BigDecimal getHorasSemanais()          { return horasSemanais; }
    public void setHorasSemanais(BigDecimal v)    { this.horasSemanais = v; }
    public long getValorCentavos()                { return valorCentavos; }
    public void setValorCentavos(long v)          { this.valorCentavos = v; }
    public long getDeslocamentoCentavos()         { return deslocamentoCentavos; }
    public void setDeslocamentoCentavos(long v)   { this.deslocamentoCentavos = v; }
    public boolean isAtivo()                     { return ativo; }
    public void setAtivo(boolean v)              { this.ativo = v; }
    public OffsetDateTime getCreatedAt()          { return createdAt; }
}
