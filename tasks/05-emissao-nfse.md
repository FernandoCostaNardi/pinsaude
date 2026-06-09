# EPIC-05 — Emissão de NFS-e

> Prioridade: **P1** — Core do produto. Sem emissão, não há faturamento nem ledger.
> ADRs: ADR-0005, ADR-0006. PRD: §7.4. RFs: RF-NF-01..09

---

## TASK-05.1 — Entidade Produção e Registro da Solicitação de Emissão

### 1. Objetivo (Por quê?)
O ciclo de emissão começa quando o médico informa a produção (tomador, valor, competência). Esta produção origina a nota fiscal e é a fonte de verdade do que foi produzido pelo médico em um período.

### 2. Descrição da Solução (O quê?)
Criar a entidade `Producao` e o endpoint de registro pelo médico no portal.

**Migração (`faturamento.V2__create_producao.sql`):**
```sql
CREATE TABLE faturamento.producao (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  medico_id       UUID NOT NULL,
  empresa_id      UUID NOT NULL,
  tomador_id      UUID NOT NULL,
  servico_id      UUID NOT NULL,
  competencia     DATE NOT NULL,      -- primeiro dia do mês (ex: 2026-06-01)
  valor_bruto     BIGINT NOT NULL,    -- centavos
  descricao       TEXT,               -- texto livre complementar
  status          VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO_EMISSAO',
  -- AGUARDANDO_EMISSAO → VALIDACAO_PENDENTE (2 primeiras) → EMITINDO → EMITIDA → CANCELADA → ERRO
  nota_fiscal_id  UUID,               -- preenchido após emissão
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_producao_medico ON faturamento.producao (cnpj_id, medico_id, competencia);
ENABLE ROW LEVEL SECURITY ON faturamento.producao;
```

**Endpoint de registro:**
```
POST /portal/producao
  role: MEDICO, OPERACAO, GESTAO
  body: {
    "tomador_id": "uuid",
    "servico_id": "uuid",
    "competencia": "2026-06",
    "valor_bruto": 1000000,     ← centavos (R$ 10.000,00)
    "descricao": "Consultas realizadas em junho/2026"
  }
  → cria Producao
  → calcula destaque via CalculoFiscal (preview, não definitivo)
  → determina se vai para validação ou direto para emissão
  → publica evento no Outbox: ProducaoRegistrada
  → retorna: { "id": "uuid", "status": "AGUARDANDO_EMISSAO", "preview_calculo": {...} }
```

**Regra de validação das primeiras notas (RF-NF-05):**
```java
// As 2 primeiras notas de médico×serviço×tomador novo passam por validação manual
boolean precisaValidacao(UUID medicoId, UUID servicoId, UUID tomadorId) {
    long qtdNotasEmitidas = notaFiscalRepository
        .countEmitidas(medicoId, servicoId, tomadorId);
    return qtdNotasEmitidas < 2;
}
// Se precisaValidacao → status VALIDACAO_PENDENTE → vai para fila de exceção
// Se não → status EMITINDO → vai direto para emissão
```

### 3. Critérios de Aceite
- [ ] `POST /portal/producao` registra produção e retorna preview do cálculo fiscal.
- [ ] Médico logado só pode registrar produção para si mesmo.
- [ ] Valor bruto em centavos; validar: > 0, <= 50.000.000 (R$ 500.000).
- [ ] Competência não pode ser futura (além do mês atual).
- [ ] Serviço sem regra fiscal → status `EXCECAO` + fila de exceção.
- [ ] Tomador novo (primeiro uso para este médico) → 2 primeiras notas vão para `VALIDACAO_PENDENTE`.
- [ ] Evento `ProducaoRegistrada` publicado no outbox.

### 4. Regras de Negócio
- Gatilho da emissão: confirmação da produção no portal (RF-NF-01).
- As 2 primeiras notas por médico×serviço passam por validação (RF-NF-05).
- Auto-emissão liberada após 1ª competência validada.
- Exceções (tomador novo, serviço sem regra): fila de revisão humana (RF-NF-06).

### 5. Cenários de Testes para o Humano
1. **Primeiro registro:** Médico faz login, informa produção para hospital X, serviço Y, R$5.000 → status `VALIDACAO_PENDENTE` (primeira nota).
2. **Terceiro registro (mesmo médico/serviço/tomador):** Após 2 notas validadas, terceiro registro → status `EMITINDO` (auto-emissão).
3. **Serviço sem regra:** Selecionar serviço sem regra fiscal cadastrada → produção criada com status `EXCECAO` e aparece na fila de exceção do backoffice.
4. **Valor inválido:** Tentar registrar produção com valor = 0 → 400 "Valor bruto deve ser maior que zero".

