-- Pin Saúde — Inicialização do banco de desenvolvimento
-- Cria schemas e usuários isolados por serviço (sem acesso cruzado)

-- Schemas
CREATE SCHEMA IF NOT EXISTS fiscal;
CREATE SCHEMA IF NOT EXISTS faturamento;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS repasse;
CREATE SCHEMA IF NOT EXISTS onboarding;
CREATE SCHEMA IF NOT EXISTS gestao;
CREATE SCHEMA IF NOT EXISTS auth;

-- Usuários de serviço (sem cross-schema access)
CREATE USER svc_fiscal       WITH PASSWORD 'fiscal_dev';
CREATE USER svc_faturamento  WITH PASSWORD 'faturamento_dev';
CREATE USER svc_ledger       WITH PASSWORD 'ledger_dev';
CREATE USER svc_repasse      WITH PASSWORD 'repasse_dev';
CREATE USER svc_onboarding   WITH PASSWORD 'onboarding_dev';
CREATE USER svc_gestao       WITH PASSWORD 'gestao_dev';

-- Grants (cada usuário só acessa o próprio schema)
GRANT ALL ON SCHEMA fiscal       TO svc_fiscal;
GRANT ALL ON SCHEMA faturamento  TO svc_faturamento;
GRANT ALL ON SCHEMA ledger       TO svc_ledger;
GRANT ALL ON SCHEMA repasse      TO svc_repasse;
GRANT ALL ON SCHEMA onboarding   TO svc_onboarding;
GRANT ALL ON SCHEMA gestao       TO svc_gestao;
