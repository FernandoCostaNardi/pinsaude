package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Vínculo N:N entre um médico alocado a um tomador (medico_tomadores) e os Setores Operacionais
// em que ele atua ali — usado quando o tomador exige controle de frequência (Tomador.exigeFrequencia)
// para filtrar o combo de Setor no Portal do Médico ao criar uma nova competência.
// Ver V37__tomador_exige_frequencia_medico_setores.sql.
@Entity
@Table(name = "medico_tomador_setores", schema = "faturamento")
public class MedicoTomadorSetor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_tomador_id", nullable = false)
    private UUID medicoTomadorId;

    @Column(name = "setor_id", nullable = false)
    private UUID setorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                        { return id; }
    public UUID getMedicoTomadorId()            { return medicoTomadorId; }
    public void setMedicoTomadorId(UUID v)      { this.medicoTomadorId = v; }
    public UUID getSetorId()                    { return setorId; }
    public void setSetorId(UUID v)              { this.setorId = v; }
    public OffsetDateTime getCreatedAt()        { return createdAt; }
}
