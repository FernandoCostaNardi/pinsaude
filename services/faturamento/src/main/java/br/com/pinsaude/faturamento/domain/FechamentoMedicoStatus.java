package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fechamento_medico_status", schema = "faturamento")
public class FechamentoMedicoStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // OK | SEM_FATURAR | NAO_TEVE

    @Column(name = "atualizado_por", length = 150)
    private String atualizadoPor;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    void tocarAtualizacao() {
        atualizadoEm = OffsetDateTime.now();
    }

    public UUID getId()                              { return id; }
    public UUID getTomadorId()                        { return tomadorId; }
    public void setTomadorId(UUID v)                  { this.tomadorId = v; }
    public UUID getMedicoId()                         { return medicoId; }
    public void setMedicoId(UUID v)                   { this.medicoId = v; }
    public String getCompetencia()                    { return competencia; }
    public void setCompetencia(String v)              { this.competencia = v; }
    public String getStatus()                         { return status; }
    public void setStatus(String v)                   { this.status = v; }
    public String getAtualizadoPor()                  { return atualizadoPor; }
    public void setAtualizadoPor(String v)            { this.atualizadoPor = v; }
    public OffsetDateTime getAtualizadoEm()           { return atualizadoEm; }
}
