package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "participacoes_producao", schema = "faturamento")
public class ParticipacaoProducao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "producao_id", nullable = false)
    private UUID producaoId;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "valor_bruto", nullable = false)
    private long valorBruto;

    @Column(name = "taxa_pin_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxaPinPct = new BigDecimal("0.1500");

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                          { return id; }
    public UUID getProducaoId()                  { return producaoId; }
    public void setProducaoId(UUID producaoId)   { this.producaoId = producaoId; }
    public UUID getMedicoId()                    { return medicoId; }
    public void setMedicoId(UUID medicoId)       { this.medicoId = medicoId; }
    public long getValorBruto()                        { return valorBruto; }
    public void setValorBruto(long valorBruto)         { this.valorBruto = valorBruto; }
    public BigDecimal getTaxaPinPct()                  { return taxaPinPct; }
    public void setTaxaPinPct(BigDecimal taxaPinPct)   { this.taxaPinPct = taxaPinPct; }
    public OffsetDateTime getCreatedAt()               { return createdAt; }
}
