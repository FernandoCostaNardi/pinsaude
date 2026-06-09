# EPIC-09 — Módulo de Gestão e Apuração Fiscal

> Prioridade: **P1** — Responde ao "o que a Pin paga de imposto e qual é o lucro".
> ADRs: ADR-0014 (read models). PRD: §7.9. RFs: implícitos em §7.9
> Dados consolidados via eventos de domínio (CQRS leitura). Sem JOIN cross-service.
> **AVISO:** Fórmulas de apuração devem ser homologadas pela contabilidade da Pin antes do go-live (PRD §13).

---

## TASK-09.1 — Read Model do Serviço de Gestão (Projeções por Eventos)

### 1. Objetivo (Por quê?)
O serviço de gestão precisa de dados de fiscal, faturamento, ledger e repasse para gerar apurações, DRE e posição de caixa. Com a fronteira de dados por serviço (ADR-0002), não pode fazer JOIN cross-schema. A solução é construir read models a partir de eventos de domínio (ADR-0014).

### 2. Descrição da Solução (O quê?)
Consumers de eventos no serviço `gestao` que projetam dados denormalizados para consultas de gestão.

**Migração (`gestao.V1__create_read_models.sql`):**
```sql
-- Snapshot de nota emitida (projeção do serviço faturamento)
CREATE TABLE gestao.rm_nota_emitida (
  id              UUID PRIMARY KEY,   -- mesmo ID da nota original
  cnpj_id         UUID NOT NULL,
  empresa_id      UUID NOT NULL,
  medico_id       UUID NOT NULL,
  tomador_id      UUID NOT NULL,
  competencia     DATE NOT NULL,
  valor_bruto     BIGINT NOT NULL,
  valor_iss       BIGINT NOT NULL DEFAULT 0,
  valor_ir        BIGINT NOT NULL DEFAULT 0,
  valor_csll      BIGINT NOT NULL DEFAULT 0,
  valor_pis       BIGINT NOT NULL DEFAULT 0,
  valor_cofins    BIGINT NOT NULL DEFAULT 0,
  impostos_zerados BOOLEAN NOT NULL DEFAULT false,
  equiparado      BOOLEAN NOT NULL DEFAULT false,
  status_nota     VARCHAR(30) NOT NULL,
  emitida_em      TIMESTAMPTZ,
  atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rm_nota_competencia ON gestao.rm_nota_emitida (cnpj_id, competencia);

-- Snapshot de recebimento conciliado
CREATE TABLE gestao.rm_recebimento (
  id           UUID PRIMARY KEY,
  cnpj_id      UUID NOT NULL,
  nota_id      UUID NOT NULL,
  medico_id    UUID NOT NULL,
  competencia  DATE NOT NULL,
  valor_recebido BIGINT NOT NULL,
  recebido_em  DATE NOT NULL,
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rm_recebimento ON gestao.rm_recebimento (cnpj_id, competencia);

-- Snapshot de repasse efetuado
CREATE TABLE gestao.rm_repasse (
  id           UUID PRIMARY KEY,
  cnpj_id      UUID NOT NULL,
  medico_id    UUID NOT NULL,
  competencia  DATE NOT NULL,
  valor_bruto  BIGINT NOT NULL,
  valor_liquido BIGINT NOT NULL,
  status       VARCHAR(20) NOT NULL,
  executado_em TIMESTAMPTZ,
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rm_repasse ON gestao.rm_repasse (cnpj_id, competencia);
```

**Consumers de eventos no serviço `gestao`:**
```java
@RabbitListener(queues = "nota.emitida.q")
public void onNotaEmitida(NotaEmitidaEvent event) {
    gestaoReadModelService.upsert(new RmNotaEmitida(event));
}

@RabbitListener(queues = "recebimento.conciliado.q")
public void onRecebimentoConciliado(RecebimentoConciliadoEvent event) {
    gestaoReadModelService.upsert(new RmRecebimento(event));
}

@RabbitListener(queues = "repasse.efetuado.q")
public void onRepasseEfetuado(RepasseEfetuadoEvent event) {
    gestaoReadModelService.upsert(new RmRepasse(event));
}
```