---

## TASK-05.2 — ACL para Agregador Fiscal (Adapter NFS-e)

### 1. Objetivo (Por quê?)
A emissão de NFS-e depende de um agregador fiscal (PlugNotas, Focus NF-e, Nuvem Fiscal ou similar) ou do Ambiente Nacional NFS-e. Isolá-los atrás de um adapter garante que trocar de fornecedor não exige mudança no domínio (ADR-0006, RF-NF-03).

### 2. Descrição da Solução (O quê?)
Implementar o Port (interface) e um Adapter concreto para o agregador escolhido, com resiliência (retry, circuit breaker, timeout).

**Port (interface no domínio `faturamento`):**
```java
public interface EmissaoNfsePort {
    EmissaoResult emitir(SolicitacaoEmissaoNfse solicitacao);
    StatusEmissao consultarStatus(String protocoloExterno);
    CancelamentoResult cancelar(String numeroNota, String motivo);
}

public record SolicitacaoEmissaoNfse(
    String cnpj,              // CNPJ do prestador
    String inscricaoMunicipal,
    KeyStore certificadoA1,   // carregado do Vault em memória
    TomadorDto tomador,
    ServicoDto servico,
    BigDecimal valorServico,
    String discriminacao,
    BigDecimal aliqIss,
    BigDecimal aliqIr,
    // ... demais campos da NFS-e
    String idempotencyKey     // para evitar duplicação
)
```

**Adapter (ex: `PlugNotasAdapter.java`):**
```java
@Component
@ConditionalOnProperty("integracoes.nfse.provider", havingValue = "plugnotas")
public class PlugNotasAdapter implements EmissaoNfsePort {
    // POST {base_url}/nfse → emite a nota
    // GET {base_url}/nfse/{protocolo} → consulta status
    // Resiliência via Resilience4j:
    @Retry(name = "nfse-emissao", fallbackMethod = "fallbackEmissao")
    @CircuitBreaker(name = "nfse-emissao")
    @TimeLimiter(name = "nfse-emissao")
    public EmissaoResult emitir(SolicitacaoEmissaoNfse solicitacao) { ... }

    public EmissaoResult fallbackEmissao(SolicitacaoEmissaoNfse s, Exception e) {
        // Registra para fallback manual (RF-NF-09)
        throw new EmissaoIndisponivelException("Serviço de emissão temporariamente indisponível. Fallback manual disponível.");
    }
}
```

**Configuração de resiliência (`application.yml`):**
```yaml
resilience4j:
  retry:
    instances:
      nfse-emissao:
        max-attempts: 3
        wait-duration: 2s
        retry-on-result-predicate: "result.isFailure()"
  circuit-breaker:
    instances:
      nfse-emissao:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
  time-limiter:
    instances:
      nfse-emissao:
        timeout-duration: 15s
```

**Chave de idempotência:** `producao_id` (UUID da produção) → o agregador não emite a mesma nota duas vezes.

### 3. Critérios de Aceite
- [ ] `EmissaoNfsePort` é uma interface pura (sem anotações de framework externo).
- [ ] Adapter concreto implementa retry (3x com backoff 2s) antes de falhar.
- [ ] Circuit breaker abre após 50% de falhas em 10 chamadas.
- [ ] Timeout de 15s por chamada ao agregador.
- [ ] Chave de idempotência enviada a cada chamada — mesma `producao_id` nunca gera 2 notas.
- [ ] Teste de contrato verifica que o payload enviado ao agregador está correto (sem subir o agregador real).
- [ ] Fallback registra nota para emissão manual (RF-NF-09).

### 4. Regras de Negócio
- Emissão nativa via agregador ou Ambiente Nacional (RF-NF-03).
- Cada nota assinada com certificado A1 do CNPJ emissor.
- Fallback de emissão manual quando o serviço estiver indisponível (RF-NF-09).
- Idempotência: mesma produção nunca resulta em 2 notas.

