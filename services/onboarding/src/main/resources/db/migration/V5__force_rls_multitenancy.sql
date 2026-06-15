-- ─── V5: Multi-tenancy — FORCE RLS em todas as tabelas (EPIC-02.5) ────────────
-- Garante que mesmo o owner (svc_onboarding) passe pelas policies RLS.
-- A variável app.current_tenant é definida por TenantAwareDataSource em cada
-- getConnection(); '' (string vazia) significa "gestão — bypass, ver tudo".
-- current_setting(..., TRUE) retorna NULL (não lança) quando a variável não existe.
-- COALESCE(NULL|'', '') = '' → bypass → todas as linhas visíveis para Flyway/gestão.

-- ─── empresas: adiciona FORCE e atualiza policy para suportar bypass ──────────

DROP POLICY IF EXISTS tenant_isolation ON onboarding.empresas;

CREATE POLICY tenant_isolation ON onboarding.empresas
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj = current_setting('app.current_tenant', TRUE)
    );

ALTER TABLE onboarding.empresas FORCE ROW LEVEL SECURITY;

-- ─── configuracoes_fiscais ────────────────────────────────────────────────────

ALTER TABLE onboarding.configuracoes_fiscais ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.configuracoes_fiscais FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.configuracoes_fiscais
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR empresa_id IN (
            SELECT id FROM onboarding.empresas
            WHERE cnpj = current_setting('app.current_tenant', TRUE)
        )
    );

-- ─── contas_bancarias ─────────────────────────────────────────────────────────

ALTER TABLE onboarding.contas_bancarias ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.contas_bancarias FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.contas_bancarias
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR empresa_id IN (
            SELECT id FROM onboarding.empresas
            WHERE cnpj = current_setting('app.current_tenant', TRUE)
        )
    );

-- ─── aliquotas_competencia ────────────────────────────────────────────────────

ALTER TABLE onboarding.aliquotas_competencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.aliquotas_competencia FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.aliquotas_competencia
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR empresa_id IN (
            SELECT id FROM onboarding.empresas
            WHERE cnpj = current_setting('app.current_tenant', TRUE)
        )
    );

-- ─── usuarios_empresas ────────────────────────────────────────────────────────

ALTER TABLE onboarding.usuarios_empresas ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.usuarios_empresas FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.usuarios_empresas
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR empresa_id IN (
            SELECT id FROM onboarding.empresas
            WHERE cnpj = current_setting('app.current_tenant', TRUE)
        )
    );
