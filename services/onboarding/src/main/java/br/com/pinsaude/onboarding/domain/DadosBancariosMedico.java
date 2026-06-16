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

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
