-- EPIC-13.1: Grupos de faturamento, Modalidades e Serviços Operacionais por tomador
-- Hospitais que faturam por tipo de serviço (não por médico) usam estes cadastros.
-- Cada grupo vira uma NFS-e com descrição-template e serviço fiscal LC116 próprio.
-- Modalidades = tabela de preços única por tomador (turno + horário + horas + valor + deslocamento).
-- Serviços operacionais = setores/especialidades vinculados a um grupo (não confundir com
-- tomador_servicos, que são vínculos com o catálogo LC116 fiscal, criado na V16).

-- ─── Grupos de faturamento ────────────────────────────────────────────────────
CREATE TABLE faturamento.tomador_grupos_faturamento (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id       UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    servico_lc116_id UUID         NOT NULL REFERENCES faturamento.servicos(id),
    nome             VARCHAR(120) NOT NULL,
    descricao_nota   TEXT         NOT NULL,
    ordem            INT          NOT NULL DEFAULT 0,
    ativo            BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE faturamento.tomador_grupos_faturamento ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomador_grupos_faturamento
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_grupos_faturamento TO svc_faturamento;

-- ─── Modalidades (tabela de preços única por tomador) ─────────────────────────
CREATE TABLE faturamento.tomador_modalidades (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id            UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    nome                  VARCHAR(120) NOT NULL,
    turno                 VARCHAR(10)  NOT NULL CHECK (turno IN ('DIURNO', 'NOTURNO')),
    horario               VARCHAR(30)  NOT NULL,
    horas                 NUMERIC(6,2) NOT NULL CHECK (horas > 0),
    valor_centavos        BIGINT       NOT NULL CHECK (valor_centavos >= 0),
    deslocamento_centavos BIGINT       NOT NULL DEFAULT 0 CHECK (deslocamento_centavos >= 0),
    ativo                 BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE faturamento.tomador_modalidades ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomador_modalidades
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_modalidades TO svc_faturamento;

-- ─── Serviços operacionais (setores) ─────────────────────────────────────────
CREATE TABLE faturamento.tomador_servicos_operacionais (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    grupo_id   UUID         NOT NULL REFERENCES faturamento.tomador_grupos_faturamento(id) ON DELETE CASCADE,
    nome       VARCHAR(150) NOT NULL,
    ativo      BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE faturamento.tomador_servicos_operacionais ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.tomador_servicos_operacionais
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_servicos_operacionais TO svc_faturamento;
