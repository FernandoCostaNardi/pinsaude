-- Schemas por serviço (ADR-0002, ADR-0003)
CREATE SCHEMA IF NOT EXISTS fiscal;
CREATE SCHEMA IF NOT EXISTS faturamento;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS repasse;
CREATE SCHEMA IF NOT EXISTS onboarding;
CREATE SCHEMA IF NOT EXISTS gestao;
CREATE SCHEMA IF NOT EXISTS auth;

-- Usuários de banco — um por serviço, acesso restrito ao próprio schema (ADR-0002)
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'svc_fiscal') THEN
    CREATE USER svc_fiscal WITH PASSWORD 'local_dev_only';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'svc_faturamento') THEN
    CREATE USER svc_faturamento WITH PASSWORD 'local_dev_only';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'svc_ledger') THEN
    CREATE USER svc_ledger WITH PASSWORD 'local_dev_only';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'svc_repasse') THEN
    CREATE USER svc_repasse WITH PASSWORD 'local_dev_only';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'svc_onboarding') THEN
    CREATE USER svc_onboarding WITH PASSWORD 'local_dev_only';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'svc_gestao') THEN
    CREATE USER svc_gestao WITH PASSWORD 'local_dev_only';
  END IF;
END
$$;

-- Grants: cada usuário acessa apenas o próprio schema
GRANT ALL ON SCHEMA fiscal      TO svc_fiscal;
GRANT ALL ON SCHEMA faturamento TO svc_faturamento;
GRANT ALL ON SCHEMA ledger      TO svc_ledger;
GRANT ALL ON SCHEMA repasse     TO svc_repasse;
GRANT ALL ON SCHEMA onboarding  TO svc_onboarding;
GRANT ALL ON SCHEMA gestao      TO svc_gestao;

-- Default privileges: objetos futuros criados no schema já ficam acessíveis ao usuário do schema
ALTER DEFAULT PRIVILEGES IN SCHEMA fiscal      GRANT ALL ON TABLES TO svc_fiscal;
ALTER DEFAULT PRIVILEGES IN SCHEMA faturamento GRANT ALL ON TABLES TO svc_faturamento;
ALTER DEFAULT PRIVILEGES IN SCHEMA ledger      GRANT ALL ON TABLES TO svc_ledger;
ALTER DEFAULT PRIVILEGES IN SCHEMA repasse     GRANT ALL ON TABLES TO svc_repasse;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding  GRANT ALL ON TABLES TO svc_onboarding;
ALTER DEFAULT PRIVILEGES IN SCHEMA gestao      GRANT ALL ON TABLES TO svc_gestao;
