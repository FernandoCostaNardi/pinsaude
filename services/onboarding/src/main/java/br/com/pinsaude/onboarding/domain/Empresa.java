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
@Table(name = "empresas", schema = "onboarding")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj", nullable = false, length = 18)
    private String cnpj;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "inscricao_municipal", length = 50)
    private String inscricaoMunicipal;

    @Column(name = "municipio")
    private String municipio;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "codigo_municipio_ibge", columnDefinition = "char(7)")
    private String codigoMunicipioIbge;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_tributario")
    @ColumnTransformer(write = "?::onboarding.regime_tributario_enum")
    private RegimeTributario regimeTributario;

    @Column(name = "logradouro", length = 255)
    private String logradouro;

    @Column(name = "bairro", length = 100)
    private String bairro;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uf", columnDefinition = "char(2)")
    private String uf;

    @Column(name = "cep", length = 9)
    private String cep;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "email_contato", length = 255)
    private String emailContato;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }

    public String getInscricaoMunicipal() { return inscricaoMunicipal; }
    public void setInscricaoMunicipal(String inscricaoMunicipal) { this.inscricaoMunicipal = inscricaoMunicipal; }

    public String getMunicipio() { return municipio; }
    public void setMunicipio(String municipio) { this.municipio = municipio; }

    public String getCodigoMunicipioIbge() { return codigoMunicipioIbge; }
    public void setCodigoMunicipioIbge(String codigoMunicipioIbge) { this.codigoMunicipioIbge = codigoMunicipioIbge; }

    public RegimeTributario getRegimeTributario() { return regimeTributario; }
    public void setRegimeTributario(RegimeTributario regimeTributario) { this.regimeTributario = regimeTributario; }

    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmailContato() { return emailContato; }
    public void setEmailContato(String emailContato) { this.emailContato = emailContato; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
