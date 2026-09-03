-- Quando uma Modalidade tem mais de um Tipo de Escala (V42), o vínculo Setor↔Modalidade passa a
-- guardar explicitamente qual tipo resolvido vale para aquele setor — permite vincular a mesma
-- modalidade duas vezes (uma linha por tipo) quando o setor aceitar mais de um tipo dela. O
-- frontend passa a oferecer, no picker de Setor Operacional, uma linha por (modalidade, tipo
-- suportado).

ALTER TABLE faturamento.setor_operacional_modalidades ADD COLUMN tipo VARCHAR(20);

-- Backfill: o (até aqui único) tipo da modalidade vinculada — tipos[1] é sempre o único elemento
-- para todo dado gerado antes desta migration (V42 acabou de rodar, nenhuma modalidade tinha mais
-- de 1 tipo até este momento).
UPDATE faturamento.setor_operacional_modalidades som
   SET tipo = tm.tipos[1]
  FROM faturamento.tomador_modalidades tm
 WHERE tm.id = som.modalidade_id;

ALTER TABLE faturamento.setor_operacional_modalidades ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE faturamento.setor_operacional_modalidades
    ADD CONSTRAINT setor_operacional_modalidades_tipo_check
        CHECK (tipo IN ('PLANTONISTA','DIARISTA','EVOLUCIONISTA','EVOLUCIONISTA_FDS'));

-- UNIQUE(setor_id, modalidade_id) criada inline no CREATE TABLE da V39 — nome gerado pela
-- convenção padrão do Postgres.
ALTER TABLE faturamento.setor_operacional_modalidades
    DROP CONSTRAINT setor_operacional_modalidades_setor_id_modalidade_id_key;
ALTER TABLE faturamento.setor_operacional_modalidades
    ADD CONSTRAINT setor_operacional_modalidades_setor_modalidade_tipo_key
        UNIQUE (setor_id, modalidade_id, tipo);
