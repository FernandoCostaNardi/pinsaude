package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "frequencia_itens", schema = "faturamento")
public class FrequenciaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "frequencia_id", nullable = false)
    private UUID frequenciaId;

    @Column(name = "modalidade_id", nullable = false)
    private UUID modalidadeId;

    @Column(name = "data_execucao", nullable = false)
    private LocalDate dataExecucao;

    @Column(name = "ocorrencia", length = 120)
    private String ocorrencia;

    // Ocorrência escolhida do catálogo (opcional — pode coexistir com o texto livre acima,
    // ou nenhum dos dois). ocorrenciaValorCentavos é o snapshot do valor calculado no momento
    // do lançamento (ver FrequenciaService.calcularValorOcorrencia).
    @Column(name = "ocorrencia_id")
    private UUID ocorrenciaId;

    @Column(name = "ocorrencia_valor_centavos")
    private Long ocorrenciaValorCentavos;

    // Horas trabalhadas neste lançamento — calculado a partir de horaInicio/horaFim para
    // modalidade DIARISTA (ver FrequenciaService.calcularHorasTrabalhadas). NULL para PLANTONISTA.
    @Column(name = "horas_trabalhadas", precision = 6, scale = 2)
    private BigDecimal horasTrabalhadas;

    // Horário de entrada/saída digitado pelo médico no lançamento (só modalidade DIARISTA —
    // PLANTONISTA usa o turno/horário já cadastrados na modalidade). horasTrabalhadas acima é
    // sempre derivado destes dois campos, nunca informado diretamente pelo cliente.
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fim")
    private LocalTime horaFim;

    // Snapshot dos preços da modalidade no momento do lançamento
    @Column(name = "valor_unitario_centavos", nullable = false)
    private long valorUnitarioCentavos;

    @Column(name = "deslocamento_centavos", nullable = false)
    private long deslocamentoCentavos;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                          { return id; }
    public UUID getFrequenciaId()                { return frequenciaId; }
    public void setFrequenciaId(UUID v)         { this.frequenciaId = v; }
    public UUID getModalidadeId()                { return modalidadeId; }
    public void setModalidadeId(UUID v)         { this.modalidadeId = v; }
    public LocalDate getDataExecucao()           { return dataExecucao; }
    public void setDataExecucao(LocalDate v)    { this.dataExecucao = v; }
    public String getOcorrencia()                { return ocorrencia; }
    public void setOcorrencia(String v)         { this.ocorrencia = v; }
    public UUID getOcorrenciaId()                { return ocorrenciaId; }
    public void setOcorrenciaId(UUID v)         { this.ocorrenciaId = v; }
    public Long getOcorrenciaValorCentavos()     { return ocorrenciaValorCentavos; }
    public void setOcorrenciaValorCentavos(Long v) { this.ocorrenciaValorCentavos = v; }
    public BigDecimal getHorasTrabalhadas()      { return horasTrabalhadas; }
    public void setHorasTrabalhadas(BigDecimal v) { this.horasTrabalhadas = v; }
    public LocalTime getHoraInicio()             { return horaInicio; }
    public void setHoraInicio(LocalTime v)      { this.horaInicio = v; }
    public LocalTime getHoraFim()                { return horaFim; }
    public void setHoraFim(LocalTime v)         { this.horaFim = v; }
    public long getValorUnitarioCentavos()       { return valorUnitarioCentavos; }
    public void setValorUnitarioCentavos(long v){ this.valorUnitarioCentavos = v; }
    public long getDeslocamentoCentavos()        { return deslocamentoCentavos; }
    public void setDeslocamentoCentavos(long v) { this.deslocamentoCentavos = v; }
    public OffsetDateTime getCreatedAt()         { return createdAt; }
}
