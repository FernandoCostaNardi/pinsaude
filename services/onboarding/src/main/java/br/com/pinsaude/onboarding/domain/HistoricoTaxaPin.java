package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "historico_taxa_pin", schema = "onboarding")
public class HistoricoTaxaPin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "taxa_anterior", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxaAnterior;

    @Column(name = "taxa_nova", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxaNova;

    @Column(name = "alterado_por", length = 200)
    private String alteradoPor;

    @CreationTimestamp
    @Column(name = "alterado_em", nullable = false, updatable = false)
    private OffsetDateTime alteradoEm;

    public HistoricoTaxaPin() {}

    public HistoricoTaxaPin(UUID medicoId, BigDecimal taxaAnterior, BigDecimal taxaNova, String alteradoPor) {
        this.medicoId    = medicoId;
        this.taxaAnterior = taxaAnterior;
        this.taxaNova    = taxaNova;
        this.alteradoPor = alteradoPor;
    }

    public UUID getId()                     { return id; }
    public UUID getMedicoId()               { return medicoId; }
    public BigDecimal getTaxaAnterior()     { return taxaAnterior; }
    public BigDecimal getTaxaNova()         { return taxaNova; }
    public String getAlteradoPor()          { return alteradoPor; }
    public OffsetDateTime getAlteradoEm()   { return alteradoEm; }
}
