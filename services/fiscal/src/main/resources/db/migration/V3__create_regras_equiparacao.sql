-- Regras de equiparação de serviços: CNAE → código LC 116/2003
-- Determina se o serviço é equiparado (sujeito a ISS) e o regime de presunção
CREATE TABLE fiscal.regras_equiparacao (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id               VARCHAR(14)  NOT NULL,
    cnae                  VARCHAR(10)  NOT NULL,
    codigo_lc116          VARCHAR(10)  NOT NULL,
    indicador_equiparacao BOOLEAN      NOT NULL DEFAULT FALSE,
    -- REDUZIDA = presunção reduzida (Lucro Presumido); CHEIA = presunção plena (Simples/MEI)
    regime_presuncao      VARCHAR(10)  CHECK (regime_presuncao IN ('REDUZIDA', 'CHEIA')),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT regras_equiparacao_uq UNIQUE (cnpj_id, cnae, codigo_lc116)
);

CREATE INDEX idx_re_cnpj_cnae ON fiscal.regras_equiparacao (cnpj_id, cnae);
CREATE INDEX idx_re_lc116     ON fiscal.regras_equiparacao (codigo_lc116);
