-- Permite ao médico ter múltiplas contas bancárias (PIX e/ou várias contas TED).
-- A tabela onboarding.dados_bancarios_medico já não tinha nenhuma restrição de
-- unicidade por medico_id (PK é o id próprio, FK apenas referencia o médico) —
-- a limitação a um único registro era só do service/frontend (upsert), nunca do
-- banco. Esta migration só adiciona um rótulo opcional para diferenciar contas
-- na UI quando o médico tem mais de uma (ex: "PIX principal", "TED Itaú salário").

ALTER TABLE onboarding.dados_bancarios_medico
    ADD COLUMN IF NOT EXISTS apelido VARCHAR(100);
