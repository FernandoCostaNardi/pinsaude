-- ─── V18: Dados Civis do Médico (EPIC-14.1) ──────────────────────────────────
-- Tabela 1:1 com medicos para os campos civis/profissionais coletados no
-- auto-cadastro público (EPIC-14) que não existem na entidade Medico hoje.
-- RLS segue o mesmo padrão de checklist_conduta (join via vinculos_medico_empresa),
-- já com WITH CHECK (true) desde a criação — não precisa do fix em 2 etapas do V7,
-- pois a tabela nasce sem o problema de INSERT bloqueado.

CREATE TYPE onboarding.estado_civil_enum AS ENUM (
    'SOLTEIRO',
    'CASADO_COMUNHAO_PARCIAL',
    'CASADO_SEPARACAO_TOTAL',
    'CASADO_COMUNHAO_UNIVERSAL',
    'UNIAO_ESTAVEL',
    'DIVORCIADO',
    'VIUVO',
    'PARTICIPACAO_FINAL_AQUESTOS',
    'OUTRO'
);

CREATE TABLE onboarding.dados_civis_medico (
    medico_id              UUID                      NOT NULL,
    data_nascimento        DATE,
    nacionalidade          VARCHAR(100),
    naturalidade           VARCHAR(150),
    estado_civil           onboarding.estado_civil_enum,
    nome_mae               VARCHAR(200),
    nome_pai               VARCHAR(200),
    logradouro             VARCHAR(255),
    numero                 VARCHAR(20),
    complemento            VARCHAR(100),
    bairro                 VARCHAR(100),
    cidade                 VARCHAR(150),
    uf                     CHAR(2),
    cep                    VARCHAR(9),
    rg_numero              VARCHAR(20),
    rg_orgao_expedidor     VARCHAR(20),
    rg_uf                  CHAR(2),
    rqe                    VARCHAR(20),
    -- canal_origem: texto livre (Google/Instagram/Facebook/Indicação/Outro) em vez de
    -- enum — mantém extensível a novos canais de marketing sem exigir nova migration.
    canal_origem           VARCHAR(50),
    -- nome_indicador só é preenchido quando canal_origem = 'Indicação'.
    nome_indicador         VARCHAR(200),
    situacao_formacao      TEXT[],
    areas_atuacao          TEXT,
    procedimentos_realiza  TEXT,
    created_at             TIMESTAMPTZ               NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ               NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dados_civis_medico PRIMARY KEY (medico_id),
    CONSTRAINT fk_dados_civis_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE
);

ALTER TABLE onboarding.dados_civis_medico ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.dados_civis_medico FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.dados_civis_medico
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
