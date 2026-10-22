-- Contatos do tomador: separa contato do endereço e permite N contatos por tomador.
-- Antes, o tomador tinha um único e-mail/telefone gravado junto com o endereço (V6). Agora cada
-- tomador pode ter vários contatos (ex.: "Financeiro" → Fernando; "Compras" → Fulano), cada um
-- com apelido (identifica o contato na lista), nome da pessoa, e-mail, telefone e observação livre.
-- Regra: todo contato precisa ter e-mail OU telefone (pelo menos um dos dois).

CREATE TABLE faturamento.tomador_contatos (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    apelido    VARCHAR(80)  NOT NULL,
    nome       VARCHAR(150),
    email      VARCHAR(255),
    telefone   VARCHAR(30),
    observacao TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tomador_contatos_email_ou_telefone_check CHECK (
        NULLIF(BTRIM(email), '') IS NOT NULL OR NULLIF(BTRIM(telefone), '') IS NOT NULL
    )
);

ALTER TABLE faturamento.tomador_contatos ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.tomador_contatos FORCE ROW LEVEL SECURITY;

-- FORCE obrigatório: mesmo motivo documentado em V21 (medico_tomadores) — o app conecta como
-- svc_faturamento, dono da tabela, e sem FORCE o owner bypassa RLS.
CREATE POLICY tenant_isolation ON faturamento.tomador_contatos
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_contatos TO svc_faturamento;

CREATE INDEX idx_tomador_contatos_tomador_id ON faturamento.tomador_contatos(tomador_id);

-- ─── Migração dos contatos existentes ───────────────────────────────────────────
-- O e-mail/telefone que estava no próprio tomador vira um contato "Principal".
INSERT INTO faturamento.tomador_contatos (tomador_id, apelido, email, telefone, created_at)
SELECT id,
       'Principal',
       NULLIF(BTRIM(email), ''),
       NULLIF(BTRIM(telefone), ''),
       created_at
FROM faturamento.tomadores
WHERE NULLIF(BTRIM(email), '') IS NOT NULL
   OR NULLIF(BTRIM(telefone), '') IS NOT NULL;

ALTER TABLE faturamento.tomadores
    DROP COLUMN email,
    DROP COLUMN telefone;
