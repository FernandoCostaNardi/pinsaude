package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "declaracoes_lgpd_medico", schema = "onboarding")
public class DeclaracoesLgpdMedico {

    @Id
    @Column(name = "medico_id")
    private UUID medicoId;

    @Column(name = "aceite_declaracao_veracidade", nullable = false)
    private boolean aceiteDeclaracaoVeracidade = false;

    @Column(name = "autorizacao_uso_dados", nullable = false)
    private boolean autorizacaoUsoDados = false;

    @Column(name = "autorizacao_compartilhamento", nullable = false)
    private boolean autorizacaoCompartilhamento = false;

    @Column(name = "aviso_privacidade_lido", nullable = false)
    private boolean avisoPrivacidadeLido = false;

    @Column(name = "assinatura_nome", length = 200)
    private String assinaturaNome;

    @Column(name = "assinado_em")
    private OffsetDateTime assinadoEm;

    @Column(name = "ip_origem", length = 45)
    private String ipOrigem;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public DeclaracoesLgpdMedico() {}

    public DeclaracoesLgpdMedico(UUID medicoId) {
        this.medicoId = medicoId;
    }

    public boolean isCompleto() {
        return aceiteDeclaracaoVeracidade && autorizacaoUsoDados
            && autorizacaoCompartilhamento && avisoPrivacidadeLido;
    }

    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }

    public boolean isAceiteDeclaracaoVeracidade() { return aceiteDeclaracaoVeracidade; }
    public void setAceiteDeclaracaoVeracidade(boolean aceiteDeclaracaoVeracidade) {
        this.aceiteDeclaracaoVeracidade = aceiteDeclaracaoVeracidade;
    }

    public boolean isAutorizacaoUsoDados() { return autorizacaoUsoDados; }
    public void setAutorizacaoUsoDados(boolean autorizacaoUsoDados) {
        this.autorizacaoUsoDados = autorizacaoUsoDados;
    }

    public boolean isAutorizacaoCompartilhamento() { return autorizacaoCompartilhamento; }
    public void setAutorizacaoCompartilhamento(boolean autorizacaoCompartilhamento) {
        this.autorizacaoCompartilhamento = autorizacaoCompartilhamento;
    }

    public boolean isAvisoPrivacidadeLido() { return avisoPrivacidadeLido; }
    public void setAvisoPrivacidadeLido(boolean avisoPrivacidadeLido) {
        this.avisoPrivacidadeLido = avisoPrivacidadeLido;
    }

    public String getAssinaturaNome() { return assinaturaNome; }
    public void setAssinaturaNome(String assinaturaNome) { this.assinaturaNome = assinaturaNome; }

    public OffsetDateTime getAssinadoEm() { return assinadoEm; }
    public void setAssinadoEm(OffsetDateTime assinadoEm) { this.assinadoEm = assinadoEm; }

    public String getIpOrigem() { return ipOrigem; }
    public void setIpOrigem(String ipOrigem) { this.ipOrigem = ipOrigem; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
