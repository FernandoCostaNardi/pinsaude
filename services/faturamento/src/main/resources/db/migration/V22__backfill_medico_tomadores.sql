-- EPIC-15.2: Backfill de faturamento.medico_tomadores a partir do histórico
-- Obrigatório antes de ativar a validação de bloqueio (422) em ProducaoService/FrequenciaService
-- (EPIC-15.7/15.8) — sem isso, médicos que já atuam em um tomador ficariam retroativamente
-- bloqueados de lançar mais produção/frequência para esse tomador onde já têm histórico.
-- ON CONFLICT DO NOTHING garante idempotência entre as 3 fontes (a mesma combinação
-- tomador+médico pode aparecer em produções e em frequências ao mesmo tempo).

-- Fonte 1: participações de produção (modelo atual, multi-médico — EPIC-04.6)
INSERT INTO faturamento.medico_tomadores (tomador_id, medico_id)
SELECT DISTINCT p.tomador_id, pp.medico_id
FROM faturamento.participacoes_producao pp
JOIN faturamento.producoes p ON p.id = pp.producao_id
ON CONFLICT (tomador_id, medico_id) DO NOTHING;

-- Fonte 2: produções legadas single-médico (pré EPIC-04.6, medico_id direto em producoes)
INSERT INTO faturamento.medico_tomadores (tomador_id, medico_id)
SELECT DISTINCT p.tomador_id, p.medico_id
FROM faturamento.producoes p
WHERE p.medico_id IS NOT NULL
ON CONFLICT (tomador_id, medico_id) DO NOTHING;

-- Fonte 3: frequências médicas (tomador_id + medico_id diretos — EPIC-13.3)
INSERT INTO faturamento.medico_tomadores (tomador_id, medico_id)
SELECT DISTINCT f.tomador_id, f.medico_id
FROM faturamento.frequencias_medicas f
ON CONFLICT (tomador_id, medico_id) DO NOTHING;
