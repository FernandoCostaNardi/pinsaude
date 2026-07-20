-- EPIC-13.3: Frequência Médica — cabeçalho + itens por data
-- Uma frequência = um documento oficial por médico + setor + competência.
-- Itens = linhas do plantão (data + modalidade), fonte do valor e do PDF.
-- Após o fechamento (EPIC-13.8), frequencias viram produções no faturamento.

-- ─── Cabeçalho ────────────────────────────────────────────────────────────────
CREATE TABLE faturamento.frequencias_medicas (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id_tenant         VARCHAR(20)  NOT NULL,
    tomador_id             UUID         NOT NULL REFERENCES faturamento.tomadores(id),
    medico_id              UUID         NOT NULL,
    servico_operacional_id UUID         NOT NULL REFERENCES faturamento.tomador_servicos_operacionais(id),
    competencia            VARCHAR(7)   NOT NULL,   -- YYYY-MM
    especialidade          VARCHAR(120) NOT NULL,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'RASCUNHO'
                               CHECK (status IN (
                                   'RASCUNHO',
                                   'PDF_GERADO',
                                   'AGUARDANDO_ASSINATURA',
                                   'ASSINADA_RECEBIDA',
                                   'ENVIADA_TOMADOR',
                                   'FATURADA'
                               )),
    documento_assinado_key VARCHAR(500) NULL,
    enviada_tomador_em     TIMESTAMPTZ  NULL,
    fechamento_id          UUID         NULL,
    producao_id            UUID         NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (medico_id, servico_operacional_id, competencia)
);

ALTER TABLE faturamento.frequencias_medicas ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.frequencias_medicas FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.frequencias_medicas
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.frequencias_medicas TO svc_faturamento;

-- ─── Itens (linhas do plantão) ────────────────────────────────────────────────
CREATE TABLE faturamento.frequencia_itens (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    frequencia_id           UUID        NOT NULL REFERENCES faturamento.frequencias_medicas(id) ON DELETE CASCADE,
    modalidade_id           UUID        NOT NULL REFERENCES faturamento.tomador_modalidades(id),
    data_execucao           DATE        NOT NULL,
    ocorrencia              VARCHAR(120) NULL,
    -- Snapshot dos preços da modalidade no momento do lançamento
    valor_unitario_centavos BIGINT      NOT NULL CHECK (valor_unitario_centavos >= 0),
    deslocamento_centavos   BIGINT      NOT NULL DEFAULT 0 CHECK (deslocamento_centavos >= 0),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE faturamento.frequencia_itens ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.frequencia_itens FORCE ROW LEVEL SECURITY;

-- Isolamento via subquery: item → frequencia → cnpj_id_tenant
CREATE POLICY tenant_isolation ON faturamento.frequencia_itens
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR frequencia_id IN (
            SELECT id FROM faturamento.frequencias_medicas
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.frequencia_itens TO svc_faturamento;
