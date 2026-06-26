-- Pin Saúde — Inicialização do banco de desenvolvimento
-- Cria schemas e usuários isolados por serviço (sem acesso cruzado)

-- Banco dedicado para o Keycloak (idempotente via \gexec)
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')
\gexec

-- Extensões necessárias para geração de UUID e criptografia
-- Executadas aqui (superuser) para evitar dependência de grants em Flyway
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Permite que svc_onboarding e svc_faturamento criem extensões trusted via Flyway (PostgreSQL 13+)
GRANT CREATE ON DATABASE pinsaude TO svc_onboarding;
GRANT CREATE ON DATABASE pinsaude TO svc_faturamento;


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
-- Portal: leitura cross-schema (onboarding + fiscal + faturamento)
CREATE USER svc_portal       WITH PASSWORD 'portal_dev';

-- Grants de schema (cada usuário só cria/acessa o próprio schema)
GRANT CREATE, USAGE ON SCHEMA fiscal       TO svc_fiscal;
GRANT CREATE, USAGE ON SCHEMA faturamento  TO svc_faturamento;
GRANT CREATE, USAGE ON SCHEMA ledger       TO svc_ledger;
GRANT CREATE, USAGE ON SCHEMA repasse      TO svc_repasse;
GRANT CREATE, USAGE ON SCHEMA onboarding   TO svc_onboarding;
GRANT CREATE, USAGE ON SCHEMA gestao       TO svc_gestao;
-- Portal: acesso de leitura nos três schemas
GRANT USAGE ON SCHEMA onboarding  TO svc_portal;
GRANT USAGE ON SCHEMA fiscal      TO svc_portal;
GRANT USAGE ON SCHEMA faturamento TO svc_portal;

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
-- Portal: SELECT em tabelas existentes
GRANT SELECT ON ALL TABLES IN SCHEMA onboarding  TO svc_portal;
GRANT SELECT ON ALL TABLES IN SCHEMA fiscal       TO svc_portal;
GRANT SELECT ON ALL TABLES IN SCHEMA faturamento  TO svc_portal;
-- Portal: SELECT em tabelas futuras criadas pelos owners de cada schema
ALTER DEFAULT PRIVILEGES FOR ROLE svc_onboarding  IN SCHEMA onboarding  GRANT SELECT ON TABLES TO svc_portal;
ALTER DEFAULT PRIVILEGES FOR ROLE svc_fiscal      IN SCHEMA fiscal       GRANT SELECT ON TABLES TO svc_portal;
ALTER DEFAULT PRIVILEGES FOR ROLE svc_faturamento IN SCHEMA faturamento  GRANT SELECT ON TABLES TO svc_portal;
