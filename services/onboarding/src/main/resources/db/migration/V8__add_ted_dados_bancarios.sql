-- Adiciona suporte a recebimento via TED em dados_bancarios_medico
ALTER TABLE onboarding.dados_bancarios_medico
    ADD COLUMN IF NOT EXISTS tipo_recebimento VARCHAR(3) NOT NULL DEFAULT 'PIX'
        CHECK (tipo_recebimento IN ('PIX', 'TED')),
    ADD COLUMN IF NOT EXISTS banco_codigo    VARCHAR(10),
    ADD COLUMN IF NOT EXISTS banco_nome      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS agencia         VARCHAR(10),
    ADD COLUMN IF NOT EXISTS conta           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS tipo_conta      VARCHAR(10)
        CHECK (tipo_conta IN ('CORRENTE', 'POUPANCA'));
