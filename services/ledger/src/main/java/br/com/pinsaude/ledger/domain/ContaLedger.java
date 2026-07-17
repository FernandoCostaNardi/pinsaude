package br.com.pinsaude.ledger.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contas_ledger", schema = "ledger")
public class ContaLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "codigo", nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 15)
    private TipoConta tipo;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected ContaLedger() {}

    public UUID getId()                { return id; }
    public String getCodigo()          { return codigo; }
    public String getNome()            { return nome; }
    public TipoConta getTipo()         { return tipo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
