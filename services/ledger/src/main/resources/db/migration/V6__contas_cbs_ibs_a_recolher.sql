-- ─── V6: Contas de CBS e IBS a recolher (CBS-40, reforma tributária) ─────────
-- CBS e IBS não são retenção do tomador (2.1.01): são débito próprio da Pin sobre a nota,
-- apurado e recolhido por ela. Ficam em contas de passivo separadas para a apuração mensal
-- (CBS-61). Códigos provisórios, a validar com o contador.
INSERT INTO ledger.contas_ledger (codigo, nome, tipo) VALUES
    ('2.1.03', 'CBS a Recolher', 'PASSIVO'),
    ('2.1.04', 'IBS a Recolher', 'PASSIVO')
ON CONFLICT (codigo) DO NOTHING;
