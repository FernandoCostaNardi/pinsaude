-- Modalidade de faturamento passa a ter um tipo:
--   PLANTAO -> turno/horario/horas obrigatorios (comportamento atual, mas horas livre)
--   MENSAL  -> sem turno/horario/horas, apenas nome + valor fixo mensal (ex: Coordenacao de UTI)
ALTER TABLE faturamento.tomador_modalidades
    ADD COLUMN tipo VARCHAR(10) NOT NULL DEFAULT 'PLANTAO' CHECK (tipo IN ('PLANTAO', 'MENSAL'));

-- turno/horario/horas passam a ser opcionais (obrigatoriedade condicional ao tipo, ver CHECK abaixo)
ALTER TABLE faturamento.tomador_modalidades ALTER COLUMN turno DROP NOT NULL;
ALTER TABLE faturamento.tomador_modalidades ALTER COLUMN horario DROP NOT NULL;
ALTER TABLE faturamento.tomador_modalidades ALTER COLUMN horas DROP NOT NULL;

-- Recria os CHECKs de turno/horas tolerando NULL (antes exigiam sempre não-nulo)
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_turno_check;
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_horas_check;

ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_turno_check CHECK (turno IS NULL OR turno IN ('DIURNO', 'NOTURNO'));
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_horas_check CHECK (horas IS NULL OR horas > 0);

-- Consistencia tipo x campos: PLANTAO exige os 3 campos preenchidos; MENSAL exige os 3 vazios
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
        (tipo = 'PLANTAO' AND turno IS NOT NULL AND horario IS NOT NULL AND horas IS NOT NULL)
        OR
        (tipo = 'MENSAL' AND turno IS NULL AND horario IS NULL AND horas IS NULL)
    );