**Replay/reconstrução:** Todos os read models são reconstruíveis via replay dos eventos históricos do RabbitMQ/outbox.

**Dado de atualização visível na UI:** `"Dados atualizados às {atualizado_em}"`.

### 3. Critérios de Aceite
- [ ] Consumer `onNotaEmitida` popula `rm_nota_emitida` com todos os campos.
- [ ] Read models são `UPSERT` por ID do evento (idempotentes).
- [ ] Sem nenhum `SELECT` em schemas de outros serviços (fiscal, faturamento, ledger, repasse).
- [ ] Replay de 100 eventos reprocessa todos os read models corretamente.
- [ ] Timestamp `atualizado_em` sempre reflete a última atualização.

### 4. Regras de Negócio
- Read models são cache derivado — fonte da verdade está nos serviços donos dos dados.
- Reconstruíveis por replay (ADR-0014).
- Consistência eventual: a UI de gestão pode estar alguns segundos atrás.
- Nenhum JOIN cross-service (ADR-0002).

### 5. Cenários de Testes para o Humano
1. **Projeção:** Emitir nota → aguardar ~2s → verificar que `gestao.rm_nota_emitida` tem o registro com dados corretos.
2. **Idempotência do read model:** Reprocessar evento → `atualizado_em` muda mas não cria duplicata.
3. **Replay:** Dropar `gestao.rm_nota_emitida`, reprocessar eventos históricos → tabela reconstruída corretamente.
4. **Sem cross-schema:** Verificar no código do serviço `gestao` que não há import de classes de outros serviços nem query em schemas alheios.

---

## TASK-09.2 — Apuração Mensal de Tributos (Lucro Presumido)

### 1. Objetivo (Por quê?)
A gestão da Pin precisa saber quanto de imposto pagar por mês (IRPJ, CSLL, PIS, COFINS, ISS), quais retenções já foram sofridas (crédito) e qual o saldo a recolher para emitir as guias (DARF federal + guia ISS municipal). PRD §7.9a.

### 2. Descrição da Solução (O quê?)
Serviço de apuração que consolida as notas do período, aplica as bases de presunção e calcula o saldo a recolher por tributo.

**Migração (`gestao.V2__create_apuracao.sql`):**
```sql
CREATE TABLE gestao.apuracao_mensal (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  empresa_id      UUID NOT NULL,
  competencia     DATE NOT NULL,    -- primeiro dia do mês
  -- Receita do período
  receita_bruta_total     BIGINT NOT NULL DEFAULT 0,
  receita_equiparada      BIGINT NOT NULL DEFAULT 0,
  receita_sem_equiparacao BIGINT NOT NULL DEFAULT 0,
  -- Bases de presunção
  base_irpj       BIGINT NOT NULL DEFAULT 0,
  base_csll       BIGINT NOT NULL DEFAULT 0,
  -- Tributos devidos
  irpj_devido     BIGINT NOT NULL DEFAULT 0,
  irpj_adicional  BIGINT NOT NULL DEFAULT 0,
  csll_devida     BIGINT NOT NULL DEFAULT 0,
  pis_devido      BIGINT NOT NULL DEFAULT 0,
  cofins_devida   BIGINT NOT NULL DEFAULT 0,
  iss_devido      BIGINT NOT NULL DEFAULT 0,
  -- Retenções sofridas (crédito)
  retencao_irpj   BIGINT NOT NULL DEFAULT 0,
  retencao_csll   BIGINT NOT NULL DEFAULT 0,
  retencao_pis    BIGINT NOT NULL DEFAULT 0,
  retencao_cofins BIGINT NOT NULL DEFAULT 0,
  -- Saldo a recolher
  saldo_irpj      BIGINT GENERATED ALWAYS AS (GREATEST(irpj_devido + irpj_adicional - retencao_irpj, 0)) STORED,
  saldo_csll      BIGINT GENERATED ALWAYS AS (GREATEST(csll_devida - retencao_csll, 0)) STORED,
  saldo_pis       BIGINT GENERATED ALWAYS AS (GREATEST(pis_devido - retencao_pis, 0)) STORED,
  saldo_cofins    BIGINT GENERATED ALWAYS AS (GREATEST(cofins_devida - retencao_cofins, 0)) STORED,
  saldo_iss       BIGINT NOT NULL DEFAULT 0,  -- ISS da Pin (2% sempre recolhido)
  -- Controle
  status          VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
  -- RASCUNHO → REVISAO → HOMOLOGADO
  gerado_por      UUID,
  gerado_em       TIMESTAMPTZ,
  observacoes     TEXT,
  UNIQUE (cnpj_id, competencia)
);
ENABLE ROW LEVEL SECURITY ON gestao.apuracao_mensal;
```

