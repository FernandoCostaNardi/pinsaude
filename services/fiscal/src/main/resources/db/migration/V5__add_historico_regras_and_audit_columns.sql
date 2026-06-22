-- Rastreabilidade de quem criou cada alíquota per-tributo
ALTER TABLE fiscal.parametros_fiscais ADD COLUMN created_by VARCHAR(100);

-- Histórico de alterações para regras de equiparação
CREATE TABLE fiscal.historico_regras_equiparacao (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    regra_id        UUID        NOT NULL REFERENCES fiscal.regras_equiparacao(id) ON DELETE CASCADE,
    campo_alterado  VARCHAR(100) NOT NULL,
    valor_anterior  TEXT,
    valor_novo      TEXT,
    alterado_por    VARCHAR(100),
    alterado_em     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hist_regra_id ON fiscal.historico_regras_equiparacao (regra_id, alterado_em DESC);
