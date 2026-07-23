package br.com.pinsaude.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dados_civis_medico", schema = "onboarding")
public class DadosCivisMedico {

    @Id
    @Column(name = "medico_id")
    private UUID medicoId;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "nacionalidade", length = 100)
    private String nacionalidade;

    @Column(name = "naturalidade", length = 150)
    private String naturalidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil")
    @ColumnTransformer(write = "?::onboarding.estado_civil_enum")
    private EstadoCivil estadoCivil;

    @Column(name = "nome_mae", length = 200)
    private String nomeMae;

    @Column(name = "nome_pai", length = 200)
    private String nomePai;

    @Column(name = "logradouro", length = 255)
    private String logradouro;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "complemento", length = 100)
    private String complemento;

    @Column(name = "bairro", length = 100)
    private String bairro;

    @Column(name = "cidade", length = 150)
    private String cidade;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uf", columnDefinition = "char(2)")
    private String uf;

    @Column(name = "cep", length = 9)
    private String cep;

    @Column(name = "rg_numero", length = 20)
    private String rgNumero;

    @Column(name = "rg_orgao_expedidor", length = 20)
    private String rgOrgaoExpedidor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "rg_uf", columnDefinition = "char(2)")
    private String rgUf;

    @Column(name = "rqe", length = 20)
    private String rqe;

    @Column(name = "canal_origem", length = 50)
    private String canalOrigem;

    @Column(name = "nome_indicador", length = 200)
    private String nomeIndicador;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "situacao_formacao", columnDefinition = "text[]")
    private String[] situacaoFormacao;

    @Column(name = "areas_atuacao", columnDefinition = "text")
    private String areasAtuacao;

    @Column(name = "procedimentos_realiza", columnDefinition = "text")
    private String procedimentosRealiza;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public DadosCivisMedico() {}

    public DadosCivisMedico(UUID medicoId) {
        this.medicoId = medicoId;
    }

    public UUID getMedicoId() { return medicoId; }
    public void setMedicoId(UUID medicoId) { this.medicoId = medicoId; }

    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }

    public String getNacionalidade() { return nacionalidade; }
    public void setNacionalidade(String nacionalidade) { this.nacionalidade = nacionalidade; }

    public String getNaturalidade() { return naturalidade; }
    public void setNaturalidade(String naturalidade) { this.naturalidade = naturalidade; }

    public EstadoCivil getEstadoCivil() { return estadoCivil; }
    public void setEstadoCivil(EstadoCivil estadoCivil) { this.estadoCivil = estadoCivil; }

    public String getNomeMae() { return nomeMae; }
    public void setNomeMae(String nomeMae) { this.nomeMae = nomeMae; }

    public String getNomePai() { return nomePai; }
    public void setNomePai(String nomePai) { this.nomePai = nomePai; }

    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public String getRgNumero() { return rgNumero; }
    public void setRgNumero(String rgNumero) { this.rgNumero = rgNumero; }

    public String getRgOrgaoExpedidor() { return rgOrgaoExpedidor; }
    public void setRgOrgaoExpedidor(String rgOrgaoExpedidor) { this.rgOrgaoExpedidor = rgOrgaoExpedidor; }

    public String getRgUf() { return rgUf; }
    public void setRgUf(String rgUf) { this.rgUf = rgUf; }

    public String getRqe() { return rqe; }
    public void setRqe(String rqe) { this.rqe = rqe; }

    public String getCanalOrigem() { return canalOrigem; }
    public void setCanalOrigem(String canalOrigem) { this.canalOrigem = canalOrigem; }

    public String getNomeIndicador() { return nomeIndicador; }
    public void setNomeIndicador(String nomeIndicador) { this.nomeIndicador = nomeIndicador; }

    public String[] getSituacaoFormacao() { return situacaoFormacao; }
    public void setSituacaoFormacao(String[] situacaoFormacao) { this.situacaoFormacao = situacaoFormacao; }

    public String getAreasAtuacao() { return areasAtuacao; }
    public void setAreasAtuacao(String areasAtuacao) { this.areasAtuacao = areasAtuacao; }

    public String getProcedimentosRealiza() { return procedimentosRealiza; }
    public void setProcedimentosRealiza(String procedimentosRealiza) { this.procedimentosRealiza = procedimentosRealiza; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
