-- Extrato do Portal do Médico: o serviço portal (svc_portal, leitura cross-schema via
-- JdbcTemplate) passa a ler ledger.lancamentos_ledger para marcar como "Pago" os lançamentos
-- do médico cuja competência já teve repasse (tipo_origem = 'REPASSE').
--
-- Condicional: a role svc_portal só existe no Postgres real (tools/db/init.sql); no
-- Testcontainers ela não existe e um GRANT direto derrubaria o Flyway nos testes.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_portal') THEN
        GRANT USAGE ON SCHEMA ledger TO svc_portal;
        GRANT SELECT ON ledger.lancamentos_ledger TO svc_portal;
    END IF;
END
$$;
