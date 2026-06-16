package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dados_bancarios_medico", schema = "onboarding")
public class DadosBancariosMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pix")
    @ColumnTransformer(write = "?::onboarding.tipo_pix_enum")
    private TipoPix tipoPix;

    @Column(name = "chave_pix_criptografada")
    private byte[] chavePIXCriptografada;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cpfs_adicionais_split", columnDefinition = "jsonb")
    private String cpfsAdicionaisSplit;

    // PIX | TED — VARCHAR para evitar cast issue do Hibernate 6 com enum PG
    @Column(name = "tipo_recebimento", nullable = false)
    private String tipoRecebimento = "PIX";

    @Column(name = "banco_codigo", length = 10)
    private String bancoCodigo;

    @Column(name = "banco_nome", length = 100)
    private String bancoNome;

    @Column(name = "agencia", length = 10)
    private String agencia;

    @Column(name = "conta", length = 20)
    private String conta;

    // CORRENTE | POUPANCA
    @Column(name = "tipo_conta", length = 10)
    private String tipoConta;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }

    public TipoPix getTipoPix() { return tipoPix; }
    public void setTipoPix(TipoPix tipoPix) { this.tipoPix = tipoPix; }

    public byte[] getChavePIXCriptografada() { return chavePIXCriptografada; }
    public void setChavePIXCriptografada(byte[] chavePIXCriptografada) { this.chavePIXCriptografada = chavePIXCriptografada; }

    public String getCpfsAdicionaisSplit() { return cpfsAdicionaisSplit; }
    public void setCpfsAdicionaisSplit(String cpfsAdicionaisSplit) { this.cpfsAdicionaisSplit = cpfsAdicionaisSplit; }

    public String getTipoRecebimento() { return tipoRecebimento; }
    public void setTipoRecebimento(String tipoRecebimento) { this.tipoRecebimento = tipoRecebimento; }

    public String getBancoCodigo() { return bancoCodigo; }
    public void setBancoCodigo(String bancoCodigo) { this.bancoCodigo = bancoCodigo; }

    public String getBancoNome() { return bancoNome; }
    public void setBancoNome(String bancoNome) { this.bancoNome = bancoNome; }

    public String getAgencia() { return agencia; }
    public void setAgencia(String agencia) { this.agencia = agencia; }

    public String getConta() { return conta; }
    public void setConta(String conta) { this.conta = conta; }

    public String getTipoConta() { return tipoConta; }
    public void setTipoConta(String tipoConta) { this.tipoConta = tipoConta; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
