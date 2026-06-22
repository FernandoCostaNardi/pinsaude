-- Tabela de controle de lotes de emissão em lote (EPIC-05.7)
-- Registra o progresso de cada rodada de emissão em lote por competência.
-- O lock distribuído é implementado por: não permitir novo lote enquanto status = 'EM_ANDAMENTO'.

CREATE TABLE fiscal.lotes_emissao (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    competencia      VARCHAR(7)  NOT NULL,
    total            INTEGER     NOT NULL DEFAULT 0,
    emitidas         INTEGER     NOT NULL DEFAULT 0,
    falhas           INTEGER     NOT NULL DEFAULT 0,
    em_processamento INTEGER     NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'EM_ANDAMENTO'
                     CONSTRAINT lotes_emissao_status_check
                     CHECK (status IN ('EM_ANDAMENTO', 'CONCLUIDO', 'FALHA')),
    iniciado_em      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    concluido_em     TIMESTAMPTZ
);

CREATE INDEX idx_lotes_competencia ON fiscal.lotes_emissao (competencia);
CREATE INDEX idx_lotes_status      ON fiscal.lotes_emissao (status);
