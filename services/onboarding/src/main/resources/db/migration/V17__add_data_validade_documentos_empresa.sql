-- ─── V17: Validade de Documentos da Empresa (EPIC-02.7) ─────────────────────
-- Adiciona data_validade opcional em documentos_empresa para suportar alertas
-- de vencimento exibidos ao gestor quando o prazo está próximo.

ALTER TABLE onboarding.documentos_empresa
    ADD COLUMN data_validade DATE NULL;

COMMENT ON COLUMN onboarding.documentos_empresa.data_validade
    IS 'Data de validade do documento; NULL = sem prazo. Gerencia alertas para gestores.';
