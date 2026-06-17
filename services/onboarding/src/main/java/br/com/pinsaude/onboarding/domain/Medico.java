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
@Table(name = "medicos", schema = "onboarding")
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cpf_criptografado", nullable = false)
    private byte[] cpfCriptografado;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "crm", nullable = false, length = 20)
    private String crm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "crm_uf", nullable = false, columnDefinition = "char(2)")
    private String crmUf;

    @Column(name = "especialidade", length = 100)
    private String especialidade;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnTransformer(write = "?::onboarding.status_medico_enum")
    private StatusMedico status = StatusMedico.RASCUNHO;

    @Column(name = "status_junta_comercial", nullable = false, length = 20)
    private String statusJuntaComercial = "AGUARDANDO";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public byte[] getCpfCriptografado() { return cpfCriptografado; }
    public void setCpfCriptografado(byte[] cpfCriptografado) { this.cpfCriptografado = cpfCriptografado; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCrm() { return crm; }
    public void setCrm(String crm) { this.crm = crm; }

    public String getCrmUf() { return crmUf; }
    public void setCrmUf(String crmUf) { this.crmUf = crmUf; }

    public String getEspecialidade() { return especialidade; }
    public void setEspecialidade(String especialidade) { this.especialidade = especialidade; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public StatusMedico getStatus() { return status; }
    public void setStatus(StatusMedico status) { this.status = status; }

    public String getStatusJuntaComercial() { return statusJuntaComercial; }
    public void setStatusJuntaComercial(String statusJuntaComercial) { this.statusJuntaComercial = statusJuntaComercial; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
