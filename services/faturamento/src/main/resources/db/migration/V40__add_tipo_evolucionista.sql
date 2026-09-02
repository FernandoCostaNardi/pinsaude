-- PINSAUDE — Adiciona os Tipos de Escala EVOLUCIONISTA e EVOLUCIONISTA_FDS, reutilizando
-- exatamente as mesmas regras de campos/valor do DIARISTA (modalidade fixa na criação da
-- frequência, sem mistura de tipos dentro da mesma frequência, horas_semanais obrigatório).
-- Também remove tipo_escala_label — o label "Tipo de Escala" do PDF passa a ser 100% calculado
-- (Tipo + Setor), sem campo de texto manual.

-- Alargar tipo pra caber EVOLUCIONISTA_FDS (18 chars) — hoje é VARCHAR(12), só cabe PLANTONISTA.
ALTER TABLE faturamento.tomador_modalidades ALTER COLUMN tipo TYPE VARCHAR(20);

ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_check;
ALTER TABLE faturamento.tomador_modalidades ADD CONSTRAINT tomador_modalidades_tipo_check
    CHECK (tipo IN ('PLANTONISTA', 'DIARISTA', 'EVOLUCIONISTA', 'EVOLUCIONISTA_FDS'));

-- Recria o CHECK de campos-por-tipo estendendo a família "fixa" (mesmas regras do DIARISTA).
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;
ALTER TABLE faturamento.tomador_modalidades ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
    (tipo = 'PLANTONISTA' AND turno IS NOT NULL AND horario IS NOT NULL AND horas IS NOT NULL AND horas_semanais IS NULL)
    OR
    (tipo IN ('DIARISTA', 'EVOLUCIONISTA', 'EVOLUCIONISTA_FDS')
        AND turno IS NULL AND horario IS NULL AND horas IS NULL AND horas_semanais IS NOT NULL)
) NOT VALID;

ALTER TABLE faturamento.frequencias_medicas DROP CONSTRAINT frequencias_medicas_tipo_medico_check;
ALTER TABLE faturamento.frequencias_medicas ADD CONSTRAINT frequencias_medicas_tipo_medico_check
    CHECK (tipo_medico IN ('PLANTONISTA', 'DIARISTA', 'EVOLUCIONISTA', 'EVOLUCIONISTA_FDS'));

-- Estende o índice de unicidade (médico+setor+competência+modalidade) pra toda a família "fixa",
-- não só DIARISTA — PLANTONISTA continua sem restrição de unicidade (permite "folhas" separadas).
DROP INDEX faturamento.frequencias_medicas_diarista_unica_idx;
CREATE UNIQUE INDEX frequencias_medicas_tipo_fixo_unica_idx
    ON faturamento.frequencias_medicas (medico_id, servico_operacional_id, competencia, modalidade_id)
    WHERE tipo_medico IN ('DIARISTA', 'EVOLUCIONISTA', 'EVOLUCIONISTA_FDS');

-- Campo "Texto no PDF" removido — label do "Tipo de Escala" agora é sempre calculado
-- (Tipo + Setor), sem necessidade de configuração manual por setor.
ALTER TABLE faturamento.tomador_servicos_operacionais DROP COLUMN tipo_escala_label;
