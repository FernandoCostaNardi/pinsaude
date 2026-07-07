ALTER TABLE faturamento.producoes
    ADD COLUMN IF NOT EXISTS cnae_codigo VARCHAR(10);
