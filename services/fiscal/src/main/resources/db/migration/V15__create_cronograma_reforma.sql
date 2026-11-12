-- CBS-10 — Reforma tributária (EC 132/2023, LC 214/2025): cronograma de alíquotas 2026–2033
-- e governança da chave IBS/CBS por empresa.
--
-- A chave continua sendo um registro de fiscal.parametro_fiscal por empresa (cnpj_id) com
-- ibs_cbs_ativo = true e vigencia_inicio = primeira competência no regime novo (ex.: 2027-01-01),
-- válido apenas quando homologado. As alíquotas de cada ano saem desta tabela global, não do
-- parâmetro da empresa: a chave é ligada uma vez e os anos seguintes andam sozinhos.
--
-- Convenção do fiscal service: alíquotas como FRAÇÃO decimal (0.0090 = 0,9%).

CREATE TABLE fiscal.cronograma_reforma (
    ano                    SMALLINT     PRIMARY KEY,
    aliq_cbs               NUMERIC(8,4),
    aliq_ibs_uf            NUMERIC(8,4),
    aliq_ibs_mun           NUMERIC(8,4),
    fator_iss              NUMERIC(6,4) NOT NULL,
    aliquotas_definitivas  BOOLEAN      NOT NULL DEFAULT FALSE,
    fonte                  TEXT,
    atualizado_por         VARCHAR(100),
    atualizado_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT cronograma_reforma_ano_ck       CHECK (ano BETWEEN 2026 AND 2040),
    CONSTRAINT cronograma_reforma_cbs_ck       CHECK (aliq_cbs     IS NULL OR aliq_cbs     BETWEEN 0 AND 1),
    CONSTRAINT cronograma_reforma_ibs_uf_ck    CHECK (aliq_ibs_uf  IS NULL OR aliq_ibs_uf  BETWEEN 0 AND 1),
    CONSTRAINT cronograma_reforma_ibs_mun_ck   CHECK (aliq_ibs_mun IS NULL OR aliq_ibs_mun BETWEEN 0 AND 1),
    CONSTRAINT cronograma_reforma_fator_iss_ck CHECK (fator_iss BETWEEN 0 AND 1),
    CONSTRAINT cronograma_reforma_definitivo_ck CHECK (
        NOT aliquotas_definitivas
        OR (aliq_cbs IS NOT NULL AND aliq_ibs_uf IS NOT NULL AND aliq_ibs_mun IS NOT NULL)
    )
);

COMMENT ON TABLE  fiscal.cronograma_reforma                       IS 'Alíquotas de referência da reforma tributária por ano (catálogo global, sem RLS). Frações decimais. Valores nulos = ainda não fixados pelo Senado/Comitê Gestor.';
COMMENT ON COLUMN fiscal.cronograma_reforma.aliq_cbs              IS 'Alíquota de referência da CBS no ano, antes da redução do serviço (60% saúde, cClassTrib 200029).';
COMMENT ON COLUMN fiscal.cronograma_reforma.aliq_ibs_uf           IS 'Parcela estadual do IBS no ano.';
COMMENT ON COLUMN fiscal.cronograma_reforma.aliq_ibs_mun          IS 'Parcela municipal do IBS no ano.';
COMMENT ON COLUMN fiscal.cronograma_reforma.fator_iss             IS 'Fração da alíquota de ISS ainda cobrada no ano: 1 até 2028, 0,9/0,8/0,7/0,6 de 2029 a 2032, 0 a partir de 2033.';
COMMENT ON COLUMN fiscal.cronograma_reforma.aliquotas_definitivas IS 'true quando os valores do ano foram confirmados em norma (resolução do Senado / Comitê Gestor). Provisório = false.';

INSERT INTO fiscal.cronograma_reforma
    (ano, aliq_cbs, aliq_ibs_uf, aliq_ibs_mun, fator_iss, aliquotas_definitivas, fonte)
VALUES
    (2026, 0.0090, 0.0010, 0.0000, 1.0000, TRUE,
     'LC 214/2025 — fase de teste: CBS 0,9% e IBS 0,1%, informativos (sem recolhimento se cumpridas as obrigações acessórias). PIS/COFINS e ISS seguem normais.'),
    (2027, 0.1120, 0.0005, 0.0005, 1.0000, FALSE,
     'PROVISÓRIO — TCU propôs CBS de referência 11,3% em 28/10/2026; em 2027–2028 a CBS é a referência menos 0,1 p.p. e o IBS é 0,05% UF + 0,05% município. Ajustar após a resolução do Senado (até 15/12/2026, task CBS-50). PIS/COFINS extintos.'),
    (2028, 0.1120, 0.0005, 0.0005, 1.0000, FALSE,
     'PROVISÓRIO — mesma regra de 2027; confirmar com a resolução do Senado.'),
    (2029, NULL, NULL, NULL, 0.9000, FALSE,
     'ISS a 90%; IBS sobe para compensar. Alíquotas a definir (Senado / Comitê Gestor do IBS).'),
    (2030, NULL, NULL, NULL, 0.8000, FALSE,
     'ISS a 80%; IBS sobe para compensar. Alíquotas a definir.'),
    (2031, NULL, NULL, NULL, 0.7000, FALSE,
     'ISS a 70%; IBS sobe para compensar. Alíquotas a definir.'),
    (2032, NULL, NULL, NULL, 0.6000, FALSE,
     'ISS a 60%; IBS sobe para compensar. Alíquotas a definir.'),
    (2033, NULL, NULL, NULL, 0.0000, FALSE,
     'ISS extinto; IBS pleno. Alíquotas a definir.');

-- ─── Chave por empresa (fiscal.parametro_fiscal) ────────────────────────────────
-- Registro de quem homologou a chave (a homologação é o que a torna válida).
ALTER TABLE fiscal.parametro_fiscal
    ADD COLUMN homologado_por VARCHAR(100),
    ADD COLUMN homologado_em  TIMESTAMPTZ;

COMMENT ON COLUMN fiscal.parametro_fiscal.ibs_cbs_ativo         IS 'Chave da reforma por empresa: true = a partir de vigencia_inicio a empresa usa o regime IBS/CBS (PIS/COFINS deixam de ser calculados; ISS, IR e CSLL continuam). Só vale homologado.';
COMMENT ON COLUMN fiscal.parametro_fiscal.aliq_ibs_cbs          IS 'DEPRECADO (CBS-10): as alíquotas vêm de fiscal.cronograma_reforma por ano. Mantido só por compatibilidade.';
COMMENT ON COLUMN fiscal.parametro_fiscal.reducao_ibs_cbs_saude IS 'DEPRECADO (CBS-10): a redução passa a ser do serviço (CST 200 / cClassTrib 200029, task CBS-12). Mantido só por compatibilidade.';
COMMENT ON COLUMN fiscal.parametro_fiscal.homologado_por        IS 'Usuário (subject do JWT) que homologou o parâmetro.';
COMMENT ON COLUMN fiscal.parametro_fiscal.homologado_em         IS 'Momento da homologação.';