**Serviço de apuração:**
```java
public ApuracaoMensal apurar(UUID cnpjId, YearMonth competencia) {
    ParametroFiscal params = motorFiscal.resolverParaCompetencia(cnpjId, competencia);

    // 1. Receita bruta do período (do read model rm_nota_emitida, status EMITIDA)
    long receitaEquiparada = rmNotaRepository.sumValorBruto(cnpjId, competencia, equiparado: true);
    long receitaSemEquip   = rmNotaRepository.sumValorBruto(cnpjId, competencia, equiparado: false);
    long receitaTotal      = receitaEquiparada + receitaSemEquip;

    // 2. Bases de presunção (PIS/COFINS em regime de caixa = receita RECEBIDA)
    long receitaCaixa = rmRecebimentoRepository.sumValorRecebido(cnpjId, competencia);
    long baseIrpj  = arred(receitaEquiparada * params.getBasePresuncaoIrpjEquiparado())
                   + arred(receitaSemEquip   * params.getBasePresuncaoIrpjSemEquip());
    long BaseCsll  = arred(receitaEquiparada * params.getBasePresuncaoCsllEquiparado())
                   + arred(receitaSemEquip   * params.getBasePresuncaoCsllSemEquip());

    // 3. IRPJ (15% + adicional 10% sobre base > R$20.000/mês)
    long irpjNormal    = arred(baseIrpj * params.getAliqIrpj());
    long irpjAdicional = Math.max(0, arred((baseIrpj - params.getLimiteIrpjAdicional()) * params.getAliqIrpjAdicional()));

    // 4. Demais tributos
    long csllDevida   = arred(BaseCsll  * params.getAliqCsllApuracao());
    long pisDevido    = arred(receitaCaixa * params.getAliqPisApuracao());
    long cofinsDevida = arred(receitaCaixa * params.getAliqCofinsApuracao());
    long issDevido    = arred(receitaTotal * params.getAliqIss());  // 2%

    // 5. Retenções sofridas (crédito)
    RetencoesConsolidadas retencoes = fiscalService.consolidarRetencoes(cnpjId, competencia);

    return new ApuracaoMensal(...); // persistir e retornar
}
```

**Endpoints:**
```
POST /apuracao?competencia=2026-06&empresa_id=uuid
  role: CONTABIL, GESTAO
  → gera ou atualiza apuração com status RASCUNHO

GET  /apuracao/{id}
  → detalhe completo com todos os campos

POST /apuracao/{id}/homologar
  role: GESTAO
  → muda status para HOMOLOGADO (não pode ser mais editada)

GET  /apuracao/{id}/guias
  → retorna resumo das guias a pagar (DARF IRPJ, DARF CSLL/PIS/COFINS, ISS)
  → com vencimentos calculados (último dia útil do mês seguinte)
```

### 3. Critérios de Aceite
- [ ] Apuração para competência 2026-06 calcula corretamente todos os tributos.
- [ ] IRPJ adicional calculado somente quando base > R$ 20.000 (limite do parâmetro fiscal).
- [ ] Retenções sofridas deduzidas corretamente do saldo a recolher.
- [ ] Saldo nunca negativo (GREATEST com 0).
- [ ] Notas CPF zeradas (impostos_zerados = true): não geram destaque mas entram na receita para apuração.
- [ ] Apuração homologada não pode ser alterada.
- [ ] Todas as fórmulas marcadas como "a homologar pela contabilidade" na documentação.

