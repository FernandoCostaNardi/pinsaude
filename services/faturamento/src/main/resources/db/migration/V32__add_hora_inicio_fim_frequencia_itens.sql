-- Para modalidade DIARISTA, o medico informa a hora de entrada e saida do dia (em vez de
-- digitar a quantidade de horas diretamente) e o backend calcula horas_trabalhadas a partir
-- da diferenca entre os dois horarios (ver FrequenciaService.calcularHorasTrabalhadas).
-- Usado tambem para reimprimir o horario trabalhado no PDF da frequencia (modalidade Diarista
-- nao tem turno/horario cadastrados, diferente de Plantonista).
ALTER TABLE faturamento.frequencia_itens
    ADD COLUMN hora_inicio TIME,
    ADD COLUMN hora_fim    TIME;

ALTER TABLE faturamento.frequencia_itens
    ADD CONSTRAINT frequencia_itens_hora_inicio_fim_check
        CHECK (hora_inicio IS NULL OR hora_fim IS NULL OR hora_inicio <> hora_fim);
