-- PINSAUDE-13.12: Associação de tomador a empresa(s) Pin Saúde
-- Hoje o tomador nunca é vinculado a uma empresa Pin específica — ao criar Produção/Nota,
-- o operador escolhe a empresa emissora manualmente toda vez. Esta tabela registra quais
-- empresas Pin atendem cada tomador, permitindo auto-selecionar a empresa emissora quando
-- há vínculo cadastrado e, no futuro, relatórios de tomadores por empresa. Um tomador pode
-- ter mais de uma empresa vinculada (casos isolados, mas suportado).
-- empresa_id fica sem FK: Empresa é entidade do onboarding (outro serviço/schema), mesmo
-- padrão já usado em producoes.empresa_id/medico_tomadores.medico_id.

CREATE TABLE faturamento.tomador_empresas (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id UUID        NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    empresa_id UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tomador_id, empresa_id)
);

ALTER TABLE faturamento.tomador_empresas ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.tomador_empresas FORCE ROW LEVEL SECURITY;

-- FORCE obrigatório: mesmo motivo documentado em V21 (medico_tomadores) — o app conecta como
-- svc_faturamento, dono da tabela, e sem FORCE o owner bypassa RLS. WITH CHECK (true) evita
-- bloquear o INSERT (o tomador_id já existe e já pertence ao tenant no momento da inserção).
CREATE POLICY tenant_isolation ON faturamento.tomador_empresas
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_empresas TO svc_faturamento;

CREATE INDEX idx_tomador_empresas_empresa_id ON faturamento.tomador_empresas(empresa_id);
