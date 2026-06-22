-- Denormaliza nome do tomador para exibição em listagens sem join cross-service
ALTER TABLE fiscal.notas_fiscais ADD COLUMN IF NOT EXISTS tomador_nome VARCHAR(200);
