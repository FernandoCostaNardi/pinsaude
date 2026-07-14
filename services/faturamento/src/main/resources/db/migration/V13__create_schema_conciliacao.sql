-- ─── V13: Schema de Conciliação Bancária (EPIC-07.1) ──────────────────────────
-- Tabelas: extratos_bancarios, lancamentos_extrato, conciliacoes
-- Padrões:
--   - Valores monetários em centavos (BIGINT)
--   - RLS com FORCE em todas as tabelas por cnpj_id_tenant
--   - lancamentos_extrato: isolamento via subquery em extratos_bancarios
--   - conciliacoes: isolamento via subquery em lancamentos_extrato → extratos_bancarios
--   - score_match e score_confianca com constraint CHECK (0-100)
--   - nota_id em conciliacoes é FK lógica para fiscal.notas_fiscais (cross-service, sem FK explícita)

-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE faturamento.status_importacao_enum AS ENUM (
    'PROCESSANDO',
    'OK',
    'ERRO'
);

CREATE TYPE faturamento.tipo_lancamento_enum AS ENUM (
    'CREDITO',
    'DEBITO'
);

CREATE TYPE faturamento.status_conciliacao_enum AS ENUM (
    'PENDENTE',
    'CONCILIADO',
    'IGNORADO'
);

CREATE TYPE faturamento.tipo_match_enum AS ENUM (
    'AUTOMATICO',
    'MANUAL'
);

-- ─── Tabela: extratos_bancarios ───────────────────────────────────────────────
-- Representa um arquivo de extrato bancário importado por um tenant.
-- periodo_inicio/fim: janela de datas cobertas pelo extrato.
-- total_lancamentos: atualizado pelo serviço após processamento do arquivo.
-- created_by: email ou ID do usuário que fez o upload (do JWT).

CREATE TABLE faturamento.extratos_bancarios (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    cnpj_id_tenant      VARCHAR(14) NOT NULL,
    data_upload         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    nome_arquivo        VARCHAR(255) NOT NULL,
    periodo_inicio      DATE        NOT NULL,
    periodo_fim         DATE        NOT NULL,
    status_importacao   faturamento.status_importacao_enum NOT NULL DEFAULT 'PROCESSANDO',
    total_lancamentos   INTEGER     NOT NULL DEFAULT 0,
    created_by          VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_extratos_bancarios    PRIMARY KEY (id),
    CONSTRAINT chk_extratos_periodo     CHECK (periodo_fim >= periodo_inicio)
);

CREATE INDEX idx_extratos_tenant    ON faturamento.extratos_bancarios (cnpj_id_tenant);
CREATE INDEX idx_extratos_periodo   ON faturamento.extratos_bancarios (periodo_inicio, periodo_fim);
CREATE INDEX idx_extratos_status    ON faturamento.extratos_bancarios (status_importacao);

ALTER TABLE faturamento.extratos_bancarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.extratos_bancarios FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.extratos_bancarios
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);

-- ─── Tabela: lancamentos_extrato ──────────────────────────────────────────────
-- Cada linha de um extrato bancário importado.
-- valor em centavos: positivo para crédito, negativo para débito.
-- identificador_externo: código único do banco (evita reimportação de duplicatas).
-- score_match: confiança do matching automático (0 = sem candidato, 100 = perfeito).
-- RLS via subquery em extratos_bancarios — sem coluna cnpj_id_tenant direta.

CREATE TABLE faturamento.lancamentos_extrato (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    extrato_id              UUID        NOT NULL,
    data_lancamento         DATE        NOT NULL,
    descricao               TEXT        NOT NULL,
    valor                   BIGINT      NOT NULL,
    tipo                    faturamento.tipo_lancamento_enum NOT NULL,
    identificador_externo   VARCHAR(100),
    status_conciliacao      faturamento.status_conciliacao_enum NOT NULL DEFAULT 'PENDENTE',
    score_match             INTEGER     NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_lancamentos_extrato   PRIMARY KEY (id),
    CONSTRAINT fk_lancamento_extrato    FOREIGN KEY (extrato_id)
        REFERENCES faturamento.extratos_bancarios(id) ON DELETE CASCADE,
    CONSTRAINT chk_score_match          CHECK (score_match BETWEEN 0 AND 100)
);

CREATE INDEX idx_lancamentos_extrato_id ON faturamento.lancamentos_extrato (extrato_id);
CREATE INDEX idx_lancamentos_status     ON faturamento.lancamentos_extrato (status_conciliacao);
CREATE INDEX idx_lancamentos_data       ON faturamento.lancamentos_extrato (data_lancamento);
CREATE INDEX idx_lancamentos_externo    ON faturamento.lancamentos_extrato (identificador_externo)
    WHERE identificador_externo IS NOT NULL;

ALTER TABLE faturamento.lancamentos_extrato ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.lancamentos_extrato FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.lancamentos_extrato
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR extrato_id IN (
            SELECT id FROM faturamento.extratos_bancarios
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

-- ─── Tabela: conciliacoes ─────────────────────────────────────────────────────
-- Associação entre um lançamento do extrato e uma nota fiscal emitida.
-- nota_id: FK lógica para fiscal.notas_fiscais — cross-service, sem FK explícita.
-- UNIQUE (lancamento_extrato_id): um lançamento só pode ter uma conciliação ativa.
-- usuario_id: preenchido apenas em conciliações manuais (MANUAL).
-- score_confianca: herdado do score_match no momento da conciliação automática.
-- RLS via subquery em lancamentos_extrato → extratos_bancarios.

CREATE TABLE faturamento.conciliacoes (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    lancamento_extrato_id   UUID        NOT NULL,
    nota_id                 UUID        NOT NULL,
    tipo_match              faturamento.tipo_match_enum NOT NULL,
    score_confianca         INTEGER     NOT NULL DEFAULT 0,
    data_conciliacao        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_id              VARCHAR(255),
    observacao              TEXT,

    CONSTRAINT pk_conciliacoes              PRIMARY KEY (id),
    CONSTRAINT fk_conciliacao_lancamento    FOREIGN KEY (lancamento_extrato_id)
        REFERENCES faturamento.lancamentos_extrato(id) ON DELETE CASCADE,
    CONSTRAINT uq_conciliacao_lancamento    UNIQUE (lancamento_extrato_id),
    CONSTRAINT chk_score_confianca          CHECK (score_confianca BETWEEN 0 AND 100)
);

CREATE INDEX idx_conciliacoes_lancamento    ON faturamento.conciliacoes (lancamento_extrato_id);
CREATE INDEX idx_conciliacoes_nota          ON faturamento.conciliacoes (nota_id);
CREATE INDEX idx_conciliacoes_tipo          ON faturamento.conciliacoes (tipo_match);
CREATE INDEX idx_conciliacoes_data          ON faturamento.conciliacoes (data_conciliacao);

ALTER TABLE faturamento.conciliacoes ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.conciliacoes FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.conciliacoes
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR lancamento_extrato_id IN (
            SELECT le.id
            FROM faturamento.lancamentos_extrato le
            JOIN faturamento.extratos_bancarios eb ON eb.id = le.extrato_id
            WHERE eb.cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);
