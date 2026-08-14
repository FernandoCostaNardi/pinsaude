-- Pedido do cliente: o médico pode abrir múltiplas frequências ("folhas") Plantonista para o
-- mesmo médico+setor+competência — ex.: uma folha pros plantões de semana, outra pros de fim de
-- semana, cada uma virando um PDF separado entregue ao hospital. Como a modalidade do
-- Plantonista deixou de ser fixada na frequência (volta a ser escolhida por lançamento — ver
-- FrequenciaService), não sobra nenhuma chave natural pra impedir duplicatas nesse caso.
--
-- A restrição de unicidade original (medico+setor+competencia, sem levar em conta o tipo de
-- escala) é substituída por um índice único PARCIAL que só vale para Diarista: continua 1
-- frequência por médico+setor+competência+modalidade quando Diarista (o médico pode ter mais de
-- um cargo/valor mensal diferente no mesmo mês, cada um em sua própria frequência). Plantonista
-- fica sem nenhuma restrição de unicidade a partir daqui.
ALTER TABLE faturamento.frequencias_medicas
    DROP CONSTRAINT frequencias_medicas_medico_id_servico_operacional_id_compet_key;

CREATE UNIQUE INDEX frequencias_medicas_diarista_unica_idx
    ON faturamento.frequencias_medicas (medico_id, servico_operacional_id, competencia, modalidade_id)
    WHERE tipo_medico = 'DIARISTA';
