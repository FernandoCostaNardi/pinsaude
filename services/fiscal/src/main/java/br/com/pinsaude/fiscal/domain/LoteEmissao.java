package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lotes_emissao", schema = "fiscal")
public class LoteEmissao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "emitidas", nullable = false)
    private int emitidas;

    @Column(name = "falhas", nullable = false)
    private int falhas;

    @Column(name = "em_processamento", nullable = false)
    private int emProcessamento;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "EM_ANDAMENTO";

    @Column(name = "iniciado_em", nullable = false)
    private OffsetDateTime iniciadoEm;

    @Column(name = "concluido_em")
    private OffsetDateTime concluidoEm;

    @PrePersist
    void prePersist() {
        if (iniciadoEm == null) iniciadoEm = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getEmitidas() { return emitidas; }
    public void setEmitidas(int emitidas) { this.emitidas = emitidas; }
    public int getFalhas() { return falhas; }
    public void setFalhas(int falhas) { this.falhas = falhas; }
    public int getEmProcessamento() { return emProcessamento; }
    public void setEmProcessamento(int emProcessamento) { this.emProcessamento = emProcessamento; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getIniciadoEm() { return iniciadoEm; }
    public OffsetDateTime getConcluidoEm() { return concluidoEm; }
    public void setConcluidoEm(OffsetDateTime concluidoEm) { this.concluidoEm = concluidoEm; }
}
