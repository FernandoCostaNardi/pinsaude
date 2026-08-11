package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_horarios_padrao", schema = "faturamento")
public class TomadorHorarioPadrao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "turno", nullable = false, length = 10)
    private String turno;   // DIURNO | NOTURNO

    @Column(name = "horas", nullable = false, precision = 6, scale = 2)
    private BigDecimal horas;

    @Column(name = "horario", nullable = false, length = 30)
    private String horario;

    @Column(name = "ordem", nullable = false)
    private int ordem = 1;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                     { return id; }
    public UUID getTomadorId()               { return tomadorId; }
    public void setTomadorId(UUID v)         { this.tomadorId = v; }
    public String getTurno()                 { return turno; }
    public void setTurno(String v)           { this.turno = v; }
    public BigDecimal getHoras()             { return horas; }
    public void setHoras(BigDecimal v)       { this.horas = v; }
    public String getHorario()               { return horario; }
    public void setHorario(String v)         { this.horario = v; }
    public int getOrdem()                    { return ordem; }
    public void setOrdem(int v)              { this.ordem = v; }
    public boolean isAtivo()                 { return ativo; }
    public void setAtivo(boolean v)          { this.ativo = v; }
    public OffsetDateTime getCreatedAt()     { return createdAt; }
}