### 4. Regras de Negócio
- PIS/COFINS: regime de caixa (receita recebida, não emitida — PRD §5.2).
- IRPJ/CSLL: presunção por competência (equiparada ou não).
- IRPJ adicional: 10% sobre base que exceder R$ 20.000/mês.
- Notas CPF zeradas: impostos apurados aqui (não no destaque da nota — PRD §5.2).
- ISS: 2%, recolhido pela Pin no domicílio fiscal (Olinda).
- Homologação pela contabilidade da Pin antes do go-live (PRD §13).

### 5. Cenários de Testes para o Humano
1. **Apuração básica:** Com 10 notas equiparadas de R$10.000 cada no mês → gerar apuração → verificar: receita bruta = R$100.000, base IRPJ = R$8.000 (8%), IRPJ = R$1.200 (15%), ISS = R$2.000 (2%).
2. **Retenções deduzidas:** Com retenções sofridas de R$5.000 no mês → verificar que saldo IRPJ = irpj_devido - retencao_irpj.
3. **Adicional IRPJ:** Base IRPJ > R$20.000 → verificar adicional de 10% calculado sobre o excesso.
4. **Nota CPF zerada:** Incluir nota CPF zerada de R$5.000 → verificar que entra na receita bruta para apuração mas não tem retencoes na fonte.
5. **Homologação:** Logar como GESTAO, homologar apuração → tentar `POST /apuracao` para mesmo mês → deve retornar 409 "Já existe apuração homologada".

---

## TASK-09.3 — DRE Simplificada / Apuração de Lucro

### 1. Objetivo (Por quê?)
A gestão precisa saber qual é o lucro da Pin no mês (receita bruta − tributos − repasses − custos = margem ~2-5%). PRD §7.9b.

### 2. Descrição da Solução (O quê?)
Projeção de DRE simplificada consolidando dados dos read models.

**Migração (`gestao.V3__create_dre.sql`):**
```sql
CREATE TABLE gestao.dre_mensal (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  competencia     DATE NOT NULL,
  -- Receita
  receita_bruta   BIGINT NOT NULL DEFAULT 0,  -- soma dos brutos das notas
  -- Deduções
  tributos_apurados BIGINT NOT NULL DEFAULT 0, -- da apuracao_mensal
  total_repasses  BIGINT NOT NULL DEFAULT 0,   -- soma dos líquidos repassados (85%)
  custos_operacionais BIGINT NOT NULL DEFAULT 0, -- informado manualmente pela gestão
  -- Resultado
  lucro_antes_distribuicao BIGINT GENERATED ALWAYS AS
    (receita_bruta - tributos_apurados - total_repasses - custos_operacionais) STORED,
  -- Margem (~2-5%)
  margem_percentual NUMERIC(5,2) GENERATED ALWAYS AS
    (CASE WHEN receita_bruta > 0
     THEN ROUND((lucro_antes_distribuicao::NUMERIC / receita_bruta) * 100, 2)
     ELSE 0 END) STORED,
  gerado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (cnpj_id, competencia)
);
ENABLE ROW LEVEL SECURITY ON gestao.dre_mensal;
```

**Endpoints:**
```
GET /gestao/dre?competencia=2026-06&empresa_id=uuid
  role: GESTAO, CONTABIL
  → retorna DRE do mês com breakdown de cada linha

GET /gestao/dre/historico?de=2026-01&ate=2026-06
  → retorna DRE dos últimos meses para comparativo

POST /gestao/dre/{id}/custos-operacionais
  body: { "valor": 500000, "descricao": "Folha de pagamento + aluguel" }
  role: GESTAO
  → atualiza custos operacionais do mês (entrada manual)
```

### 3. Critérios de Aceite
- [ ] DRE gerada automaticamente a partir dos read models.
- [ ] Margem calculada corretamente: (receita - tributos - repasses - custos) / receita.
- [ ] Custos operacionais inseríveis manualmente pela gestão.
- [ ] Apenas GESTAO e CONTABIL acessam a DRE.
- [ ] Histórico dos últimos 12 meses disponível.

