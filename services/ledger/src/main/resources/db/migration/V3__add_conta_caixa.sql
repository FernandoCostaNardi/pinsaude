-- ─── V3: Conta Caixa e Bancos (EPIC-08.3) ────────────────────────────────────
-- Necessária para os lançamentos automáticos de recebimento (entrada de caixa) e
-- repasse efetuado (saída de caixa) gerados pelos consumers RabbitMQ.
INSERT INTO ledger.contas_ledger (codigo, nome, tipo) VALUES
    ('1.1.02', 'Caixa e Bancos', 'ATIVO');
