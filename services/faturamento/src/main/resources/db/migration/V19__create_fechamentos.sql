-- EPIC-13.8: Fechamento — agregação de frequências por grupo → produções
-- Um fechamento por (tomador, competência). Idempotência via UNIQUE.
-- O fechamento agrega os itens das frequencias por grupo → cria uma producao por grupo.

CREATE TABLE faturamento.fechamentos (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id_tenant VARCHAR(20)  NOT NULL,
    tomador_id     UUID         NOT NULL REFERENCES faturamento.tomadores(id),
    competencia    VARCHAR(7)   NOT NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'ABERTO'
                       CHECK (status IN ('ABERTO', 'FECHADO')),
    total_centavos BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fechado_em     TIMESTAMPTZ  NULL,
    UNIQUE (tomador_id, competencia)
);

ALTER TABLE faturamento.fechamentos ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.fechamentos FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.fechamentos
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.fechamentos TO svc_faturamento;
