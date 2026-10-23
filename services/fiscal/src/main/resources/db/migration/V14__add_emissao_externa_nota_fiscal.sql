-- Emissão externa: a NFS-e é emitida fora do sistema (portal da prefeitura) e a operação
-- registra aqui o número/data e anexa o PDF e/ou XML recebidos (colunas pdf_nota / xml_nota).
ALTER TABLE fiscal.notas_fiscais
    ADD COLUMN emissao_externa BOOLEAN NOT NULL DEFAULT FALSE;
