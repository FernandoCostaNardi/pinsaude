-- EPIC-03.6: Fluxo de Onboarding — Convite, Contrato (Clicksign) e Junta Comercial

-- Status da Junta Comercial diretamente na tabela de médicos
ALTER TABLE onboarding.medicos
    ADD COLUMN status_junta_comercial VARCHAR(20) NOT NULL DEFAULT 'AGUARDANDO'
        CHECK (status_junta_comercial IN ('AGUARDANDO','EM_ANALISE','APROVADO','RECUSADO'));

-- Rastreamento de convites enviados ao médico por e-mail
CREATE TABLE onboarding.convites_medico (
    id              UUID         NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    medico_id       UUID         NOT NULL REFERENCES onboarding.medicos(id) ON DELETE CASCADE,
    token           VARCHAR(200) NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE'
                        CHECK (status IN ('PENDENTE','ENVIADO','UTILIZADO','EXPIRADO')),
    email_destino   VARCHAR(200),
    enviado_em      TIMESTAMPTZ,
    expira_em       TIMESTAMPTZ,
    utilizado_em    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_convites_medico_token    ON onboarding.convites_medico(token);
CREATE INDEX idx_convites_medico_medico_id ON onboarding.convites_medico(medico_id);

-- Rastreamento do contrato de assinatura no Clicksign
CREATE TABLE onboarding.contratos_assinatura (
    id               UUID          NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
    medico_id        UUID          NOT NULL REFERENCES onboarding.medicos(id) ON DELETE CASCADE,
    documento_key    VARCHAR(500),
    signatario_key   VARCHAR(500),
    link_assinatura  VARCHAR(2000),
    status           VARCHAR(30)   NOT NULL DEFAULT 'AGUARDANDO_ENVIO'
                         CHECK (status IN ('AGUARDANDO_ENVIO','ENVIADO','VISUALIZADO','ASSINADO','RECUSADO')),
    enviado_em       TIMESTAMPTZ,
    assinado_em      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_contratos_assinatura_medico_id    ON onboarding.contratos_assinatura(medico_id);
CREATE INDEX idx_contratos_assinatura_documento_key ON onboarding.contratos_assinatura(documento_key);

-- RLS: convites_medico
ALTER TABLE onboarding.convites_medico ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.convites_medico FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON onboarding.convites_medico
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR medico_id IN (
            SELECT v.medico_id FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

-- RLS: contratos_assinatura
ALTER TABLE onboarding.contratos_assinatura ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.contratos_assinatura FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON onboarding.contratos_assinatura
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR medico_id IN (
            SELECT v.medico_id FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);
