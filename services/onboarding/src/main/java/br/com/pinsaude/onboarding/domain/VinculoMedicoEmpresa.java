package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vinculos_medico_empresa", schema = "onboarding")
public class VinculoMedicoEmpresa {

    @EmbeddedId
    private VinculoMedicoEmpresaId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_societario", nullable = false)
    @ColumnTransformer(write = "?::onboarding.status_societario_enum")
    private StatusSocietario statusSocietario = StatusSocietario.ATIVO;

    @Column(name = "data_alteracao_junta")
    private LocalDate dataAlteracaoJunta;

    @Column(name = "data_ativacao")
    private LocalDate dataAtivacao;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public VinculoMedicoEmpresa() {}

    public VinculoMedicoEmpresa(VinculoMedicoEmpresaId id, StatusSocietario statusSocietario) {
        this.id = id;
        this.statusSocietario = statusSocietario;
    }

    public VinculoMedicoEmpresaId getId() { return id; }
    public void setId(VinculoMedicoEmpresaId id) { this.id = id; }

    public StatusSocietario getStatusSocietario() { return statusSocietario; }
    public void setStatusSocietario(StatusSocietario statusSocietario) { this.statusSocietario = statusSocietario; }

    public LocalDate getDataAlteracaoJunta() { return dataAlteracaoJunta; }
    public void setDataAlteracaoJunta(LocalDate dataAlteracaoJunta) { this.dataAlteracaoJunta = dataAlteracaoJunta; }

    public LocalDate getDataAtivacao() { return dataAtivacao; }
    public void setDataAtivacao(LocalDate dataAtivacao) { this.dataAtivacao = dataAtivacao; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
