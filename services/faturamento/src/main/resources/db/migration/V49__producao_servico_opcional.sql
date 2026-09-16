-- Pedido do cliente: no Portal do Médico, a escolha do Serviço (LC 116/2003) deixa de ser feita
-- pelo médico — passa a ser responsabilidade do time da Pin Saúde, atribuída depois (antes de
-- emitir a NFS-e). A produção passa a poder nascer sem servico_id; a operação preenche via
-- PUT /api/producoes/{id}/servico antes da emissão (ver ProducaoService.atualizarServico).
ALTER TABLE faturamento.producoes ALTER COLUMN servico_id DROP NOT NULL;
