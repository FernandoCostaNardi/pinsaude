-- EPIC-05.9: Capturar taxa Pin negociada no momento do registro da produção
-- O percentual é armazenado por participação para preservar o histórico correto
-- mesmo que a taxa seja renegociada no futuro

ALTER TABLE faturamento.participacoes_producao
    ADD COLUMN taxa_pin_pct DECIMAL(5,4) NOT NULL DEFAULT 0.1500;

COMMENT ON COLUMN faturamento.participacoes_producao.taxa_pin_pct
    IS 'Percentual da Taxa Pin vigente no momento do registro (snapshot). '
       'Independe de renegociações futuras.';
