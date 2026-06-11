-- ─── V4: Configuração Fiscal da Empresa (EPIC-02.4) ──────────────────────────
-- Amplia configuracoes_fiscais com CNAE completo e certificado A1.
-- Cria aliquotas_competencia para versionamento por competência:
-- notas emitidas em uma competência usam sempre a alíquota daquela competência,
-- independente de mudanças futuras.

-- Ajustes na tabela existente: remove colunas simples não versionadas,
-- adiciona campos completos de CNAE e certificado A1
ALTER TABLE onboarding.configuracoes_fiscais
    DROP COLUMN IF EXISTS aliquota_iss,
    DROP COLUMN IF EXISTS regime_presuncao,
    ADD COLUMN IF NOT EXISTS cnae_descricao             VARCHAR(500),
    ADD COLUMN IF NOT EXISTS vencimento_certificado_a1  DATE,
    ADD COLUMN IF NOT EXISTS updated_by                 VARCHAR(255);

-- Renomeia cnae_principal → cnae_codigo para clareza
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'onboarding'
          AND table_name   = 'configuracoes_fiscais'
          AND column_name  = 'cnae_principal'
    ) THEN
        ALTER TABLE onboarding.configuracoes_fiscais
            RENAME COLUMN cnae_principal TO cnae_codigo;
    END IF;
END
$$;

-- ─── Tabela: aliquotas_competencia ───────────────────────────────────────────
-- Uma linha por empresa por competência (YYYY-MM).
-- A emissão de NFS-e buscará a alíquota exata da competência do mês da nota.
-- Mudança de alíquota em meses futuros NÃO retroage em notas já emitidas.

CREATE TABLE onboarding.aliquotas_competencia (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    empresa_id          UUID            NOT NULL,
    competencia         VARCHAR(7)      NOT NULL,   -- formato YYYY-MM
    aliquota_iss        NUMERIC(5, 2)   NOT NULL DEFAULT 0
        CONSTRAINT chk_iss   CHECK (aliquota_iss    BETWEEN 0 AND 100),
    aliquota_ir         NUMERIC(5, 2)   NOT NULL DEFAULT 0
        CONSTRAINT chk_ir    CHECK (aliquota_ir     BETWEEN 0 AND 100),
    aliquota_csll       NUMERIC(5, 2)   NOT NULL DEFAULT 0
        CONSTRAINT chk_csll  CHECK (aliquota_csll   BETWEEN 0 AND 100),
    aliquota_pis        NUMERIC(5, 2)   NOT NULL DEFAULT 0
        CONSTRAINT chk_pis   CHECK (aliquota_pis    BETWEEN 0 AND 100),
    aliquota_cofins     NUMERIC(5, 2)   NOT NULL DEFAULT 0
        CONSTRAINT chk_cofins CHECK (aliquota_cofins BETWEEN 0 AND 100),
    regime_presuncao    VARCHAR(10)     NOT NULL DEFAULT 'CHEIA'
        CONSTRAINT chk_regime CHECK (regime_presuncao IN ('REDUZIDA', 'CHEIA')),
    created_by          VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_aliquotas_competencia PRIMARY KEY (id),
    CONSTRAINT fk_aliquotas_empresa
        FOREIGN KEY (empresa_id)
        REFERENCES onboarding.empresas(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_aliquotas_empresa_competencia
        UNIQUE (empresa_id, competencia)
);

-- Índice para lookup por empresa + competência decrescente (histórico recente primeiro)
CREATE INDEX idx_aliquotas_empresa_competencia
    ON onboarding.aliquotas_competencia(empresa_id, competencia DESC);
