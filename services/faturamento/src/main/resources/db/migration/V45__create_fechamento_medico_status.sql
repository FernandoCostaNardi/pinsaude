-- Nova aba "Médicos" na tela de Fechamento por Grupo: mostra todos os médicos alocados ao
-- tomador (faturamento.medico_tomadores, EPIC-15.1) e o valor total a faturar de cada um na
-- competência selecionada. O gestor classifica manualmente cada médico com um status (OK,
-- SEM_FATURAR, NAO_TEVE) — réplica digital da planilha atual (coluna "Status" à esquerda,
-- ver img/medicos.png). O status é só uma anotação manual: nunca influencia o cálculo do
-- fechamento nem bloqueia a execução — puramente informativo para o gestor acompanhar quem já
-- revisou o quê antes de fechar.

CREATE TABLE faturamento.fechamento_medico_status (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id     UUID        NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    medico_id      UUID        NOT NULL,
    competencia    VARCHAR(7)  NOT NULL,
    status         VARCHAR(20) NOT NULL,
    atualizado_por VARCHAR(150),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fechamento_medico_status_status_check CHECK (status IN ('OK', 'SEM_FATURAR', 'NAO_TEVE')),
    CONSTRAINT fechamento_medico_status_competencia_check CHECK (competencia ~ '^\d{4}-\d{2}$'),
    UNIQUE (tomador_id, medico_id, competencia)
);

ALTER TABLE faturamento.fechamento_medico_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.fechamento_medico_status FORCE ROW LEVEL SECURITY;

-- FORCE obrigatório (mesmo motivo documentado em medico_tomadores/tomador_ocorrencias): o app
-- conecta como svc_faturamento, dono da tabela — sem FORCE o owner bypassa a policy.
-- medico_id fica sem FK: Medico é entidade do onboarding (outro serviço/schema), mesmo padrão
-- já usado em producoes.medico_id/medico_tomadores.medico_id/frequencias_medicas.medico_id.
CREATE POLICY tenant_isolation ON faturamento.fechamento_medico_status
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.fechamento_medico_status TO svc_faturamento;

CREATE INDEX idx_fechamento_medico_status_tomador_competencia
    ON faturamento.fechamento_medico_status(tomador_id, competencia);
