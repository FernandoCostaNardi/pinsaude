-- Coracao da valoracao proporcional (modalidade META): cada item de frequencia pode informar
-- quantas horas o medico trabalhou naquele lancamento. Usado no calculo do valor_unitario_centavos
-- quando a modalidade e do tipo META com unidade_calculo=HORA (ver FrequenciaService).
-- Para PLANTAO/MENSAL e META/DIA o campo fica NULL (nao participa do calculo).
ALTER TABLE faturamento.frequencia_itens
    ADD COLUMN horas_trabalhadas NUMERIC(6,2);

ALTER TABLE faturamento.frequencia_itens
    ADD CONSTRAINT frequencia_itens_horas_trabalhadas_check
        CHECK (horas_trabalhadas IS NULL OR horas_trabalhadas > 0);
