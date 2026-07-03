-- ─── V15: Documentos da Empresa (EPIC-02.6) ──────────────────────────────────
-- Tabela documentos_empresa com 11 tipos + versionamento de Contrato Social.
-- RLS por cnpj do tenant (acesso direto via empresa.cnpj).

CREATE TYPE onboarding.tipo_documento_empresa_enum AS ENUM (
    'CONTRATO_SOCIAL',
    'CONSELHO',
    'ENDERECO_FISCAL',
    'DIRECAO_TECNICA',
    'DOCUMENTACAO_SOCIO_ADMINISTRADOR',
    'NADA_CONSTA_ESTADUAL',
    'CND_FALENCIA',
    'CND_FEDERAL',
    'CND_FGTS',
    'CND_MUNICIPAL',
    'CND_TRABALHISTA'
);

CREATE TABLE onboarding.documentos_empresa (
    id                  UUID                                       NOT NULL DEFAULT gen_random_uuid(),
    empresa_id          UUID                                       NOT NULL,
    tipo                onboarding.tipo_documento_empresa_enum     NOT NULL,
    nome_arquivo        VARCHAR(500)                               NOT NULL,
    caminho_storage     VARCHAR(1000)                              NOT NULL,
    status_validacao    onboarding.status_validacao_documento_enum NOT NULL DEFAULT 'PENDENTE',
    motivo_reprovacao   TEXT,
    -- Somente relevante para CONTRATO_SOCIAL:
    -- true  = versão vigente exibida na listagem principal
    -- false = versão anterior acessível apenas pelo histórico
    versao_atual        BOOLEAN                                    NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ                                NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_documentos_empresa PRIMARY KEY (id),
    CONSTRAINT fk_documentos_empresa FOREIGN KEY (empresa_id)
        REFERENCES onboarding.empresas (id) ON DELETE CASCADE
);

CREATE INDEX idx_documentos_empresa_empresa_id ON onboarding.documentos_empresa (empresa_id);
CREATE INDEX idx_documentos_empresa_tipo       ON onboarding.documentos_empresa (empresa_id, tipo);

-- ─── RLS — documentos_empresa ────────────────────────────────────────────────
-- Isolamento via empresa.cnpj (acesso direto, sem join intermediário).

ALTER TABLE onboarding.documentos_empresa ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.documentos_empresa FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.documentos_empresa
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR empresa_id IN (
            SELECT id FROM onboarding.empresas
            WHERE cnpj = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

-- Permissões
GRANT SELECT, INSERT, UPDATE, DELETE ON onboarding.documentos_empresa TO svc_onboarding;
