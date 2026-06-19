package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "historico_regras_equiparacao", schema = "fiscal")
public class HistoricoRegraEquiparacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "regra_id", nullable = false)
    private UUID regraId;

    @Column(name = "campo_alterado", nullable = false, length = 100)
    private String campoAlterado;

    @Column(name = "valor_anterior", columnDefinition = "text")
    private String valorAnterior;

    @Column(name = "valor_novo", columnDefinition = "text")
    private String valorNovo;

    @Column(name = "alterado_por", length = 100)
    private String alteradoPor;

    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;

    @PrePersist
    void prePersist() {
        if (alteradoEm == null) alteradoEm = OffsetDateTime.now();
    }

    public UUID getId()                  { return id; }
    public UUID getRegraId()             { return regraId; }
    public void setRegraId(UUID v)       { this.regraId = v; }
    public String getCampoAlterado()     { return campoAlterado; }
    public void setCampoAlterado(String v) { this.campoAlterado = v; }
    public String getValorAnterior()     { return valorAnterior; }
    public void setValorAnterior(String v) { this.valorAnterior = v; }
    public String getValorNovo()         { return valorNovo; }
    public void setValorNovo(String v)   { this.valorNovo = v; }
    public String getAlteradoPor()       { return alteradoPor; }
    public void setAlteradoPor(String v) { this.alteradoPor = v; }
    public OffsetDateTime getAlteradoEm() { return alteradoEm; }
}
