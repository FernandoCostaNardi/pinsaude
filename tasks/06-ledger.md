# EPIC-06 — Conta Virtual / Ledger do Médico

> Prioridade: **P0** — É o coração financeiro. O repasse depende do ledger.
> ADRs: ADR-0008. PRD: §5.3, §7.6. RFs: RF-LED-01..04
> **CRÍTICO:** Lançamentos são IMUTÁVEIS (append-only). Correções = estorno + novo lançamento. Dinheiro em centavos (BIGINT).

---

## TASK-06.1 — Modelo de Dados do Ledger (Partidas Dobradas, Append-Only)

### 1. Objetivo (Por quê?)
O ledger registra todos os movimentos financeiros do médico (crédito da nota, retenções, ISS, taxa administrativa, repasse, ajustes). É a fonte de verdade do que o médico tem a receber e do que já recebeu. Erros aqui têm impacto direto em dinheiro real (ADR-0008).

### 2. Descrição da Solução (O quê?)
Criar o modelo de dados do ledger com lançamentos imutáveis em partidas dobradas, em centavos.

**Migração (`ledger.V1__create_ledger.sql`):**
```sql
-- Contas do plano de contas (lado débito/crédito)
CREATE TABLE ledger.conta (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  codigo      VARCHAR(20) NOT NULL UNIQUE,
  nome        VARCHAR(100) NOT NULL,
  tipo        VARCHAR(10) NOT NULL CHECK (tipo IN ('ATIVO','PASSIVO','RECEITA','DESPESA')),
  natureza    VARCHAR(6) NOT NULL CHECK (natureza IN ('DEBIT','CREDIT'))
  -- ATIVO/DEBIT: saldo aumenta com débito
  -- PASSIVO/CREDIT: saldo aumenta com crédito
);

-- Contas do sistema (seed):
-- 1001 CREDITO_NOTA    ATIVO    DEBIT   (crédito da nota a receber)
-- 1002 RETENCAO_IR     PASSIVO  CREDIT  (IR retido na fonte)
-- 1003 RETENCAO_PIS    PASSIVO  CREDIT
-- 1004 RETENCAO_COFINS PASSIVO  CREDIT
-- 1005 RETENCAO_CSLL   PASSIVO  CREDIT
-- 1006 ISS             PASSIVO  CREDIT
-- 1007 TAXA_ADM        PASSIVO  CREDIT
-- 1008 REPASSE         PASSIVO  CREDIT  (repasse a pagar ao médico)
-- 1009 AJUSTE          ATIVO    DEBIT   (ajustes manuais)

CREATE TABLE ledger.lancamento (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id        UUID NOT NULL,
  medico_id      UUID NOT NULL,
  competencia    DATE NOT NULL,      -- primeiro dia do mês
  descricao      VARCHAR(500) NOT NULL,
  origem_tipo    VARCHAR(30) NOT NULL,  -- 'NOTA_FISCAL','REPASSE','AJUSTE','ESTORNO'
  origem_id      UUID NOT NULL,         -- ID da nota, repasse, etc.
  regra_fiscal_id UUID,                 -- parâmetro fiscal usado (para auditoria)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- Lançamento é imutável: sem updated_at, sem soft-delete
  -- Cancelamento = novo lançamento de ESTORNO com origem_tipo = 'ESTORNO'
  CONSTRAINT lancamento_imutavel CHECK (true)  -- marker semântico
);

CREATE TABLE ledger.partida (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lancamento_id  UUID NOT NULL REFERENCES ledger.lancamento(id),
  conta_id       UUID NOT NULL REFERENCES ledger.conta(id),
  tipo           CHAR(6) NOT NULL CHECK (tipo IN ('DEBIT','CREDIT')),
  valor          BIGINT NOT NULL CHECK (valor > 0),  -- sempre positivo; tipo define direção
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Regra de integridade: soma debits = soma credits por lançamento
-- Verificação por trigger ou no domínio antes de salvar
CREATE INDEX idx_lancamento_medico ON ledger.lancamento (cnpj_id, medico_id, competencia);
CREATE INDEX idx_partida_lancamento ON ledger.partida (lancamento_id);
ENABLE ROW LEVEL SECURITY ON ledger.lancamento;
ENABLE ROW LEVEL SECURITY ON ledger.partida;
```

