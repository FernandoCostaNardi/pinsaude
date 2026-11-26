-- APU-00 — Regras de apuração mensal da Pin (Lucro Presumido + reforma), parametrizáveis.
--
-- As fórmulas do PRD §7.9 ainda não foram homologadas pela contabilidade. Por isso nada fica
-- fixo no código: presunções, alíquotas, adicional de IRPJ, regime (caixa/competência),
-- periodicidade, abatimento de retenções, códigos de receita e vencimentos vêm destas tabelas,
-- versionadas por competência e com homologação.
--
-- Uma regra vale da competencia_inicio até a próxima regra do mesmo escopo. Escopo:
--   cnpj_id preenchido = regra da empresa; cnpj_id nulo = padrão global, usado pela empresa
--   que ainda não tem regra própria na competência.
--
-- Convenção do fiscal service: alíquotas e percentuais como FRAÇÃO decimal (0.0800 = 8%);
-- valores monetários em CENTAVOS (BIGINT).

CREATE TABLE fiscal.regra_apuracao (
    id                            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id                       VARCHAR(14),
    competencia_inicio            VARCHAR(7)   NOT NULL,

    -- Lucro Presumido: percentuais de presunção sobre a receita
    presuncao_irpj_equiparado     NUMERIC(8,4) NOT NULL,
    presuncao_csll_equiparado     NUMERIC(8,4) NOT NULL,
    presuncao_irpj_padrao         NUMERIC(8,4) NOT NULL,
    presuncao_csll_padrao         NUMERIC(8,4) NOT NULL,

    -- Adicional de IRPJ: alíquota sobre a base presumida que exceder o limite mensal × meses do período
    aliq_adicional_irpj           NUMERIC(8,4) NOT NULL,
    limite_adicional_irpj_mensal  BIGINT       NOT NULL,

    -- Teto do Lucro Presumido (monitor, APU-03)
    teto_receita_anual            BIGINT       NOT NULL,
    alerta_teto_percentual        NUMERIC(8,4) NOT NULL,

    -- Guia abaixo deste valor não é gerada: o saldo acumula para o próximo período (DARF mínimo)
    valor_minimo_guia             BIGINT       NOT NULL DEFAULT 0,

    -- Governança
    homologado                    BOOLEAN      NOT NULL DEFAULT FALSE,
    homologado_por                VARCHAR(100),
    homologado_em                 TIMESTAMPTZ,
    fonte                         TEXT,
    observacoes                   TEXT,
    atualizado_por                VARCHAR(100),
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT regra_apuracao_comp_ck      CHECK (competencia_inicio ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT regra_apuracao_cnpj_ck      CHECK (cnpj_id IS NULL OR cnpj_id ~ '^\d{14}$'),
    CONSTRAINT regra_apuracao_presuncao_ck CHECK (
        presuncao_irpj_equiparado BETWEEN 0 AND 1 AND presuncao_csll_equiparado BETWEEN 0 AND 1
        AND presuncao_irpj_padrao BETWEEN 0 AND 1 AND presuncao_csll_padrao BETWEEN 0 AND 1),
    CONSTRAINT regra_apuracao_adicional_ck CHECK (aliq_adicional_irpj BETWEEN 0 AND 1 AND limite_adicional_irpj_mensal >= 0),
    CONSTRAINT regra_apuracao_teto_ck      CHECK (teto_receita_anual >= 0 AND alerta_teto_percentual BETWEEN 0 AND 1),
    CONSTRAINT regra_apuracao_minimo_ck    CHECK (valor_minimo_guia >= 0)
);

-- Uma regra por escopo e competência (o padrão global tem cnpj_id nulo)
CREATE UNIQUE INDEX regra_apuracao_escopo_uq
    ON fiscal.regra_apuracao (COALESCE(cnpj_id, '*'), competencia_inicio);

COMMENT ON TABLE  fiscal.regra_apuracao                       IS 'APU-00 — regras da apuração mensal por empresa (cnpj_id) ou padrão global (cnpj_id nulo), por competência de início. Rascunho até homologado pela contabilidade.';
COMMENT ON COLUMN fiscal.regra_apuracao.presuncao_irpj_equiparado    IS 'Presunção do IRPJ para receita com equiparação hospitalar (PRD: 8%).';
COMMENT ON COLUMN fiscal.regra_apuracao.presuncao_csll_equiparado    IS 'Presunção da CSLL para receita com equiparação hospitalar (PRD: 12%).';
COMMENT ON COLUMN fiscal.regra_apuracao.presuncao_irpj_padrao        IS 'Presunção do IRPJ sem equiparação (PRD: 32%).';
COMMENT ON COLUMN fiscal.regra_apuracao.presuncao_csll_padrao        IS 'Presunção da CSLL sem equiparação (PRD: 32%).';
COMMENT ON COLUMN fiscal.regra_apuracao.limite_adicional_irpj_mensal IS 'Centavos por mês; no período trimestral o limite é multiplicado por 3 (PRD: R$ 20.000/mês).';
COMMENT ON COLUMN fiscal.regra_apuracao.teto_receita_anual           IS 'Centavos (PRD: R$ 78 milhões por CNPJ).';

CREATE TABLE fiscal.regra_apuracao_tributo (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    regra_id               UUID         NOT NULL REFERENCES fiscal.regra_apuracao(id) ON DELETE CASCADE,
    tributo                VARCHAR(10)  NOT NULL,
    ativo                  BOOLEAN      NOT NULL DEFAULT TRUE,
    aplica_em              VARCHAR(10)  NOT NULL DEFAULT 'AMBOS',
    aliquota               NUMERIC(8,4),
    base_calculo           VARCHAR(20)  NOT NULL,
    regime_reconhecimento  VARCHAR(12)  NOT NULL,
    periodicidade          VARCHAR(12)  NOT NULL,
    deduz_retencao         BOOLEAN      NOT NULL DEFAULT TRUE,
    codigo_receita         VARCHAR(10),
    guia                   VARCHAR(40),
    vencimento_dia         SMALLINT,
    vencimento_meses_apos  SMALLINT,
    vencimento_ajuste      VARCHAR(10)  NOT NULL DEFAULT 'NENHUM',

    CONSTRAINT regra_apuracao_tributo_uq        UNIQUE (regra_id, tributo),
    CONSTRAINT regra_apuracao_tributo_ck        CHECK (tributo IN ('IRPJ','CSLL','PIS','COFINS','ISS','CBS','IBS')),
    CONSTRAINT regra_apuracao_aplica_ck         CHECK (aplica_em IN ('LEGADO','IBS_CBS','AMBOS')),
    CONSTRAINT regra_apuracao_aliquota_ck       CHECK (aliquota IS NULL OR aliquota BETWEEN 0 AND 1),
    CONSTRAINT regra_apuracao_base_ck           CHECK (base_calculo IN ('RECEITA_BRUTA','BASE_PRESUMIDA','SOMA_NOTAS')),
    CONSTRAINT regra_apuracao_regime_ck         CHECK (regime_reconhecimento IN ('CAIXA','COMPETENCIA')),
    CONSTRAINT regra_apuracao_periodicidade_ck  CHECK (periodicidade IN ('MENSAL','TRIMESTRAL')),
    CONSTRAINT regra_apuracao_venc_dia_ck       CHECK (vencimento_dia IS NULL OR vencimento_dia BETWEEN 1 AND 31),
    CONSTRAINT regra_apuracao_venc_meses_ck     CHECK (vencimento_meses_apos IS NULL OR vencimento_meses_apos BETWEEN 0 AND 12),
    CONSTRAINT regra_apuracao_venc_ajuste_ck    CHECK (vencimento_ajuste IN ('ANTECIPA','POSTERGA','NENHUM'))
);

COMMENT ON TABLE  fiscal.regra_apuracao_tributo                  IS 'APU-00 — como cada tributo é apurado dentro de uma regra.';
COMMENT ON COLUMN fiscal.regra_apuracao_tributo.aplica_em        IS 'LEGADO = só com a chave CBS/IBS desligada (PIS/COFINS); IBS_CBS = só no regime novo (CBS/IBS); AMBOS = sempre.';
COMMENT ON COLUMN fiscal.regra_apuracao_tributo.aliquota         IS 'Fração decimal. Nula só para CBS/IBS: usa a alíquota do ano em fiscal.cronograma_reforma.';
COMMENT ON COLUMN fiscal.regra_apuracao_tributo.base_calculo     IS 'RECEITA_BRUTA; BASE_PRESUMIDA (IRPJ/CSLL: receita × presunção); SOMA_NOTAS (CBS/IBS: soma do valor calculado em cada nota, que já tem a redução do serviço).';
COMMENT ON COLUMN fiscal.regra_apuracao_tributo.vencimento_dia   IS 'Dia do vencimento (31 = último dia do mês). Nulo = vencimento não cadastrado.';
COMMENT ON COLUMN fiscal.regra_apuracao_tributo.vencimento_meses_apos IS 'Meses após o fim do período de apuração (1 = mês seguinte).';
COMMENT ON COLUMN fiscal.regra_apuracao_tributo.vencimento_ajuste IS 'Quando cai em fim de semana: ANTECIPA para o dia útil anterior, POSTERGA para o seguinte. Feriados ainda não são considerados.';

-- ─── Padrão global inicial (rascunho) ─────────────────────────────────────────
-- Valores do PRD v1.1 §7.9 e da legislação do Lucro Presumido, a homologar pela contabilidade.
WITH regra AS (
    INSERT INTO fiscal.regra_apuracao (
        cnpj_id, competencia_inicio,
        presuncao_irpj_equiparado, presuncao_csll_equiparado, presuncao_irpj_padrao, presuncao_csll_padrao,
        aliq_adicional_irpj, limite_adicional_irpj_mensal,
        teto_receita_anual, alerta_teto_percentual, valor_minimo_guia,
        fonte, observacoes, atualizado_por)
    VALUES (
        NULL, '2026-01',
        0.0800, 0.1200, 0.3200, 0.3200,
        0.1000, 2000000,
        7800000000, 0.8000, 1000,
        'PRD v1.1 §7.9 (proposta de desenho) e regras gerais do Lucro Presumido.',
        'RASCUNHO — aguardando homologação da contabilidade (APU-00). Pontos a confirmar: periodicidade de IRPJ/CSLL (PRD diz mensal; a lei prevê trimestral), regime de caixa para IRPJ/CSLL, vencimentos de ISS e CBS/IBS.',
        'migration V18')
    RETURNING id
)
INSERT INTO fiscal.regra_apuracao_tributo (
    regra_id, tributo, ativo, aplica_em, aliquota, base_calculo, regime_reconhecimento, periodicidade,
    deduz_retencao, codigo_receita, guia, vencimento_dia, vencimento_meses_apos, vencimento_ajuste)
SELECT regra.id, t.* FROM regra, (VALUES
    ('IRPJ',   TRUE,  'AMBOS',   0.1500::NUMERIC, 'BASE_PRESUMIDA', 'COMPETENCIA', 'TRIMESTRAL', TRUE, '2089', 'DARF',          31::SMALLINT, 1::SMALLINT, 'ANTECIPA'),
    ('CSLL',   TRUE,  'AMBOS',   0.0900,          'BASE_PRESUMIDA', 'COMPETENCIA', 'TRIMESTRAL', TRUE, '2372', 'DARF',          31,           1,           'ANTECIPA'),
    ('PIS',    TRUE,  'LEGADO',  0.0065,          'RECEITA_BRUTA',  'CAIXA',       'MENSAL',     TRUE, '8109', 'DARF',          25,           1,           'ANTECIPA'),
    ('COFINS', TRUE,  'LEGADO',  0.0300,          'RECEITA_BRUTA',  'CAIXA',       'MENSAL',     TRUE, '2172', 'DARF',          25,           1,           'ANTECIPA'),
    ('ISS',    TRUE,  'AMBOS',   0.0200,          'RECEITA_BRUTA',  'COMPETENCIA', 'MENSAL',     TRUE, NULL,   'Guia municipal', NULL,        NULL,        'NENHUM'),
    ('CBS',    TRUE,  'IBS_CBS', NULL,            'SOMA_NOTAS',     'COMPETENCIA', 'MENSAL',     TRUE, NULL,   'DARF',          NULL,         NULL,        'NENHUM'),
    ('IBS',    TRUE,  'IBS_CBS', NULL,            'SOMA_NOTAS',     'COMPETENCIA', 'MENSAL',     TRUE, NULL,   'Guia IBS',      NULL,         NULL,        'NENHUM')
) AS t(tributo, ativo, aplica_em, aliquota, base_calculo, regime_reconhecimento, periodicidade,
       deduz_retencao, codigo_receita, guia, vencimento_dia, vencimento_meses_apos, vencimento_ajuste);
