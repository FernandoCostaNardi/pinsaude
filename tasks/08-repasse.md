# EPIC-08 — Repasse ao Médico

> Prioridade: **P1** — O produto só tem valor se o médico recebe.
> ADRs: ADR-0006. PRD: §7.8. RFs: RF-REP-01..05
> **MVP:** PIX executado manualmente pelo colaborador. BaaS automatizado é Fase 2.

---

## TASK-08.1 — Cálculo do Líquido e Geração da Worklist de Repasses

### 1. Objetivo (Por quê?)
Após o recebimento ser conciliado, o sistema deve calcular o valor líquido (85%) de cada médico e gerar uma lista clara de repasses a executar. No MVP, o PIX é executado manualmente — o sistema controla a worklist para evitar erros e pagamentos em duplicidade (ADR-0006, RF-REP-01).

### 2. Descrição da Solução (O quê?)
Consumer do evento `ProducaoRepasseAutorizado` que cria o registro de repasse e o disponibiliza na worklist.

**Migração (`repasse.V1__create_repasse.sql`):**
```sql
CREATE TABLE repasse.repasse (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  medico_id       UUID NOT NULL,
  producao_id     UUID NOT NULL UNIQUE,  -- 1:1 com a produção
  nota_fiscal_id  UUID NOT NULL,
  valor_bruto     BIGINT NOT NULL,
  valor_liquido   BIGINT NOT NULL,       -- = 85% do bruto (invariante)
  status          VARCHAR(20) NOT NULL DEFAULT 'AUTORIZADO',
  -- AUTORIZADO → APROVADO → EXECUTANDO → LIQUIDADO → FALHOU
  aprovado_por    UUID,
  aprovado_em     TIMESTAMPTZ,
  executado_por   UUID,                  -- colaborador que fez o PIX manual
  executado_em    TIMESTAMPTZ,
  comprovante_path TEXT,                 -- caminho no MinIO
  chave_pix_destino VARCHAR(255) NOT NULL,  -- chave PIX do médico (criptografada)
  idempotency_key VARCHAR(64) NOT NULL UNIQUE,  -- impede duplicidade de pagamento
  -- = SHA256(producao_id + medico_id + valor_liquido)
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_repasse_medico ON repasse.repasse (cnpj_id, medico_id, status);
ENABLE ROW LEVEL SECURITY ON repasse.repasse;

-- Parcelas para médico com CPFs adicionais (split acima R$40k)
CREATE TABLE repasse.parcela_repasse (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id    UUID NOT NULL,
  repasse_id UUID NOT NULL REFERENCES repasse.repasse(id),
  cpf_destino BYTEA NOT NULL,       -- CPF criptografado
  chave_pix  VARCHAR(255) NOT NULL, -- chave PIX criptografada
  valor      BIGINT NOT NULL,
  status     VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  -- PENDENTE → EXECUTADO → FALHOU
  executado_em TIMESTAMPTZ
);
ENABLE ROW LEVEL SECURITY ON repasse.parcela_repasse;
```

**Consumer: `ProducaoRepasseAutorizado` → cria repasse:**
```java
@RabbitListener(queues = "producao.repasse-autorizado.q")
@Transactional
public void onRepasseAutorizado(ProducaoRepasseAutorizadoEvent event) {
    if (processedEvents.exists(event.eventId())) return;

    Producao producao = producaoService.buscar(event.producaoId());
    Medico medico = medicoService.buscar(producao.getMedicoId());

    // Invariante: líquido = 85% do bruto
    long valorLiquido = Math.round(producao.getValorBruto() * 0.85);

    // Chave de idempotência: impede duplicidade
    String idempotencyKey = DigestUtils.sha256Hex(
        producao.getId() + medico.getId() + valorLiquido
    );

    // Se já existe repasse com esta chave, não cria outro
    if (repasseRepository.existsByIdempotencyKey(idempotencyKey)) {
        processedEvents.save(event.eventId());
        return;
    }

    // Determinar parcelas se necessário (RF-REP-03)
    List<ParcelaRepasse> parcelas = calcularParcelas(medico, valorLiquido);

    var repasse = new Repasse(producao, medico, valorLiquido, idempotencyKey, parcelas);
    repasseRepository.save(repasse);

    processedEvents.save(event.eventId());
}
```