**Exemplo de lançamento para nota de R$10.000 (PJ, com retenção):**
```
Lançamento: "Nota NFS-e 2026/00001 - Hospital X - R$ 10.000"
  DÉBITO   1001 CREDITO_NOTA    R$ 10.000,00   (bruto a receber)
  CRÉDITO  1002 RETENCAO_IR     R$    150,00   (retido pelo hospital)
  CRÉDITO  1003 RETENCAO_PIS    R$     65,00
  CRÉDITO  1004 RETENCAO_COFINS R$    300,00
  CRÉDITO  1005 RETENCAO_CSLL   R$    100,00
  CRÉDITO  1006 ISS             R$    200,00   (recolhido pela Pin)
  CRÉDITO  1007 TAXA_ADM        R$    685,00   (complemento até 15%)
  CRÉDITO  1008 REPASSE         R$  8.500,00   ← débito e créditos somam R$10.000
```

**Invariante a verificar em código (não apenas em teste):**
```java
void validarEquilibrio(List<Partida> partidas) {
    long totalDebito  = partidas.stream().filter(p -> p.tipo() == DEBIT).mapToLong(Partida::valor).sum();
    long totalCredito = partidas.stream().filter(p -> p.tipo() == CREDIT).mapToLong(Partida::valor).sum();
    if (totalDebito != totalCredito) {
        throw new LancamentoDesequilibradoException(totalDebito, totalCredito);
    }
}
```

### 3. Critérios de Aceite
- [ ] Migração Flyway executa sem erro.
- [ ] Seed de contas criado (1001..1009).
- [ ] `validarEquilibrio()` lança exceção se débitos ≠ créditos.
- [ ] Não há `UPDATE` ou `DELETE` nas tabelas `lancamento` e `partida` — somente `INSERT`.
- [ ] RLS isola lançamentos por tenant.
- [ ] Nenhuma coluna de valor usa `FLOAT` ou `DOUBLE` — apenas `BIGINT`.

### 4. Regras de Negócio
- Lançamentos são imutáveis (append-only) — ADR-0008.
- Correções: estorno (lançamento negativo espelho) + novo lançamento correto.
- Partidas dobradas: soma débitos = soma créditos por lançamento (invariante absoluta).
- Dinheiro em inteiro: centavos (`BIGINT`) — nunca `FLOAT`/`DOUBLE`.
- Cada lançamento referencia a `origem_id` (nota, repasse, etc.) para rastreabilidade.

### 5. Cenários de Testes para o Humano
1. **Invariante desequilibrada:** Tentar salvar lançamento onde débitos ≠ créditos → deve lançar `LancamentoDesequilibradoException`.
2. **Imutabilidade:** Tentar executar `UPDATE ledger.lancamento SET descricao = 'alterado' WHERE id = '...'` → deve retornar "permission denied" ou equivalente.
3. **Lançamento correto:** Emitir nota de R$10.000 → verificar que o lançamento criado tem débito de 1.000.000 centavos na conta CREDITO_NOTA e os créditos corretos.
4. **Estorno:** Cancelar nota → verificar que um novo lançamento de estorno é criado com os valores invertidos (débito/crédito trocados).

---

## TASK-06.2 — Lançamentos Automáticos por Evento

### 1. Objetivo (Por quê?)
O ledger precisa ser alimentado automaticamente a cada evento financeiro relevante (nota emitida, repasse efetuado, cancelamento). Sem automação, depende de intervenção manual e é propenso a erros.

