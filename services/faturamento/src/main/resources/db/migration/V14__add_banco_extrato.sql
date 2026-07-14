-- V14: Adiciona coluna banco em extratos_bancarios (INTER/BTG/OUTRO)
ALTER TABLE faturamento.extratos_bancarios
    ADD COLUMN banco VARCHAR(20);
