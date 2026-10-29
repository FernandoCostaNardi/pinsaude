-- PIS/NIT do médico (planilha de sócios). Dado pessoal: criptografado com pgcrypto,
-- mesmo padrão do CPF e da chave PIX.
ALTER TABLE onboarding.dados_civis_medico
    ADD COLUMN IF NOT EXISTS pis_nit_criptografado BYTEA;
