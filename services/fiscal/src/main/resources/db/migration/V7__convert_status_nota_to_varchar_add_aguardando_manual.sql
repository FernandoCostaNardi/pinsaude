-- Converte status para varchar e adiciona AGUARDANDO_EMISSAO_MANUAL (EPIC-05.4)
-- Mesmo motivo de V6: Hibernate 6 não faz cast automático de varchar → enum PostgreSQL em WHERE.
-- AGUARDANDO_EMISSAO_MANUAL: fallback quando agregador fiscal está indisponível.
-- Passo 1: remover default que referencia o enum (default 'PENDENTE' usa o tipo enum)
ALTER TABLE fiscal.notas_fiscais
    ALTER COLUMN status DROP DEFAULT;

-- Passo 2: converter a coluna para varchar; sem default pendente o DROP TYPE funciona
ALTER TABLE fiscal.notas_fiscais
    ALTER COLUMN status TYPE varchar(40) USING status::text;

-- Passo 3: agora é seguro remover o tipo
DROP TYPE fiscal.status_nota_enum;

-- Passo 4: restaurar default e adicionar CHECK constraint
ALTER TABLE fiscal.notas_fiscais
    ALTER COLUMN status SET DEFAULT 'PENDENTE';

ALTER TABLE fiscal.notas_fiscais
    ADD CONSTRAINT notas_fiscais_status_check
    CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'EMITIDA', 'CANCELADA', 'ERRO',
                      'REJEITADA', 'AGUARDANDO_EMISSAO_MANUAL'));
