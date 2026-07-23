-- Roles referenciadas via GRANT em migrations do onboarding (V14, V15: svc_onboarding,
-- svc_portal) existem no Postgres real (criadas por tools/db/init.sql como superuser),
-- mas NÃO existem no container efêmero do Testcontainers — o que faz o Flyway falhar
-- em "GRANT ... TO svc_onboarding" com "role does not exist" antes mesmo do contexto
-- Spring subir. Executado via withInitScript, roda como superuser assim que o
-- container inicia, antes do Flyway se conectar.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_onboarding') THEN
        CREATE ROLE svc_onboarding;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'svc_portal') THEN
        CREATE ROLE svc_portal;
    END IF;
END
$$;

-- No Postgres real, tools/db/init.sql faz "ALTER USER svc_onboarding SET search_path TO
-- onboarding, public" — isso faz com que pgcrypto (instalado no schema onboarding pelo
-- Flyway, cujo search_path é onboarding via spring.flyway.schemas) seja resolvido também
-- nas conexões normais da aplicação (ex: onboarding.encrypt_sensitive() chama
-- pgp_sym_encrypt() sem qualificar o schema). O usuário "test" do Testcontainers não tem
-- esse ALTER, então o search_path da sessão fica só "$user", public — sem "onboarding" —
-- e qualquer chamada real a CryptoService.encrypt/decrypt falha com
-- "function pgp_sym_encrypt(text, text) does not exist". Replicamos o mesmo ALTER aqui,
-- no nível do banco (cobre qualquer usuário que conecte nesta database de teste).
ALTER DATABASE test SET search_path TO onboarding, public;
