package br.com.pinsaude.fiscal.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "retencoes_sofridas", schema = "fiscal",
       uniqueConstraints = @UniqueConstraint(columnNames = {"nota_id", "tipo_tributo"}))
public class RetencaoSofrida {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_id", nullable = false)
    private NotaFiscal nota;

    // VARCHAR+CHECK no banco (ISS/IR/CSLL/PIS/COFINS/INSS) — sem enum PostgreSQL nesta tabela
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tributo", nullable = false, length = 10)
    private TipoRetencao tipoTributo;

    @Column(name = "valor_retido", nullable = false)
    private Long valorRetido;   // centavos

    public UUID getId()                        { return id; }
    public NotaFiscal getNota()                { return nota; }
    public void setNota(NotaFiscal v)          { this.nota = v; }
    public TipoRetencao getTipoTributo()       { return tipoTributo; }
    public void setTipoTributo(TipoRetencao v) { this.tipoTributo = v; }
    public Long getValorRetido()               { return valorRetido; }
    public void setValorRetido(Long v)         { this.valorRetido = v; }
}
