-- Correção pós-implantação: o cadastro de Setor Operacional deve permitir vincular MAIS DE UMA
-- Modalidade de referência (V38 só permitia 1, via FK direta modalidade_id). Quando o setor tem
-- mais de uma modalidade cadastrada, a tela de Nova Frequência volta a perguntar qual será usada
-- (Tipo de Escala e/ou Modalidade específica, conforme o caso) — ver CLAUDE.md.
--
-- Mesmo padrão já usado em V35 (setores deixaram de ter grupo_id direto, viraram N:N via
-- tomador_grupo_setores): FORCE ROW LEVEL SECURITY obrigatório (app conecta como
-- svc_faturamento, dono da tabela — sem FORCE o owner bypassa a policy), WITH CHECK (true) pra
-- não bloquear o INSERT (a linha pai setor já existe e já pertence ao tenant no momento da
-- inserção).

-- 1. Tabela de vínculo N:N setor↔modalidade.
CREATE TABLE faturamento.setor_operacional_modalidades (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    setor_id      UUID        NOT NULL REFERENCES faturamento.tomador_servicos_operacionais(id) ON DELETE CASCADE,
    modalidade_id UUID        NOT NULL REFERENCES faturamento.tomador_modalidades(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (setor_id, modalidade_id)
);

ALTER TABLE faturamento.setor_operacional_modalidades ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.setor_operacional_modalidades FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.setor_operacional_modalidades
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR setor_id IN (
            SELECT s.id FROM faturamento.tomador_servicos_operacionais s
            JOIN faturamento.tomadores t ON t.id = s.tomador_id
            WHERE t.cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.setor_operacional_modalidades TO svc_faturamento;

CREATE INDEX idx_setor_operacional_modalidades_setor_id ON faturamento.setor_operacional_modalidades (setor_id);

-- 2. Backfill: preserva o vínculo único de cada setor (V38) como a primeira linha N:N.
INSERT INTO faturamento.setor_operacional_modalidades (setor_id, modalidade_id)
SELECT id, modalidade_id FROM faturamento.tomador_servicos_operacionais
WHERE modalidade_id IS NOT NULL;

-- 3. Setor deixa de ter modalidade própria — vira catálogo N:N.
ALTER TABLE faturamento.tomador_servicos_operacionais DROP COLUMN modalidade_id;
