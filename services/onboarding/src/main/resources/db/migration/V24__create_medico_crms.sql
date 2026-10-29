-- CRMs: vários por médico, no máximo um por UF, um deles principal.
-- medicos.crm / crm_uf continuam existindo e espelham o CRM principal, porque Portal e
-- telas de listagem já leem esses campos.

CREATE TABLE onboarding.medico_crms (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    medico_id   UUID        NOT NULL,
    numero      VARCHAR(20) NOT NULL,
    uf          CHAR(2)     NOT NULL,
    principal   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_medico_crms PRIMARY KEY (id),
    CONSTRAINT fk_medico_crms_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE,
    -- um CRM por UF para o mesmo médico
    CONSTRAINT uq_medico_crms_medico_uf UNIQUE (medico_id, uf),
    -- o mesmo CRM/UF não pode pertencer a dois médicos
    CONSTRAINT uq_medico_crms_numero_uf UNIQUE (numero, uf)
);

-- no máximo um principal por médico
CREATE UNIQUE INDEX uq_medico_crms_principal ON onboarding.medico_crms (medico_id) WHERE principal;

ALTER TABLE onboarding.medico_crms ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.medico_crms FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.medico_crms
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

GRANT SELECT, INSERT, UPDATE, DELETE ON onboarding.medico_crms TO svc_onboarding;

-- Backfill: o CRM atual de cada médico vira o principal.
INSERT INTO onboarding.medico_crms (medico_id, numero, uf, principal)
SELECT id, crm, crm_uf, TRUE
FROM onboarding.medicos
WHERE crm IS NOT NULL AND crm_uf IS NOT NULL;

-- ─── CPF e CRM opcionais enquanto o médico não está ATIVO ────────────────────
-- Permite cadastrar sócios que ainda não informaram CPF/CRM (importação da planilha de
-- sócios entra como RASCUNHO). Para ATIVO os dois continuam obrigatórios.
ALTER TABLE onboarding.medicos
    ALTER COLUMN cpf_criptografado DROP NOT NULL,
    ALTER COLUMN crm DROP NOT NULL,
    ALTER COLUMN crm_uf DROP NOT NULL;

ALTER TABLE onboarding.medicos
    ADD CONSTRAINT ck_medicos_cpf_crm_ativo CHECK (
        status <> 'ATIVO'
        OR (cpf_criptografado IS NOT NULL AND crm IS NOT NULL AND crm_uf IS NOT NULL)
    );
