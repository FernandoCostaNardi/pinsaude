package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documentos_empresa", schema = "onboarding")
public class DocumentoEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    @ColumnTransformer(write = "?::onboarding.tipo_documento_empresa_enum")
    private TipoDocumentoEmpresa tipo;

    @Column(name = "nome_arquivo", nullable = false, length = 500)
    private String nomeArquivo;

    @Column(name = "caminho_storage", nullable = false, length = 1000)
    private String caminhoStorage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_validacao", nullable = false)
    @ColumnTransformer(write = "?::onboarding.status_validacao_documento_enum")
    private StatusValidacaoDocumento statusValidacao = StatusValidacaoDocumento.PENDENTE;

    @Column(name = "motivo_reprovacao")
    private String motivoReprovacao;

    @Column(name = "versao_atual", nullable = false)
    private boolean versaoAtual = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmpresaId() { return empresaId; }
    public void setEmpresaId(UUID empresaId) { this.empresaId = empresaId; }

    public TipoDocumentoEmpresa getTipo() { return tipo; }
    public void setTipo(TipoDocumentoEmpresa tipo) { this.tipo = tipo; }

    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }

    public String getCaminhoStorage() { return caminhoStorage; }
    public void setCaminhoStorage(String caminhoStorage) { this.caminhoStorage = caminhoStorage; }

    public StatusValidacaoDocumento getStatusValidacao() { return statusValidacao; }
    public void setStatusValidacao(StatusValidacaoDocumento statusValidacao) { this.statusValidacao = statusValidacao; }

    public String getMotivoReprovacao() { return motivoReprovacao; }
    public void setMotivoReprovacao(String motivoReprovacao) { this.motivoReprovacao = motivoReprovacao; }

    public boolean isVersaoAtual() { return versaoAtual; }
    public void setVersaoAtual(boolean versaoAtual) { this.versaoAtual = versaoAtual; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
