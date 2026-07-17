package br.com.pinsaude.ledger.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cabeçalho de um lançamento contábil. IMUTÁVEL (append-only) — o banco bloqueia
 * UPDATE/DELETE via trigger. Correções são feitas por novos lançamentos de AJUSTE.
 */
@Entity
@Table(name = "lancamentos_ledger", schema = "ledger")
public class LancamentoLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id_tenant", nullable = false, length = 14, updatable = false)
    private String cnpjIdTenant;

    @Column(name = "medico_id", updatable = false)
    private UUID medicoId;

    @Column(name = "data_lancamento", nullable = false, updatable = false)
    private LocalDate dataLancamento;

    @Column(name = "competencia", nullable = false, length = 7, updatable = false)
    private String competencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_origem", nullable = false, length = 15, updatable = false)
    private TipoOrigem tipoOrigem;

    @Column(name = "origem_id", updatable = false)
    private UUID origemId;

    @Column(name = "descricao", nullable = false, updatable = false)
    private String descricao;

    @Column(name = "correlation_id", nullable = false, length = 120, updatable = false)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "lancamento", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<PartidaLedger> partidas = new ArrayList<>();

    protected LancamentoLedger() {}

    public LancamentoLedger(String cnpjIdTenant, UUID medicoId, LocalDate dataLancamento,
                            String competencia, TipoOrigem tipoOrigem, UUID origemId,
                            String descricao, String correlationId) {
        this.cnpjIdTenant = cnpjIdTenant;
        this.medicoId = medicoId;
        this.dataLancamento = dataLancamento;
        this.competencia = competencia;
        this.tipoOrigem = tipoOrigem;
        this.origemId = origemId;
        this.descricao = descricao;
        this.correlationId = correlationId;
    }

    public void addPartida(PartidaLedger partida) {
        partida.setLancamento(this);
        this.partidas.add(partida);
    }

    public UUID getId()                  { return id; }
    public String getCnpjIdTenant()      { return cnpjIdTenant; }
    public UUID getMedicoId()            { return medicoId; }
    public LocalDate getDataLancamento() { return dataLancamento; }
    public String getCompetencia()       { return competencia; }
    public TipoOrigem getTipoOrigem()    { return tipoOrigem; }
    public UUID getOrigemId()            { return origemId; }
    public String getDescricao()         { return descricao; }
    public String getCorrelationId()     { return correlationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<PartidaLedger> getPartidas() { return partidas; }
}
