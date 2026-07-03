ALTER TABLE faturamento.tomadores
    ADD COLUMN IF NOT EXISTS nome_fantasia VARCHAR(255);
