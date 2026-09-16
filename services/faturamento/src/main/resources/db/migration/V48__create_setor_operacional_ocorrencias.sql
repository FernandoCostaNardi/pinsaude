-- Pedido do cliente: Ocorrências devem ser configuráveis por Setor Operacional — até aqui toda
-- ocorrência cadastrada (aba "Ocorrências") ficava disponível para qualquer setor do tomador, sem
-- nenhuma distinção. Mesmo padrão já usado em V39 (setor↔modalidade) e V35 (grupo↔setor): N:N via
-- tabela de vínculo, FORCE ROW LEVEL SECURITY obrigatório (app conecta como svc_faturamento, dono
-- da tabela — sem FORCE o owner bypassa a policy), WITH CHECK (true) pra não bloquear o INSERT (a
-- linha pai setor já existe e já pertence ao tenant no momento da inserção).
--
-- Compatibilidade: uma ocorrência SEM nenhum vínculo em setor_operacional_ocorrencias continua
-- disponível para QUALQUER setor do tomador (bypass) — evita que toda ocorrência já cadastrada
-- antes desta migration suma de todo lugar até alguém configurar manualmente os setores. Mesmo
-- espírito já usado para tipoMedico nulo em FrequenciaMedica (coupling opcional/legado).

CREATE TABLE faturamento.setor_operacional_ocorrencias (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    setor_id      UUID        NOT NULL REFERENCES faturamento.tomador_servicos_operacionais(id) ON DELETE CASCADE,
    ocorrencia_id UUID        NOT NULL REFERENCES faturamento.tomador_ocorrencias(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (setor_id, ocorrencia_id)
);

ALTER TABLE faturamento.setor_operacional_ocorrencias ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.setor_operacional_ocorrencias FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.setor_operacional_ocorrencias
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR setor_id IN (
            SELECT s.id FROM faturamento.tomador_servicos_operacionais s
            JOIN faturamento.tomadores t ON t.id = s.tomador_id
            WHERE t.cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.setor_operacional_ocorrencias TO svc_faturamento;

CREATE INDEX idx_setor_operacional_ocorrencias_setor_id ON faturamento.setor_operacional_ocorrencias (setor_id);
CREATE INDEX idx_setor_operacional_ocorrencias_ocorrencia_id ON faturamento.setor_operacional_ocorrencias (ocorrencia_id);