### 4. Regras de Negócio
- Repasse médico = 85% (invariante) — sempre a maior despesa.
- Margem da Pin: ~2–5% (PRD §7.9b).
- Custos operacionais inseridos manualmente no MVP.
- DRE é read-only para CONTABIL.

### 5. Cenários de Testes para o Humano
1. **DRE básica:** Com notas de R$100.000, tributos de R$8.000, repasses de R$85.000, custos de R$3.000 → verificar lucro = R$4.000 e margem = 4%.
2. **Custos manuais:** Inserir custos operacionais de R$5.000 → verificar DRE atualizada.
3. **Histórico:** Verificar DRE de 3 meses seguidos com tendência de crescimento de receita.
4. **Acesso negado:** Logar como FINANCEIRO (sem GESTAO/CONTABIL), tentar `GET /gestao/dre` → 403.

---

## TASK-09.4 — Monitor de Teto Fiscal por CNPJ

### 1. Objetivo (Por quê?)
O Lucro Presumido tem teto de R$ 78 milhões/CNPJ/ano. Ultrapassar o teto exige mudança de regime (custoso). O monitor alerta antecipadamente para acionar o roteamento de novos médicos para outro CNPJ (PRD §7.9c, §14).

### 2. Descrição da Solução (O quê?)
Monitor que calcula a receita acumulada dos últimos 12 meses por CNPJ e alerta quando próxima ao teto.

**Migração (`gestao.V4__create_monitor_teto.sql`):**
```sql
CREATE TABLE gestao.monitor_teto_fiscal (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  empresa_id      UUID NOT NULL,
  competencia_ref DATE NOT NULL,    -- mês base do cálculo (últimos 12 meses a partir daqui)
  receita_acumulada_12m BIGINT NOT NULL,
  teto_lucro_presumido  BIGINT NOT NULL DEFAULT 7_800_000_000, -- R$78.000.000 em centavos
  percentual_utilizado  NUMERIC(5,2) NOT NULL,
  status_alerta  VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  -- NORMAL (< 70%) → ATENCAO (70-90%) → CRITICO (> 90%) → TETO_ATINGIDO (100%)
  calculado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);
ENABLE ROW LEVEL SECURITY ON gestao.monitor_teto_fiscal;
```

**Job de cálculo mensal:**
```java
@Scheduled(cron = "0 0 6 1 * *")  // Todo dia 1 às 6h
public void calcularTetoFiscal() {
    for (Empresa empresa : empresaRepository.findAllAtivas()) {
        long receita12m = rmNotaRepository.sumReceita12meses(empresa.getCnpjId());
        long teto = 7_800_000_000L;  // R$78mi em centavos, mas vem do parâmetro fiscal!
        double pct = (double) receita12m / teto * 100;

        StatusAlerta status = pct < 70 ? NORMAL : pct < 90 ? ATENCAO : pct < 100 ? CRITICO : TETO_ATINGIDO;

        monitorRepository.save(new MonitorTetoFiscal(empresa, receita12m, pct, status));

        if (status != NORMAL) {
            emailService.enviar(EmailTemplate.ALERTA_TETO_FISCAL, gestores, Map.of(
                "empresa", empresa.getRazaoSocial(),
                "percentual", pct,
                "status", status
            ));
        }
    }
}
```

**Endpoints:**
```
GET /gestao/teto-fiscal?empresa_id=uuid
  role: GESTAO, CONTABIL
  → posição atual do teto com histórico

GET /gestao/teto-fiscal/todas-empresas
  → visão consolidada de todas as empresas (role: GESTAO)
```

### 3. Critérios de Aceite
- [ ] Job calcula receita acumulada dos últimos 12 meses por CNPJ.
- [ ] Percentual calculado corretamente: (receita_12m / teto) * 100.
- [ ] Alerta por e-mail quando status = ATENCAO, CRITICO ou TETO_ATINGIDO.
- [ ] Histórico dos últimos 12 cálculos visível.
- [ ] Teto (R$ 78mi) parametrizável via `parametro_fiscal` (não fixo no código).
- [ ] Visão consolidada de todas as empresas disponível para GESTAO.

