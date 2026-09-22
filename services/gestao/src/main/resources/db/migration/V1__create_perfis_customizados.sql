CREATE TABLE gestao.perfis_customizados (
    id                  UUID PRIMARY KEY,
    nome                VARCHAR(100) NOT NULL,
    keycloak_role_name  VARCHAR(80)  NOT NULL UNIQUE,
    permissoes          TEXT[]       NOT NULL DEFAULT '{}',
    criado_por          VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_perfis_customizados_nome ON gestao.perfis_customizados (LOWER(nome));
