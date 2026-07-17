package br.com.pinsaude.ledger.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Partida (débito ou crédito) de um lançamento. valor_centavos é sempre > 0 —
 * o sinal contábil é dado por {@link #tipo}. Append-only (UPDATE/DELETE bloqueados no banco).
 */
@Entity
@Table(name = "partidas_ledger", schema = "ledger")
public class PartidaLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lancamento_id", nullable = false, updatable = false)
    private LancamentoLedger lancamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false, updatable = false)
    private ContaLedger conta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10, updatable = false)
    private TipoPartida tipo;

    @Column(name = "valor_centavos", nullable = false, updatable = false)
    private long valorCentavos;

    protected PartidaLedger() {}

    public PartidaLedger(ContaLedger conta, TipoPartida tipo, long valorCentavos) {
        this.conta = conta;
        this.tipo = tipo;
        this.valorCentavos = valorCentavos;
    }

    void setLancamento(LancamentoLedger lancamento) {
        this.lancamento = lancamento;
    }

    public UUID getId()                { return id; }
    public LancamentoLedger getLancamento() { return lancamento; }
    public ContaLedger getConta()      { return conta; }
    public TipoPartida getTipo()       { return tipo; }
    public long getValorCentavos()     { return valorCentavos; }
}