### 2. Descrição da Solução (O quê?)
Consumers de eventos que criam os lançamentos corretos no ledger automaticamente.

**Consumer: `NotaEmitida` → cria lançamento de crédito:**
```java
@RabbitListener(queues = "nota.emitida.q")
@Transactional
public void onNotaEmitida(NotaEmitidaEvent event) {
    if (processedEvents.exists(event.eventId())) return;

    // Monta as partidas conforme o resultado do cálculo fiscal
    var lancamento = LancamentoBuilder.paraEmissao(event.nota());
    // débito: CREDITO_NOTA (valor bruto)
    // créditos: RETENCAO_IR, RETENCAO_PIS, etc., ISS, TAXA_ADM, REPASSE

    ledgerService.registrar(lancamento);
    processedEvents.save(event.eventId());
}
```

**Consumer: `NotaCancelada` → cria lançamento de estorno:**
```java
@RabbitListener(queues = "nota.cancelada.q")
@Transactional
public void onNotaCancelada(NotaCanceladaEvent event) {
    if (processedEvents.exists(event.eventId())) return;

    // Busca o lançamento original da nota cancelada
    // Cria lançamento espelho com débitos/créditos invertidos
    var estorno = LancamentoBuilder.estorno(lancamentoOriginal, "Cancelamento da nota " + event.numeroNota());
    ledgerService.registrar(estorno);
    processedEvents.save(event.eventId());
}
```

**Consumer: `RepasseEfetuado` → baixa o saldo de REPASSE a pagar:**
```java
@RabbitListener(queues = "repasse.efetuado.q")
public void onRepasseEfetuado(RepasseEfetuadoEvent event) {
    // débito: REPASSE (baixa o passivo "a pagar")
    // crédito: conta de PIX executado
    var lancamento = LancamentoBuilder.paraRepasse(event.repasse());
    ledgerService.registrar(lancamento);
}
```

### 3. Critérios de Aceite
- [ ] Evento `NotaEmitida` cria lançamento com todas as partidas corretas.
- [ ] Evento `NotaCancelada` cria lançamento de estorno (débitos e créditos invertidos).
- [ ] Evento `RepasseEfetuado` cria lançamento de baixa do repasse.
- [ ] Todos os consumers são idempotentes (mesmo `event_id` processado 2x não cria 2 lançamentos).
- [ ] PBT: para 1.000+ casos, soma débitos = soma créditos em todos os lançamentos gerados.

### 4. Regras de Negócio
- Lançamento no ledger é acionado por evento, não por chamada síncrona (ADR-0005).
- Idempotência obrigatória em todos os consumers (ADR-0008).
- Regime de caixa: repasse só após o hospital pagar (RF-LED-02).
- Lançamento de estorno referencia o lançamento original via `origem_id`.

### 5. Cenários de Testes para o Humano
1. **Nota → lançamento:** Emitir nota de R$5.000 → verificar criação do lançamento no ledger com partidas corretas.
2. **Idempotência:** Reenviar evento `NotaEmitida` manualmente → verificar que apenas 1 lançamento existe para aquela nota.
3. **Estorno automático:** Cancelar nota → verificar criação automática do lançamento de estorno.
4. **Repasse:** Efetuar repasse → verificar lançamento de baixa do saldo de REPASSE.

---

## TASK-06.3 — Saldo, Extrato e Exportação

### 1. Objetivo (Por quê?)
O médico precisa ver seu extrato (o que foi creditado, retido e repassado) e o saldo atual. Extrato transparente é um diferencial do produto e reduz o volume de dúvidas para a operação (RF-LED-03).

### 2. Descrição da Solução (O quê?)
API de saldo (derivado dos lançamentos) e extrato filtrável com exportação PDF/CSV.

