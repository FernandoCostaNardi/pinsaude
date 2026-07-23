-- ─── V21: Origem do Cadastro e Vínculo com Keycloak (EPIC-14.1) ──────────────
-- origem_cadastro: distingue médicos criados via auto-cadastro público (EPIC-14)
--   dos criados manualmente por operação/gestão, sem precisar de um novo valor em
--   StatusMedico (a fila de aprovação continua filtrando por status = RASCUNHO).
-- keycloak_user_id: rastreia o usuário Keycloak criado (desabilitado) ao final da
--   candidatura pública, usado depois para liberar o acesso na ativação (EPIC-14.4).
ALTER TABLE onboarding.medicos
    ADD COLUMN IF NOT EXISTS origem_cadastro  VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS keycloak_user_id VARCHAR(64);
