ALTER TABLE onboarding.empresas
    ADD COLUMN IF NOT EXISTS logradouro    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS bairro        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS uf            CHAR(2),
    ADD COLUMN IF NOT EXISTS cep           VARCHAR(9),
    ADD COLUMN IF NOT EXISTS telefone      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS email_contato VARCHAR(255);
