-- PINSAUDE-13.20: Preenchimento rapido de turno configuravel por tomador.
-- Ate aqui, os botoes de "Preencher rapido" na modalidade Por Plantao (HORARIOS_FIXOS no
-- frontend) eram fixos e globais — 4 combinacoes turno x horas x horario iguais para todos os
-- tomadores. Cada cliente pode ter turnos diferentes (ex: um hospital usa 07:00-13:00, outro
-- usa 07:00-15:00), entao isso vira uma tabela por tomador, mesmo padrao de child-catalog ja
-- usado para tomador_ocorrencias/tomador_modalidades/tomador_servicos_operacionais.

CREATE TABLE faturamento.tomador_horarios_padrao (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    turno      VARCHAR(10)  NOT NULL,
    horas      NUMERIC(6,2) NOT NULL,
    horario    VARCHAR(30)  NOT NULL,
    ordem      INT          NOT NULL DEFAULT 1,
    ativo      BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tomador_horarios_padrao_turno_check CHECK (turno IN ('DIURNO', 'NOTURNO')),
    CONSTRAINT tomador_horarios_padrao_horas_check CHECK (horas > 0)
);

ALTER TABLE faturamento.tomador_horarios_padrao ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.tomador_horarios_padrao FORCE ROW LEVEL SECURITY;

-- FORCE obrigatorio (mesmo motivo documentado em tomador_ocorrencias/medico_tomadores): o app
-- conecta como svc_faturamento, dono da tabela, e sem FORCE o owner bypassa a policy.
CREATE POLICY tenant_isolation ON faturamento.tomador_horarios_padrao
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_horarios_padrao TO svc_faturamento;

CREATE INDEX idx_tomador_horarios_padrao_tomador_id ON faturamento.tomador_horarios_padrao(tomador_id);

-- Backfill: replica os 4 presets fixos que existiam globalmente no frontend (HORARIOS_FIXOS)
-- para todos os tomadores ja cadastrados, para nao quebrar a experiencia no dia do deploy —
-- a partir daqui cada tomador pode editar/remover/adicionar livremente os seus.
INSERT INTO faturamento.tomador_horarios_padrao (tomador_id, turno, horas, horario, ordem)
SELECT id, 'DIURNO', 6, '07:00 as 13:00', 1 FROM faturamento.tomadores
UNION ALL
SELECT id, 'DIURNO', 12, '07:00 as 19:00', 2 FROM faturamento.tomadores
UNION ALL
SELECT id, 'NOTURNO', 6, '19:00 as 00:00', 3 FROM faturamento.tomadores
UNION ALL
SELECT id, 'NOTURNO', 12, '19:00 as 07:00', 4 FROM faturamento.tomadores;
