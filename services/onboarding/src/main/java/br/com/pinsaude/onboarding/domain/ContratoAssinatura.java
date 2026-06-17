package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contratos_assinatura", schema = "onboarding")
public class ContratoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "documento_key", length = 500)
    private String documentoKey;

    @Column(name = "signatario_key", length = 500)
    private String signatarioKey;

    @Column(name = "link_assinatura", length = 2000)
    private String linkAssinatura;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "AGUARDANDO_ENVIO";

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;

    @Column(name = "assinado_em")
    private OffsetDateTime assinadoEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }
    public String getDocumentoKey() { return documentoKey; }
    public void setDocumentoKey(String documentoKey) { this.documentoKey = documentoKey; }
    public String getSignatarioKey() { return signatarioKey; }
    public void setSignatarioKey(String signatarioKey) { this.signatarioKey = signatarioKey; }
    public String getLinkAssinatura() { return linkAssinatura; }
    public void setLinkAssinatura(String linkAssinatura) { this.linkAssinatura = linkAssinatura; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getEnviadoEm() { return enviadoEm; }
    public void setEnviadoEm(OffsetDateTime enviadoEm) { this.enviadoEm = enviadoEm; }
    public OffsetDateTime getAssinadoEm() { return assinadoEm; }
    public void setAssinadoEm(OffsetDateTime assinadoEm) { this.assinadoEm = assinadoEm; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
