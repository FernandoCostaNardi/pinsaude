-- Contas bancárias por empresa (múltiplas contas, campos individuais)

CREATE TYPE onboarding.tipo_conta_enum AS ENUM (
    'CORRENTE',
    'POUPANCA'
);

CREATE TABLE onboarding.contas_bancarias (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    empresa_id  UUID        NOT NULL,
    banco       VARCHAR(100) NOT NULL,
    agencia     VARCHAR(10) NOT NULL,
    conta       VARCHAR(20) NOT NULL,
    tipo_conta  onboarding.tipo_conta_enum NOT NULL,
    chave_pix   VARCHAR(150),
    principal   BOOLEAN     NOT NULL DEFAULT FALSE,
    ativo       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_contas_bancarias PRIMARY KEY (id),
    CONSTRAINT fk_cb_empresa
        FOREIGN KEY (empresa_id)
        REFERENCES onboarding.empresas(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_contas_bancarias_empresa_id ON onboarding.contas_bancarias(empresa_id);
CREATE INDEX idx_contas_bancarias_ativo       ON onboarding.contas_bancarias(ativo);

ALTER TABLE onboarding.empresas DROP COLUMN IF EXISTS conta_bancaria;
