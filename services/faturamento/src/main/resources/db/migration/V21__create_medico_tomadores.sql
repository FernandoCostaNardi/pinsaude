-- EPIC-15.1: Alocação de médico a tomadores
-- Hoje o médico é vinculado a empresas Pin (onboarding.vinculos_medico_empresa) só para saber
-- por onde emitir a NFS-e, mas nunca a tomadores (clientes hospitalares/clínicas). Isso fazia
-- as telas de Produção e Frequência (admin e Portal do Médico) mostrarem todos os tomadores do
-- tenant para qualquer médico. Esta tabela registra quais tomadores cada médico está autorizado
-- a atender — passa a alimentar filtros de UI e validações de bloqueio (EPIC-15.7/15.8).
-- medico_id fica sem FK: Medico é entidade do onboarding (outro serviço/schema), mesmo padrão
-- já usado em producoes.medico_id/participacoes_producao.medico_id/frequencias_medicas.medico_id.

CREATE TABLE faturamento.medico_tomadores (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id UUID        NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    medico_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tomador_id, medico_id)
);

ALTER TABLE faturamento.medico_tomadores ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.medico_tomadores FORCE ROW LEVEL SECURITY;

-- FORCE é obrigatório aqui: o app conecta como svc_faturamento, que é o MESMO usuário dono da
-- tabela (Flyway roda com o datasource da aplicação) — sem FORCE, o owner bypassa RLS e a
-- política vira letra morta para o próprio serviço. Confirmado que tomador_grupos_faturamento/
-- tomador_modalidades/tomador_servicos_operacionais (V17) NÃO têm FORCE e por isso sofrem do
-- mesmo bypass; o padrão correto (usado em producoes/participacoes_producao/conciliacoes/
-- frequencias_medicas) sempre inclui FORCE. WITH CHECK (true) evita bloquear o INSERT: o
-- tomador_id referenciado já existe e já pertence ao tenant antes deste insert (sem o
-- problema de "USING sem WITH CHECK" documentado no CLAUDE.md para tabelas com cnpj próprio).
CREATE POLICY tenant_isolation ON faturamento.medico_tomadores
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.medico_tomadores TO svc_faturamento;

CREATE INDEX idx_medico_tomadores_medico_id ON faturamento.medico_tomadores(medico_id);
