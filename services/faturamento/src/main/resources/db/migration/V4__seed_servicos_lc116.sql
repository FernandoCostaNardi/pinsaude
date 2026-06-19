-- ─── V4: Seed de serviços LC 116/2003 — subitem 4 (Saúde) ───────────────────
-- Alíquotas padrão nacionais para serviços médicos.
-- ISS: 5% (alíquota máxima LC 116 — deduzido conforme município do tomador)
-- IR:  1.5%, CSLL: 1%, PIS: 0.65%, COFINS: 3% (retenções sobre serviços médicos)
-- Valores ajustáveis por empresa via aliquotas_competencia (EPIC-02.4).

INSERT INTO faturamento.servicos (id, codigo_lc116, cnae, descricao_padrao, indicador_equiparacao,
    aliquota_iss, aliquota_ir, aliquota_csll, aliquota_pis, aliquota_cofins)
VALUES
    (gen_random_uuid(), '4.01', '8630-5/01', 'Medicina e biomedicina',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.02', '8640-2/01', 'Análises clínicas, patologia, eletricidade médica, radioterapia, quimioterapia',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.03', '8610-1/01', 'Hospitais, clínicas, laboratórios, sanatórios, manicômios, casas de saúde',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.06', '8640-2/99', 'Enfermagem, inclusive serviços auxiliares',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.07', '8650-0/01', 'Serviços farmacêuticos',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.08', '8650-0/99', 'Terapia ocupacional, fisioterapia e fonoaudiologia',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.09', '8650-0/04', 'Terapias de qualquer espécie destinadas ao tratamento físico, orgânico e mental',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.11', '8630-5/02', 'Obstetrícia',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.12', '8630-5/04', 'Odontologia',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.13', '8640-2/02', 'Diagnose, raios X, radiodiagnose, tomografia, eletricidade médica, eletroterapia',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.14', '8640-2/03', 'Próteses sob encomenda',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.16', '8650-0/06', 'Psicologia, psicanálise, terapia ocupacional, acupuntura, podologia',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.17', '8690-9/99', 'Casas de repouso e de recuperação, creches, asilos e congêneres',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.22', '8660-7/00', 'Planos de medicina de grupo ou individual e convênios',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000),

    (gen_random_uuid(), '4.23', '6550-8/00', 'Outros planos de saúde que se cumpram através de serviços de terceiros',
     FALSE, 5.0000, 1.5000, 1.0000, 0.6500, 3.0000);
