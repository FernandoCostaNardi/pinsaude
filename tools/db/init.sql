-- Pin Saúde — Inicialização do banco de desenvolvimento
-- Cria schemas e usuários isolados por serviço (sem acesso cruzado)

-- Schemas de domínio (6 serviços)
CREATE SCHEMA IF NOT EXISTS fiscal;
CREATE SCHEMA IF NOT EXISTS faturamento;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS repasse;
CREATE SCHEMA IF NOT EXISTS onboarding;
CREATE SCHEMA IF NOT EXISTS gestao;

-- Usuários de serviço (isolados — sem cross-schema access)
CREATE USER svc_fiscal       WITH PASSWORD 'fiscal_dev';
CREATE USER svc_faturamento  WITH PASSWORD 'faturamento_dev';
CREATE USER svc_ledger       WITH PASSWORD 'ledger_dev';
CREATE USER svc_repasse      WITH PASSWORD 'repasse_dev';
CREATE USER svc_onboarding   WITH PASSWORD 'onboarding_dev';
CREATE USER svc_gestao       WITH PASSWORD 'gestao_dev';

-- Grants de schema (cada usuário só cria/acessa o próprio schema)
GRANT CREATE, USAGE ON SCHEMA fiscal       TO svc_fiscal;
GRANT CREATE, USAGE ON SCHEMA faturamento  TO svc_faturamento;
GRANT CREATE, USAGE ON SCHEMA ledger       TO svc_ledger;
GRANT CREATE, USAGE ON SCHEMA repasse      TO svc_repasse;
GRANT CREATE, USAGE ON SCHEMA onboarding   TO svc_onboarding;
GRANT CREATE, USAGE ON SCHEMA gestao       TO svc_gestao;

-- search_path padrão por usuário (Flyway encontra o schema sem prefixo)
ALTER USER svc_fiscal       SET search_path TO fiscal, public;
ALTER USER svc_faturamento  SET search_path TO faturamento, public;
ALTER USER svc_ledger       SET search_path TO ledger, public;
ALTER USER svc_repasse      SET search_path TO repasse, public;
ALTER USER svc_onboarding   SET search_path TO onboarding, public;
ALTER USER svc_gestao       SET search_path TO gestao, public;

-- Default privileges: objetos criados pelo superuser também acessíveis ao dono do schema
ALTER DEFAULT PRIVILEGES IN SCHEMA fiscal       GRANT ALL ON TABLES TO svc_fiscal;
ALTER DEFAULT PRIVILEGES IN SCHEMA faturamento  GRANT ALL ON TABLES TO svc_faturamento;
ALTER DEFAULT PRIVILEGES IN SCHEMA ledger       GRANT ALL ON TABLES TO svc_ledger;
ALTER DEFAULT PRIVILEGES IN SCHEMA repasse      GRANT ALL ON TABLES TO svc_repasse;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding   GRANT ALL ON TABLES TO svc_onboarding;
ALTER DEFAULT PRIVILEGES IN SCHEMA gestao       GRANT ALL ON TABLES TO svc_gestao;