**View materializada para performance de saldo:**
```sql
CREATE MATERIALIZED VIEW ledger.saldo_medico AS
SELECT
  l.cnpj_id,
  l.medico_id,
  c.codigo AS conta_codigo,
  c.nome AS conta_nome,
  c.natureza,
  SUM(CASE WHEN p.tipo = 'DEBIT' THEN p.valor ELSE -p.valor END) AS saldo
FROM ledger.lancamento l
JOIN ledger.partida p ON p.lancamento_id = l.id
JOIN ledger.conta c ON c.id = p.conta_id
GROUP BY l.cnpj_id, l.medico_id, c.codigo, c.nome, c.natureza;

CREATE UNIQUE INDEX ON ledger.saldo_medico (cnpj_id, medico_id, conta_codigo);
-- Refresh após cada lançamento via trigger ou job
```

**Endpoint de extrato:**
```
GET /portal/medico/me/extrato
  role: MEDICO
  query: ?de=2026-01&ate=2026-06&tipo=NOTA_FISCAL,REPASSE
  → retorna lista de lançamentos com:
    - data
    - descricao
    - tipo (NOTA_FISCAL, REPASSE, AJUSTE, ESTORNO)
    - valor_bruto, valor_repasse, status_repasse
  → paginado (page, size)

GET /portal/medico/me/extrato/exportar
  query: ?de=2026-01&ate=2026-06&formato=PDF|CSV
  → retorna arquivo PDF ou CSV com cabeçalho, lançamentos e totais

GET /portal/medico/me/saldo
  → { "saldo_a_receber": 850000, "repasse_efetuado_mes": 8500000, ... }
```

**DTO de extrato (item):**
```json
{
  "data": "2026-06-15",
  "descricao": "NFS-e 2026/00001 - Hospital São Marcos - R$ 10.000,00",
  "tipo": "NOTA_FISCAL",
  "valor_bruto": 1000000,
  "retencoes": 61500,
  "iss": 20000,
  "taxa_administrativa": 68500,
  "valor_repasse": 850000,
  "status_repasse": "AGUARDANDO_REPASSE"
}
```

### 3. Critérios de Aceite
- [ ] `GET /portal/medico/me/extrato` retorna apenas lançamentos do médico logado.
- [ ] Filtro por período funciona corretamente.
- [ ] Exportação PDF tem cabeçalho, tabela de lançamentos e total.
- [ ] Exportação CSV tem cabeçalho e linhas separadas por vírgula.
- [ ] Saldo calculado corretamente (derivado dos lançamentos, não de saldo mutável).
- [ ] Médico A não acessa extrato do médico B (403).
- [ ] Conciliação do ledger: soma de débitos = soma de créditos no período (RF-LED-04).

### 4. Regras de Negócio
- Saldo derivado dos lançamentos — nunca coluna de saldo mutável (ADR-0008).
- Regime de caixa: saldo só inclui notas com recebimento confirmado (RF-LED-02).
- Extrato filtrável por período e tipo de lançamento (RF-LED-03).
- Exportação: PDF e CSV (RF-LED-03).
- Conciliação com contabilidade oficial (RF-LED-04).

### 5. Cenários de Testes para o Humano
1. **Extrato médico:** Logar como médico com 3 notas emitidas → `GET /portal/medico/me/extrato` → deve listar os 3 lançamentos com valores corretos.
2. **Filtro por período:** Emitir notas em meses diferentes, filtrar apenas um mês → apenas as notas do mês aparecem.
3. **Exportação PDF:** Clicar em "Exportar PDF" → verificar que o arquivo baixado tem nome, CPF, período, lançamentos e total.
4. **Exportação CSV:** `GET /extrato/exportar?formato=CSV` → abrir no Excel, verificar que colunas e valores estão corretos.
5. **Saldo correto:** Após emitir nota de R$10.000 e não ter repasse → saldo a receber = R$8.500.
6. **Isolamento:** Logar como médico A, tentar `GET /portal/medico/{id_medico_B}/extrato` → 403.
