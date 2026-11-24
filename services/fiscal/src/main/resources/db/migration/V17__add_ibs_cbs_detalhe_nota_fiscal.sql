-- CBS-41 — Detalhe de CBS/IBS gravado na nota para montar o grupo IBSCBS da NFS-e.
--
-- A V16 (CBS-11) guarda o total de CBS e de IBS. O leiaute nacional da NFS-e pede também a base
-- de cálculo e o IBS separado entre UF e município, e o ledger precisa saber se CBS/IBS foram
-- somados ao preço (por fora) ou estão dentro do valor bruto.
--
-- Convenções do fiscal service: valores em centavos (BIGINT), alíquotas em FRAÇÃO.
-- Notas já existentes ficam com base nula, IBS UF/município zerados e "dentro do preço".

ALTER TABLE fiscal.notas_fiscais
    ADD COLUMN base_calculo_ibs_cbs BIGINT,
    ADD COLUMN cbs_ibs_por_fora     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reducao_ibs_cbs      NUMERIC(5,4),
    ADD COLUMN aliq_ibs_uf          NUMERIC(8,4),
    ADD COLUMN aliq_ibs_mun         NUMERIC(8,4),
    ADD COLUMN valor_ibs_uf         BIGINT  NOT NULL DEFAULT 0,
    ADD COLUMN valor_ibs_mun        BIGINT  NOT NULL DEFAULT 0;

ALTER TABLE fiscal.notas_fiscais
    ADD CONSTRAINT notas_fiscais_base_ibs_cbs_ck   CHECK (base_calculo_ibs_cbs IS NULL OR base_calculo_ibs_cbs >= 0),
    ADD CONSTRAINT notas_fiscais_reducao_ibs_cbs_ck CHECK (reducao_ibs_cbs IS NULL OR reducao_ibs_cbs BETWEEN 0 AND 1),
    ADD CONSTRAINT notas_fiscais_aliq_ibs_uf_ck    CHECK (aliq_ibs_uf  IS NULL OR aliq_ibs_uf  BETWEEN 0 AND 1),
    ADD CONSTRAINT notas_fiscais_aliq_ibs_mun_ck   CHECK (aliq_ibs_mun IS NULL OR aliq_ibs_mun BETWEEN 0 AND 1),
    ADD CONSTRAINT notas_fiscais_valor_ibs_uf_ck   CHECK (valor_ibs_uf  >= 0),
    ADD CONSTRAINT notas_fiscais_valor_ibs_mun_ck  CHECK (valor_ibs_mun >= 0),
    ADD CONSTRAINT notas_fiscais_ibs_partes_ck     CHECK (valor_ibs_uf + valor_ibs_mun IN (0, valor_ibs));

COMMENT ON COLUMN fiscal.notas_fiscais.base_calculo_ibs_cbs IS 'Base de cálculo de CBS/IBS em centavos (bruto sem CBS/IBS quando estão dentro do preço). Nula em nota LEGADO.';
COMMENT ON COLUMN fiscal.notas_fiscais.cbs_ibs_por_fora     IS 'true = CBS/IBS somados ao preço (acerto com o tomador); false = dentro do valor bruto.';
COMMENT ON COLUMN fiscal.notas_fiscais.reducao_ibs_cbs      IS 'Redução de CBS/IBS do serviço usada na nota (fração, 0.6 = 60%). Nula em nota LEGADO.';
COMMENT ON COLUMN fiscal.notas_fiscais.aliq_ibs_uf          IS 'Alíquota efetiva do IBS estadual (fração, já com a redução do serviço). Nula em nota LEGADO.';
COMMENT ON COLUMN fiscal.notas_fiscais.aliq_ibs_mun         IS 'Alíquota efetiva do IBS municipal (fração, já com a redução do serviço). Nula em nota LEGADO.';
COMMENT ON COLUMN fiscal.notas_fiscais.valor_ibs_uf         IS 'Parte estadual do IBS, em centavos. valor_ibs_uf + valor_ibs_mun = valor_ibs.';
COMMENT ON COLUMN fiscal.notas_fiscais.valor_ibs_mun        IS 'Parte municipal do IBS, em centavos.';
