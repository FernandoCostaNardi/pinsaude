package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "convites_medico", schema = "onboarding")
public class ConviteMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "token", nullable = false, unique = true, length = 200)
    private String token;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDENTE";

    @Column(name = "email_destino", length = 200)
    private String emailDestino;

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;

    @Column(name = "expira_em")
    private OffsetDateTime expiraEm;

    @Column(name = "utilizado_em")
    private OffsetDateTime utilizadoEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEmailDestino() { return emailDestino; }
    public void setEmailDestino(String emailDestino) { this.emailDestino = emailDestino; }
    public OffsetDateTime getEnviadoEm() { return enviadoEm; }
    public void setEnviadoEm(OffsetDateTime enviadoEm) { this.enviadoEm = enviadoEm; }
    public OffsetDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(OffsetDateTime expiraEm) { this.expiraEm = expiraEm; }
    public OffsetDateTime getUtilizadoEm() { return utilizadoEm; }
    public void setUtilizadoEm(OffsetDateTime utilizadoEm) { this.utilizadoEm = utilizadoEm; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
