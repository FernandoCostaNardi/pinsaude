-- EPIC-04.6: Suporte a múltiplos médicos por produção
-- Cria tabela de participações e torna medico_id nullable em producoes

-- 1. Tabela de participações (N médicos por produção)
CREATE TABLE faturamento.participacoes_producao (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    producao_id UUID        NOT NULL,
    medico_id   UUID        NOT NULL,
    valor_bruto BIGINT      NOT NULL CHECK (valor_bruto > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_participacoes PRIMARY KEY (id),
    CONSTRAINT fk_participacoes_producao
        FOREIGN KEY (producao_id) REFERENCES faturamento.producoes(id) ON DELETE CASCADE,
    CONSTRAINT uq_participacao_medico UNIQUE (producao_id, medico_id)
);

CREATE INDEX idx_part_producao ON faturamento.participacoes_producao(producao_id);
CREATE INDEX idx_part_medico   ON faturamento.participacoes_producao(medico_id);

-- RLS: isolamento via JOIN com producoes (que já tem tenant)
ALTER TABLE faturamento.participacoes_producao ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.participacoes_producao FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.participacoes_producao
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR producao_id IN (
            SELECT id FROM faturamento.producoes
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

-- 2. Migrar produções existentes: cada produção single-médico vira uma participação
INSERT INTO faturamento.participacoes_producao (producao_id, medico_id, valor_bruto)
SELECT id, medico_id, valor_bruto
FROM faturamento.producoes
WHERE medico_id IS NOT NULL;

-- 3. medico_id em producoes agora é opcional (breakdown detalhado fica em participacoes)
ALTER TABLE faturamento.producoes ALTER COLUMN medico_id DROP NOT NULL;
