-- ─── V1: Schema do Ledger — Partidas Dobradas (EPIC-08.1) ────────────────────
-- Livro-razão financeiro com partidas dobradas (double-entry bookkeeping).
--
-- Invariantes garantidas no nível do banco:
--   1. EQUILÍBRIO   — para cada lançamento, SUM(DEBITO) = SUM(CREDITO)
--                     (constraint trigger DEFERRABLE — validada no COMMIT)
--   2. IMUTABILIDADE — lancamentos_ledger e partidas_ledger são append-only:
--                     UPDATE e DELETE são bloqueados por trigger (livro-razão não se altera)
--   3. IDEMPOTÊNCIA  — correlation_id é NOT NULL UNIQUE: reprocessar o mesmo evento
--                     (ex.: mesma NFS-e) não duplica lançamentos
--   4. MULTI-TENANCY — RLS por cnpj_id_tenant (EPIC-02.5). Partidas isolam via subquery.
--
-- Convenções:
--   - Valores monetários em centavos (BIGINT) — sem ponto flutuante
--   - competencia no formato YYYY-MM (ex.: '2026-07')
--   - contas_ledger é catálogo compartilhado (plano de contas) — sem RLS
--   - gen_random_uuid() é nativo do PostgreSQL 13+ (não requer extensão)

-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE ledger.tipo_conta_enum AS ENUM (
    'ATIVO',
    'PASSIVO',
    'RECEITA',
    'DESPESA',
    'INTERMEDIARIO'
);

CREATE TYPE ledger.tipo_origem_enum AS ENUM (
    'NOTA',
    'CONCILIACAO',
    'REPASSE',
    'AJUSTE'
);

CREATE TYPE ledger.tipo_partida_enum AS ENUM (
    'DEBITO',
    'CREDITO'
);

-- ─── Tabela: contas_ledger (plano de contas — catálogo compartilhado) ─────────
-- Sem RLS: o plano de contas é comum a todos os tenants.

CREATE TABLE ledger.contas_ledger (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    codigo      VARCHAR(20) NOT NULL,
    nome        VARCHAR(120) NOT NULL,
    tipo        ledger.tipo_conta_enum NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_contas_ledger PRIMARY KEY (id),
    CONSTRAINT uq_contas_ledger_codigo UNIQUE (codigo)
);

-- ─── Tabela: lancamentos_ledger (cabeçalho — IMUTÁVEL) ────────────────────────
-- Cada lançamento agrupa 2+ partidas equilibradas.
-- correlation_id: chave de idempotência (ex.: "NOTA:<uuid-nfse>"). NOT NULL UNIQUE.
-- origem_id: UUID da entidade de origem (NFS-e, conciliação, repasse) — nullable p/ AJUSTE.
-- medico_id: nullable (lançamentos institucionais sem médico específico).

CREATE TABLE ledger.lancamentos_ledger (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    cnpj_id_tenant  VARCHAR(14) NOT NULL,
    medico_id       UUID,
    data_lancamento DATE        NOT NULL DEFAULT CURRENT_DATE,
    competencia     VARCHAR(7)  NOT NULL,
    tipo_origem     ledger.tipo_origem_enum NOT NULL,
    origem_id       UUID,
    descricao       TEXT        NOT NULL,
    correlation_id  VARCHAR(120) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_lancamentos_ledger PRIMARY KEY (id),
    CONSTRAINT uq_lancamentos_correlation UNIQUE (correlation_id)
);

CREATE INDEX idx_lancamentos_tenant      ON ledger.lancamentos_ledger (cnpj_id_tenant);
CREATE INDEX idx_lancamentos_competencia ON ledger.lancamentos_ledger (competencia);
CREATE INDEX idx_lancamentos_medico      ON ledger.lancamentos_ledger (medico_id);
CREATE INDEX idx_lancamentos_origem      ON ledger.lancamentos_ledger (tipo_origem, origem_id);

ALTER TABLE ledger.lancamentos_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger.lancamentos_ledger FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ledger.lancamentos_ledger
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);

-- ─── Tabela: partidas_ledger (débitos e créditos) ─────────────────────────────
-- valor_centavos > 0 sempre; o sinal contábil é dado pelo campo tipo (DEBITO/CREDITO).
-- Sem coluna de tenant: isolamento via subquery no lançamento pai.

