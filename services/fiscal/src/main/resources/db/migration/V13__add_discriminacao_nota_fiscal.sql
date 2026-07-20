-- EPIC-13.9: adiciona campo de discriminacao/descricao da NFS-e
-- Permite propagar descricao_complementar da producao (grupo de faturamento) para a nota fiscal
ALTER TABLE fiscal.notas_fiscais ADD COLUMN discriminacao TEXT;