**Cálculo de parcelas (RF-REP-03):**
```java
// Limite PIX para conta PF: R$ 40.000 = 4.000.000 centavos
private List<ParcelaRepasse> calcularParcelas(Medico medico, long valorLiquido) {
    final long LIMITE_PF = 4_000_000L;

    if (valorLiquido <= LIMITE_PF || medico.getCpfsAdicionais().isEmpty()) {
        // Repasse único para a chave PIX principal
        return List.of(new ParcelaRepasse(medico.getChavePix(), valorLiquido));
    }

    // Split entre CPF principal e CPFs adicionais
    // Exemplo: R$50.000 → R$40.000 para CPF principal + R$10.000 para CPF adicional
    return dividirEmParcelas(valorLiquido, LIMITE_PF, medico.getChavePix(), medico.getCpfsAdicionais());
}
```

**Worklist no backoffice:**
```
GET /repasses?status=AUTORIZADO&empresa_id=uuid
  role: FINANCEIRO, GESTAO
  → lista repasses aguardando aprovação
  → campos: médico, valor_liquido, chave_pix, parcelas, nota_fiscal

GET /repasses/exportar?status=APROVADO
  → planilha CSV com todos os repasses aprovados (para execução no banco)
```

### 3. Critérios de Aceite
- [ ] `valorLiquido = 85% * valorBruto` (invariante absoluta — nunca pode ser diferente).
- [ ] `idempotency_key` única por (producao_id + medico_id + valor_liquido) — impossível criar 2 repasses para a mesma produção.
- [ ] Repasses acima de R$ 40.000 são divididos em parcelas para os CPFs adicionais.
- [ ] Worklist exibe repasses com status `AUTORIZADO` e `APROVADO`.
- [ ] Consumer idempotente: mesmo evento processado 2x não cria 2 repasses.

### 4. Regras de Negócio
- Repasse = 85% do bruto (invariante absoluta — PRD §5.3).
- Repasse somente autorizado após recebimento confirmado (regime de caixa — RF-LED-02).
- Idempotência obrigatória: sistema bloqueia pagamento em duplicidade.
- Split para valores > R$ 40.000 (limite de conta PF) usando CPFs adicionais (RF-REP-03).
- SLA de repasse: 48h úteis após recebimento (PRD §7.8).

### 5. Cenários de Testes para o Humano
1. **Invariante 85%:** Conciliar recebimento de nota de R$7.000 → verificar repasse criado com `valor_liquido = R$5.950` (7.000 * 85%).
2. **Idempotência:** Reenviar evento de autorização duas vezes → verificar que apenas 1 repasse existe na tabela.
3. **Split acima R$40k:** Nota de R$60.000 para médico com CPF adicional → verificar 2 parcelas: R$40.000 + R$20.000.
4. **Worklist:** Logar como FINANCEIRO, acessar `GET /repasses?status=AUTORIZADO` → ver lista com médicos, valores e chaves PIX.

---

## TASK-08.2 — Aprovação e Execução Manual de Repasse (PIX MVP)

### 1. Objetivo (Por quê?)
No MVP, o PIX é executado manualmente por um colaborador no app do banco. O sistema controla o workflow de aprovação, registra o comprovante e bloqueia execução duplicada. Sem este controle, o risco de pagamento em duplicidade é real (ADR-0006).

### 2. Descrição da Solução (O quê?)
Workflow de aprovação + upload de comprovante + atualização de status com rastreabilidade completa.

**Endpoints:**
```
POST /repasses/{id}/aprovar
  role: FINANCEIRO, GESTAO
  body: { "observacao": "..." }
  → status: AUTORIZADO → APROVADO
  → registra quem aprovou e quando
  → notifica colaborador (e-mail) que há repasses a executar

POST /repasses/{id}/executar
  role: FINANCEIRO, GESTAO
  multipart: comprovante (PDF/JPG), data_execucao
  body: { "data_execucao": "2026-06-15", "observacao": "PIX executado via app Inter" }
  → status: APROVADO → EXECUTANDO
  → valida: repasse ainda não executado (idempotency_key)
  → salva comprovante no MinIO
  → status: EXECUTANDO → LIQUIDADO
  → publica evento RepasseEfetuado
  → notifica médico por e-mail com comprovante

POST /repasses/{id}/marcar-falha
  role: FINANCEIRO, GESTAO
  body: { "motivo": "Chave PIX não encontrada" }
  → status: EXECUTANDO → FALHOU
  → notifica médico por e-mail
  → registra em audit_log para investigação

GET /repasses/{id}/comprovante
  → URL assinada do comprovante no MinIO (role: FINANCEIRO, GESTAO, MEDICO proprietário)
```

