package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_servicos_operacionais", schema = "faturamento")
public class TomadorServicoOperacional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    // Categoria livre (ex: "Emergência", "UTI", "Ambulatório") — organiza o catálogo de setores
    // do tomador; nullable pra não quebrar setores cadastrados antes deste campo existir.
    @Column(name = "categoria", length = 100)
    private String categoria;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                  { return id; }
    public UUID getTomadorId()           { return tomadorId; }
    public void setTomadorId(UUID v)     { this.tomadorId = v; }
    public String getNome()              { return nome; }
    public void setNome(String v)        { this.nome = v; }
    public String getCategoria()         { return categoria; }
    public void setCategoria(String v)   { this.categoria = v; }
    public boolean isAtivo()            { return ativo; }
    public void setAtivo(boolean v)     { this.ativo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
