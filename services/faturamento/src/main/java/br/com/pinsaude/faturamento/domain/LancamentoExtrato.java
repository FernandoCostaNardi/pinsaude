package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lancamentos_extrato", schema = "faturamento")
public class LancamentoExtrato {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "extrato_id", nullable = false)
    private UUID extratoId;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    @Column(name = "descricao", nullable = false, columnDefinition = "TEXT")
    private String descricao;

    // centavos: positivo = crédito, negativo = débito
    @Column(name = "valor", nullable = false)
    private long valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    @ColumnTransformer(write = "?::faturamento.tipo_lancamento_enum")
    private TipoLancamentoExtrato tipo;

    @Column(name = "identificador_externo", length = 100)
    private String identificadorExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_conciliacao", nullable = false)
    @ColumnTransformer(write = "?::faturamento.status_conciliacao_enum")
    private StatusConciliacao statusConciliacao = StatusConciliacao.PENDENTE;

    @Column(name = "score_match")
    private int scoreMatch = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public UUID getExtratoId() { return extratoId; }
    public void setExtratoId(UUID extratoId) { this.extratoId = extratoId; }
    public LocalDate getDataLancamento() { return dataLancamento; }
    public void setDataLancamento(LocalDate dataLancamento) { this.dataLancamento = dataLancamento; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public long getValor() { return valor; }
    public void setValor(long valor) { this.valor = valor; }
    public TipoLancamentoExtrato getTipo() { return tipo; }
    public void setTipo(TipoLancamentoExtrato tipo) { this.tipo = tipo; }
    public String getIdentificadorExterno() { return identificadorExterno; }
    public void setIdentificadorExterno(String identificadorExterno) { this.identificadorExterno = identificadorExterno; }
    public StatusConciliacao getStatusConciliacao() { return statusConciliacao; }
    public void setStatusConciliacao(StatusConciliacao statusConciliacao) { this.statusConciliacao = statusConciliacao; }
    public int getScoreMatch() { return scoreMatch; }
    public void setScoreMatch(int scoreMatch) { this.scoreMatch = scoreMatch; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
