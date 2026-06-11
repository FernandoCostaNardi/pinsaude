-- Schema de cadastros: empresas, configurações fiscais e vínculo usuário-empresa.
-- Isolamento por tenant via Row Level Security (RLS) na tabela empresas.

-- ─── Tipos enum ───────────────────────────────────────────────────────────────

CREATE TYPE onboarding.regime_tributario_enum AS ENUM (
    'SIMPLES_NACIONAL',
    'LUCRO_PRESUMIDO',
    'LUCRO_REAL'
);

CREATE TYPE onboarding.regime_presuncao_enum AS ENUM (
    'FIXO',
    'ESTIMADO',
    'PRESUMIDO'
);

CREATE TYPE onboarding.perfil_enum AS ENUM (
    'medico',
    'operacao',
    'financeiro',
    'contabil',
    'gestao'
);

-- ─── Tabela: empresas ─────────────────────────────────────────────────────────

CREATE TABLE onboarding.empresas (
    id                      UUID                            NOT NULL DEFAULT gen_random_uuid(),
    cnpj                    VARCHAR(18)                     NOT NULL,
    razao_social            VARCHAR(255)                    NOT NULL,
    inscricao_municipal     VARCHAR(50),
    municipio               VARCHAR(255),
    codigo_municipio_ibge   CHAR(7),
    regime_tributario       onboarding.regime_tributario_enum NOT NULL,
    conta_bancaria          JSONB,
    ativo                   BOOLEAN                         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ                     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ                     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_empresas PRIMARY KEY (id),
    CONSTRAINT uq_empresas_cnpj UNIQUE (cnpj)
);

-- ─── RLS: isolamento por tenant ───────────────────────────────────────────────
-- A policy usa current_setting('app.current_tenant', TRUE) — o segundo argumento
-- TRUE evita erro quando a variável não está definida (retorna NULL, não lança).
-- O owner (svc_onboarding) ignora RLS por padrão; FORCE RLS será adicionado em
-- EPIC-02.5 quando o serviço estiver pronto para setar o tenant em cada request.

ALTER TABLE onboarding.empresas ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.empresas
    USING (cnpj = current_setting('app.current_tenant', TRUE));

-- ─── Tabela: configuracoes_fiscais ────────────────────────────────────────────

CREATE TABLE onboarding.configuracoes_fiscais (
    id                                  UUID                                NOT NULL DEFAULT gen_random_uuid(),
    empresa_id                          UUID                                NOT NULL,
    cnae_principal                      VARCHAR(10),
    codigo_lc116                        VARCHAR(10),
    aliquota_iss                        NUMERIC(5, 2),
    regime_presuncao                    onboarding.regime_presuncao_enum,
    indicador_equiparacao_hospitalar    BOOLEAN                             NOT NULL DEFAULT FALSE,
    created_at                          TIMESTAMPTZ                         NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ                         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_configuracoes_fiscais PRIMARY KEY (id),
    CONSTRAINT fk_config_empresa
        FOREIGN KEY (empresa_id)
        REFERENCES onboarding.empresas(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_config_empresa UNIQUE (empresa_id)
);

-- ─── Tabela: usuarios_empresas ────────────────────────────────────────────────

CREATE TABLE onboarding.usuarios_empresas (
    usuario_id  VARCHAR(255)            NOT NULL,
    empresa_id  UUID                    NOT NULL,
    perfil      onboarding.perfil_enum  NOT NULL,
    created_at  TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_usuarios_empresas PRIMARY KEY (usuario_id, empresa_id),
    CONSTRAINT fk_ue_empresa
        FOREIGN KEY (empresa_id)
        REFERENCES onboarding.empresas(id)
        ON DELETE CASCADE
);

-- ─── Índices ──────────────────────────────────────────────────────────────────

-- Filtragem por tenant e lookups por CNPJ
CREATE INDEX idx_empresas_cnpj
    ON onboarding.empresas(cnpj);

CREATE INDEX idx_empresas_ativo
    ON onboarding.empresas(ativo);

-- Joins e lookups em configurações fiscais
CREATE INDEX idx_configuracoes_fiscais_empresa_id
    ON onboarding.configuracoes_fiscais(empresa_id);

-- Lookups de usuários por empresa e por usuário
CREATE INDEX idx_usuarios_empresas_empresa_id
    ON onboarding.usuarios_empresas(empresa_id);

CREATE INDEX idx_usuarios_empresas_usuario_id
    ON onboarding.usuarios_empresas(usuario_id);