**Aprovação em lote:**
```
POST /repasses/aprovar-lote
  body: { "repasse_ids": ["uuid1", "uuid2", ...] }
  role: FINANCEIRO, GESTAO
  → aprova múltiplos repasses de uma vez
  → retorna: aprovados com sucesso + falhos com motivo
```

### 3. Critérios de Aceite
- [ ] Repasse `AUTORIZADO` pode ser aprovado (FINANCEIRO ou GESTAO).
- [ ] Repasse `APROVADO` pode ser executado com upload de comprovante.
- [ ] Comprovante obrigatório para executar o repasse.
- [ ] Após execução, médico recebe e-mail com valor e comprovante.
- [ ] Evento `RepasseEfetuado` publicado no outbox (dispara lançamento no ledger — TASK-06.2).
- [ ] `idempotency_key` impede que o mesmo repasse seja marcado como executado duas vezes.
- [ ] Auditoria: cada mudança de status registrada em `audit_log`.

### 4. Regras de Negócio
- Aprovação + execução são passos separados (RF-REP-04) — controle de 4 olhos.
- Execução manual: colaborador faz PIX no banco e registra comprovante na plataforma.
- Comprovante obrigatório e armazenado para auditoria.
- `idempotency_key` bloqueia execução duplicada mesmo que colaborador clique duas vezes.
- SLA: 48h úteis após recebimento (PRD §7.8).

### 5. Cenários de Testes para o Humano
1. **Fluxo completo:** Autorizar repasse → aprovar → executar com comprovante → verificar status `LIQUIDADO` e e-mail ao médico.
2. **Dupla execução:** Tentar marcar como executado um repasse já `LIQUIDADO` → deve retornar 422 "Repasse já liquidado".
3. **Comprovante obrigatório:** Tentar executar sem anexar comprovante → deve retornar 400 "Comprovante obrigatório".
4. **Aprovação em lote:** Selecionar 5 repasses autorizados e aprovar em lote → todos vão para `APROVADO`.
5. **Download do comprovante:** Logar como médico, acessar `GET /repasses/{id}/comprovante` → URL assinada retorna o arquivo.

---

## TASK-08.3 — Status e Histórico de Repasses no Portal do Médico

### 1. Objetivo (Por quê?)
O médico precisa acompanhar o status do repasse em tempo real: quando foi autorizado, quando foi aprovado, quando foi executado e o comprovante (RF-REP-05). Isso reduz dúvidas e contatos com a operação.

### 2. Descrição da Solução (O quê?)
Endpoint de histórico de repasses no portal do médico com status detalhado e comprovante.

**Endpoints no portal:**
```
GET /portal/medico/me/repasses
  role: MEDICO
  query: ?de=2026-01&ate=2026-06&status=LIQUIDADO
  → retorna lista de repasses do médico logado
  → campos: competencia, valor_bruto, valor_liquido, status, aprovado_em, liquidado_em

GET /portal/medico/me/repasses/{id}
  role: MEDICO
  → detalhe do repasse com linha do tempo dos status
  → URL assinada do comprovante (se liquidado)

GET /portal/medico/me/repasses/{id}/comprovante
  role: MEDICO
  → redireciona para URL assinada do comprovante (1h)
```

**DTO de repasse no portal:**
```json
{
  "id": "uuid",
  "competencia": "2026-06",
  "nota_numero": "2026/00123",
  "tomador": "Hospital São Marcos",
  "valor_bruto": 1000000,
  "valor_liquido": 850000,
  "status": "LIQUIDADO",
  "timeline": [
    { "status": "AUTORIZADO", "em": "2026-06-10T10:00:00Z" },
    { "status": "APROVADO",   "em": "2026-06-11T09:00:00Z" },
    { "status": "LIQUIDADO",  "em": "2026-06-12T14:30:00Z" }
  ],
  "comprovante_disponivel": true
}
```

