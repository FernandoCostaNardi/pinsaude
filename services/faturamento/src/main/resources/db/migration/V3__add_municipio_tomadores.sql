-- ─── V3: Adiciona coluna municipio à tabela tomadores ───────────────────────
-- Necessário para exibir o município na tela de listagem e preencher
-- automaticamente via consulta à Receita Federal (BrasilAPI).

ALTER TABLE faturamento.tomadores
    ADD COLUMN IF NOT EXISTS municipio VARCHAR(100);
