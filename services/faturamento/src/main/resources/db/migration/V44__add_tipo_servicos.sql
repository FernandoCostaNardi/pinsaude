-- Pedido do cliente: nova modalidade "Serviços" — 3ª família de comportamento (não mistura com
-- "fixa" nem "por lançamento" — ver TipoEscala.java). Modalidade escolhida a cada lançamento
-- (igual Plantonista/EvolucionistaFDS), mas paga quantidade × valorCentavos por lançamento (em
-- vez de valor flat). Cadastro da Modalidade só pede nome + valor (preço unitário do serviço) —
-- sem turno/horário/horas/horas_semanais. Lançamento do item pede uma quantidade de serviços
-- realizados naquela data (nova coluna quantidade em frequencia_itens). Não precisa gerar PDF —
-- decisão só de frontend, sem impacto de schema/backend.

-- 1. Nova coluna do item — contável (INTEGER), nullable (só usada pela família SERVICOS).
ALTER TABLE faturamento.frequencia_itens ADD COLUMN quantidade INTEGER;
ALTER TABLE faturamento.frequencia_itens
    ADD CONSTRAINT frequencia_itens_quantidade_check
        CHECK (quantidade IS NULL OR quantidade > 0);

-- 2. tomador_modalidades: SERVICOS entra no array de tipos válidos.
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipos_check;
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipos_check
        CHECK (
            array_length(tipos, 1) > 0
            AND NOT (NULL = ANY(tipos))
            AND tipos <@ ARRAY['PLANTONISTA','DIARISTA','EVOLUCIONISTA','EVOLUCIONISTA_FDS','SERVICOS']::text[]
        );

-- 3. tomador_modalidades: campos-por-tipo ganha o 3º ramo (SERVICOS: sem turno/horário/horas/
--    horas_semanais), mantendo a exclusão mútua entre as 3 famílias. NOT VALID: mesma convenção
--    já usada em V31/V40/V41/V42 para não revalidar retroativamente linhas legadas grandfathered.
ALTER TABLE faturamento.tomador_modalidades DROP CONSTRAINT tomador_modalidades_tipo_campos_check;
ALTER TABLE faturamento.tomador_modalidades ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
    (tipos && ARRAY['PLANTONISTA','EVOLUCIONISTA_FDS']::text[]
        AND NOT (tipos && ARRAY['DIARISTA','EVOLUCIONISTA','SERVICOS']::text[])
        AND turno IS NOT NULL AND horario IS NOT NULL AND horas IS NOT NULL AND horas_semanais IS NULL)
    OR
    (tipos && ARRAY['DIARISTA','EVOLUCIONISTA']::text[]
        AND NOT (tipos && ARRAY['PLANTONISTA','EVOLUCIONISTA_FDS','SERVICOS']::text[])
        AND turno IS NULL AND horario IS NULL AND horas IS NULL AND horas_semanais IS NOT NULL)
    OR
    (tipos && ARRAY['SERVICOS']::text[]
        AND NOT (tipos && ARRAY['PLANTONISTA','EVOLUCIONISTA_FDS','DIARISTA','EVOLUCIONISTA']::text[])
        AND turno IS NULL AND horario IS NULL AND horas IS NULL AND horas_semanais IS NULL)
) NOT VALID;

-- 4. frequencias_medicas: SERVICOS entra como tipo_medico válido.
ALTER TABLE faturamento.frequencias_medicas DROP CONSTRAINT frequencias_medicas_tipo_medico_check;
ALTER TABLE faturamento.frequencias_medicas ADD CONSTRAINT frequencias_medicas_tipo_medico_check
    CHECK (tipo_medico IN ('PLANTONISTA', 'DIARISTA', 'EVOLUCIONISTA', 'EVOLUCIONISTA_FDS', 'SERVICOS'));

-- 5. setor_operacional_modalidades: SERVICOS entra como tipo válido do vínculo setor↔modalidade.
ALTER TABLE faturamento.setor_operacional_modalidades DROP CONSTRAINT setor_operacional_modalidades_tipo_check;
ALTER TABLE faturamento.setor_operacional_modalidades
    ADD CONSTRAINT setor_operacional_modalidades_tipo_check
        CHECK (tipo IN ('PLANTONISTA','DIARISTA','EVOLUCIONISTA','EVOLUCIONISTA_FDS','SERVICOS'));
