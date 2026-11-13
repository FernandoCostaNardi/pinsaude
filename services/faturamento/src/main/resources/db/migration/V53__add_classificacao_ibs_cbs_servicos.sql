-- CBS-12 — Classificação tributária do serviço para CBS/IBS (reforma tributária).
-- A redução de 60% depende da natureza do serviço (Anexo III, cClassTrib 200029), não do médico.
-- O motor fiscal (CBS-13) lê estes campos do serviço da produção.
--   nbs              código NBS da nota (formato 1.2301.19.00)
--   cst_ibs_cbs      CST do grupo IBSCBS (3 dígitos)
--   c_class_trib     classificação tributária (6 dígitos)
--   reducao_ibs_cbs  redução da alíquota como fração (0.6000 = 60%)

ALTER TABLE faturamento.servicos
    ADD COLUMN nbs             VARCHAR(12),
    ADD COLUMN cst_ibs_cbs     VARCHAR(3),
    ADD COLUMN c_class_trib    VARCHAR(6),
    ADD COLUMN reducao_ibs_cbs NUMERIC(5,4) NOT NULL DEFAULT 0;

ALTER TABLE faturamento.servicos
    ADD CONSTRAINT servicos_nbs_check          CHECK (nbs IS NULL OR nbs ~ '^[0-9]\.[0-9]{4}\.[0-9]{2}\.[0-9]{2}$'),
    ADD CONSTRAINT servicos_cst_ibs_cbs_check  CHECK (cst_ibs_cbs IS NULL OR cst_ibs_cbs ~ '^[0-9]{3}$'),
    ADD CONSTRAINT servicos_c_class_trib_check CHECK (c_class_trib IS NULL OR c_class_trib ~ '^[0-9]{6}$'),
    ADD CONSTRAINT servicos_reducao_check      CHECK (reducao_ibs_cbs >= 0 AND reducao_ibs_cbs <= 1);

COMMENT ON COLUMN faturamento.servicos.nbs             IS 'Código NBS (Nomenclatura Brasileira de Serviços) informado na NFS-e';
COMMENT ON COLUMN faturamento.servicos.cst_ibs_cbs     IS 'CST do IBS/CBS (200 = tributação com redução)';
COMMENT ON COLUMN faturamento.servicos.c_class_trib    IS 'Classificação tributária do IBS/CBS (200029 = saúde humana, Anexo III)';
COMMENT ON COLUMN faturamento.servicos.reducao_ibs_cbs IS 'Redução da alíquota de IBS/CBS como fração (0.6000 = 60%)';

-- Serviços médicos prestados pelos médicos da Pin: CST 200 / cClassTrib 200029 / redução de 60%
-- (validado pelo contador em 11/11/2026, CBS-04). NBS inicial a confirmar com o contador:
--   1.2301.19.00 = outros serviços hospitalares (plantões em hospital)
--   1.2301.22.00 = serviços médicos especializados não hospitalares (exames, diagnóstico)
UPDATE faturamento.servicos SET nbs = '1.2301.19.00', cst_ibs_cbs = '200', c_class_trib = '200029', reducao_ibs_cbs = 0.6000
 WHERE codigo_lc116 IN ('4.01', '4.03', '4.11');
UPDATE faturamento.servicos SET nbs = '1.2301.22.00', cst_ibs_cbs = '200', c_class_trib = '200029', reducao_ibs_cbs = 0.6000
 WHERE codigo_lc116 IN ('4.02', '4.13');
