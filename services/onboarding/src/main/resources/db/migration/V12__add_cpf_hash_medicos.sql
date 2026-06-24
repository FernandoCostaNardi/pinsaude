-- Adiciona coluna de hash do CPF para garantir unicidade sem expor o valor criptografado.
-- O hash é calculado pela aplicação (SHA-256 do CPF com apenas dígitos).
-- Índice parcial: ignora registros anteriores que ainda não tenham hash preenchido.
ALTER TABLE onboarding.medicos ADD COLUMN cpf_hash VARCHAR(64);

CREATE UNIQUE INDEX medicos_cpf_hash_uidx
    ON onboarding.medicos(cpf_hash)
    WHERE cpf_hash IS NOT NULL;
