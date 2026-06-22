-- Adiciona AGUARDANDO_VALIDACAO ao CHECK constraint de status (EPIC-05.5)
-- Usado para 1ª nota de um médico novo — aguarda validação da equipe antes de emitir.
ALTER TABLE fiscal.notas_fiscais DROP CONSTRAINT notas_fiscais_status_check;

ALTER TABLE fiscal.notas_fiscais
    ADD CONSTRAINT notas_fiscais_status_check
    CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'EMITIDA', 'CANCELADA', 'ERRO',
                      'REJEITADA', 'AGUARDANDO_EMISSAO_MANUAL', 'AGUARDANDO_VALIDACAO'));
