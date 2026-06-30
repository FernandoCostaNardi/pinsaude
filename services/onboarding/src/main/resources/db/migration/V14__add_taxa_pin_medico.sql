-- EPIC-05.9: Taxa Pin negociada por médico (default 15%)
-- Cada médico pode ter um percentual diferente negociado com a Pin Saúde

ALTER TABLE onboarding.medicos
    ADD COLUMN taxa_pin_pct DECIMAL(5,4) NOT NULL DEFAULT 0.1500;

COMMENT ON COLUMN onboarding.medicos.taxa_pin_pct
    IS 'Percentual da Taxa Pin negociado individualmente (ex: 0.1500 = 15%). '
       'O médico recebe sempre valorBruto - (valorBruto * taxa_pin_pct).';

-- Histórico de alterações da taxa negociada
CREATE TABLE onboarding.historico_taxa_pin (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    medico_id       UUID        NOT NULL,
    taxa_anterior   DECIMAL(5,4) NOT NULL,
    taxa_nova       DECIMAL(5,4) NOT NULL,
    alterado_por    VARCHAR(200),
    alterado_em     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_historico_taxa_pin PRIMARY KEY (id),
    CONSTRAINT fk_historico_taxa_pin_medico
        FOREIGN KEY (medico_id) REFERENCES onboarding.medicos(id) ON DELETE CASCADE
);

CREATE INDEX idx_hist_taxa_pin_medico ON onboarding.historico_taxa_pin(medico_id);

ALTER TABLE onboarding.historico_taxa_pin ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding.historico_taxa_pin FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON onboarding.historico_taxa_pin
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

GRANT SELECT, INSERT ON onboarding.historico_taxa_pin TO svc_onboarding;
GRANT SELECT ON onboarding.historico_taxa_pin TO svc_portal;
