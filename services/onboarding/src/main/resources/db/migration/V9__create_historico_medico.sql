-- ─── Histórico de ações do médico (EPIC-03.5) ────────────────────────────────

CREATE TABLE onboarding.historico_medico (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    medico_id   UUID        NOT NULL REFERENCES onboarding.medicos(id) ON DELETE CASCADE,
    tipo_acao   VARCHAR(50) NOT NULL,
    descricao   TEXT        NOT NULL,
    usuario     VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_historico_medico_medico_id
    ON onboarding.historico_medico(medico_id, created_at DESC);

-- ─── RLS ─────────────────────────────────────────────────────────────────────

ALTER TABLE onboarding.historico_medico ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.historico_medico FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.historico_medico
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR medico_id IN (
            SELECT v.medico_id
            FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);
