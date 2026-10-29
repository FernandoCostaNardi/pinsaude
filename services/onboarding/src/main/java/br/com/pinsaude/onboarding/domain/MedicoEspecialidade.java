package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medico_especialidades", schema = "onboarding")
public class MedicoEspecialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "especialidade_id", nullable = false)
    private UUID especialidadeId;

    // 1 = principal (espelhada em medicos.especialidade)
    @Column(name = "ordem", nullable = false)
    private short ordem;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public MedicoEspecialidade() {}

    public MedicoEspecialidade(UUID medicoId, UUID especialidadeId, short ordem) {
        this.medicoId = medicoId;
        this.especialidadeId = especialidadeId;
        this.ordem = ordem;
    }

    public UUID getId() { return id; }
    public UUID getMedicoId() { return medicoId; }
    public UUID getEspecialidadeId() { return especialidadeId; }
    public short getOrdem() { return ordem; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
