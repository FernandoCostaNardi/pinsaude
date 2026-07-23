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
