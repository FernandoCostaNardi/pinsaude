-- Pedido do cliente pós-implantação: o cadastro de Setores Operacionais precisa de uma
-- categoria própria (ex: "Emergência", "UTI", "Ambulatório") para organização — texto livre,
-- sem lista fechada, com autocomplete no frontend a partir das categorias já usadas pelo tomador.
ALTER TABLE faturamento.tomador_servicos_operacionais ADD COLUMN categoria VARCHAR(100);
