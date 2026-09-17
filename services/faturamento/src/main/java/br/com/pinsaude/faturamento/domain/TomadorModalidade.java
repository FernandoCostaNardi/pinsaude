package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "tomador_modalidades", schema = "faturamento")
public class TomadorModalidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    // Pedido do cliente (pós-EPIC-13.26): uma modalidade pode servir a mais de um Tipo de Escala,
    // desde que todos pertençam à mesma família de comportamento (nunca mistura fixa com
    // por-lançamento — ver TipoEscala.isModalidadeFixa). Evita cadastrar a mesma modalidade
    // (mesmo turno/horas ou horas_semanais, mesmo valor) uma vez por tipo quando os tipos têm
    // exatamente o mesmo formato de campos (ex: Diarista + Evolucionista). Ver
    // V42__tomador_modalidade_multiplos_tipos.sql.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tipos", columnDefinition = "text[]", nullable = false)
    private String[] tipos = new String[]{"PLANTONISTA"};

    @Column(name = "turno", length = 10)
    private String turno;

    @Column(name = "horario", length = 30)
    private String horario;

    @Column(name = "horas", precision = 6, scale = 2)
    private BigDecimal horas;

    // Dias da semana em que este turno pode ser lançado (ex: "só de segunda a sexta") — só
    // usado pelas modalidades "por lançamento" (que têm turno). Nulo/vazio = sem restrição,
    // disponível em qualquer dia (ver V50__add_dias_semana_tomador_modalidades.sql). Valores são
    // nomes de java.time.DayOfWeek.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dias_semana", columnDefinition = "text[]")
    private String[] diasSemana;

    // ─── Campo do tipo DIARISTA (carga horária semanal obrigatória) ─────────────
    @Column(name = "horas_semanais", precision = 6, scale = 2)
    private BigDecimal horasSemanais;

    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;

    @Column(name = "deslocamento_centavos", nullable = false)
    private long deslocamentoCentavos;

    // Ordem de exibição definida por drag-and-drop na aba Modalidades do modal de Faturamento
    // por Grupo — reflete direto no seletor de modalidade das telas de Frequência Médica, que
    // só consomem a ordem já vinda do GET, sem reordenar no cliente (ver TomadorService.
    // reordenarModalidades).
    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                           { return id; }
    public UUID getTomadorId()                    { return tomadorId; }
    public void setTomadorId(UUID v)              { this.tomadorId = v; }
    public String getNome()                       { return nome; }
    public void setNome(String v)                 { this.nome = v; }
    public String[] getTipos()                    { return tipos; }
    public void setTipos(String... v)             { this.tipos = v; }
    public String getTurno()                      { return turno; }
    public void setTurno(String v)                { this.turno = v; }
    public String getHorario()                    { return horario; }
    public void setHorario(String v)              { this.horario = v; }
    public BigDecimal getHoras()                  { return horas; }
    public void setHoras(BigDecimal v)            { this.horas = v; }
    public String[] getDiasSemana()               { return diasSemana; }
    public void setDiasSemana(String[] v)         { this.diasSemana = v; }
    public BigDecimal getHorasSemanais()          { return horasSemanais; }
    public void setHorasSemanais(BigDecimal v)    { this.horasSemanais = v; }
    public long getValorCentavos()                { return valorCentavos; }
    public void setValorCentavos(long v)          { this.valorCentavos = v; }
    public long getDeslocamentoCentavos()         { return deslocamentoCentavos; }
    public void setDeslocamentoCentavos(long v)   { this.deslocamentoCentavos = v; }
    public int getOrdem()                         { return ordem; }
    public void setOrdem(int v)                   { this.ordem = v; }
    public boolean isAtivo()                     { return ativo; }
    public void setAtivo(boolean v)              { this.ativo = v; }
    public OffsetDateTime getCreatedAt()          { return createdAt; }

    // Família de comportamento é homogênea dentro do array (garantido na validação de escrita,
    // TomadorService.aplicarCamposPorTipo) — qualquer elemento serve como representante.
    public boolean isFixa() {
        return tipos != null && tipos.length > 0 && TipoEscala.isModalidadeFixa(tipos[0]);
    }

    public boolean isServico() {
        return tipos != null && tipos.length > 0 && TipoEscala.isModalidadeServico(tipos[0]);
    }

    public boolean suportaTipo(String tipo) {
        return tipos != null && Arrays.asList(tipos).contains(tipo);
    }

    // Sem nenhum dia marcado = sem restrição, disponível em qualquer dia da semana.
    public boolean permiteDiaSemana(DayOfWeek dia) {
        return diasSemana == null || diasSemana.length == 0 || Arrays.asList(diasSemana).contains(dia.name());
    }
}
