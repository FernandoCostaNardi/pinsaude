-- Converte tipo de tipo_documento_empresa_enum para varchar(60).
-- @ColumnTransformer(write) só cobre INSERT/UPDATE, não cláusulas WHERE —
-- Hibernate 6 envia o enum como character varying e PG rejeita por falta de cast implícito.
-- Padrão adotado também em aliquotas_competencia.regime_presuncao (EPIC-02.4).

ALTER TABLE onboarding.documentos_empresa
    ALTER COLUMN tipo TYPE varchar(60) USING tipo::text;

DROP TYPE IF EXISTS onboarding.tipo_documento_empresa_enum;

ALTER TABLE onboarding.documentos_empresa
    ADD CONSTRAINT documentos_empresa_tipo_check CHECK (tipo IN (
        'CONTRATO_SOCIAL',
        'CONSELHO',
        'ENDERECO_FISCAL',
        'DIRECAO_TECNICA',
        'DOCUMENTACAO_SOCIO_ADMINISTRADOR',
        'NADA_CONSTA_ESTADUAL',
        'CND_FALENCIA',
        'CND_FEDERAL',
        'CND_FGTS',
        'CND_MUNICIPAL',
        'CND_TRABALHISTA'
    ));
