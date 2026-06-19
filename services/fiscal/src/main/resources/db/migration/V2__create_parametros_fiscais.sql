-- Enum de tipos de tributo para parâmetros fiscais por competência
CREATE TYPE fiscal.tipo_tributo_enum AS ENUM ('ISS', 'IR', 'CSLL', 'PIS', 'COFINS');

-- Parâmetros fiscais por tributo e por competência (normalized, per-row model)
-- Complementa parametro_fiscal (V1) que armazena o regime geral e IBS/CBS
CREATE TABLE fiscal.parametros_fiscais (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id             VARCHAR(14)  NOT NULL,
    competencia_inicio  VARCHAR(7)   NOT NULL,          -- YYYY-MM (inclusive)
    competencia_fim     VARCHAR(7),                     -- YYYY-MM (inclusive, nullable = sem prazo)
    tipo_tributo        fiscal.tipo_tributo_enum NOT NULL,
    valor_aliquota      NUMERIC(8,4) NOT NULL,          -- fração decimal: 0.0500 = 5%
    descricao           TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT parametros_fiscais_uq UNIQUE (cnpj_id, competencia_inicio, tipo_tributo)
);

-- Índice para queries de vigência: buscar alíquota de um tributo para uma competência
CREATE INDEX idx_pf_cnpj_comp_tributo
    ON fiscal.parametros_fiscais (cnpj_id, tipo_tributo, competencia_inicio DESC);
