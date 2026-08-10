-- Modalidade "META": pagamento proporcional por unidade (HORA ou DIA), com meta por bloco.
-- valor_centavos = valor de UM bloco completo da meta.
--   Ex "Diaria 40h": unidade_calculo=HORA, meta_horas=40, valor_centavos = valor do bloco de 40h.
--   Ex "Evolucionista": unidade_calculo=HORA ou DIA, meta no mes; a outra meta (dias/horas) e
--   opcional e serve so de validacao secundaria.
-- turno/horario/horas NAO se aplicam a META. PLANTAO e MENSAL permanecem inalterados.
ALTER TABLE faturamento.tomador_modalidades
    ADD COLUMN unidade_calculo VARCHAR(4),
    ADD COLUMN meta_horas      NUMERIC(6,2),
    ADD COLUMN meta_dias       INT;

ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_unidade_calculo_check
        CHECK (unidade_calculo IS NULL OR unidade_calculo IN ('HORA', 'DIA'));
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_meta_horas_check
        CHECK (meta_horas IS NULL OR meta_horas > 0);
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_meta_dias_check
        CHECK (meta_dias IS NULL OR meta_dias > 0);

-- tipo passa a aceitar META (alem de PLANTAO/MENSAL)
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_check;
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_check
        CHECK (tipo IN ('PLANTAO', 'MENSAL', 'META'));

-- Consistencia tipo x campos:
--   PLANTAO: horas obrigatorio; campos de META vazios.
--   MENSAL : turno/horario/horas vazios; campos de META vazios.
--   META   : turno/horario/horas vazios; unidade_calculo obrigatorio; a meta da unidade
--            obrigatoria (HORA -> meta_horas, DIA -> meta_dias). A outra meta e opcional.
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
        (tipo = 'PLANTAO' AND horas IS NOT NULL
            AND unidade_calculo IS NULL AND meta_horas IS NULL AND meta_dias IS NULL)
        OR
        (tipo = 'MENSAL' AND turno IS NULL AND horario IS NULL AND horas IS NULL
            AND unidade_calculo IS NULL AND meta_horas IS NULL AND meta_dias IS NULL)
        OR
        (tipo = 'META' AND turno IS NULL AND horario IS NULL AND horas IS NULL
            AND unidade_calculo IS NOT NULL
            AND ((unidade_calculo = 'HORA' AND meta_horas IS NOT NULL)
              OR (unidade_calculo = 'DIA'  AND meta_dias  IS NOT NULL)))
    );
