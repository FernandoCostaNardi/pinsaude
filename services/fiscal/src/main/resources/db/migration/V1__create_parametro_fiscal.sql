-- TASK-04.5 — Parametrização IBS/CBS (Reforma Tributária 2027)
-- Motor seleciona o registro mais recente com vigencia_inicio <= data do fato gerador.

CREATE TABLE fiscal.parametro_fiscal (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id               VARCHAR(14) NOT NULL,
    vigencia_inicio       DATE        NOT NULL,

    -- Regime tributário atual (substituído gradualmente pelo IBS/CBS a partir de 2027)
    aliq_iss              NUMERIC(8,4) NOT NULL DEFAULT 2.0000,
    aliq_ir               NUMERIC(8,4) NOT NULL DEFAULT 1.5000,
    aliq_csll             NUMERIC(8,4) NOT NULL DEFAULT 1.0000,
    aliq_pis              NUMERIC(8,4) NOT NULL DEFAULT 0.6500,
    aliq_cofins           NUMERIC(8,4) NOT NULL DEFAULT 3.0000,

    -- IBS/CBS — Reforma Tributária (EC 132/2023), transição a partir de 2027
    ibs_cbs_ativo         BOOLEAN     NOT NULL DEFAULT FALSE,
    aliq_ibs_cbs          NUMERIC(8,4),
    reducao_ibs_cbs_saude NUMERIC(8,4) DEFAULT 0.6000,

    -- Controle / governança
    homologado            BOOLEAN     NOT NULL DEFAULT FALSE,
    observacoes           TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT parametro_fiscal_uq UNIQUE (cnpj_id, vigencia_inicio)
);

COMMENT ON TABLE  fiscal.parametro_fiscal                         IS 'Parâmetros fiscais por empresa e vigência. Motor seleciona o mais recente válido para a competência do fato gerador.';
COMMENT ON COLUMN fiscal.parametro_fiscal.ibs_cbs_ativo           IS 'Habilita o regime IBS/CBS. Ativar em jan/2027 conforme cronograma da Reforma Tributária.';
COMMENT ON COLUMN fiscal.parametro_fiscal.aliq_ibs_cbs            IS 'Alíquota base IBS/CBS como fração decimal (ex: 0.10 = 10%). Fase-teste 2027: 0.0100.';
COMMENT ON COLUMN fiscal.parametro_fiscal.reducao_ibs_cbs_saude   IS '60% de redução para serviços de saúde (NBS 200029, Anexo III). Valor padrão: 0.6000.';
COMMENT ON COLUMN fiscal.parametro_fiscal.homologado              IS 'Parâmetros confirmados pela contabilidade. Enquanto false são tratados como rascunho.';

CREATE INDEX idx_pf_cnpj_vigencia ON fiscal.parametro_fiscal (cnpj_id, vigencia_inicio DESC);