CREATE TABLE ledger.partidas_ledger (
    id              UUID   NOT NULL DEFAULT gen_random_uuid(),
    lancamento_id   UUID   NOT NULL,
    conta_id        UUID   NOT NULL,
    tipo            ledger.tipo_partida_enum NOT NULL,
    valor_centavos  BIGINT NOT NULL,

    CONSTRAINT pk_partidas_ledger PRIMARY KEY (id),
    CONSTRAINT fk_partidas_lancamento FOREIGN KEY (lancamento_id)
        REFERENCES ledger.lancamentos_ledger(id) ON DELETE CASCADE,
    CONSTRAINT fk_partidas_conta FOREIGN KEY (conta_id)
        REFERENCES ledger.contas_ledger(id),
    CONSTRAINT ck_partidas_valor_positivo CHECK (valor_centavos > 0)
);

CREATE INDEX idx_partidas_lancamento ON ledger.partidas_ledger (lancamento_id);
CREATE INDEX idx_partidas_conta      ON ledger.partidas_ledger (conta_id);

ALTER TABLE ledger.partidas_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger.partidas_ledger FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ledger.partidas_ledger
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR lancamento_id IN (
            SELECT id FROM ledger.lancamentos_ledger
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

-- ─── Constraint de equilíbrio (partidas dobradas) ────────────────────────────
-- Validada no COMMIT (DEFERRABLE INITIALLY DEFERRED): permite inserir o lançamento
-- e suas N partidas na mesma transação; ao commitar, exige SUM(DEBITO) = SUM(CREDITO).
-- Um lançamento sem partidas (0 = 0) é trivialmente equilibrado.

CREATE OR REPLACE FUNCTION ledger.fn_verifica_equilibrio()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
DECLARE
    v_lancamento UUID;
    v_debito     BIGINT;
    v_credito    BIGINT;
BEGIN
    v_lancamento := COALESCE(NEW.lancamento_id, OLD.lancamento_id);

    SELECT
        COALESCE(SUM(valor_centavos) FILTER (WHERE tipo = 'DEBITO'), 0),
        COALESCE(SUM(valor_centavos) FILTER (WHERE tipo = 'CREDITO'), 0)
    INTO v_debito, v_credito
    FROM ledger.partidas_ledger
    WHERE lancamento_id = v_lancamento;

    IF v_debito <> v_credito THEN
        RAISE EXCEPTION 'Lancamento % desequilibrado: debitos=% creditos=% (partidas dobradas exige igualdade)',
            v_lancamento, v_debito, v_credito
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_equilibrio_partidas
    AFTER INSERT OR UPDATE OR DELETE ON ledger.partidas_ledger
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION ledger.fn_verifica_equilibrio();

-- ─── Constraint de imutabilidade (livro-razão append-only) ───────────────────
-- Bloqueia UPDATE e DELETE em lancamentos_ledger e partidas_ledger.
-- O ledger é um registro histórico: correções são feitas por novos lançamentos de AJUSTE.

CREATE OR REPLACE FUNCTION ledger.fn_bloqueia_alteracao()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Tabela %.% e imutavel (append-only): operacao % nao permitida. Use um lancamento de AJUSTE.',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_imutabilidade_lancamentos
    BEFORE UPDATE OR DELETE ON ledger.lancamentos_ledger
    FOR EACH ROW
    EXECUTE FUNCTION ledger.fn_bloqueia_alteracao();

CREATE TRIGGER trg_imutabilidade_partidas
    BEFORE UPDATE OR DELETE ON ledger.partidas_ledger
    FOR EACH ROW
    EXECUTE FUNCTION ledger.fn_bloqueia_alteracao();

-- ─── Contas iniciais (plano de contas) ───────────────────────────────────────
-- Estrutura: 1=ATIVO, 2=PASSIVO, 3=RECEITA, 4=DESPESA, 9=INTERMEDIARIO
-- Cobre o fluxo Pin Saúde: honorários a receber, retenções/repasses a pagar,
-- receita de honorários, taxa administrativa (15%) e conta transitória.

INSERT INTO ledger.contas_ledger (codigo, nome, tipo) VALUES
    ('1.1.01', 'Honorários a Receber',                 'ATIVO'),
    ('2.1.01', 'Retenções de Impostos a Recolher',     'PASSIVO'),
    ('2.1.02', 'Repasses a Médicos a Pagar',           'PASSIVO'),
    ('3.1.01', 'Receita de Honorários Médicos',        'RECEITA'),
    ('3.2.01', 'Taxa Administrativa Pin Saúde',        'RECEITA'),
    ('9.1.01', 'Conta Transitória de Compensação',     'INTERMEDIARIO');
