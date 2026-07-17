-- ─── V2: Converte os enums nativos para VARCHAR + CHECK (EPIC-08.2) ───────────
-- Motivo: Hibernate 6 envia enums como "character varying" nas cláusulas WHERE e o
-- PostgreSQL não faz cast automático (ERRO: operator does not exist: enum = varchar).
-- A API do ledger filtra lançamentos por tipo_origem, então o enum nativo quebraria a
-- consulta paginada. Padrão já adotado no serviço fiscal (V6) e recomendado no CLAUDE.md.
--
-- As triggers de equilíbrio e imutabilidade comparam com literais de texto ('DEBITO',
-- 'CREDITO'), que continuam válidos com colunas VARCHAR — nenhuma trigger precisa mudar.
-- Nenhuma coluna tem DEFAULT referenciando o enum, então DROP TYPE não falha.

-- contas_ledger.tipo
ALTER TABLE ledger.contas_ledger
    ALTER COLUMN tipo TYPE VARCHAR(15) USING tipo::text;
ALTER TABLE ledger.contas_ledger
    ADD CONSTRAINT ck_contas_tipo
    CHECK (tipo IN ('ATIVO', 'PASSIVO', 'RECEITA', 'DESPESA', 'INTERMEDIARIO'));

-- lancamentos_ledger.tipo_origem
ALTER TABLE ledger.lancamentos_ledger
    ALTER COLUMN tipo_origem TYPE VARCHAR(15) USING tipo_origem::text;
ALTER TABLE ledger.lancamentos_ledger
    ADD CONSTRAINT ck_lancamentos_tipo_origem
    CHECK (tipo_origem IN ('NOTA', 'CONCILIACAO', 'REPASSE', 'AJUSTE'));

-- partidas_ledger.tipo
ALTER TABLE ledger.partidas_ledger
    ALTER COLUMN tipo TYPE VARCHAR(10) USING tipo::text;
ALTER TABLE ledger.partidas_ledger
    ADD CONSTRAINT ck_partidas_tipo
    CHECK (tipo IN ('DEBITO', 'CREDITO'));

-- Remove os tipos enum agora órfãos
DROP TYPE ledger.tipo_conta_enum;
DROP TYPE ledger.tipo_origem_enum;
DROP TYPE ledger.tipo_partida_enum;
