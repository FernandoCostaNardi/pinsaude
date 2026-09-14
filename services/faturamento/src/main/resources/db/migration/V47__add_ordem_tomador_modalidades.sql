-- Permite reordenar (drag-and-drop) as modalidades na aba "Modalidades" do modal de
-- Faturamento por Grupo — a mesma ordem passa a ser refletida no seletor de modalidade das
-- telas de Frequência Médica (admin e Portal), que hoje só consomem GET /modalidades sem
-- reordenar no cliente.
ALTER TABLE faturamento.tomador_modalidades ADD COLUMN ordem INT NOT NULL DEFAULT 0;

-- Backfill: preserva a ordem alfabética atual (comportamento de antes desta migration) como
-- ordem inicial, por tomador — evita que a lista "embaralhe" na primeira tela depois do deploy.
WITH numerado AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY tomador_id ORDER BY nome ASC) - 1 AS rn
    FROM faturamento.tomador_modalidades
)
UPDATE faturamento.tomador_modalidades m
SET ordem = numerado.rn
FROM numerado
WHERE m.id = numerado.id;
