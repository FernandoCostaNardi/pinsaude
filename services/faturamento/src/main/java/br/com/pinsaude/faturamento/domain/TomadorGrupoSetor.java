package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Vínculo N:N entre Grupo de Faturamento e Serviço Operacional (setor) — permite que o mesmo
// setor (ex: "Emergência Cardiológica") seja reutilizado em quantos grupos forem necessários,
// sem recadastrar. Ver V35__setores_operacionais_catalogo_multi_grupo.sql.
@Entity
@Table(name = "tomador_grupo_setores", schema = "faturamento")
public class TomadorGrupoSetor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "setor_id", nullable = false)
    private UUID setorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                  { return id; }
    public UUID getGrupoId()             { return grupoId; }
    public void setGrupoId(UUID v)       { this.grupoId = v; }
    public UUID getSetorId()             { return setorId; }
    public void setSetorId(UUID v)       { this.setorId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
