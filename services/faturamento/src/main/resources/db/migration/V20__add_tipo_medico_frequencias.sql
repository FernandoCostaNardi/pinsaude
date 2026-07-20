-- PINSAUDE-13.11: Tipo de escala do médico (Plantonista/Diarista) na frequência médica
-- Substitui o campo texto livre especialidade por um campo estruturado.
ALTER TABLE faturamento.frequencias_medicas
    ADD COLUMN tipo_medico VARCHAR(20)
        CHECK (tipo_medico IN ('PLANTONISTA', 'DIARISTA'));

-- Torna especialidade nullable para compatibilidade com registros existentes
ALTER TABLE faturamento.frequencias_medicas
    ALTER COLUMN especialidade DROP NOT NULL;
