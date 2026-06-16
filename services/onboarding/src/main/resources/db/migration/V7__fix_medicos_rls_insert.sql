-- ─── V7: Fix RLS INSERT em medicos e tabelas filhas (EPIC-03.2) ──────────────
-- Problema: policy USING sem WITH CHECK bloqueia INSERT em medicos porque o
-- novo medico_id ainda não existe em vinculos_medico_empresa no momento do INSERT.
-- Solução: WITH CHECK (true) permite INSERT; USING continua restringindo SELECT/UPDATE.
-- O mesmo padrão é aplicado em checklist_conduta, dados_bancarios_medico e
-- documentos_medico, cujo INSERT ocorre imediatamente após o medico ser criado
-- (dentro da mesma transação, mas vinculos pode ainda não ser visível em sub-SELECTs
-- dependendo do momento de avaliação).

ALTER POLICY tenant_isolation ON onboarding.medicos WITH CHECK (true);

ALTER POLICY tenant_isolation ON onboarding.checklist_conduta WITH CHECK (true);

ALTER POLICY tenant_isolation ON onboarding.dados_bancarios_medico WITH CHECK (true);

ALTER POLICY tenant_isolation ON onboarding.documentos_medico WITH CHECK (true);
