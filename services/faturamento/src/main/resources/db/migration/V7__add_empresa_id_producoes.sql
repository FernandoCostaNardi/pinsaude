-- Adiciona empresa_id em producoes para registrar qual empresa (filial) emite a NFS-e.
-- Nullable para retrocompatibilidade com produções já existentes.
ALTER TABLE faturamento.producoes
    ADD COLUMN empresa_id UUID;
