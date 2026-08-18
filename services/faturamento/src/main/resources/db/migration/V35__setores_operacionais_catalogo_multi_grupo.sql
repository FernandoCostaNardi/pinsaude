-- Setores Operacionais deixam de pertencer a um único Grupo de Faturamento (1:N) e passam a
-- ser um catálogo por tomador, reutilizável em quantos Grupos forem necessários (N:N) — pedido
-- do cliente pra não recadastrar o mesmo setor (ex: "Emergência Cardiológica") em cada grupo.

-- 1. Frequência Médica ganha grupo_id explícito. Necessário porque, com o mesmo setor podendo
--    pertencer a vários grupos, o Fechamento não consegue mais inferir "a qual grupo (= qual
--    NFS-e) esta frequência pertence" só olhando o grupo do setor — a partir de agora isso é
--    decidido explicitamente na criação da frequência (Grupo + Setor, Setor filtrado pelo Grupo).
ALTER TABLE faturamento.frequencias_medicas ADD COLUMN grupo_id UUID;

-- Backfill: cada setor já pertencia a exatamente 1 grupo antes desta migration — preserva o
-- vínculo já existente em toda frequência já lançada.
UPDATE faturamento.frequencias_medicas f
   SET grupo_id = s.grupo_id
  FROM faturamento.tomador_servicos_operacionais s
 WHERE f.servico_operacional_id = s.id;

CREATE INDEX idx_frequencias_medicas_grupo_id ON faturamento.frequencias_medicas (grupo_id);

-- 2. Tabela de vínculo N:N grupo↔setor — substitui a FK direta tomador_servicos_operacionais.grupo_id.
CREATE TABLE faturamento.tomador_grupo_setores (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    grupo_id   UUID        NOT NULL REFERENCES faturamento.tomador_grupos_faturamento(id) ON DELETE CASCADE,
    setor_id   UUID        NOT NULL REFERENCES faturamento.tomador_servicos_operacionais(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (grupo_id, setor_id)
);

ALTER TABLE faturamento.tomador_grupo_setores ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.tomador_grupo_setores FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomador_grupo_setores
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR grupo_id IN (
            SELECT g.id FROM faturamento.tomador_grupos_faturamento g
            JOIN faturamento.tomadores t ON t.id = g.tomador_id
            WHERE t.cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_grupo_setores TO svc_faturamento;

-- Backfill: preserva o vínculo 1:1 anterior como a primeira linha N:N de cada setor.
INSERT INTO faturamento.tomador_grupo_setores (grupo_id, setor_id)
SELECT grupo_id, id FROM faturamento.tomador_servicos_operacionais;

-- 3. Setor deixa de ter grupo próprio — vira catálogo por tomador.
ALTER TABLE faturamento.tomador_servicos_operacionais DROP COLUMN grupo_id;
