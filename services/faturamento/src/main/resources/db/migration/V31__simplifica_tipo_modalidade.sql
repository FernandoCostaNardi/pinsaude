-- PINSAUDE-13.22: simplifica os tipos de modalidade de 3 (PLANTAO/MENSAL/META) para apenas 2,
-- com o mesmo vocabulario ja usado em frequencias_medicas.tipo_medico: PLANTONISTA e DIARISTA.
--   PLANTONISTA -> turno, horario e horas passam a ser todos obrigatorios (reverte a
--                  flexibilizacao das migrations V25/V26 -- so vale para cadastros/edicoes
--                  novas, sem forcar retroativamente as modalidades ja cadastradas sem turno).
--   DIARISTA    -> substitui o antigo tipo META. Exige horas_semanais (renomeia meta_horas);
--                  nao tem mais unidade_calculo/meta_dias -- o motor de calculo do valor
--                  mensal unico e o acompanhamento semanal sao implementados em PINSAUDE-13.23.
-- MENSAL deixa de existir como tipo (nao havia nenhuma modalidade cadastrada com esse tipo).

-- 1. Widen tipo (VARCHAR(10) nao cabe "PLANTONISTA", 11 caracteres) e atualiza o DEFAULT
--    (o antigo 'PLANTAO' nao seria mais um valor valido pro novo CHECK)
ALTER TABLE faturamento.tomador_modalidades ALTER COLUMN tipo TYPE VARCHAR(12);
ALTER TABLE faturamento.tomador_modalidades ALTER COLUMN tipo SET DEFAULT 'PLANTONISTA';

-- 2. Remove os CHECKs antigos que dependem de tipo/unidade_calculo/meta_dias antes de alterar dados/colunas
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_check;
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_unidade_calculo_check;
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_meta_dias_check;

-- 3. Reclassifica os dados existentes (ambiente de dev, sem restricao de zero-downtime)
UPDATE faturamento.tomador_modalidades SET tipo = 'PLANTONISTA' WHERE tipo = 'PLANTAO';
UPDATE faturamento.tomador_modalidades SET tipo = 'DIARISTA'    WHERE tipo = 'META';

-- 4. Renomeia meta_horas -> horas_semanais (unica meta que sobrevive, agora obrigatoria pro Diarista)
ALTER TABLE faturamento.tomador_modalidades RENAME COLUMN meta_horas TO horas_semanais;
ALTER TABLE faturamento.tomador_modalidades RENAME CONSTRAINT tomador_modalidades_meta_horas_check
    TO tomador_modalidades_horas_semanais_check;

-- 5. Remove a coluna que nao existe mais (unidade_calculo ja foi tratada; meta_dias so sobrevivia
--    para o extinto META/DIA -- nenhuma modalidade DIARISTA nova tera essa distincao)
ALTER TABLE faturamento.tomador_modalidades DROP COLUMN unidade_calculo;
ALTER TABLE faturamento.tomador_modalidades DROP COLUMN meta_dias;

-- 6. Backfill de horas_semanais para os registros reclassificados de META/DIA -> DIARISTA:
--    esses nao tinham meta_horas preenchido (usavam meta_dias, que deixou de existir). Sem uma
--    conversao direta possivel, aplica um valor placeholder -- deve ser revisado/ajustado pela
--    operacao junto ao tomador real antes de qualquer novo lancamento nessa modalidade.
UPDATE faturamento.tomador_modalidades
   SET horas_semanais = 20.00
 WHERE tipo = 'DIARISTA' AND horas_semanais IS NULL;

-- 7. Novos CHECKs com o modelo de 2 tipos
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_check
        CHECK (tipo IN ('PLANTONISTA', 'DIARISTA'));

-- NOT VALID: nao revalida as linhas ja existentes (ex.: "Diario 10h" continua sem turno/horario
-- ate alguem editar essa modalidade), mas passa a valer para todo INSERT/UPDATE dai em diante.
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
        (tipo = 'PLANTONISTA' AND turno IS NOT NULL AND horario IS NOT NULL AND horas IS NOT NULL
            AND horas_semanais IS NULL)
        OR
        (tipo = 'DIARISTA' AND turno IS NULL AND horario IS NULL AND horas IS NULL
            AND horas_semanais IS NOT NULL)
    ) NOT VALID;
