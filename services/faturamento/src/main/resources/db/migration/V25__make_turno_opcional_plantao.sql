-- Turno (DIURNO/NOTURNO) passa a ser opcional tambem para modalidades PLANTAO
-- (ex: "Diaria 15h" pode nao ter um turno definido) -- so horario+horas continuam obrigatorios.
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;

ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
        (tipo = 'PLANTAO' AND horario IS NOT NULL AND horas IS NOT NULL)
        OR
        (tipo = 'MENSAL' AND turno IS NULL AND horario IS NULL AND horas IS NULL)
    );
