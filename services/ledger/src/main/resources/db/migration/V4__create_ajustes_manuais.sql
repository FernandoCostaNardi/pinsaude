-- ─── V4: Ajustes manuais com dupla aprovação (EPIC-08.4) ─────────────────────
-- Workflow de ajuste contábil: o solicitante cria um ajuste PENDENTE; um segundo
-- usuário, com PERFIL DIFERENTE, aprova — só então o lançamento (imutável) é gerado.
-- Diferente das tabelas do razão, esta é MUTÁVEL (a linha muda ao ser decidida).
CREATE TABLE ledger.ajustes_manuais (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id_tenant        VARCHAR(14)  NOT NULL,
    medico_id             UUID,
    competencia           VARCHAR(7)   NOT NULL,
    conta_debito_codigo   VARCHAR(20)  NOT NULL,
    conta_credito_codigo  VARCHAR(20)  NOT NULL,
    valor_centavos        BIGINT       NOT NULL CHECK (valor_centavos > 0),
    motivo                TEXT         NOT NULL,
    solicitante_id        VARCHAR(100) NOT NULL,
    solicitante_perfil    VARCHAR(20)  NOT NULL,
    aprovador_id          VARCHAR(100),
    aprovador_perfil      VARCHAR(20),
    status                VARCHAR(15)  NOT NULL DEFAULT 'PENDENTE'
                              CHECK (status IN ('PENDENTE', 'APROVADO', 'REJEITADO')),
    lancamento_id         UUID,
    motivo_rejeicao       TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decided_at            TIMESTAMPTZ
);

CREATE INDEX idx_ajustes_status ON ledger.ajustes_manuais (status);
CREATE INDEX idx_ajustes_tenant ON ledger.ajustes_manuais (cnpj_id_tenant);

ALTER TABLE ledger.ajustes_manuais ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger.ajustes_manuais FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ledger.ajustes_manuais
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);
