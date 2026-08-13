-- Modalidade e Ocorrencia passam a ser escolhidas uma unica vez na criacao da frequencia
-- (nao mais por lancamento de plantao). Nullable de proposito: frequencias legadas que ja
-- tem itens com modalidades variadas (confirmado que existem no ambiente real) ficam com
-- modalidade_id NULL e continuam usando o fluxo antigo de selecionar por lancamento
-- (ver FrequenciaService.adicionarItem/atualizarItem).
ALTER TABLE faturamento.frequencias_medicas
    ADD COLUMN modalidade_id UUID NULL REFERENCES faturamento.tomador_modalidades(id),
    ADD COLUMN ocorrencia_id UUID NULL REFERENCES faturamento.tomador_ocorrencias(id);

-- Backfill: so preenche quando TODOS os itens ja lancados de uma frequencia usam a mesma
-- modalidade — nesse caso e seguro assumir que essa e a modalidade "fixa" da frequencia.
-- Frequencias com modalidades variadas entre os itens ficam com modalidade_id NULL de
-- proposito (nao da pra escolher uma sozinha sem perder informacao).
-- PostgreSQL nao tem agregador MIN/MAX nativo para UUID — usa array_agg(DISTINCT ...)[1] para
-- pegar o unico valor distinto (ja garantido pelo HAVING COUNT(DISTINCT ...) = 1 abaixo).
UPDATE faturamento.frequencias_medicas f
SET modalidade_id = sub.modalidade_id
FROM (
    SELECT frequencia_id, (array_agg(DISTINCT modalidade_id))[1] AS modalidade_id
    FROM faturamento.frequencia_itens
    GROUP BY frequencia_id
    HAVING COUNT(DISTINCT modalidade_id) = 1
) sub
WHERE f.id = sub.frequencia_id;
