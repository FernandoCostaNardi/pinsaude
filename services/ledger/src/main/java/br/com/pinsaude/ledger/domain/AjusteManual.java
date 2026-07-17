package br.com.pinsaude.ledger.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Solicitação de ajuste contábil com dupla aprovação. Enquanto PENDENTE, nenhum lançamento
 * é gerado; ao ser APROVADO por um segundo usuário (de perfil diferente), o lançamento
 * imutável é criado e {@link #lancamentoId} é preenchido. Tabela mutável (workflow).
 */
@Entity
@Table(name = "ajustes_manuais", schema = "ledger")
public class AjusteManual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id_tenant", nullable = false, length = 14, updatable = false)
    private String cnpjIdTenant;

    @Column(name = "medico_id", updatable = false)
    private UUID medicoId;

    @Column(name = "competencia", nullable = false, length = 7, updatable = false)
    private String competencia;

    @Column(name = "conta_debito_codigo", nullable = false, length = 20, updatable = false)
    private String contaDebitoCodigo;

    @Column(name = "conta_credito_codigo", nullable = false, length = 20, updatable = false)
    private String contaCreditoCodigo;

    @Column(name = "valor_centavos", nullable = false, updatable = false)
    private long valorCentavos;

    @Column(name = "motivo", nullable = false, updatable = false)
    private String motivo;

    @Column(name = "solicitante_id", nullable = false, length = 100, updatable = false)
    private String solicitanteId;

    @Column(name = "solicitante_perfil", nullable = false, length = 20, updatable = false)
    private String solicitantePerfil;

    @Column(name = "aprovador_id", length = 100)
    private String aprovadorId;

    @Column(name = "aprovador_perfil", length = 20)
    private String aprovadorPerfil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusAjuste status = StatusAjuste.PENDENTE;

    @Column(name = "lancamento_id")
    private UUID lancamentoId;

    @Column(name = "motivo_rejeicao")
    private String motivoRejeicao;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    protected AjusteManual() {}

    public AjusteManual(String cnpjIdTenant, UUID medicoId, String competencia,
                        String contaDebitoCodigo, String contaCreditoCodigo, long valorCentavos,
                        String motivo, String solicitanteId, String solicitantePerfil) {
        this.cnpjIdTenant = cnpjIdTenant;
        this.medicoId = medicoId;
        this.competencia = competencia;
        this.contaDebitoCodigo = contaDebitoCodigo;
        this.contaCreditoCodigo = contaCreditoCodigo;
        this.valorCentavos = valorCentavos;
        this.motivo = motivo;
        this.solicitanteId = solicitanteId;
        this.solicitantePerfil = solicitantePerfil;
    }

    public void aprovar(String aprovadorId, String aprovadorPerfil, UUID lancamentoId) {
        this.aprovadorId = aprovadorId;
        this.aprovadorPerfil = aprovadorPerfil;
        this.lancamentoId = lancamentoId;
        this.status = StatusAjuste.APROVADO;
        this.decidedAt = OffsetDateTime.now();
    }

    public void rejeitar(String aprovadorId, String aprovadorPerfil, String motivoRejeicao) {
        this.aprovadorId = aprovadorId;
        this.aprovadorPerfil = aprovadorPerfil;
        this.motivoRejeicao = motivoRejeicao;
        this.status = StatusAjuste.REJEITADO;
        this.decidedAt = OffsetDateTime.now();
    }

    public UUID getId()                 { return id; }
    public String getCnpjIdTenant()     { return cnpjIdTenant; }
    public UUID getMedicoId()           { return medicoId; }
    public String getCompetencia()      { return competencia; }
    public String getContaDebitoCodigo(){ return contaDebitoCodigo; }
    public String getContaCreditoCodigo(){ return contaCreditoCodigo; }
    public long getValorCentavos()      { return valorCentavos; }
    public String getMotivo()           { return motivo; }
    public String getSolicitanteId()    { return solicitanteId; }
    public String getSolicitantePerfil(){ return solicitantePerfil; }
    public String getAprovadorId()      { return aprovadorId; }
    public String getAprovadorPerfil()  { return aprovadorPerfil; }
    public StatusAjuste getStatus()     { return status; }
    public UUID getLancamentoId()       { return lancamentoId; }
    public String getMotivoRejeicao()   { return motivoRejeicao; }
    public OffsetDateTime getCreatedAt(){ return createdAt; }
    public OffsetDateTime getDecidedAt(){ return decidedAt; }
}