### 4. Regras de Negócio
- Teto Lucro Presumido: R$ 78 mi/CNPJ/ano (PRD §14).
- Receita acumulada: últimos 12 meses (rolling 12m).
- Alertas: > 70% = ATENÇÃO, > 90% = CRÍTICO (acionar roteamento imediato).
- Ao atingir o teto, novos médicos são roteados para outro CNPJ.

### 5. Cenários de Testes para o Humano
1. **Cálculo correto:** Inserir receita acumulada de R$ 60mi no read model → verificar 76.9% utilizado e status CRITICO.
2. **Alerta por e-mail:** Forçar execução do job com empresa em CRITICO → verificar e-mail recebido com percentual.
3. **Abaixo do teto:** Empresa com R$ 30mi → status NORMAL, sem e-mail.
4. **Visão consolidada:** Empresa A = 50%, Empresa B = 92% → `GET /gestao/teto-fiscal/todas-empresas` → ambas aparecem com percentuais.

---

## TASK-09.5 — Posição de Caixa

### 1. Objetivo (Por quê?)
A gestão precisa saber quanto está a receber dos hospitais, quanto foi recebido e ainda não repassado, e quanto já foi repassado. É o painel de liquidez da Pin (PRD §7.9d).

### 2. Descrição da Solução (O quê?)
Visão derivada dos read models de notas, recebimentos e repasses.

**Query de posição de caixa:**
```sql
-- A receber: notas EMITIDAS não recebidas
SELECT SUM(valor_bruto * 0.85) as a_receber_repasse
FROM gestao.rm_nota_emitida
WHERE cnpj_id = :cnpjId
  AND status_nota = 'EMITIDA'
  AND id NOT IN (SELECT nota_id FROM gestao.rm_recebimento WHERE cnpj_id = :cnpjId);

-- A repassar: notas recebidas + repasse não executado
SELECT SUM(valor_liquido) as a_repassar
FROM gestao.rm_repasse
WHERE cnpj_id = :cnpjId AND status IN ('AUTORIZADO','APROVADO','EXECUTANDO');

-- Já repassado no mês
SELECT SUM(valor_liquido) as repassado_mes
FROM gestao.rm_repasse
WHERE cnpj_id = :cnpjId AND status = 'LIQUIDADO'
  AND DATE_TRUNC('month', executado_em) = DATE_TRUNC('month', NOW());
```

**Endpoint:**
```
GET /gestao/posicao-caixa?empresa_id=uuid
  role: GESTAO, FINANCEIRO
  → {
      "a_receber_hospitais": 15000000,     ← notas emitidas não pagas
      "a_repassar_medicos": 8500000,       ← recebido mas não repassado
      "repassado_no_mes": 42500000,        ← repassado este mês
      "total_notas_em_aberto": 12,
      "atualizado_em": "2026-06-08T14:30:00Z"
    }
```

### 3. Critérios de Aceite
- [ ] "A receber" = soma dos repasses de notas emitidas não conciliadas.
- [ ] "A repassar" = soma dos repasses autorizados/aprovados não liquidados.
- [ ] "Repassado no mês" = soma dos repasses liquidados no mês corrente.
- [ ] Dados derivados dos read models (não cross-service).
- [ ] Timestamp `atualizado_em` mostra hora da última atualização dos read models.

### 4. Regras de Negócio
- Posição de caixa é always eventually consistent (baseada em eventos).
- "A receber" considera somente notas EMITIDAS (não canceladas).
- Valores em centavos na API, formatados para R$ na UI.

### 5. Cenários de Testes para o Humano
1. **Posição inicial:** 3 notas emitidas não pagas de R$10.000 → `a_receber = R$25.500` (85% * R$30.000).
2. **Após conciliação:** 1 nota conciliada → `a_receber = R$17.000`, `a_repassar = R$8.500`.
3. **Após repasse:** Repasse executado → `a_repassar = 0`, `repassado_no_mes` incrementa.

