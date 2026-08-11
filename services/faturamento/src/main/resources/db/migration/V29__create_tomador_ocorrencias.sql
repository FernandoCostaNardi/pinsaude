-- EPIC-13.19.5: Catalogo de Ocorrencias por tomador + valor no lancamento da frequencia.
-- Ocorrencia pode ter valor PERCENTUAL (% sobre o valor da modalidade do item), FIXO (centavos),
-- ou SEM_VALOR (texto/observacao livre, sem impacto financeiro). O calculo soma os dois campos
-- quando ambos estao preenchidos: ocorrencia_valor = round(modalidade.valor * pct/100) + fixo —
-- por isso tipo_valor so exige que o campo "principal" do tipo esteja preenchido, mas nao proibe
-- o outro campo tambem estar (ex: uma ocorrencia PERCENTUAL pode ter um extra FIXO agregado).

CREATE TABLE faturamento.tomador_ocorrencias (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tomador_id       UUID         NOT NULL REFERENCES faturamento.tomadores(id) ON DELETE CASCADE,
    nome             VARCHAR(120) NOT NULL,
    tipo_valor       VARCHAR(12)  NOT NULL,
    valor_percentual NUMERIC(8,4),
    valor_centavos   BIGINT,
    ativo            BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tomador_ocorrencias_tipo_valor_check
        CHECK (tipo_valor IN ('PERCENTUAL', 'FIXO', 'SEM_VALOR')),
    CONSTRAINT tomador_ocorrencias_valor_percentual_check
        CHECK (valor_percentual IS NULL OR valor_percentual >= 0),
    CONSTRAINT tomador_ocorrencias_valor_centavos_check
        CHECK (valor_centavos IS NULL OR valor_centavos >= 0),
    -- Cada tipo exige o seu campo "principal"; o outro campo fica livre (permite combinar
    -- % + fixo simultaneamente, ver comentario acima). SEM_VALOR exige os dois campos vazios.
    CONSTRAINT tomador_ocorrencias_tipo_campos_check CHECK (
        (tipo_valor = 'SEM_VALOR' AND valor_percentual IS NULL AND valor_centavos IS NULL)
        OR (tipo_valor = 'PERCENTUAL' AND valor_percentual IS NOT NULL)
        OR (tipo_valor = 'FIXO' AND valor_centavos IS NOT NULL)
    )
);

ALTER TABLE faturamento.tomador_ocorrencias ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.tomador_ocorrencias FORCE ROW LEVEL SECURITY;

-- FORCE obrigatorio (mesmo motivo documentado em medico_tomadores/V21): o app conecta como
-- svc_faturamento, dono da tabela, e sem FORCE o owner bypassa a policy.
CREATE POLICY tenant_isolation ON faturamento.tomador_ocorrencias
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR tomador_id IN (
            SELECT id FROM faturamento.tomadores
            WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
        )
    )
    WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON faturamento.tomador_ocorrencias TO svc_faturamento;

CREATE INDEX idx_tomador_ocorrencias_tomador_id ON faturamento.tomador_ocorrencias(tomador_id);

-- ─── Aplicacao da ocorrencia no item de frequencia ─────────────────────────────
-- ocorrencia_id: quando a ocorrencia vem do catalogo (nullable — continua permitindo texto
-- livre via a coluna "ocorrencia" ja existente, sem catalogo, sem valor).
-- ocorrencia_valor_centavos: snapshot do valor calculado no momento do lancamento, mesmo
-- padrao de snapshot ja usado para valor_unitario_centavos/deslocamento_centavos.
ALTER TABLE faturamento.frequencia_itens
    ADD COLUMN ocorrencia_id             UUID REFERENCES faturamento.tomador_ocorrencias(id),
    ADD COLUMN ocorrencia_valor_centavos BIGINT;

ALTER TABLE faturamento.frequencia_itens
    ADD CONSTRAINT frequencia_itens_ocorrencia_valor_centavos_check
        CHECK (ocorrencia_valor_centavos IS NULL OR ocorrencia_valor_centavos >= 0);
