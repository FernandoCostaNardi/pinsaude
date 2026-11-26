-- APU-01 — motor da apuração mensal sobre as notas reais.
--
-- Qual data põe a nota na receita "por competência" de um mês ainda não foi confirmada pela
-- contabilidade, então fica na regra (versionada e homologável, como o resto da APU-00):
--   COMPETENCIA_NOTA = mês de competência gravado na nota (mês em que o serviço foi prestado);
--   DATA_EMISSAO     = mês da data de emissão da nota (fuso de São Paulo).
-- A receita "por caixa" usa sempre a data do depósito conciliado (Faturamento).

ALTER TABLE fiscal.regra_apuracao
    ADD COLUMN referencia_competencia VARCHAR(20) NOT NULL DEFAULT 'COMPETENCIA_NOTA',
    ADD CONSTRAINT regra_apuracao_ref_comp_ck
        CHECK (referencia_competencia IN ('COMPETENCIA_NOTA', 'DATA_EMISSAO'));

COMMENT ON COLUMN fiscal.regra_apuracao.referencia_competencia IS
    'APU-01 — mês da receita por competência: COMPETENCIA_NOTA (competência da nota) ou DATA_EMISSAO (data de emissão). A confirmar com a contabilidade.';
