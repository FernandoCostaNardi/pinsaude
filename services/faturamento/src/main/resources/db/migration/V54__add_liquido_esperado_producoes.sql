-- CBS-31 — Conciliação pelo líquido esperado da nota.
-- O tomador deposita o bruto menos o que reteve na fonte (6,15% até 2026-12; 2,5% a partir de 2027-01).
-- O Fiscal informa esses dados no evento "produção emitida" e o matching compara o depósito com eles.
--   valor_liquido_esperado  centavos: bruto da nota − tributos retidos pelo tomador (ISS/IR/CSLL/PIS/COFINS)
--   nota_emitida_em         data de emissão da NFS-e (referência de data do matching)
-- Nulos em produções emitidas antes desta migration: o matching estima o líquido pelo cadastro do tomador.

ALTER TABLE faturamento.producoes
    ADD COLUMN valor_liquido_esperado BIGINT,
    ADD COLUMN nota_emitida_em        DATE;

ALTER TABLE faturamento.producoes
    ADD CONSTRAINT producoes_liquido_esperado_check
        CHECK (valor_liquido_esperado IS NULL OR (valor_liquido_esperado >= 0 AND valor_liquido_esperado <= valor_bruto));
