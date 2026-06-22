-- Converte tipo_tributo de enum para varchar para evitar falha de cast no Hibernate 6
-- O Hibernate envia enums como character varying no WHERE clause; PG enum não faz cast automático.
-- Padrão adotado: varchar com check constraint (igual a regime_presuncao em aliquotas_competencia).
ALTER TABLE fiscal.parametros_fiscais
    ALTER COLUMN tipo_tributo TYPE varchar(20) USING tipo_tributo::text;

ALTER TABLE fiscal.parametros_fiscais
    ADD CONSTRAINT parametros_fiscais_tipo_tributo_check
    CHECK (tipo_tributo IN ('ISS', 'IR', 'CSLL', 'PIS', 'COFINS'));

DROP TYPE IF EXISTS fiscal.tipo_tributo_enum;
