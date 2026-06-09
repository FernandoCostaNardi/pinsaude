# EPIC-07 — Recebimento e Conciliação

> Prioridade: **P1** — Sem conciliação, o ledger não é atualizado e o repasse não é autorizado.
> ADRs: ADR-0006. PRD: §7.7. RFs: RF-REC-01..03
> **MVP:** Conciliação por importação de extrato (CSV/OFX). Open Finance (banco Inter/BTG) é Fase 2.

---

## TASK-07.1 — Importação de Extrato Bancário (Adapter Manual MVP)

### 1. Objetivo (Por quê?)
O MVP não integra com bancos via API (Open Finance é Fase 2 — ADR-0006). A conciliação é feita por importação de arquivo de extrato (CSV/OFX) exportado do banco pela equipe financeira. Sem essa importação, não é possível saber quais notas foram pagas.

### 2. Descrição da Solução (O quê?)
Upload de extrato bancário com parser de CSV/OFX e armazenamento das entradas para conciliação.

**Port (interface do domínio `recebimento`):**
```java
public interface LeituraExtratoPort {
    List<EntradaBancaria> importar(InputStream arquivo, FormatoExtrato formato);
}
public enum FormatoExtrato { CSV_INTER, CSV_BTG, OFX }
```

**Adapter de importação:**
```java
@Component
public class ImportacaoExtratoAdapter implements LeituraExtratoPort {
    // Parser CSV: detecta separador, mapeia colunas
    // Parser OFX: extrai STMTTRN elements
    // Retorna lista de EntradaBancaria normalizadas
}
```

**Migração (`recebimento.V1__create_recebimento.sql`):**
```sql
CREATE TABLE recebimento.entrada_bancaria (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  empresa_id      UUID NOT NULL,
  data_movimento  DATE NOT NULL,
  valor           BIGINT NOT NULL,   -- centavos, sempre positivo (filtrar créditos)
  descricao       TEXT,              -- texto do extrato (identificador do pagador)
  documento       VARCHAR(50),       -- CPF/CNPJ do pagador se disponível
  nosso_numero    VARCHAR(50),       -- identificador da transação no banco
  status          VARCHAR(20) NOT NULL DEFAULT 'NAO_CONCILIADO',
  -- NAO_CONCILIADO → CONCILIADO_PARCIAL → CONCILIADO → IGNORADO
  lote_importacao UUID NOT NULL,     -- ID do arquivo importado (rastreabilidade)
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entrada_status ON recebimento.entrada_bancaria (cnpj_id, status, data_movimento);
ENABLE ROW LEVEL SECURITY ON recebimento.entrada_bancaria;

CREATE TABLE recebimento.lote_importacao (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id      UUID NOT NULL,
  empresa_id   UUID NOT NULL,
  nome_arquivo VARCHAR(255) NOT NULL,
  formato      VARCHAR(20) NOT NULL,
  periodo_de   DATE NOT NULL,
  periodo_ate  DATE NOT NULL,
  total_entradas INT NOT NULL,
  total_valor   BIGINT NOT NULL,
  importado_por UUID NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Endpoint de importação:**
```
POST /recebimentos/importar-extrato
  role: FINANCEIRO, GESTAO
  multipart: arquivo (CSV ou OFX), empresa_id, formato (CSV_INTER | CSV_BTG | OFX)
  → faz upload para MinIO (armazena o arquivo original)
  → parseia o arquivo
  → cria lote_importacao
  → cria entrada_bancaria para cada crédito (movimentos positivos)
  → retorna: { "lote_id": "uuid", "total_entradas": 45, "total_valor": 125000000 }

GET /recebimentos/entradas?status=NAO_CONCILIADO&empresa_id=uuid
  role: FINANCEIRO, GESTAO
  → lista entradas pendentes de conciliação

GET /recebimentos/lotes
  → histórico de importações (role: FINANCEIRO, GESTAO)
