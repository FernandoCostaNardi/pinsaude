package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medico_crms", schema = "onboarding")
public class MedicoCrm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "numero", nullable = false, length = 20)
    private String numero;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uf", nullable = false, columnDefinition = "char(2)")
    private String uf;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public MedicoCrm() {}

    public MedicoCrm(UUID medicoId, String numero, String uf, boolean principal) {
        this.medicoId = medicoId;
        this.numero = numero;
        this.uf = uf;
        this.principal = principal;
    }

    public UUID getId() { return id; }
    public UUID getMedicoId() { return medicoId; }
    public String getNumero() { return numero; }
    public String getUf() { return uf; }
    public boolean isPrincipal() { return principal; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
