-- ─── V19: Declarações LGPD e Assinatura Eletrônica Simples (EPIC-14.1) ───────
-- Tabela 1:1 com medicos para os aceites de LGPD e a assinatura eletrônica
-- simples (nome digitado) coletados no auto-cadastro público (EPIC-14).
-- Mesmo padrão de RLS de dados_civis_medico (V18).

CREATE TABLE onboarding.declaracoes_lgpd_medico (
    medico_id                     UUID          NOT NULL,
    aceite_declaracao_veracidade  BOOLEAN       NOT NULL DEFAULT FALSE,
    autorizacao_uso_dados         BOOLEAN       NOT NULL DEFAULT FALSE,
    autorizacao_compartilhamento  BOOLEAN       NOT NULL DEFAULT FALSE,
    aviso_privacidade_lido        BOOLEAN       NOT NULL DEFAULT FALSE,
    assinatura_nome               VARCHAR(200),
    assinado_em                   TIMESTAMPTZ,
    -- ip_origem: VARCHAR(45) comporta o pior caso de IPv6 (ex: forma mapeada IPv4-em-IPv6).
    ip_origem                     VARCHAR(45),
    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_declaracoes_lgpd_medico PRIMARY KEY (medico_id),
    CONSTRAINT fk_declaracoes_lgpd_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE
);

ALTER TABLE onboarding.declaracoes_lgpd_medico ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.declaracoes_lgpd_medico FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.declaracoes_lgpd_medico
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
