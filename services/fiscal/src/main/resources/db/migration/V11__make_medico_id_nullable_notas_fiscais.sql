-- EPIC-04.6: medico_id fica null em notas de produções multi-médico
ALTER TABLE fiscal.notas_fiscais ALTER COLUMN medico_id DROP NOT NULL;
