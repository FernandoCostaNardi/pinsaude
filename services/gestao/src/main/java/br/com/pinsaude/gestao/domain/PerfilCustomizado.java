package br.com.pinsaude.gestao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "perfis_customizados", schema = "gestao")
public class PerfilCustomizado {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", length = 100, nullable = false)
    private String nome;

    @Column(name = "keycloak_role_name", length = 80, nullable = false, unique = true)
    private String keycloakRoleName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissoes", columnDefinition = "text[]", nullable = false)
    private String[] permissoes = new String[0];

    @Column(name = "criado_por", length = 200)
    private String criadoPor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public PerfilCustomizado() {}

    public PerfilCustomizado(String nome, String[] permissoes, String criadoPor) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.keycloakRoleName = "perfil_custom_" + this.id;
        this.permissoes = permissoes;
        this.criadoPor = criadoPor;
    }

    public UUID getId() { return id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getKeycloakRoleName() { return keycloakRoleName; }

    public String[] getPermissoes() { return permissoes; }
    public void setPermissoes(String[] permissoes) { this.permissoes = permissoes; }

    public String getCriadoPor() { return criadoPor; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