### 5. Cenários de Testes para o Humano
1. **Emissão com sucesso:** Informar produção → verificar que o adapter envia a requisição correta ao agregador (sandbox) e retorna protocolo.
2. **Retry automático:** Configurar agregador para falhar nas 2 primeiras tentativas (mock) → verificar que a 3ª tentativa tem sucesso e a nota é emitida.
3. **Circuit breaker:** Configurar 6 falhas consecutivas → circuit breaker abre → próximas chamadas vão direto para fallback sem tentar o agregador.
4. **Idempotência:** Enviar mesma `producao_id` duas vezes ao adapter → agregador deve receber idempotency key e retornar o mesmo resultado.

---

## TASK-05.3 — Fila de Emissão Assíncrona (Outbox → Worker)

### 1. Objetivo (Por quê?)
A emissão de NFS-e é uma operação que depende de terceiros e pode ser lenta. O médico não pode ficar esperando. Com a fila assíncrona, o portal responde imediatamente e a emissão acontece em background com retry automático.

### 2. Descrição da Solução (O quê?)
Implementar o worker consumidor da fila de emissão com processamento idempotente e atualização de status da nota.

**Migração (`faturamento.V3__create_nota_fiscal.sql`):**
```sql
CREATE TABLE faturamento.nota_fiscal (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id          UUID NOT NULL,
  producao_id      UUID NOT NULL UNIQUE,
  empresa_id       UUID NOT NULL,
  medico_id        UUID NOT NULL,
  tomador_id       UUID NOT NULL,
  servico_id       UUID NOT NULL,
  competencia      DATE NOT NULL,
  numero_nota      VARCHAR(50),           -- preenchido após emissão
  protocolo_externo VARCHAR(200),         -- ID no agregador/prefeitura
  status           VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO',
  -- AGUARDANDO → EMITINDO → EMITIDA → REJEITADA → CANCELADA
  valor_bruto      BIGINT NOT NULL,
  valor_iss        BIGINT NOT NULL DEFAULT 0,
  valor_ir         BIGINT NOT NULL DEFAULT 0,
  valor_csll       BIGINT NOT NULL DEFAULT 0,
  valor_pis        BIGINT NOT NULL DEFAULT 0,
  valor_cofins     BIGINT NOT NULL DEFAULT 0,
  valor_repasse    BIGINT NOT NULL,
  impostos_zerados BOOLEAN NOT NULL DEFAULT false,
  regra_fiscal_id  UUID NOT NULL,        -- parâmetro fiscal usado
  xml_nota         TEXT,                 -- XML da NFS-e assinada
  pdf_danfse_path  TEXT,                 -- caminho no MinIO
  motivo_rejeicao  TEXT,
  emitida_em       TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_nota_medico ON faturamento.nota_fiscal (cnpj_id, medico_id, competencia);
ENABLE ROW LEVEL SECURITY ON faturamento.nota_fiscal;
```

**Consumer da fila `nota.emitir.q`:**
```java
@RabbitListener(queues = "nota.emitir.q")
@Transactional
public void processarEmissao(EmitirNotaCommand cmd) {
    // 1. Idempotência: verificar se já processou este event_id
    if (processedEventsRepository.exists(cmd.eventId())) return;

    // 2. Buscar dados da produção, calcular fiscal
    Producao producao = producaoRepository.findById(cmd.producaoId()).orElseThrow();
    ParametroFiscal params = motorFiscal.resolverParaCompetencia(...);
    ResultadoCalculo calculo = calculoFiscal.calcular(...);

    // 3. Criar nota_fiscal com status EMITINDO
    NotaFiscal nota = notaFiscalRepository.save(new NotaFiscal(producao, calculo));

    // 4. Carregar A1 do Vault e chamar adapter
    KeyStore a1 = certificadoA1Service.carregarA1(empresa.getCnpjId());
    EmissaoResult resultado = emissaoNfsePort.emitir(buildSolicitacao(nota, a1));

    // 5. Atualizar status da nota
    nota.atualizar(resultado);

    // 6. Publicar evento NotaEmitida ou NotaRejeitada no outbox
    outboxService.publish(resultado.isSuccess()
        ? new NotaEmitida(nota)
        : new NotaRejeitada(nota, resultado.motivo()));

    // 7. Marcar como processado
    processedEventsRepository.save(cmd.eventId());
}
```

