-- Correção de comportamento (pedido do cliente, após V40): EVOLUCIONISTA se comporta como
-- DIARISTA por trás dos panos (modalidade fixa, horas semanais), mas EVOLUCIONISTA_FDS se
-- comporta como PLANTONISTA (modalidade por lançamento, turno/horário/horas obrigatórios).
-- A família "tipo fixo" passa de {DIARISTA, EVOLUCIONISTA, EVOLUCIONISTA_FDS} para
-- {DIARISTA, EVOLUCIONISTA} — EVOLUCIONISTA_FDS sai da família fixa e entra na família
-- "por lançamento" junto com PLANTONISTA. Nenhuma linha existente em tomador_modalidades ou
-- frequencias_medicas usa EVOLUCIONISTA/EVOLUCIONISTA_FDS ainda (conferido antes de aplicar).

ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;
ALTER TABLE faturamento.tomador_modalidades ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
    (tipo IN ('PLANTONISTA', 'EVOLUCIONISTA_FDS') AND turno IS NOT NULL AND horario IS NOT NULL AND horas IS NOT NULL AND horas_semanais IS NULL)
    OR
    (tipo IN ('DIARISTA', 'EVOLUCIONISTA')
        AND turno IS NULL AND horario IS NULL AND horas IS NULL AND horas_semanais IS NOT NULL)
) NOT VALID;

DROP INDEX faturamento.frequencias_medicas_tipo_fixo_unica_idx;
CREATE UNIQUE INDEX frequencias_medicas_tipo_fixo_unica_idx
    ON faturamento.frequencias_medicas (medico_id, servico_operacional_id, competencia, modalidade_id)
    WHERE tipo_medico IN ('DIARISTA', 'EVOLUCIONISTA');
