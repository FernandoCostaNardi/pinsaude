-- Alíquotas fiscais específicas por tomador (override das alíquotas do catálogo de serviços)
-- UNIQUE (tomador_id, tipo_tributo) — upsert: um registro por tributo por tomador
CREATE TABLE faturamento.tomador_aliquotas (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id      UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    tipo_tributo    VARCHAR(10)  NOT NULL CHECK (tipo_tributo IN ('ISS','IR','CSLL','PIS','COFINS')),
    valor_aliquota  NUMERIC(8,4) NOT NULL,  -- percentual: 5.0000 = 5%
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tomador_id, tipo_tributo)
);

ALTER TABLE faturamento.tomador_aliquotas ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomador_aliquotas
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_aliquotas TO svc_faturamento;
