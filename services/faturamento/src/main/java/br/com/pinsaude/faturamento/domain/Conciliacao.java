package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "conciliacoes", schema = "faturamento")
public class Conciliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lancamento_extrato_id", nullable = false)
    private UUID lancamentoExtratoId;

    @Column(name = "nota_id", nullable = false)
    private UUID notaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_match", nullable = false)
    @ColumnTransformer(write = "?::faturamento.tipo_match_enum")
    private TipoMatchEnum tipoMatch;

    @Column(name = "score_confianca")
    private int scoreConfianca;

    @Column(name = "data_conciliacao")
    private OffsetDateTime dataConciliacao;

    @Column(name = "usuario_id")
    private String usuarioId;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (dataConciliacao == null) dataConciliacao = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getLancamentoExtratoId() { return lancamentoExtratoId; }
    public void setLancamentoExtratoId(UUID lancamentoExtratoId) { this.lancamentoExtratoId = lancamentoExtratoId; }
    public UUID getNotaId() { return notaId; }
    public void setNotaId(UUID notaId) { this.notaId = notaId; }
    public TipoMatchEnum getTipoMatch() { return tipoMatch; }
    public void setTipoMatch(TipoMatchEnum tipoMatch) { this.tipoMatch = tipoMatch; }
    public int getScoreConfianca() { return scoreConfianca; }
    public void setScoreConfianca(int scoreConfianca) { this.scoreConfianca = scoreConfianca; }
    public OffsetDateTime getDataConciliacao() { return dataConciliacao; }
    public void setDataConciliacao(OffsetDateTime dataConciliacao) { this.dataConciliacao = dataConciliacao; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
