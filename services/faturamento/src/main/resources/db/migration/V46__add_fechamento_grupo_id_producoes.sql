-- Bug real: GET /api/fechamentos e GET /api/fechamentos/{id} sempre retornavam
-- producoes: [] — FechamentoService.listar()/buscarPorId() chamam
-- FechamentoResponse.from(f, List.of()), nunca consultando as produções de verdade.
-- Só a resposta SÍNCRONA do POST /api/fechamentos (o momento exato do "Fechar
-- Competência") tinha a lista correta, construída em memória durante o loop de
-- executar() e nunca persistida — assim que o usuário navega para outra tela (ou
-- consulta o Histórico de Fechamentos depois), o link "Emitir NFS-e" da produção
-- desaparece, mesmo a produção existindo normalmente no banco.
--
-- Corrige guardando a origem (fechamento + grupo) direto na produção, para que
-- listar()/buscarPorId() consigam reconstruir producoes[] a qualquer momento.
-- Nullable: produções da tela "Nova Produção" (fora do fluxo de Fechamento) não
-- têm fechamento/grupo de origem.

ALTER TABLE faturamento.producoes
    ADD COLUMN fechamento_id UUID NULL REFERENCES faturamento.fechamentos(id) ON DELETE SET NULL,
    ADD COLUMN grupo_id      UUID NULL REFERENCES faturamento.tomador_grupos_faturamento(id) ON DELETE SET NULL;

CREATE INDEX idx_producoes_fechamento_id ON faturamento.producoes(fechamento_id);

-- Backfill dos fechamentos já executados antes desta correção.
-- fechamento_id: (tomador_id, competencia) é UNIQUE em fechamentos (V19) — join direto e seguro.
UPDATE faturamento.producoes p
SET fechamento_id = f.id
FROM faturamento.fechamentos f
WHERE p.tomador_id = f.tomador_id
  AND p.competencia = f.competencia
  AND f.status = 'FECHADO'
  AND p.fechamento_id IS NULL;

-- grupo_id: uma produção não guarda o grupo de origem diretamente, mas toda frequência
-- daquele grupo foi marcada com producao_id + grupo_id ao fechar (FechamentoService.
-- executar) — join reverso via frequencias_medicas recupera o grupo correto mesmo com
-- múltiplas produções (uma por grupo) na mesma competência.
UPDATE faturamento.producoes p
SET grupo_id = fm.grupo_id
FROM faturamento.frequencias_medicas fm
WHERE fm.producao_id = p.id
  AND fm.grupo_id IS NOT NULL
  AND p.grupo_id IS NULL;
