package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Vínculo N:N entre Setor Operacional e Modalidade de referência — um setor pode ter mais de uma
// modalidade cadastrada (ex: duas modalidades Diarista com valores diferentes, ou Plantonista +
// Diarista simultâneos). Quando há mais de uma, a tela de Nova Frequência pergunta qual usar.
// Ver V39__setor_modalidades_multiplas.sql.
@Entity
@Table(name = "setor_operacional_modalidades", schema = "faturamento")
public class SetorOperacionalModalidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "setor_id", nullable = false)
    private UUID setorId;

    @Column(name = "modalidade_id", nullable = false)
    private UUID modalidadeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                  { return id; }
    public UUID getSetorId()             { return setorId; }
    public void setSetorId(UUID v)       { this.setorId = v; }
    public UUID getModalidadeId()        { return modalidadeId; }
    public void setModalidadeId(UUID v)  { this.modalidadeId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
