package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checklist_conduta", schema = "onboarding")
public class ChecklistConduta {

    @Id
    @Column(name = "medico_id")
    private UUID medicoId;

    @Column(name = "numero_conselho_verificado", nullable = false)
    private boolean numeroConselhoVerificado = false;

    @Column(name = "registros_disciplinares", nullable = false)
    private boolean registrosDisciplinares = false;

    @Column(name = "processos_medicos", nullable = false)
    private boolean processosMedicos = false;

    @Column(name = "verificado_por", length = 200)
    private String verificadoPor;

    @Column(name = "verificado_em")
    private OffsetDateTime verificadoEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public ChecklistConduta() {}

    public ChecklistConduta(UUID medicoId) {
        this.medicoId = medicoId;
    }

    public boolean isCompleto() {
        return numeroConselhoVerificado && registrosDisciplinares && processosMedicos;
    }

    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }

    public boolean isNumeroConselhoVerificado() { return numeroConselhoVerificado; }
    public void setNumeroConselhoVerificado(boolean numeroConselhoVerificado) {
        this.numeroConselhoVerificado = numeroConselhoVerificado;
    }

    public boolean isRegistrosDisciplinares() { return registrosDisciplinares; }
    public void setRegistrosDisciplinares(boolean registrosDisciplinares) {
        this.registrosDisciplinares = registrosDisciplinares;
    }

    public boolean isProcessosMedicos() { return processosMedicos; }
    public void setProcessosMedicos(boolean processosMedicos) {
        this.processosMedicos = processosMedicos;
    }

    public String getVerificadoPor() { return verificadoPor; }
    public void setVerificadoPor(String verificadoPor) { this.verificadoPor = verificadoPor; }

    public OffsetDateTime getVerificadoEm() { return verificadoEm; }
    public void setVerificadoEm(OffsetDateTime verificadoEm) { this.verificadoEm = verificadoEm; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
