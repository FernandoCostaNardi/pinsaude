package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Pessoa de contato de um tomador (ex.: apelido "Financeiro", nome "Fernando"). Um tomador
// pode ter vários. Todo contato precisa ter e-mail ou telefone. Ver V51.
@Entity
@Table(name = "tomador_contatos", schema = "faturamento")
public class TomadorContato {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "apelido", nullable = false, length = 80)
    private String apelido;

    @Column(name = "nome", length = 150)
    private String nome;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "observacao", columnDefinition = "text")
    private String observacao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                     { return id; }
    public UUID getTomadorId()              { return tomadorId; }
    public void setTomadorId(UUID v)        { this.tomadorId = v; }
    public String getApelido()              { return apelido; }
    public void setApelido(String v)        { this.apelido = v; }
    public String getNome()                 { return nome; }
    public void setNome(String v)           { this.nome = v; }
    public String getEmail()                { return email; }
    public void setEmail(String v)          { this.email = v; }
    public String getTelefone()             { return telefone; }
    public void setTelefone(String v)       { this.telefone = v; }
    public String getObservacao()           { return observacao; }
    public void setObservacao(String v)     { this.observacao = v; }
    public OffsetDateTime getCreatedAt()    { return createdAt; }
}
