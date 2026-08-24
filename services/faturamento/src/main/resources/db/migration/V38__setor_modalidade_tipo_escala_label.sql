-- Pedido do cliente: o cadastro de Setor Operacional passa a definir explicitamente qual a
-- Modalidade daquele setor (usada para derivar o Tipo de Escala — Plantonista/Diarista — da
-- frequência assim que o setor é escolhido, sem precisar mais perguntar isso na tela de Nova
-- Frequência) e um texto customizável exibido no campo "Tipo de Escala" do PDF (sugestão default
-- "Modalidade - Setor", editável pelo usuário).
--
-- Nullable pra não quebrar setores já cadastrados — obrigatório só nos cadastros/edições novas,
-- validado em TomadorServicoOperacionalRequest (Bean Validation), mesmo padrão já usado em outras
-- colunas "novas-obrigatórias" deste schema (ex: horas_semanais em tomador_modalidades).
ALTER TABLE faturamento.tomador_servicos_operacionais
    ADD COLUMN modalidade_id UUID REFERENCES faturamento.tomador_modalidades(id),
    ADD COLUMN tipo_escala_label VARCHAR(150);