### 3. Critérios de Aceite
- [ ] Consumer processa mensagem da fila e cria `nota_fiscal` com status correto.
- [ ] Idempotência: mesma mensagem processada 2x não cria 2 notas.
- [ ] Ao emitir com sucesso: `nota_fiscal.status = EMITIDA`, `xml_nota` preenchido, PDF salvo no MinIO.
- [ ] Ao rejeitar: `nota_fiscal.status = REJEITADA`, `motivo_rejeicao` preenchido, produção vai para fila de exceção.
- [ ] Evento `NotaEmitida` publicado no outbox ao emitir com sucesso.
- [ ] DLQ configurada: após 3 tentativas, mensagem vai para `pinsaude.dead-letter.q` com alerta.

### 4. Regras de Negócio
- Emissão assíncrona → portal responde imediatamente (não espera o agregador).
- Status transitório: nota fica `EMITINDO` durante o processamento.
- Rejeição pelo agregador → nota vai para fila de exceção (RF-NF-06).
- XML e PDF da NFS-e disponibilizados ao médico (RF-NF-07).

### 5. Cenários de Testes para o Humano
1. **Fluxo assíncrono:** Informar produção → portal retorna imediatamente com status `EMITINDO` → aguardar ~5s → status muda para `EMITIDA` (sem refresh manual, via polling ou WebSocket).
2. **Nota rejeitada:** Configurar valor inválido para o agregador sandbox → nota vai para `REJEITADA` → aparece na fila de exceção do backoffice.
3. **XML disponível:** Após emissão, `GET /notas/{id}/xml` → deve retornar o XML da NFS-e.
4. **PDF DANFSE:** `GET /notas/{id}/pdf` → deve retornar URL assinada do PDF no MinIO.

---

## TASK-05.4 — Validação Manual das Primeiras Notas e Fila de Exceção

### 1. Objetivo (Por quê?)
As 2 primeiras notas de cada combinação médico×serviço passam por validação manual (RF-NF-05). Além disso, notas com tomador novo ou serviço sem regra fiscal vão para revisão (RF-NF-06). Sem este controle, notas incorretas seriam emitidas em massa.

### 2. Descrição da Solução (O quê?)
Fila de validação no backoffice com workflow de aprovação/rejeição pela operação.

**Endpoint da fila de validação:**
```
GET  /notas/fila-validacao
  role: OPERACAO, GESTAO
  query: ?status=PENDENTE&empresa_id=uuid
  → lista notas aguardando validação com dados do médico, tomador, valor, preview fiscal

GET  /notas/fila-validacao/{id}
  → detalhe completo da nota pendente

POST /notas/fila-validacao/{id}/aprovar
  body: { "observacao": "..." }
  role: OPERACAO, GESTAO
  → muda status para EMITINDO
  → publica EmitirNotaCommand na fila de emissão

POST /notas/fila-validacao/{id}/rejeitar
  body: { "motivo": "Valor inconsistente com contrato" }
  role: OPERACAO, GESTAO
  → muda status para REJEITADA
  → notifica médico por e-mail com o motivo
  → cria registro em audit_log

POST /notas/fila-excecao/{id}/configurar-servico
  body: { "servico_id": "uuid" }
  role: OPERACAO, GESTAO
  → associa regra fiscal e recoloca na fila de validação
```

**Contagem de notas emitidas por médico×serviço (para determinar 1ª/2ª nota):**
```java
// Consulta em nota_fiscal: quantas notas EMITIDAS para este médico+serviço
// Se < 2 → vai para VALIDACAO_PENDENTE
// Se >= 2 → vai para EMITINDO (auto-emissão)
```

### 3. Critérios de Aceite
- [ ] Backoffice exibe fila de validação com todas as notas pendentes.
- [ ] Aprovar nota → emissão disparada na fila assíncrona.
- [ ] Rejeitar nota → médico recebe e-mail com motivo.
- [ ] Após 2 notas validadas para médico×serviço, próximas vão direto para emissão.
- [ ] Nota com tomador novo também vai para a fila (além das 2 primeiras).
- [ ] Nota com serviço sem regra fiscal fica em `EXCECAO` até operação configurar o serviço.

### 4. Regras de Negócio
- Validação manual: 2 primeiras notas por médico×serviço (RF-NF-05).
- Exceção obrigatória: tomador novo, serviço sem regra (RF-NF-06).
- Após 1ª competência validada, auto-emissão liberada (RF-NF-05).
- Rejeição notifica médico por e-mail (RF-NOT-01).

