-- Adiciona coluna created_at que faltou na criação da tabela conciliacoes (V13)
-- Registros existentes recebem NOW() como fallback (sem impacto operacional)
ALTER TABLE faturamento.conciliacoes
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
