-- Especialidades: catálogo + uma ou mais por médico (planilha de sócios, colunas
-- ESPECIALIDADE 1/2/3). medicos.especialidade continua existindo e espelha a primeira
-- especialidade da lista, porque Portal, perfil, frequências e produção já leem esse campo.

-- ─── Catálogo ────────────────────────────────────────────────────────────────
-- Global (sem tenant): é uma lista de referência compartilhada por todas as empresas.
CREATE TABLE onboarding.especialidades (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    nome        VARCHAR(100) NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_especialidades PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_especialidades_nome ON onboarding.especialidades (LOWER(nome));

-- Lista usada pela Pin na planilha de cadastro de sócios.
INSERT INTO onboarding.especialidades (nome) VALUES
    ('Arritmologia'),
    ('Cardiologia'),
    ('Cirurgia Cardíaca'),
    ('Cirurgia Geral'),
    ('Cirurgia Vascular'),
    ('Clínica Geral'),
    ('Ecocardiografia'),
    ('Ginecologia e Obstetrícia'),
    ('Hemodinâmica'),
    ('Medicina Intensiva'),
    ('Nefrologia'),
    ('Odontologia'),
    ('Oncologia'),
    ('Reumatologia'),
    ('Ultrassom Vascular');

-- Especialidades em texto livre já gravadas em médicos existentes entram no catálogo.
INSERT INTO onboarding.especialidades (nome)
SELECT DISTINCT ON (LOWER(BTRIM(m.especialidade))) BTRIM(m.especialidade)
FROM onboarding.medicos m
WHERE m.especialidade IS NOT NULL AND BTRIM(m.especialidade) <> ''
ON CONFLICT DO NOTHING;

GRANT SELECT, INSERT, UPDATE ON onboarding.especialidades TO svc_onboarding;

-- ─── Médico × especialidade ──────────────────────────────────────────────────
-- ordem 1 = principal (espelhada em medicos.especialidade).
CREATE TABLE onboarding.medico_especialidades (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    medico_id         UUID        NOT NULL,
    especialidade_id  UUID        NOT NULL,
    ordem             SMALLINT    NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_medico_especialidades PRIMARY KEY (id),
    CONSTRAINT fk_medico_especialidades_medico FOREIGN KEY (medico_id)
        REFERENCES onboarding.medicos (id) ON DELETE CASCADE,
    CONSTRAINT fk_medico_especialidades_especialidade FOREIGN KEY (especialidade_id)
        REFERENCES onboarding.especialidades (id),
    CONSTRAINT uq_medico_especialidades UNIQUE (medico_id, especialidade_id)
);

CREATE INDEX idx_medico_especialidades_medico ON onboarding.medico_especialidades (medico_id, ordem);

ALTER TABLE onboarding.medico_especialidades ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.medico_especialidades FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.medico_especialidades
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

GRANT SELECT, INSERT, UPDATE, DELETE ON onboarding.medico_especialidades TO svc_onboarding;

-- Backfill: a especialidade atual de cada médico vira a primeira da lista.
INSERT INTO onboarding.medico_especialidades (medico_id, especialidade_id, ordem)
SELECT m.id, e.id, 1
FROM onboarding.medicos m
JOIN onboarding.especialidades e ON LOWER(e.nome) = LOWER(BTRIM(m.especialidade))
WHERE m.especialidade IS NOT NULL AND BTRIM(m.especialidade) <> '';
