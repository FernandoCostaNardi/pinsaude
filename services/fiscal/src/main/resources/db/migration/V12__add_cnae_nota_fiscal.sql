-- CNAE selecionado na emissão da NFS-e (vem dos CNAEs cadastrados no tomador)
ALTER TABLE fiscal.notas_fiscais
    ADD COLUMN IF NOT EXISTS cnae_codigo VARCHAR(20);
