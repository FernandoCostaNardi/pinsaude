-- CNAEs por tomador — permite múltiplos CNAEs por tomador; selecionado na emissão da NFS-e
CREATE TABLE faturamento.tomador_cnaes (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id    UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    codigo_cnae   VARCHAR(20)  NOT NULL,
    descricao     VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tomador_id, codigo_cnae)
);

ALTER TABLE faturamento.tomador_cnaes ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomador_cnaes
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_cnaes TO svc_faturamento;