### 5. Cenários de Testes para o Humano
1. **Primeira nota na fila:** Novo médico informa primeira produção → verificar nota aparece em `GET /notas/fila-validacao`.
2. **Aprovação e emissão:** Logar como OPERACAO, aprovar nota na fila → verificar que o status muda para `EMITINDO` e depois `EMITIDA`.
3. **Auto-emissão após 2 validadas:** Após 2 notas aprovadas e emitidas para médico X + serviço Y → informar terceira produção → status vai direto para `EMITINDO` (sem passar pela fila).
4. **Serviço sem regra:** Produção com serviço sem CNAE configurado → aparece em fila de exceção → configurar serviço → volta para validação normal.

---

## TASK-05.5 — Status, Cancelamento e Substituição de Nota

### 1. Objetivo (Por quê?)
Notas podem ser emitidas erradas (valor, tomador, competência). O sistema precisa suportar cancelamento com motivo e registro de substituição para rastreabilidade fiscal e legal (RF-NF-07, RF-NF-08).

### 2. Descrição da Solução (O quê?)
Workflow de cancelamento/substituição com motivo obrigatório, trilha de auditoria e atualização do ledger.

**Status completo da nota (máquina de estados):**
```
AGUARDANDO → VALIDACAO_PENDENTE → EMITINDO → EMITIDA
                                           → REJEITADA → (nova tentativa ou arquivada)
                              → CANCELADA
```

**Endpoint de cancelamento:**
```
POST /notas/{id}/cancelar
  role: OPERACAO, GESTAO
  body: { "motivo": "Valor informado incorretamente pelo médico" }
  → valida: nota pode ser cancelada (somente EMITIDA; prazo do município)
  → chama EmissaoNfsePort.cancelar()
  → muda status para CANCELADA
  → cria estorno no ledger (lançamento negativo)
  → publica evento NotaCancelada
  → notifica médico por e-mail
  → registra em audit_log
```

**Substitutição:**
```
POST /notas/{id}/substituir
  role: OPERACAO, GESTAO
  body: { "motivo": "...", "nova_producao": { ... } }
  → cancela nota original
  → registra nova producao com referência à nota cancelada
  → nota_substituta_de_id preenchido
```

### 3. Critérios de Aceite
- [ ] Cancelamento de nota `EMITIDA` chama o agregador e muda status para `CANCELADA`.
- [ ] Nota `AGUARDANDO` ou `EMITINDO` pode ser cancelada internamente (sem chamar o agregador).
- [ ] Cancelamento cria estorno no ledger (TASK-06).
- [ ] Evento `NotaCancelada` publicado no outbox.
- [ ] Médico recebe e-mail ao ter nota cancelada.
- [ ] `GET /notas/{id}` retorna todos os status possíveis com timestamps.
- [ ] Motivo de cancelamento obrigatório e registrado em `audit_log`.

### 4. Regras de Negócio
- Cancelamento: somente para notas `EMITIDA` (com protocolo) ou `AGUARDANDO`/`EMITINDO` (sem protocolo).
- Prazo de cancelamento: depende da prefeitura (geralmente 24-72h após emissão).
- Cancelamento de nota cancela os lançamentos do ledger associados.
- Substituição cria novo ciclo de emissão referenciando a nota cancelada.

### 5. Cenários de Testes para o Humano
1. **Cancelamento normal:** Emitir nota, depois cancelar com motivo → verificar status `CANCELADA`, estorno no ledger e e-mail ao médico.
2. **Cancelamento inválido:** Tentar cancelar nota `REJEITADA` → deve retornar 422 "Nota neste status não pode ser cancelada".
3. **Trilha:** Após cancelamento, consultar `audit_log` → deve ter registro com `action = 'nota.cancelada'` e motivo.
4. **Substituição:** Cancelar nota errada e criar substituta → verificar referência entre as notas e novo ciclo de emissão.

---

## TASK-05.6 — Escrituração no Conta Azul

### 1. Objetivo (Por quê?)
O Conta Azul é o ERP contábil da Pin. As notas emitidas pela plataforma precisam ser escrituradas lá para manter a contabilidade oficial íntegra (RF-NF-04).

### 2. Descrição da Solução (O quê?)
Adapter do Conta Azul para escrituração das notas emitidas, acionado assincronamente via evento `NotaEmitida`.

**Port:**
```java
public interface ContaAzulEscrituradorPort {
    void escriturarNota(NotaFiscalDto nota);
    // fallback: log da falha + retry via fila
}
```

