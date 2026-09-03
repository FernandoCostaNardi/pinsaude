-- Pedido do cliente: uma Modalidade pode ter mais de um Tipo de Escala, desde que os tipos
-- pertençam à MESMA família de comportamento (nunca mistura família "fixa" — DIARISTA,
-- EVOLUCIONISTA — com família "por lançamento" — PLANTONISTA, EVOLUCIONISTA_FDS — ver
-- TipoEscala.java). Evita cadastrar a mesma modalidade 2x só para rotulá-la sob 2 tipos idênticos
-- em campos/comportamento (ex: "Diarista 40h" e "Evolucionista 40h" com os mesmos horas_semanais
-- e valor). Reaproveita o mesmo padrão de coluna array já usado em
-- onboarding.dados_civis_medico.situacao_formacao (@JdbcTypeCode(SqlTypes.ARRAY), text[]).

-- 1. Nova coluna array (nullable por enquanto — vira NOT NULL só depois do backfill).
ALTER TABLE faturamento.tomador_modalidades ADD COLUMN tipos TEXT[];

-- 2. Remove os CHECKs antigos ANTES do backfill (não depois, ver armadilha abaixo).
--    tomador_modalidades_tipo_campos_check foi criada NOT VALID (V31/V40/V41) — não valida
--    linhas legadas no momento da criação, mas o Postgres revalida TODO CHECK constraint da
--    tabela a cada UPDATE de uma linha, mesmo quando o UPDATE só toca uma coluna não referenciada
--    por ele. Descoberto ao aplicar esta migration: existe uma modalidade real (dev) com
--    tipo=PLANTONISTA e turno/horario NULL, grandfathered pelo NOT VALID — rodar o UPDATE de
--    backfill com a constraint antiga ainda ativa falha pra essa linha. Remover a constraint
--    primeiro evita revalidar dado legado que só o NOT VALID tolerava.
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_check;

-- 3. Backfill a partir do tipo escalar existente, torna obrigatório, remove a coluna antiga.
UPDATE faturamento.tomador_modalidades SET tipos = ARRAY[tipo];
ALTER TABLE faturamento.tomador_modalidades ALTER COLUMN tipos SET NOT NULL;
ALTER TABLE faturamento.tomador_modalidades DROP COLUMN tipo;

-- 4. Array não pode ser vazio, nem conter NULL, nem valor fora dos 4 tipos válidos (equivalente
--    ao antigo CHECK IN (...), mas sobre o array inteiro via <@ subset). Validada normalmente
--    contra o dado já migrado (sem NOT VALID) — todo `tipo` legado já era garantidamente um dos 4
--    valores pelo antigo tomador_modalidades_tipo_check (esse sim validado na criação, V40).
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipos_check
        CHECK (
            array_length(tipos, 1) > 0
            AND NOT (NULL = ANY(tipos))
            AND tipos <@ ARRAY['PLANTONISTA','DIARISTA','EVOLUCIONISTA','EVOLUCIONISTA_FDS']::text[]
        );

-- 5. Nunca misturar as 2 famílias no mesmo array + campos por tipo continuam condicionais (agora
--    via && overlap em vez de IN). NOT VALID: mesma convenção já usada em V31/V40/V41 para regra
--    nova de campos-por-tipo — não revalida retroativamente as linhas existentes (preserva o
--    mesmo dado legado grandfathered — ex: a modalidade PLANTONISTA sem turno/horário do passo 2
--    — exatamente como já acontecia antes desta migration).
ALTER TABLE faturamento.tomador_modalidades ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
    (tipos && ARRAY['PLANTONISTA','EVOLUCIONISTA_FDS']::text[]
        AND NOT (tipos && ARRAY['DIARISTA','EVOLUCIONISTA']::text[])
        AND turno IS NOT NULL AND horario IS NOT NULL AND horas IS NOT NULL AND horas_semanais IS NULL)
    OR
    (tipos && ARRAY['DIARISTA','EVOLUCIONISTA']::text[]
        AND NOT (tipos && ARRAY['PLANTONISTA','EVOLUCIONISTA_FDS']::text[])
        AND turno IS NULL AND horario IS NULL AND horas IS NULL AND horas_semanais IS NOT NULL)
) NOT VALID;
