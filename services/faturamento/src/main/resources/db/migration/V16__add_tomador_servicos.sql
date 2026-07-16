-- Serviços LC 116/2003 por tomador — permite pré-selecionar quais serviços do catálogo
-- ficam disponíveis na produção deste tomador (mesmo padrão de tomador_cnaes).
-- Na Nova Produção, apenas os serviços vinculados aparecem; se houver só um, é auto-selecionado.
CREATE TABLE faturamento.tomador_servicos (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id    UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    servico_id    UUID         NOT NULL REFERENCES faturamento.servicos(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tomador_id, servico_id)
);

ALTER TABLE faturamento.tomador_servicos ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomador_servicos
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_servicos TO svc_faturamento;
