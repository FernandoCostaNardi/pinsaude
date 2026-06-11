-- Extensões PostgreSQL necessárias para o schema onboarding.
-- pgcrypto: gen_random_uuid() para geração de UUIDs primários.
-- uuid-ossp: uuid_generate_v4() como alternativa portável.
-- Ambas são "trusted extensions" — requerem GRANT CREATE ON DATABASE ao usuário Flyway.
-- O init.sql (superuser) já concede esse grant a svc_onboarding.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
