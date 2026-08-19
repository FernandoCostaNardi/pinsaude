-- Pedido do cliente: tomador ganha uma configuração explícita "exige controle de frequência" e,
-- quando marcada, cada médico alocado a esse tomador passa a ter os Setores Operacionais em que
-- atua explicitamente atribuídos (um ou mais) — usado para filtrar o combo de Setor no Portal do
-- Médico ao criar uma nova competência de Frequência.

-- 1. Flag no cadastro do Tomador. Default false preserva o comportamento atual para todo tomador
--    já cadastrado (não exige seleção de setor por médico, catálogo completo continua visível).
ALTER TABLE faturamento.tomadores ADD COLUMN exige_frequencia BOOLEAN NOT NULL DEFAULT false;

-- 2. Vínculo N:N médico-alocado (medico_tomadores) ↔ setor operacional — mesmo padrão de
--    tomador_grupo_setores (V35): FORCE ROW LEVEL SECURITY obrigatório (app conecta como
--    svc_faturamento, dono da tabela — sem FORCE o owner bypassa a policy), WITH CHECK (true)
--    para não bloquear o INSERT (a linha pai medico_tomadores já existe e já pertence ao tenant
--    no momento da inserção).
CREATE TABLE faturamento.medico_tomador_setores (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    medico_tomador_id UUID        NOT NULL REFERENCES faturamento.medico_tomadores(id) ON DELETE CASCADE,
    setor_id          UUID        NOT NULL REFERENCES faturamento.tomador_servicos_operacionais(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (medico_tomador_id, setor_id)
);

ALTER TABLE faturamento.medico_tomador_setores ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.medico_tomador_setores FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.medico_tomador_setores
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR medico_tomador_id IN (
            SELECT mt.id FROM faturamento.medico_tomadores mt
            JOIN faturamento.tomadores t ON t.id = mt.tomador_id
            WHERE t.cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.medico_tomador_setores TO svc_faturamento;

CREATE INDEX idx_medico_tomador_setores_medico_tomador_id ON faturamento.medico_tomador_setores (medico_tomador_id);

-- Nota: svc_portal já enxerga esta tabela automaticamente via
-- ALTER DEFAULT PRIVILEGES FOR ROLE svc_faturamento IN SCHEMA faturamento GRANT SELECT ON TABLES TO svc_portal
-- (tools/db/init.sql) — nenhum GRANT adicional necessário aqui.
