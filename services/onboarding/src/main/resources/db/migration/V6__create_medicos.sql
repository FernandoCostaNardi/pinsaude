-- ─── V6: Schema de Médicos e Documentos (EPIC-03.1) ─────────────────────────
-- Tabelas: medicos, vinculos_medico_empresa, dados_bancarios_medico,
--          documentos_medico, checklist_conduta.
-- CPF e chave PIX são armazenados criptografados via pgcrypto (pgp_sym_encrypt).
-- RLS ativo em todas as tabelas; isolamento via vínculo médico ↔ empresa ↔ CNPJ.

-- ─── Funções auxiliares de criptografia ──────────────────────────────────────
-- A chave é fornecida pela aplicação (variável de ambiente CRYPTO_KEY).
-- SECURITY DEFINER garante que apenas o owner execute diretamente.

CREATE OR REPLACE FUNCTION onboarding.encrypt_sensitive(data TEXT, crypto_key TEXT)
    RETURNS BYTEA
    LANGUAGE sql
    SECURITY DEFINER AS
$$
SELECT pgp_sym_encrypt(data, crypto_key);
$$;

CREATE OR REPLACE FUNCTION onboarding.decrypt_sensitive(data BYTEA, crypto_key TEXT)
    RETURNS TEXT
    LANGUAGE sql
    SECURITY DEFINER AS
$$
SELECT pgp_sym_decrypt(data, crypto_key);
$$;

-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE onboarding.status_medico_enum AS ENUM (
    'RASCUNHO',
    'ATIVO',
    'INATIVO',
    'SUSPENSO'
);

CREATE TYPE onboarding.status_societario_enum AS ENUM (
    'ATIVO',
    'INATIVO',
    'SUSPENSO',
    'AFASTADO'
);

CREATE TYPE onboarding.tipo_pix_enum AS ENUM (
    'CPF',
    'CNPJ',
    'EMAIL',
    'TELEFONE',
    'ALEATORIA'
);

CREATE TYPE onboarding.tipo_documento_medico_enum AS ENUM (
    'CRM',
    'DIPLOMA',
    'IDENTIDADE',
    'RESIDENCIA',
    'CONTRATO'
);

CREATE TYPE onboarding.status_validacao_documento_enum AS ENUM (
    'PENDENTE',
    'APROVADO',
    'REPROVADO'
);

-- ─── medicos ─────────────────────────────────────────────────────────────────
-- cpf_criptografado: bytea — resultado de pgp_sym_encrypt(cpf, key).
-- A aplicação nunca escreve CPF em texto plano nesta coluna.

CREATE TABLE onboarding.medicos (
    id              UUID                         NOT NULL DEFAULT gen_random_uuid(),
    cpf_criptografado BYTEA                      NOT NULL,
    nome            VARCHAR(200)                 NOT NULL,
    crm             VARCHAR(20)                  NOT NULL,
    crm_uf          CHAR(2)                      NOT NULL,
    especialidade   VARCHAR(100),
    email           VARCHAR(200),
    telefone        VARCHAR(20),
    status          onboarding.status_medico_enum NOT NULL DEFAULT 'RASCUNHO',
    created_at      TIMESTAMPTZ                  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ                  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_medicos PRIMARY KEY (id),
    CONSTRAINT uq_medicos_crm UNIQUE (crm, crm_uf)
);

-- ─── vinculos_medico_empresa ──────────────────────────────────────────────────
-- Relacionamento N:N entre médicos e empresas com metadados societários.
-- É a âncora do RLS: todas as tabelas filhas resolvem isolamento por aqui.

CREATE TABLE onboarding.vinculos_medico_empresa (
    medico_id              UUID                               NOT NULL,
    empresa_id             UUID                               NOT NULL,
    status_societario      onboarding.status_societario_enum  NOT NULL DEFAULT 'ATIVO',
    data_alteracao_junta   DATE,
    data_ativacao          DATE,
    created_at             TIMESTAMPTZ                        NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_vinculos_medico_empresa PRIMARY KEY (medico_id, empresa_id),
    CONSTRAINT fk_vinculos_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE,
    CONSTRAINT fk_vinculos_empresa FOREIGN KEY (empresa_id)
        REFERENCES onboarding.empresas (id) ON DELETE CASCADE
);

