-- ALTER TYPE ... ADD VALUE não é transacional no PostgreSQL.
-- Deve ficar em migration isolada (sem outros DDLs na mesma transação) —
-- mesmo padrão de V11__add_especialidades_tipo_documento.sql.
-- Novos tipos exigidos pelo auto-cadastro público (EPIC-14): certidão de casamento,
-- comprovante de endereço e RQE (Registro de Qualificação de Especialista).
ALTER TYPE onboarding.tipo_documento_medico_enum ADD VALUE IF NOT EXISTS 'CERTIDAO_CASAMENTO';
ALTER TYPE onboarding.tipo_documento_medico_enum ADD VALUE IF NOT EXISTS 'COMPROVANTE_ENDERECO';
ALTER TYPE onboarding.tipo_documento_medico_enum ADD VALUE IF NOT EXISTS 'RQE';
