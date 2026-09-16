package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Vínculo N:N entre Setor Operacional e Ocorrência — pedido do cliente pra restringir quais
// ocorrências do catálogo do tomador (aba "Ocorrências") são sugeridas em cada setor na tela de
// Frequência. Uma ocorrência SEM nenhuma linha aqui continua disponível para qualquer setor do
// tomador (bypass, ver V48__create_setor_operacional_ocorrencias.sql) — só passa a ser restrita
// quando pelo menos 1 vínculo é criado.
@Entity
@Table(name = "setor_operacional_ocorrencias", schema = "faturamento")
public class SetorOperacionalOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "setor_id", nullable = false)
    private UUID setorId;

    @Column(name = "ocorrencia_id", nullable = false)
    private UUID ocorrenciaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                  { return id; }
    public UUID getSetorId()             { return setorId; }
    public void setSetorId(UUID v)       { this.setorId = v; }
    public UUID getOcorrenciaId()        { return ocorrenciaId; }
    public void setOcorrenciaId(UUID v)  { this.ocorrenciaId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