-- ─── dados_bancarios_medico ───────────────────────────────────────────────────
-- chave_pix_criptografada: bytea — resultado de pgp_sym_encrypt(chave, key).
-- cpfs_adicionais_split: JSONB — lista de CPFs coparticipantes (sem criptografia,
--   pois são referências de terceiros, não dados do próprio médico neste contexto).

CREATE TABLE onboarding.dados_bancarios_medico (
    id                       UUID                       NOT NULL DEFAULT gen_random_uuid(),
    medico_id                UUID                       NOT NULL,
    tipo_pix                 onboarding.tipo_pix_enum,
    chave_pix_criptografada  BYTEA,
    cpfs_adicionais_split    JSONB,
    created_at               TIMESTAMPTZ                NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ                NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dados_bancarios_medico PRIMARY KEY (id),
    CONSTRAINT fk_dados_bancarios_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE
);

-- ─── documentos_medico ────────────────────────────────────────────────────────

CREATE TABLE onboarding.documentos_medico (
    id                  UUID                                      NOT NULL DEFAULT gen_random_uuid(),
    medico_id           UUID                                      NOT NULL,
    tipo                onboarding.tipo_documento_medico_enum     NOT NULL,
    nome_arquivo        VARCHAR(500)                              NOT NULL,
    caminho_storage     VARCHAR(1000)                             NOT NULL,
    status_validacao    onboarding.status_validacao_documento_enum NOT NULL DEFAULT 'PENDENTE',
    motivo_reprovacao   TEXT,
    created_at          TIMESTAMPTZ                               NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_documentos_medico PRIMARY KEY (id),
    CONSTRAINT fk_documentos_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE
);

-- ─── checklist_conduta ────────────────────────────────────────────────────────

CREATE TABLE onboarding.checklist_conduta (
    medico_id                    UUID         NOT NULL,
    numero_conselho_verificado   BOOLEAN      NOT NULL DEFAULT FALSE,
    registros_disciplinares      BOOLEAN      NOT NULL DEFAULT FALSE,
    processos_medicos            BOOLEAN      NOT NULL DEFAULT FALSE,
    verificado_por               VARCHAR(200),
    verificado_em                TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_checklist_conduta PRIMARY KEY (medico_id),
    CONSTRAINT fk_checklist_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE
);

-- ─── Índices ──────────────────────────────────────────────────────────────────

CREATE INDEX idx_vinculos_empresa_id ON onboarding.vinculos_medico_empresa (empresa_id);
CREATE INDEX idx_dados_bancarios_medico_id ON onboarding.dados_bancarios_medico (medico_id);
CREATE INDEX idx_documentos_medico_id ON onboarding.documentos_medico (medico_id);
CREATE INDEX idx_documentos_status ON onboarding.documentos_medico (status_validacao);

-- ─── RLS — medicos ───────────────────────────────────────────────────────────
-- Um médico é visível quando tem vínculo com uma empresa cujo CNPJ é o do tenant.
-- Bypass para gestão/Flyway quando tenant = '' ou variável não definida.

ALTER TABLE onboarding.medicos ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.medicos FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.medicos
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR id IN (
            SELECT v.medico_id
            FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    );

-- ─── RLS — vinculos_medico_empresa ───────────────────────────────────────────

ALTER TABLE onboarding.vinculos_medico_empresa ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.vinculos_medico_empresa FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.vinculos_medico_empresa
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR empresa_id IN (
            SELECT id FROM onboarding.empresas
            WHERE cnpj = current_setting('app.current_tenant', TRUE)
        )
    );

-- ─── RLS — dados_bancarios_medico ────────────────────────────────────────────

ALTER TABLE onboarding.dados_bancarios_medico ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.dados_bancarios_medico FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.dados_bancarios_medico
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR medico_id IN (
            SELECT v.medico_id
            FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    );

-- ─── RLS — documentos_medico ─────────────────────────────────────────────────

ALTER TABLE onboarding.documentos_medico ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.documentos_medico FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.documentos_medico
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR medico_id IN (
            SELECT v.medico_id
            FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    );

-- ─── RLS — checklist_conduta ─────────────────────────────────────────────────

ALTER TABLE onboarding.checklist_conduta ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.checklist_conduta FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.checklist_conduta
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR medico_id IN (
            SELECT v.medico_id
            FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    );