---

## TASK-09.6 — Calendário Fiscal com Vencimentos e Lembretes

### 1. Objetivo (Por quê?)
A operação/contabilidade precisa saber quando as guias de tributos vencem para não pagar multa. O calendário centraliza esses vencimentos por empresa (PRD §7.9e).

### 2. Descrição da Solução (O quê?)
Geração automática de calendário fiscal por empresa/competência com alertas por e-mail.

**Migração (`gestao.V5__create_calendario_fiscal.sql`):**
```sql
CREATE TABLE gestao.calendario_fiscal (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id     UUID NOT NULL,
  empresa_id  UUID NOT NULL,
  competencia DATE NOT NULL,
  tributo     VARCHAR(20) NOT NULL,  -- 'IRPJ','CSLL_PIS_COFINS','ISS','DARF'
  valor       BIGINT,                 -- preenchido após apuração
  vencimento  DATE NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  -- PENDENTE → PAGO → VENCIDO
  observacoes TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ENABLE ROW LEVEL SECURITY ON gestao.calendario_fiscal;
```

**Geração do calendário (ao homologar a apuração):**
```java
// Vencimentos Lucro Presumido:
// IRPJ + CSLL: último dia útil do mês seguinte à apuração trimestral
// PIS + COFINS: até dia 25 do mês seguinte
// ISS: varia por município (Olinda: dia 10 do mês seguinte)
public void gerarCalendario(ApuracaoMensal apuracao) {
    List<ItemCalendario> itens = vencimentoFiscalService.calcular(apuracao);
    calendarioRepository.saveAll(itens);
}
```

**Job de lembretes (diário):**
```java
@Scheduled(cron = "0 0 8 * * *")  // Diário às 8h
public void enviarLembretes() {
    // Vencimentos em 5 dias e em 1 dia → enviar e-mail para GESTAO e CONTABIL
    List<ItemCalendario> proximosVencimentos = calendarioRepository
        .findVencendoEm(LocalDate.now().plusDays(1), LocalDate.now().plusDays(5));

    proximosVencimentos.forEach(item ->
        emailService.enviar(EmailTemplate.LEMBRETE_VENCIMENTO_FISCAL, ...)
    );
}
```

**Endpoint:**
```
GET /gestao/calendario-fiscal?empresa_id=uuid&de=2026-01&ate=2026-12
  role: GESTAO, CONTABIL
  → lista vencimentos do período com status

POST /gestao/calendario-fiscal/{id}/marcar-pago
  body: { "data_pagamento": "2026-07-25", "comprovante_ref": "..." }
  role: GESTAO, CONTABIL
  → marca vencimento como pago
```

### 3. Critérios de Aceite
- [ ] Ao homologar apuração, itens do calendário são gerados com vencimentos corretos.
- [ ] Lembrete por e-mail enviado 5 dias e 1 dia antes do vencimento.
- [ ] Vencimento passado sem pagamento muda automaticamente para `VENCIDO`.
- [ ] Marcar como pago registra data e referência do comprovante.
- [ ] Médico não tem acesso ao calendário fiscal (RF-NF-01 — apenas gestão interna).

### 4. Regras de Negócio
- Calendário não é exposto ao médico — é gestão interna da Pin (PRD §7.9e).
- Vencimentos calculados conforme o regime Lucro Presumido e o município.
- ISS Olinda: dia 10 do mês seguinte.
- PIS/COFINS: dia 25 do mês seguinte.

### 5. Cenários de Testes para o Humano
1. **Geração automática:** Homologar apuração de junho/2026 → verificar itens no calendário: ISS dia 10/07, PIS/COFINS dia 25/07.
2. **Lembrete:** Definir data de vencimento como hoje + 3 dias, executar job de lembretes → verificar e-mail recebido.
3. **Vencido:** Deixar vencimento passar → verificar que status muda para `VENCIDO`.
4. **Marcar pago:** Clicar em "Marcar como pago" para ISS → status muda para `PAGO`.
