-- ─── V1: Extensões PostgreSQL necessárias para o schema faturamento ──────────
-- pgcrypto : pgp_sym_encrypt/decrypt (CPF de tomadores PF) + gen_random_uuid()
-- uuid-ossp: uuid_generate_v4() como alternativa portável
-- Ambas são "trusted extensions" — requerem GRANT CREATE ON DATABASE a svc_faturamento.
-- O init.sql (superuser) já concede esse grant.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
