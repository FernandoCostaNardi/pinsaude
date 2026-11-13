-- CBS-11 — Reforma tributária: regime e valores de CBS/IBS gravados na nota fiscal.
--
-- Cada nota passa a guardar o snapshot do regime usado no cálculo. Telas e relatórios leem da
-- nota, nunca do parâmetro fiscal atual da empresa (que pode mudar depois da emissão).
-- Notas já existentes ficam como LEGADO (PIS/COFINS), com CBS/IBS zerados.
--
-- Convenções do fiscal service: valores em centavos (BIGINT), alíquotas em FRAÇÃO (0.0090 = 0,9%).
-- Retenção de CBS/IBS (só tomador público) fica fora de escopo: retencoes_sofridas não muda.

ALTER TABLE fiscal.notas_fiscais
    ADD COLUMN regime_tributario VARCHAR(10)  NOT NULL DEFAULT 'LEGADO',
    ADD COLUMN valor_cbs         BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN valor_ibs         BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN aliq_cbs          NUMERIC(8,4),
    ADD COLUMN aliq_ibs          NUMERIC(8,4),
    ADD COLUMN cst_ibs_cbs       VARCHAR(3),
    ADD COLUMN c_class_trib      VARCHAR(6),
    ADD COLUMN fator_iss         NUMERIC(6,4);

ALTER TABLE fiscal.notas_fiscais
    ADD CONSTRAINT notas_fiscais_regime_tributario_ck CHECK (regime_tributario IN ('LEGADO', 'IBS_CBS')),
    ADD CONSTRAINT notas_fiscais_valor_cbs_ck         CHECK (valor_cbs >= 0),
    ADD CONSTRAINT notas_fiscais_valor_ibs_ck         CHECK (valor_ibs >= 0),
    ADD CONSTRAINT notas_fiscais_aliq_cbs_ck          CHECK (aliq_cbs  IS NULL OR aliq_cbs  BETWEEN 0 AND 1),
    ADD CONSTRAINT notas_fiscais_aliq_ibs_ck          CHECK (aliq_ibs  IS NULL OR aliq_ibs  BETWEEN 0 AND 1),
    ADD CONSTRAINT notas_fiscais_fator_iss_ck         CHECK (fator_iss IS NULL OR fator_iss BETWEEN 0 AND 1),
    ADD CONSTRAINT notas_fiscais_cst_ibs_cbs_ck       CHECK (cst_ibs_cbs  IS NULL OR cst_ibs_cbs  ~ '^[0-9]{3}$'),
    ADD CONSTRAINT notas_fiscais_c_class_trib_ck      CHECK (c_class_trib IS NULL OR c_class_trib ~ '^[0-9]{6}$');

COMMENT ON COLUMN fiscal.notas_fiscais.regime_tributario IS 'Regime usado no cálculo desta nota: LEGADO (PIS/COFINS) ou IBS_CBS. Snapshot no momento do cálculo.';
COMMENT ON COLUMN fiscal.notas_fiscais.valor_cbs         IS 'CBS da nota, em centavos (já com a redução do serviço).';
COMMENT ON COLUMN fiscal.notas_fiscais.valor_ibs         IS 'IBS da nota (UF + município), em centavos (já com a redução do serviço).';
COMMENT ON COLUMN fiscal.notas_fiscais.aliq_cbs          IS 'Alíquota efetiva de CBS aplicada (fração), já com a redução. Nula em nota LEGADO.';
COMMENT ON COLUMN fiscal.notas_fiscais.aliq_ibs          IS 'Alíquota efetiva de IBS aplicada (fração, UF + município), já com a redução. Nula em nota LEGADO.';
COMMENT ON COLUMN fiscal.notas_fiscais.cst_ibs_cbs       IS 'CST do IBS/CBS usado na nota (ex.: 200 = alíquota reduzida).';
COMMENT ON COLUMN fiscal.notas_fiscais.c_class_trib      IS 'Classificação tributária do IBS/CBS (ex.: 200029 = serviços de saúde, redução de 60%).';
COMMENT ON COLUMN fiscal.notas_fiscais.fator_iss         IS 'Fração da alíquota de ISS cobrada no ano da competência (cronograma_reforma.fator_iss).';