**Adapter:**
```java
@Component
public class ContaAzulAdapter implements ContaAzulEscrituradorPort {
    // POST /v1/revenue → cria receita no Conta Azul
    // Autenticação OAuth2 com credenciais do cofre de segredos
    @Retry(name = "conta-azul", fallbackMethod = "fallbackEscrituracao")
    public void escriturarNota(NotaFiscalDto nota) { ... }
}
```

**Consumer do evento `NotaEmitida`:**
```java
@RabbitListener(queues = "nota.emitida.q")
public void onNotaEmitida(NotaEmitidaEvent event) {
    // Idempotência: verificar event_id
    contaAzulEscrituradorPort.escriturarNota(event.nota());
}
```

### 3. Critérios de Aceite
- [ ] Ao emitir nota, o Conta Azul recebe a escrituração em até 30s.
- [ ] Falha na escrituração NÃO cancela a nota (escrituração é assíncrona e não bloqueia).
- [ ] Retry automático em caso de indisponibilidade do Conta Azul.
- [ ] Falha persistente (após retries) vai para DLQ com alerta para a equipe.
- [ ] Idempotência: mesma nota não é escriturada duas vezes.

### 4. Regras de Negócio
- Conta Azul segue como fonte contábil, capturando notas da plataforma e notas emitidas fora (RF-NF-04).
- Escrituração é assíncrona e não deve bloquear o fluxo principal.
- Indisponibilidade do Conta Azul não impede a emissão da nota.

### 5. Cenários de Testes para o Humano
1. **Escrituração automática:** Emitir nota → verificar no Conta Azul (sandbox) que a receita foi criada com os valores corretos.
2. **Conta Azul indisponível:** Bloquear acesso à API do Conta Azul → emitir nota → nota emitida com sucesso → escrituração vai para retry.
3. **Idempotência:** Reenviar evento `NotaEmitida` → verificar que o Conta Azul não recebe duplicidade.

---

## TASK-05.7 — Fallback de Emissão Manual

### 1. Objetivo (Por quê?)
Quando o agregador fiscal ou a prefeitura estiver indisponível, a operação precisa emitir a nota manualmente (no site da prefeitura ou Conta Azul) e registrar o XML na plataforma. Sem este fallback, a operação para completamente (RF-NF-09).

### 2. Descrição da Solução (O quê?)
Tela de fallback no backoffice para upload manual do XML da nota emitida externamente.

**Endpoint:**
```
POST /notas/{producao_id}/emissao-manual
  role: OPERACAO, GESTAO
  multipart: xml_nota (arquivo .xml da NFS-e), numero_nota, protocolo
  body: { "numero_nota": "2026/00123", "protocolo_externo": "ABC123" }
  → valida XML da NFS-e (schema XSD)
  → salva XML no MinIO
  → atualiza nota_fiscal com status EMITIDA + dados da nota manual
  → publica evento NotaEmitida (tratado igual à emissão automática)
  → registra em audit_log com flag "emissao_manual = true"
```

### 3. Critérios de Aceite
- [ ] Upload de XML válido → nota marcada como `EMITIDA` com flag `emissao_manual = true`.
- [ ] XML inválido (não passa no schema XSD) retorna 400.
- [ ] Nota emitida manualmente dispara os mesmos eventos que a emissão automática (escrituração Conta Azul, lançamento ledger).
- [ ] Auditoria registra emissão manual com o usuário responsável.
- [ ] Endpoint disponível apenas para OPERACAO e GESTAO.

### 4. Regras de Negócio
- Fallback operacional para indisponibilidade do agregador/prefeitura (RF-NF-09).
- Nota manual tem o mesmo tratamento financeiro da nota automática.
- Marcação de auditoria indica que foi emissão manual.

### 5. Cenários de Testes para o Humano
1. **Emissão manual:** Fazer download de um XML válido de NFS-e, fazer upload via fallback → verificar nota marcada como `EMITIDA` com `emissao_manual = true`.
2. **XML inválido:** Tentar upload de arquivo .txt como XML → deve retornar 400.
3. **Mesmo fluxo downstream:** Após emissão manual, verificar que o ledger foi creditado e o Conta Azul foi escriturado normalmente.
4. **Auditoria:** Verificar `audit_log` com `action = 'nota.emissao-manual'` e `actor_id` do operador.
