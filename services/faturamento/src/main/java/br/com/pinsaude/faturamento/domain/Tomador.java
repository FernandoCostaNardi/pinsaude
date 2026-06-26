package br.com.pinsaude.faturamento.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tomadores", schema = "faturamento")
public class Tomador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cnpj_id_tenant", nullable = false, length = 14)
    private String cnpjIdTenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    @ColumnTransformer(write = "?::faturamento.tipo_tomador_enum")
    private TipoTomador tipo;

    @Column(name = "cnpj_cpf_tomador_criptografado", nullable = false, columnDefinition = "bytea")
    private byte[] cnpjCpfTomadorCriptografado;

    @Column(name = "razao_social_nome", nullable = false, length = 255)
    private String razaoSocialNome;

    @Column(name = "inscricao_municipal", length = 20)
    private String inscricaoMunicipal;

    @Column(name = "indicador_retencao_federal", nullable = false)
    private boolean indicadorRetencaoFederal;

    @Column(name = "indicador_retencao_iss", nullable = false)
    private boolean indicadorRetencaoIss;

    @Column(name = "municipio", length = 100)
    private String municipio;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "logradouro", length = 255)
    private String logradouro;

    @Column(name = "bairro", length = 100)
    private String bairro;

    @Column(name = "cep", length = 9)
    private String cep;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uf", columnDefinition = "char(2)")
    private String uf;

    @Column(name = "pais", length = 50)
    private String pais;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCnpjIdTenant() { return cnpjIdTenant; }
    public void setCnpjIdTenant(String cnpjIdTenant) { this.cnpjIdTenant = cnpjIdTenant; }
    public TipoTomador getTipo() { return tipo; }
    public void setTipo(TipoTomador tipo) { this.tipo = tipo; }
    public byte[] getCnpjCpfTomadorCriptografado() { return cnpjCpfTomadorCriptografado; }
    public void setCnpjCpfTomadorCriptografado(byte[] v) { this.cnpjCpfTomadorCriptografado = v; }
    public String getRazaoSocialNome() { return razaoSocialNome; }
    public void setRazaoSocialNome(String razaoSocialNome) { this.razaoSocialNome = razaoSocialNome; }
    public String getInscricaoMunicipal() { return inscricaoMunicipal; }
    public void setInscricaoMunicipal(String inscricaoMunicipal) { this.inscricaoMunicipal = inscricaoMunicipal; }
    public boolean isIndicadorRetencaoFederal() { return indicadorRetencaoFederal; }
    public void setIndicadorRetencaoFederal(boolean v) { this.indicadorRetencaoFederal = v; }
    public boolean isIndicadorRetencaoIss() { return indicadorRetencaoIss; }
    public void setIndicadorRetencaoIss(boolean v) { this.indicadorRetencaoIss = v; }
    public String getMunicipio() { return municipio; }
    public void setMunicipio(String municipio) { this.municipio = municipio; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
