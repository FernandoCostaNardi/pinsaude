-- ─── V2: Schema de Produção e Tomadores (EPIC-04.1) ──────────────────────────
-- Tabelas: tomadores, servicos, producoes
-- Padrões:
--   - CPF/CNPJ de tomadores armazenado como bytea (pgp_sym_encrypt) via encrypt_sensitive
--   - Valores monetários em centavos (BIGINT) — sem ponto flutuante
--   - RLS com FORCE em tomadores e producoes por cnpj_id_tenant
--   - servicos é tabela de catálogo compartilhada — sem RLS

-- ─── Funções auxiliares de criptografia ──────────────────────────────────────

CREATE OR REPLACE FUNCTION faturamento.encrypt_sensitive(data TEXT, crypto_key TEXT)
    RETURNS BYTEA
    LANGUAGE sql
    SECURITY DEFINER AS
$$
SELECT pgp_sym_encrypt(data, crypto_key);
$$;

CREATE OR REPLACE FUNCTION faturamento.decrypt_sensitive(data BYTEA, crypto_key TEXT)
    RETURNS TEXT
    LANGUAGE sql
    SECURITY DEFINER AS
$$
SELECT pgp_sym_decrypt(data, crypto_key);
$$;

-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE faturamento.tipo_tomador_enum AS ENUM (
    'HOSPITAL',
    'CLINICA',
    'OPERADORA',
    'PACIENTE_PF'
);

CREATE TYPE faturamento.status_producao_enum AS ENUM (
    'RASCUNHO',
    'CONFIRMADA',
    'EMITIDA',
    'CANCELADA'
);

-- ─── Tabela: tomadores ────────────────────────────────────────────────────────
-- Cada tomador pertence a um tenant (cnpj_id_tenant) e pode ser PJ ou PF.
-- cnpj_cpf_tomador_criptografado: bytea — armazena CPF (PF) ou CNPJ (PJ) criptografado.
-- RLS isola por cnpj_id_tenant; WITH CHECK (true) permite INSERT antes do tenant ser definido.

CREATE TABLE faturamento.tomadores (
    id                              UUID        NOT NULL DEFAULT gen_random_uuid(),
    cnpj_id_tenant                  VARCHAR(14) NOT NULL,
    tipo                            faturamento.tipo_tomador_enum NOT NULL,
    cnpj_cpf_tomador_criptografado  BYTEA       NOT NULL,
    razao_social_nome               VARCHAR(255) NOT NULL,
    inscricao_municipal             VARCHAR(20),
    indicador_retencao_federal      BOOLEAN     NOT NULL DEFAULT FALSE,
    indicador_retencao_iss          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tomadores PRIMARY KEY (id)
);

ALTER TABLE faturamento.tomadores ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.tomadores FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomadores
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);

-- ─── Tabela: servicos ─────────────────────────────────────────────────────────
-- Catálogo de serviços fiscais (LC 116/2003).
-- Sem isolamento por tenant — catálogo compartilhado entre todos os tenants.
-- aliquotas como NUMERIC(7,4): ex. 5.0000 = 5%, 0.6500 = 0.65%

CREATE TABLE faturamento.servicos (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    codigo_lc116            VARCHAR(10)  NOT NULL,
    cnae                    VARCHAR(10),
    descricao_padrao        TEXT         NOT NULL,
    indicador_equiparacao   BOOLEAN      NOT NULL DEFAULT FALSE,
    aliquota_iss            NUMERIC(7,4) NOT NULL DEFAULT 0,
    aliquota_ir             NUMERIC(7,4) NOT NULL DEFAULT 0,
    aliquota_csll           NUMERIC(7,4) NOT NULL DEFAULT 0,
    aliquota_pis            NUMERIC(7,4) NOT NULL DEFAULT 0,
    aliquota_cofins         NUMERIC(7,4) NOT NULL DEFAULT 0,

    CONSTRAINT pk_servicos PRIMARY KEY (id),
    CONSTRAINT uq_servicos_codigo UNIQUE (codigo_lc116)
);

-- ─── Tabela: producoes ────────────────────────────────────────────────────────
-- Registro de produção médica: vincula médico, tomador e serviço.
-- valor_bruto em centavos (BIGINT) — ex. R$ 1.500,00 → 150000
-- competencia no formato YYYY-MM — ex. '2025-06'
-- medico_id referencia onboarding.medicos (cross-service — sem FK explícita)
-- RLS isola por cnpj_id_tenant; WITH CHECK (true) pelo mesmo motivo que tomadores

CREATE TABLE faturamento.producoes (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    cnpj_id_tenant          VARCHAR(14) NOT NULL,
    medico_id               UUID        NOT NULL,
    tomador_id              UUID        NOT NULL,
    servico_id              UUID        NOT NULL,
    valor_bruto             BIGINT      NOT NULL CHECK (valor_bruto >= 0),
    competencia             VARCHAR(7)  NOT NULL,
    descricao_complementar  TEXT,
    status                  faturamento.status_producao_enum NOT NULL DEFAULT 'RASCUNHO',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_producoes PRIMARY KEY (id),
    CONSTRAINT fk_producoes_tomador  FOREIGN KEY (tomador_id)  REFERENCES faturamento.tomadores(id),
    CONSTRAINT fk_producoes_servico  FOREIGN KEY (servico_id)  REFERENCES faturamento.servicos(id)
);

CREATE INDEX idx_producoes_tenant     ON faturamento.producoes (cnpj_id_tenant);
CREATE INDEX idx_producoes_medico     ON faturamento.producoes (medico_id);
CREATE INDEX idx_producoes_competencia ON faturamento.producoes (competencia);
CREATE INDEX idx_producoes_status     ON faturamento.producoes (status);

ALTER TABLE faturamento.producoes ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.producoes FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.producoes
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);
