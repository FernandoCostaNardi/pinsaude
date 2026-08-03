-- Horario tambem passa a ser opcional em modalidades PLANTAO (ex: modalidade so com quantidade
-- de horas, sem turno nem horario definidos) -- agora so "horas" e obrigatorio para PLANTAO.
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;

ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
        (tipo = 'PLANTAO' AND horas IS NOT NULL)
        OR
        (tipo = 'MENSAL' AND turno IS NULL AND horario IS NULL AND horas IS NULL)
    );
