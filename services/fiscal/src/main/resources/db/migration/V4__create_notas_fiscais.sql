-- Enum de status do ciclo de vida da NFS-e
CREATE TYPE fiscal.status_nota_enum AS ENUM (
    'PENDENTE',     -- aguardando envio para SEFAZ/prefeitura
    'PROCESSANDO',  -- em trânsito / aguardando retorno
    'EMITIDA',      -- nota emitida com sucesso
    'CANCELADA',    -- cancelada após emissão
    'ERRO',         -- falha técnica no envio
    'REJEITADA'     -- recusada pela SEFAZ (dados inválidos)
);

-- Notas fiscais de serviços (NFS-e) emitidas pela Pin Saúde
-- Todos os valores monetários em centavos (BIGINT)
-- Modelo financeiro: médico sempre recebe 85% (valor_bruto - taxa_pin)
--                    tributos são custo da Pin, saem dos 15% da taxa_pin
CREATE TABLE fiscal.notas_fiscais (
    id                   UUID                   PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_id_tenant       VARCHAR(14)            NOT NULL,
    producao_id          UUID                   NOT NULL,  -- FK lógica → faturamento.producoes
    medico_id            UUID                   NOT NULL,  -- FK lógica → onboarding.medicos
    tomador_id           UUID                   NOT NULL,  -- FK lógica → faturamento.tomadores
    numero_nota          VARCHAR(20),                      -- número RPS / NFS-e após emissão
    competencia          VARCHAR(7)             NOT NULL,  -- YYYY-MM do fato gerador
    valor_bruto          BIGINT                 NOT NULL,  -- centavos: total bruto do tomador
    taxa_pin             BIGINT                 NOT NULL,  -- centavos: 15% retidos pela Pin
    valor_iss            BIGINT                 NOT NULL DEFAULT 0,
    valor_ir             BIGINT                 NOT NULL DEFAULT 0,
    valor_csll           BIGINT                 NOT NULL DEFAULT 0,
    valor_pis            BIGINT                 NOT NULL DEFAULT 0,
    valor_cofins         BIGINT                 NOT NULL DEFAULT 0,
    valor_liquido_medico BIGINT                 NOT NULL,  -- = valor_bruto - taxa_pin (sempre 85%)
    status               fiscal.status_nota_enum NOT NULL DEFAULT 'PENDENTE',
    xml_nota             TEXT,                             -- XML RPS enviado / NFS-e retornado
    pdf_nota             BYTEA,                            -- PDF da nota
    protocolo_emissao    VARCHAR(100),
    observacoes          TEXT,
    created_at           TIMESTAMPTZ            NOT NULL DEFAULT NOW(),
    emitida_at           TIMESTAMPTZ,

    -- Garante consistência: médico sempre recebe exatamente valor_bruto - taxa_pin
    CONSTRAINT nf_liquido_check CHECK (valor_liquido_medico = valor_bruto - taxa_pin)
);

-- Índices para processamento em lote e consultas por tenant/período
CREATE INDEX idx_nf_tenant_comp   ON fiscal.notas_fiscais (cnpj_id_tenant, competencia);
CREATE INDEX idx_nf_status        ON fiscal.notas_fiscais (status);
CREATE INDEX idx_nf_producao      ON fiscal.notas_fiscais (producao_id);
CREATE INDEX idx_nf_medico_comp   ON fiscal.notas_fiscais (medico_id, competencia);
CREATE INDEX idx_nf_comp_status   ON fiscal.notas_fiscais (competencia, status);  -- lote

-- Retenções sofridas por nota (ISS retido pelo tomador, IR retido na fonte, etc.)
-- tipo_tributo como VARCHAR+CHECK para incluir INSS futuramente sem alterar enum
CREATE TABLE fiscal.retencoes_sofridas (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    nota_id      UUID        NOT NULL REFERENCES fiscal.notas_fiscais(id) ON DELETE CASCADE,
    tipo_tributo VARCHAR(10) NOT NULL
        CHECK (tipo_tributo IN ('ISS', 'IR', 'CSLL', 'PIS', 'COFINS', 'INSS')),
    valor_retido BIGINT      NOT NULL,  -- centavos

    CONSTRAINT retencoes_sofridas_uq UNIQUE (nota_id, tipo_tributo)
);

CREATE INDEX idx_ret_nota ON fiscal.retencoes_sofridas (nota_id);
