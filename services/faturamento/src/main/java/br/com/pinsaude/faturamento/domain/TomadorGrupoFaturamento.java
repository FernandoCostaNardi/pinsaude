package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomador_grupos_faturamento", schema = "faturamento")
public class TomadorGrupoFaturamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "servico_lc116_id", nullable = false)
    private UUID servicoLc116Id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao_nota", nullable = false, columnDefinition = "text")
    private String descricaoNota;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                     { return id; }
    public UUID getTomadorId()              { return tomadorId; }
    public void setTomadorId(UUID v)        { this.tomadorId = v; }
    public UUID getServicoLc116Id()         { return servicoLc116Id; }
    public void setServicoLc116Id(UUID v)   { this.servicoLc116Id = v; }
    public String getNome()                 { return nome; }
    public void setNome(String v)           { this.nome = v; }
    public String getDescricaoNota()        { return descricaoNota; }
    public void setDescricaoNota(String v)  { this.descricaoNota = v; }
    public int getOrdem()                   { return ordem; }
    public void setOrdem(int v)             { this.ordem = v; }
    public boolean isAtivo()               { return ativo; }
    public void setAtivo(boolean v)        { this.ativo = v; }
    public OffsetDateTime getCreatedAt()    { return createdAt; }
}