### 3. Critérios de Aceite
- [ ] Médico vê apenas os próprios repasses.
- [ ] Timeline mostra todos os status com timestamps.
- [ ] Comprovante disponível para download após status `LIQUIDADO`.
- [ ] Filtro por período e status funciona.
- [ ] Médico A não acessa repasses do médico B (403).

### 4. Regras de Negócio
- Status completo do repasse: AUTORIZADO → APROVADO → EXECUTANDO → LIQUIDADO (RF-REP-05).
- Comprovante disponível ao médico após liquidação.
- SLA de 48h úteis visível para o médico.

### 5. Cenários de Testes para o Humano
1. **Histórico:** Logar como médico com 3 repasses em estados diferentes → verificar lista com status corretos.
2. **Timeline:** Acessar repasse liquidado → verificar timeline com as 3 datas (autorização, aprovação, liquidação).
3. **Comprovante:** Clicar em "Baixar comprovante" para repasse liquidado → arquivo baixado corretamente.
4. **Filtro:** Filtrar por status `LIQUIDADO` → apenas repasses liquidados aparecem.
5. **Isolamento:** Logar como médico A e tentar `GET /portal/medico/{id_medico_B}/repasses` → 403.

---

## TASK-08.4 — Notificação de Repasse ao Médico

### 1. Objetivo (Por quê?)
O médico não precisa verificar o portal constantemente para saber se o repasse foi efetuado. A notificação por e-mail ao liquidar é parte do SLA e da experiência do produto (RF-NOT-01, RF-REP-05).

### 2. Descrição da Solução (O quê?)
Consumer do evento `RepasseEfetuado` que envia e-mail ao médico com detalhes e comprovante.

**Consumer:**
```java
@RabbitListener(queues = "repasse.efetuado.q")
public void onRepasseEfetuado(RepasseEfetuadoEvent event) {
    Repasse repasse = repasseRepository.findById(event.repasseId());
    Medico medico = medicoRepository.findById(repasse.getMedicoId());

    emailService.enviar(EmailTemplate.REPASSE_EFETUADO, medico.getEmail(), Map.of(
        "nome", medico.getNomeCompleto(),
        "valor_liquido", formatarMoeda(repasse.getValorLiquido()),
        "competencia", repasse.getCompetencia(),
        "data_repasse", repasse.getExecutadoEm(),
        "url_comprovante", storageService.gerarUrlAssinada(repasse.getComprovantePath(), 7 * 24 * 3600)
    ));
}
```

**Template de e-mail `REPASSE_EFETUADO`:**
```
Assunto: Seu repasse de [competência] foi efetuado — R$ [valor]

Dr(a). [Nome],
Seu repasse referente à competência [mês/ano] foi efetuado com sucesso.

Valor: R$ [valor_liquido]
Data: [data_repasse]

[Botão: Baixar Comprovante]

Atenciosamente,
Pin Saúde
```

**E-mails obrigatórios no MVP (RF-NOT-01):**
- Repasse efetuado (este)
- Nota emitida
- Nota rejeitada
- Pendência de cadastro/documento
- Boas-vindas (ativação)

### 3. Critérios de Aceite
- [ ] Médico recebe e-mail ao ter repasse liquidado, com valor e link para comprovante.
- [ ] Link do comprovante no e-mail tem validade de 7 dias.
- [ ] E-mail enviado em até 60s após evento `RepasseEfetuado`.
- [ ] Falha no envio de e-mail NÃO reverte o status do repasse.
- [ ] Template renderiza corretamente com nome, valor e data.

### 4. Regras de Negócio
- Notificação por e-mail no MVP (RF-NOT-01). WhatsApp é Fase 2.
- Falha de e-mail não deve impactar o fluxo financeiro.
- Link do comprovante no e-mail com validade de 7 dias.

### 5. Cenários de Testes para o Humano
1. **E-mail recebido:** Liquidar repasse → verificar e-mail recebido no inbox com valor correto e link do comprovante.
2. **Link válido:** Clicar no link do comprovante no e-mail → arquivo baixado sem erro.
3. **Link expirado:** Aguardar 8 dias e clicar no link → deve retornar "Link expirado" (404/403).
4. **Falha de e-mail:** Configurar e-mail inválido temporariamente → liquidar repasse → status do repasse deve continuar `LIQUIDADO` mesmo com falha no e-mail.
