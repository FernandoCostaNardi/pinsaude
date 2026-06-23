-- ALTER TYPE ... ADD VALUE não é transacional no PostgreSQL.
-- Deve ficar em migration isolada (sem outros DDLs na mesma transação).
ALTER TYPE onboarding.tipo_documento_medico_enum ADD VALUE IF NOT EXISTS 'ESPECIALIDADES';
