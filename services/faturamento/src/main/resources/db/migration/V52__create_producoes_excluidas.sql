-- Exclusão de produção lançada errado (backoffice e Portal do Médico).
-- A produção é apagada de verdade (participacoes_producao cai junto por ON DELETE CASCADE), para
-- não poluir listas, extrato, posição de caixa e relatórios. Antes de apagar, uma cópia completa
-- fica aqui como auditoria: quem excluiu, quando, por qual perfil, motivo e o JSON da produção
-- (tomador, serviço, participantes e valores) no momento da exclusão.
-- Só é permitido excluir antes de existir NFS-e/conciliação (regra em ExclusaoProducaoService).

CREATE TABLE faturamento.producoes_excluidas (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    producao_id      UUID         NOT NULL,
    cnpj_id_tenant   VARCHAR(14)  NOT NULL,
    tomador_id       UUID,
    competencia      VARCHAR(7),
    valor_bruto      BIGINT,
    status_anterior  VARCHAR(20),
    snapshot         JSONB        NOT NULL,
    motivo           TEXT,
    excluida_por     VARCHAR(255),
    perfil           VARCHAR(20)  NOT NULL,   -- BACKOFFICE | MEDICO
    excluida_em      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE faturamento.producoes_excluidas ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.producoes_excluidas FORCE ROW LEVEL SECURITY;

-- FORCE obrigatório: mesmo motivo documentado em V21 (medico_tomadores).
CREATE POLICY tenant_isolation ON faturamento.producoes_excluidas
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.producoes_excluidas TO svc_faturamento;

CREATE INDEX idx_producoes_excluidas_producao_id ON faturamento.producoes_excluidas(producao_id);
CREATE INDEX idx_producoes_excluidas_excluida_em ON faturamento.producoes_excluidas(excluida_em DESC);
