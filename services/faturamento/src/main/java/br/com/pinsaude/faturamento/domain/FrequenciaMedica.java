package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "frequencias_medicas", schema = "faturamento")
public class FrequenciaMedica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id_tenant", nullable = false, length = 20)
    private String cnpjIdTenant;

    @Column(name = "tomador_id", nullable = false)
    private UUID tomadorId;

    @Column(name = "medico_id", nullable = false)
    private UUID medicoId;

    @Column(name = "servico_operacional_id", nullable = false)
    private UUID servicoOperacionalId;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    @Column(name = "especialidade", length = 120)
    private String especialidade;

    @Column(name = "tipo_medico", length = 20)
    private String tipoMedico;

    // Escolhidas uma única vez na criação da frequência (PINSAUDE-13.26) — nullable pra dado
    // legado (frequências criadas antes dessa mudança, algumas com modalidades variadas entre
    // os itens já lançados). Quando não nulo, todo item lançado usa sempre este valor, ignorando
    // qualquer modalidade/ocorrência vinda do request do item (ver FrequenciaService).
    @Column(name = "modalidade_id")
    private UUID modalidadeId;

    @Column(name = "ocorrencia_id")
    private UUID ocorrenciaId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "RASCUNHO";

    @Column(name = "documento_assinado_key", length = 500)
    private String documentoAssinadoKey;

    @Column(name = "enviada_tomador_em")
    private OffsetDateTime enviadaTomadorEm;

    @Column(name = "fechamento_id")
    private UUID fechamentoId;

    @Column(name = "producao_id")
    private UUID producaoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId()                            { return id; }
    public String getCnpjIdTenant()               { return cnpjIdTenant; }
    public void setCnpjIdTenant(String v)         { this.cnpjIdTenant = v; }
    public UUID getTomadorId()                     { return tomadorId; }
    public void setTomadorId(UUID v)              { this.tomadorId = v; }
    public UUID getMedicoId()                      { return medicoId; }
    public void setMedicoId(UUID v)               { this.medicoId = v; }
    public UUID getServicoOperacionalId()          { return servicoOperacionalId; }
    public void setServicoOperacionalId(UUID v)   { this.servicoOperacionalId = v; }
    public String getCompetencia()                 { return competencia; }
    public void setCompetencia(String v)          { this.competencia = v; }
    public String getEspecialidade()               { return especialidade; }
    public void setEspecialidade(String v)        { this.especialidade = v; }
    public String getTipoMedico()                  { return tipoMedico; }
    public void setTipoMedico(String v)           { this.tipoMedico = v; }
    public UUID getModalidadeId()                  { return modalidadeId; }
    public void setModalidadeId(UUID v)           { this.modalidadeId = v; }
    public UUID getOcorrenciaId()                  { return ocorrenciaId; }
    public void setOcorrenciaId(UUID v)           { this.ocorrenciaId = v; }
    public String getStatus()                      { return status; }
    public void setStatus(String v)               { this.status = v; }
    public String getDocumentoAssinadoKey()        { return documentoAssinadoKey; }
    public void setDocumentoAssinadoKey(String v) { this.documentoAssinadoKey = v; }
    public OffsetDateTime getEnviadaTomadorEm()    { return enviadaTomadorEm; }
    public void setEnviadaTomadorEm(OffsetDateTime v) { this.enviadaTomadorEm = v; }
    public UUID getFechamentoId()                  { return fechamentoId; }
    public void setFechamentoId(UUID v)           { this.fechamentoId = v; }
    public UUID getProducaoId()                    { return producaoId; }
    public void setProducaoId(UUID v)             { this.producaoId = v; }
    public OffsetDateTime getCreatedAt()           { return createdAt; }
}