```

### 3. Critérios de Aceite
- [ ] Upload de CSV do banco Inter parseia corretamente e cria entradas.
- [ ] Upload de OFX parseia corretamente e cria entradas.
- [ ] Apenas créditos (entradas positivas) são importados — débitos são ignorados.
- [ ] Arquivo já importado (mesmo nome + empresa + período) retorna 409 "Extrato já importado".
- [ ] Arquivo original armazenado no MinIO para auditoria.
- [ ] Importação idempotente: mesma `nosso_numero` + empresa não cria duplicata.

### 4. Regras de Negócio
- MVP: conciliação por importação de arquivo (ADR-0006). Fase 2: Open Finance.
- Apenas créditos são importados (entradas da conta da Pin).
- Idempotência: mesmo número de transação não importado duas vezes.
- Arquivo original mantido para auditoria.

### 5. Cenários de Testes para o Humano
1. **Upload CSV:** Fazer upload de arquivo CSV de extrato do Inter com 10 lançamentos → verificar criação de 7 créditos (ignorando 3 débitos) na tabela.
2. **Upload OFX:** Fazer upload de arquivo OFX → verificar mesmo comportamento.
3. **Duplicidade:** Importar o mesmo arquivo duas vezes → segunda importação retorna 409.
4. **Arquivo corrompido:** Upload de arquivo CSV mal-formatado → retorna 400 com descrição do erro de parse.

---

## TASK-07.2 — Matching de Pagamento com Nota Fiscal

### 1. Objetivo (Por quê?)
Uma entrada bancária precisa ser vinculada à(s) nota(s) fiscal(is) que ela quitou. Hoje não há identificador no pagamento — o matching é por valor + tomador + data (premissa O2 do PRD). Sem o matching, o ledger não pode ser creditado.

### 2. Descrição da Solução (O quê?)
Motor de matching que sugere automaticamente a(s) nota(s) candidata(s) para cada entrada bancária, com confirmação humana.

**Migração (`recebimento.V2__create_conciliacao.sql`):**
```sql
CREATE TABLE recebimento.conciliacao (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id          UUID NOT NULL,
  entrada_id       UUID NOT NULL REFERENCES recebimento.entrada_bancaria(id),
  nota_fiscal_id   UUID NOT NULL,    -- FK lógica para faturamento.nota_fiscal
  tipo             VARCHAR(10) NOT NULL CHECK (tipo IN ('TOTAL','PARCIAL')),
  valor_conciliado BIGINT NOT NULL,   -- centavos
  confirmado_por   UUID,              -- usuário que confirmou
  confirmado_em    TIMESTAMPTZ,
  status           VARCHAR(20) NOT NULL DEFAULT 'SUGERIDO',
  -- SUGERIDO → CONFIRMADO → REJEITADO
  score_match      SMALLINT,          -- 0-100, score de confiança
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Algoritmo de matching (PRD O2 — RF-REC-02):**
```java
public List<CandidatoMatch> sugerirMatch(EntradaBancaria entrada) {
    // Critérios em ordem de prioridade:
    // 1. Valor exato: nota com valor_bruto = entrada.valor (score: 80)
    // 2. Tomador no texto: entrada.descricao ILIKE '%' + tomador.razao_social + '%' (+15)
    // 3. Data proxima: emissao_nota dentro de 30 dias da entrada.data_movimento (+5)
    // 4. Nota EMITIDA e não conciliada ainda (requisito)

    // Para pagamentos em lote (um pagamento cobre várias notas):
    // combinação de notas cuja soma = entrada.valor (NP-hard, implementar com limite)

    return candidatos ordenados por score;
}
```

**Endpoint de matching:**
```
GET /recebimentos/entradas/{id}/sugestoes
  → retorna lista de candidatos com score
  role: FINANCEIRO, GESTAO

POST /recebimentos/entradas/{id}/confirmar-match
  body: { "nota_fiscal_ids": ["uuid1", "uuid2"], "tipo": "TOTAL" }
  role: FINANCEIRO, GESTAO
  → valida que a soma das notas = valor da entrada
  → cria registros de conciliacao com status CONFIRMADO
  → atualiza entrada para CONCILIADO
  → publica evento RecebimentoConciliado com todas as notas vinculadas
  → registra em audit_log

POST /recebimentos/entradas/{id}/ignorar
  body: { "motivo": "Devolução de duplicidade" }
  role: FINANCEIRO, GESTAO
  → atualiza entrada para IGNORADO
```

### 3. Critérios de Aceite
- [ ] `GET /entradas/{id}/sugestoes` retorna candidatos ordenados por score.
- [ ] Nota com valor exato igual à entrada tem score >= 80.
- [ ] Confirmação com valor que não bate com as notas selecionadas retorna 400.
- [ ] Evento `RecebimentoConciliado` publicado após confirmação.
- [ ] Entrada confirmada muda para `CONCILIADO`.
- [ ] Tentativa de confirmar uma nota já conciliada retorna 409.

### 4. Regras de Negócio
- Matching por valor + tomador + data (premissa O2 — PRD §13).
- Confirmação humana obrigatória (não automática no MVP).
- Pagamentos em lote: um pagamento pode cobrir múltiplas notas.
- Nota só pode ser conciliada uma vez (idempotência).
- Confirmação registrada em auditoria (quem conciliou e quando).

### 5. Cenários de Testes para o Humano
1. **Match exato:** Emitir nota de R$5.000 para Hospital X. Importar extrato com entrada de R$5.000 do Hospital X → sugestão deve aparecer com score alto.
2. **Match por lote:** Emitir 2 notas de R$2.000 e R$3.000 para mesmo hospital. Importar extrato com entrada de R$5.000 → sugestão deve mostrar combinação das 2 notas.
3. **Confirmação:** Confirmar match para nota X → entrada vai para `CONCILIADO`, nota_fiscal recebe referência do recebimento.
4. **Dupla conciliação:** Tentar conciliar a mesma nota com outra entrada → deve retornar 409 "Nota já conciliada".
5. **Ignorar entrada:** Entrada de R$100 sem nota correspondente → ignorar com motivo → entrada vai para `IGNORADO`.

---

## TASK-07.3 — Baixa do Recebimento e Crédito no Ledger

### 1. Objetivo (Por quê?)
Após a conciliação confirmada, o ledger precisa ser atualizado: o médico tem o saldo de repasse liberado para ser executado. Em regime de caixa, o repasse só é autorizado após o hospital pagar (RF-REC-03, RF-LED-02).

### 2. Descrição da Solução (O quê?)
Consumer do evento `RecebimentoConciliado` que cria os lançamentos de baixa no ledger e libera o repasse.

**Consumer: `RecebimentoConciliado` → lançamento de recebimento:**
```java
@RabbitListener(queues = "recebimento.conciliado.q")
@Transactional
public void onRecebimentoConciliado(RecebimentoConciliadoEvent event) {
    if (processedEvents.exists(event.eventId())) return;

    for (UUID notaId : event.notasFiscaisIds()) {
        NotaFiscal nota = notaFiscalRepository.findById(notaId);

        // Lançamento: registra que o hospital pagou
        // débito:  CAIXA (ou conta bancária da Pin)
        // crédito: CREDITO_NOTA (baixa o ativo a receber)
        var lancamento = LancamentoBuilder.paraRecebimento(nota, event.valorRecebido());
        ledgerService.registrar(lancamento);

        // Liberar repasse: muda status da produção para REPASSE_AUTORIZADO
        producaoService.autorizarRepasse(nota.getProducaoId());
    }

    processedEvents.save(event.eventId());
}
```

**Atualização da nota fiscal:**
```java
// nota_fiscal: adicionar campos
ALTER TABLE faturamento.nota_fiscal
  ADD COLUMN entrada_bancaria_id UUID,
  ADD COLUMN recebida_em TIMESTAMPTZ,
  ADD COLUMN status_recebimento VARCHAR(20) DEFAULT 'PENDENTE';
-- PENDENTE → RECEBIDA → PARCIAL
```

### 3. Critérios de Aceite
- [ ] Evento `RecebimentoConciliado` cria lançamento de baixa no ledger para cada nota conciliada.
- [ ] Após baixa, produção muda para `REPASSE_AUTORIZADO` (disponível para o worker de repasse).
- [ ] Consumer é idempotente: mesmo evento processado 2x não cria 2 lançamentos.
- [ ] Lançamento de baixa referencia `nota_fiscal_id` e `entrada_bancaria_id`.
- [ ] `GET /portal/medico/me/saldo` reflete o novo saldo após recebimento.

### 4. Regras de Negócio
- Regime de caixa: repasse só autorizado após pagamento confirmado (RF-LED-02, RF-REC-03).
- Baixa do recebimento é trigger do repasse.
- Médico não precisa fazer nada — é automático via evento.
- Um lançamento de baixa por nota conciliada.

### 5. Cenários de Testes para o Humano
1. **Fluxo completo:** Emitir nota → importar extrato → confirmar match → verificar: lançamento de recebimento criado no ledger + produção com status `REPASSE_AUTORIZADO`.
2. **Saldo atualizado:** Antes da conciliação: saldo "a receber" = R$8.500. Após: saldo "disponível para repasse" = R$8.500.
3. **Idempotência:** Reenviar evento `RecebimentoConciliado` → verificar que apenas 1 lançamento de baixa existe.
4. **Regime de caixa:** Emitir nota mas não importar extrato → verificar que `status_recebimento = PENDENTE` e repasse não fica disponível.
