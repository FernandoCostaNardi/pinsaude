# EPIC-00 — Setup e Infraestrutura do Projeto

> Prioridade: **P0** — Todas as tasks dependem deste épico.
> ADRs de referência: ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0005, ADR-0009, ADR-0010, ADR-0011, ADR-0012

---

## TASK-00.1 — Setup do Monorepo (Nx + Maven + pnpm)

### 1. Objetivo (Por quê?)
Toda a plataforma Pin Saúde vive num monorepo único. Sem esta estrutura base não é possível iniciar nenhuma outra task. O monorepo garante commits atômicos entre backend, contratos e frontend, CI por "affected" e fronteiras de módulo verificáveis.

### 2. Descrição da Solução (O quê?)
Criar o repositório com a estrutura de pastas definida no ADR-0001, configurar o Nx como orquestrador, o Maven reactor multi-módulo como build do JVM e o pnpm workspace para JS/TS.

**Estrutura de pastas a criar:**
```
pin-saude/
├── apps/
│   └── web/                     # React SPA (portal + backoffice)
├── services/                    # microsserviços Spring Boot
│   ├── fiscal/
│   ├── faturamento/
│   ├── ledger/
│   ├── repasse/
│   ├── onboarding/
│   └── gestao/
├── gateway/                     # API Gateway / BFF
├── libs/
│   └── frontend/                # libs JS/TS compartilhadas
├── contracts/                   # OpenAPI por serviço + schemas de eventos
├── tools/                       # geradores, scripts CI, hooks
├── docs/adr/                    # ADRs (já existentes)
├── nx.json
├── pnpm-workspace.yaml
└── pom.xml                      # POM-pai (reactor Maven)
```

**Configurações obrigatórias:**
- `nx.json`: definir targets (`build`, `test`, `lint`) para projetos Java e JS/TS; habilitar cache local.
- `pom.xml` (raiz): `<packaging>pom</packaging>` listando todos os `services/*` e `gateway/` como módulos.
- Cada serviço Spring Boot: `pom.xml` filho herdando do pai; `spring-boot-maven-plugin`.
- `pnpm-workspace.yaml`: incluindo `apps/*` e `libs/frontend/*`.
- `.gitignore` cobrindo `target/`, `node_modules/`, `.nx/cache/`.
- Nx module boundaries configuradas: proibir imports de `services/X` em `services/Y` diretamente.

**Serviços Spring Boot a criar (esqueleto):**
Cada serviço deve ter estrutura: `src/main/java/br/com/pinsaude/<servico>/` com pacotes `controller`, `service`, `repository`, `domain`, `dto`, `config`.

### 3. Critérios de Aceite
- [ ] `nx build --all` executa sem erro (pode ser vazio, mas sem falha de configuração).
- [ ] `nx affected:test` detecta corretamente o que mudou a partir de um commit de exemplo.
- [ ] `mvn clean package -pl services/fiscal -am` compila o serviço `fiscal` isoladamente.
- [ ] `pnpm install` resolve dependências em `apps/web` sem conflito.
- [ ] Módulos do Nx impedem import de `services/ledger` dentro de `services/fiscal` (boundary enforcement gera erro no lint).
- [ ] README raiz documenta como rodar cada parte localmente.

### 4. Regras de Negócio
- N/A (task de infraestrutura).

### 5. Cenários de Testes para o Humano
1. **Build isolado de serviço:** Alterar um arquivo em `services/fiscal/`, rodar `nx affected:build` — apenas `fiscal` deve ser compilado.
2. **Cache Nx:** Rodar `nx build fiscal` duas vezes; na segunda execução deve aparecer "cache hit" e terminar em < 2s.
3. **Boundary violation:** Adicionar `import br.com.pinsaude.ledger.*` dentro de `services/fiscal/` e rodar lint — deve falhar com mensagem de boundary violation.
4. **pnpm workspace:** Rodar `pnpm -r build` na raiz — todas as libs JS/TS devem compilar.

---

## TASK-00.2 — Setup do Banco de Dados (PostgreSQL + Flyway + RLS)

### 1. Objetivo (Por quê?)
Todos os serviços precisam de um banco com schemas isolados, migrações versionadas e isolamento multi-tenant via RLS desde o primeiro dia. Sem isso, o ADR-0002, ADR-0003 e ADR-0004 não são respeitados.

### 2. Descrição da Solução (O quê?)
Configurar PostgreSQL com um schema por serviço, Flyway por serviço, usuários de banco isolados e policies de RLS.

**Schemas a criar:**
```sql
CREATE SCHEMA fiscal;
CREATE SCHEMA faturamento;
CREATE SCHEMA ledger;
CREATE SCHEMA repasse;
CREATE SCHEMA onboarding;
CREATE SCHEMA gestao;
CREATE SCHEMA auth;    -- usuários, perfis, tokens
```

**Usuários de banco (um por serviço):**
```sql
CREATE USER svc_fiscal         WITH PASSWORD '...';
CREATE USER svc_faturamento    WITH PASSWORD '...';
CREATE USER svc_ledger         WITH PASSWORD '...';
CREATE USER svc_repasse        WITH PASSWORD '...';
CREATE USER svc_onboarding     WITH PASSWORD '...';
CREATE USER svc_gestao         WITH PASSWORD '...';
-- GRANT apenas no schema próprio de cada usuário
GRANT ALL ON SCHEMA fiscal TO svc_fiscal;
-- NÃO conceder acesso cross-schema
```

**Flyway por serviço:**
- Cada serviço em `services/<nome>/src/main/resources/db/migration/` contém migrações `V1__init.sql`, `V2__...sql`.
- `application.yml` de cada serviço:
```yaml
spring:
  flyway:
    schemas: <nome_schema>
    default-schema: <nome_schema>
    table: flyway_schema_history   # tabela própria no schema do serviço
  datasource:
    url: jdbc:postgresql://localhost:5432/pinsaude
    username: svc_<nome>
    password: ${DB_PASSWORD_<NOME>}
  jpa:
    hibernate:
      ddl-auto: validate
```

**RLS — padrão a seguir em TODA tabela multi-tenant:**
```sql
-- Exemplo para tabela fiscal.nota_fiscal
ALTER TABLE fiscal.nota_fiscal ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fiscal.nota_fiscal
  USING (cnpj_id = current_setting('app.current_tenant')::uuid);
-- Índice composto obrigatório:
CREATE INDEX idx_nota_fiscal_tenant ON fiscal.nota_fiscal (cnpj_id, id);
```

**Propagação do tenant na aplicação (Spring Boot):**
```java
// Em um Filter/Interceptor, após validar o JWT:
entityManager.createNativeQuery(
  "SELECT set_config('app.current_tenant', :tenantId, true)"
).setParameter("tenantId", tenantId.toString()).getSingleResult();
```

**Docker Compose para desenvolvimento local:**
```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: pinsaude
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: local_dev_only
    ports: ["5432:5432"]
    volumes:
      - ./tools/db/init.sql:/docker-entrypoint-initdb.d/init.sql
```

### 3. Critérios de Aceite
- [ ] `docker compose up postgres` sobe sem erro; schemas criados automaticamente.
- [ ] Flyway de cada serviço executa migrações sem erro ao subir a aplicação.
- [ ] `svc_fiscal` não consegue fazer `SELECT * FROM ledger.lancamento_ledger` (permissão negada).
- [ ] Com `SET app.current_tenant = '<cnpj_id_A>'`, uma query em qualquer tabela com RLS retorna apenas dados do tenant A.
- [ ] Com tenant não configurado (null), a query falha (RLS bloqueia acesso total).
- [ ] Migrações são idempotentes em ambiente limpo e em ambiente com schema já existente.

### 4. Regras de Negócio
- Toda tabela multi-tenant DEVE ter coluna `cnpj_id UUID NOT NULL` como primeira chave de partição.
- Índice primário composto: `(cnpj_id, id)`.
- Nenhum FK cruzando schemas.
- Dinheiro: colunas de valor sempre `BIGINT` (centavos) ou `NUMERIC(15,2)` com escala explícita — NUNCA `FLOAT` ou `DOUBLE`.

### 5. Cenários de Testes para o Humano
1. **Isolamento de tenant:** Inserir 2 registros com `cnpj_id` diferentes. Com `SET app.current_tenant = 'cnpj_A'`, fazer SELECT — deve retornar só o registro de `cnpj_A`.
2. **Bloqueio cross-schema:** Logar com `svc_fiscal` no psql e tentar `SELECT 1 FROM ledger.lancamento_ledger` — deve retornar "permission denied".
3. **Flyway replay:** Dropar e recriar o schema de um serviço, subir a aplicação — Flyway deve reaplicar todas as migrações na ordem correta.
4. **Validação ddl-auto=validate:** Alterar manualmente o nome de uma coluna no banco sem migração e subir a aplicação — deve lançar `SchemaValidationException`.

---

## TASK-00.3 — Setup do Broker RabbitMQ + Transactional Outbox

### 1. Objetivo (Por quê?)
Emissão fiscal, conciliação e repasse dependem de processamento assíncrono confiável. O padrão Outbox garante que nenhum evento seja perdido mesmo se o broker estiver temporariamente fora.

### 2. Descrição da Solução (O quê?)
Configurar RabbitMQ com exchanges, filas e DLQs. Implementar a tabela `outbox_event` em cada schema de serviço produtor e um relay (poller) que publica eventos pendentes.

**Docker Compose:**
```yaml
rabbitmq:
  image: rabbitmq:3.13-management
  ports: ["5672:5672", "15672:15672"]
  environment:
    RABBITMQ_DEFAULT_USER: pinsaude
    RABBITMQ_DEFAULT_PASS: local_dev_only
```

**Topologia de exchanges (criar via `RabbitMQConfig.java`):**
```
Exchange: pinsaude.domain (topic)
  ├── routing key: nota.emitida          → fila: nota.emitida.q
  ├── routing key: nota.rejeitada        → fila: nota.rejeitada.q
  ├── routing key: recebimento.conciliado → fila: recebimento.conciliado.q
  ├── routing key: repasse.efetuado      → fila: repasse.efetuado.q
  └── routing key: medico.ativado        → fila: medico.ativado.q

Exchange: pinsaude.dlx (fanout) → fila: pinsaude.dead-letter.q
```

**Tabela `outbox_event` (migração em cada serviço produtor):**
```sql
CREATE TABLE <schema>.outbox_event (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type  VARCHAR(100) NOT NULL,
  routing_key VARCHAR(200) NOT NULL,
  payload     JSONB        NOT NULL,
  cnpj_id     UUID         NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  attempts    INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON <schema>.outbox_event (created_at)
  WHERE published_at IS NULL;
```

**Envelope padrão de evento (ADR-0014):**
```json
{
  "event_id":     "uuid",
  "type":         "NotaEmitida",
  "version":      "1",
  "occurred_at":  "2026-06-08T12:00:00Z",
  "cnpj_id":      "uuid-do-tenant",
  "aggregate_id": "uuid-da-nota",
  "payload":      { ... }
}
```

**Relay (poller):** `@Scheduled(fixedDelay = 5000)` que busca eventos com `published_at IS NULL`, publica no exchange e atualiza `published_at`. Retry até 5 tentativas, depois encaminha para DLQ.

**Consumidores:** Sempre idempotentes — verificar se `event_id` já foi processado em tabela `processed_events (event_id PK, processed_at)` antes de executar a ação.

### 3. Critérios de Aceite
- [ ] `docker compose up rabbitmq` sobe; management UI acessível em `localhost:15672`.
- [ ] Exchanges e filas criadas automaticamente pelo `RabbitMQConfig` ao subir qualquer serviço.
- [ ] Inserir um registro na `outbox_event` com `published_at = NULL`; em até 10s o relay publica e atualiza `published_at`.
- [ ] Parar o RabbitMQ, gerar um evento de negócio (ex: emitir nota) → o evento fica na `outbox_event`; ao subir o RabbitMQ o relay entrega o evento.
- [ ] Publicar o mesmo `event_id` duas vezes para um consumidor — a ação de negócio deve ser executada apenas uma vez (idempotência verificada via tabela `processed_events`).
- [ ] Evento com 5 tentativas falhas vai para `pinsaude.dead-letter.q` e aparece no painel do RabbitMQ.

### 4. Regras de Negócio
- Todo evento DEVE conter o envelope padrão com `event_id`, `type`, `version`, `occurred_at`, `cnpj_id`, `aggregate_id`.
- Nenhuma operação financeira sem chave de idempotência.
- O relay NÃO deve publicar sem antes persistir o evento na mesma transação de negócio.

### 5. Cenários de Testes para o Humano
1. **Resiliência do outbox:** Simular queda do broker (parar container), disparar 5 eventos de negócio, subir broker — todos os 5 eventos devem chegar nas filas.
2. **Idempotência:** Via RabbitMQ Management, republicar manualmente uma mensagem já processada — verificar que a ação de negócio não se repete (ex: nota não é emitida duas vezes).
3. **DLQ:** Configurar um consumidor que sempre lança exception. Publicar evento → após 5 tentativas verificar a mensagem na fila `pinsaude.dead-letter.q`.
4. **Isolamento de tenant no evento:** Publicar dois eventos de tenants diferentes, verificar que cada consumidor só processa eventos do seu contexto de tenant correto.

---

## TASK-00.4 — Setup do IdP (OAuth2/OIDC)

### 1. Objetivo (Por quê?)
A plataforma não pode ter autenticação própria (risco de segurança). Precisamos de um IdP OIDC antes de qualquer tela ou endpoint ser acessível.

### 2. Descrição da Solução (O quê?)
Configurar Keycloak (self-hosted) para desenvolvimento local, com realm `pinsaude`, clients para o frontend e backend, e suporte a MFA.

**Docker Compose:**
```yaml
keycloak:
  image: quay.io/keycloak/keycloak:24.0
  command: start-dev
  environment:
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: local_dev_only
  ports: ["8080:8080"]
```

**Configuração do realm `pinsaude` (export/import via JSON):**
- Client `pinsaude-web` (public, PKCE): redirect URIs `http://localhost:3000/*`.
- Client `pinsaude-api` (confidential): para validação de tokens no backend (resource server).
- Roles: `MEDICO`, `OPERACAO`, `FINANCEIRO`, `CONTABIL`, `GESTAO`.
- Claim customizado: `cnpj_ids` (lista de UUIDs dos CNPJs que o usuário pode acessar).
- MFA: configurar OTP policy (TOTP, algorithm SHA1, 30s); obrigatório para roles OPERACAO, FINANCEIRO, CONTABIL, GESTAO.

**Backend — cada serviço Spring Boot como resource server:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/pinsaude
```

**Extração de claims no backend:**
```java
@Component
public class TenantResolver {
    public UUID resolveTenant(JwtAuthenticationToken token) {
        // Valida que o cnpj_id do path/body está na lista de cnpj_ids do token
        List<String> allowedCnpjs = token.getToken().getClaimAsStringList("cnpj_ids");
        // lança Forbidden se cnpj_id não estiver na lista
    }
}
```

### 3. Critérios de Aceite
- [ ] Keycloak sobe em `localhost:8080`; realm `pinsaude` importado automaticamente via arquivo JSON no startup.
- [ ] Frontend consegue fazer login via PKCE e obter access token JWT.
- [ ] Endpoint protegido retorna 401 sem token e 200 com token válido.
- [ ] Token de usuário MEDICO não acessa endpoint com `@PreAuthorize("hasRole('GESTAO')")`.
- [ ] Usuário com role GESTAO é obrigado a configurar TOTP antes de completar login.
- [ ] Claim `cnpj_ids` aparece no token após login.

### 4. Regras de Negócio
- MFA obrigatório para: OPERACAO, FINANCEIRO, CONTABIL, GESTAO (RF-AUTH-02).
- Um usuário pode ter acesso a 1..N empresas (claim `cnpj_ids` é lista).
- A aplicação valida tokens, nunca guarda senhas.
- Operações de alteração de dados bancários exigem step-up authentication (RF-ONB-08).

### 5. Cenários de Testes para o Humano
1. **Login médico:** Acessar o portal, fazer login com usuário de role MEDICO — deve entrar sem MFA.
2. **Login operação com MFA:** Fazer login com usuário de role OPERACAO — deve ser redirecionado para configurar/confirmar TOTP antes de acessar.
3. **Acesso negado:** Logar como MEDICO e tentar acessar uma rota de backoffice — deve retornar 403.
4. **Multi-tenant no token:** Logar com usuário associado a 2 CNPJs — verificar que o claim `cnpj_ids` contém ambos os UUIDs.
5. **Token expirado:** Aguardar expiração do access token sem refresh — próxima chamada deve retornar 401.

---

## TASK-00.5 — Setup de Observabilidade (OpenTelemetry)

### 1. Objetivo (Por quê?)
Com microsserviços e fluxos assíncronos (emissão → conciliação → repasse), sem rastreamento distribuído é impossível diagnosticar problemas em produção. Obrigatório desde o dia 1 conforme ADR-0011.

### 2. Descrição da Solução (O quê?)
Instrumentar todos os serviços Spring Boot com OpenTelemetry Java Agent, propagar `trace_id` pelo RabbitMQ e configurar Jaeger como backend local.

**Docker Compose:**
```yaml
jaeger:
  image: jaegertracing/all-in-one:1.57
  ports: ["16686:16686", "4317:4317"]   # UI + OTLP gRPC
```

**Configuração nos serviços (via `javaagent` no startup):**
```
-javaagent:/opt/opentelemetry-javaagent.jar
-Dotel.service.name=pinsaude-fiscal
-Dotel.exporter.otlp.endpoint=http://localhost:4317
-Dotel.resource.attributes=service.version=1.0.0
```

**Propagação de `trace_id` pelo RabbitMQ:**
```java
// Ao publicar mensagem: injetar W3C TraceContext nos headers da mensagem AMQP
// Ao consumir: extrair e restaurar o span context antes de processar
```

**Trilha de auditoria de negócio (separada dos logs):**
```sql
CREATE TABLE auth.audit_log (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id     UUID        NOT NULL,
  actor_id    UUID        NOT NULL,  -- usuário que executou a ação
  action      VARCHAR(100) NOT NULL, -- ex: 'nota.emitida', 'repasse.aprovado'
  entity_type VARCHAR(100),
  entity_id   UUID,
  before_json JSONB,
  after_json  JSONB,
  trace_id    VARCHAR(64),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Mascaramento de PII em logs:** Nunca logar CPF, dados bancários ou valores de paciente em claro. Usar `***` para campos sensíveis.

### 3. Critérios de Aceite
- [ ] Jaeger UI em `localhost:16686` exibe traces de chamadas HTTP aos serviços.
- [ ] Um fluxo completo (informar produção → emissão → lançamento ledger) aparece como um trace distribuído com spans de todos os serviços envolvidos.
- [ ] `correlation_id` é propagado do HTTP header até as mensagens RabbitMQ e aparece nos logs estruturados.
- [ ] Tabela `audit_log` recebe um registro ao: emitir nota, aprovar repasse, alterar dados bancários.
- [ ] Logs não contêm CPF, dados bancários ou senhas em claro.

### 4. Regras de Negócio
- Toda ação fiscal/financeira sensível DEVE gerar registro em `audit_log` com `actor_id`, `action` e `cnpj_id`.
- Dados de paciente PF (CPF/nome) em notas devem ser tratados conforme LGPD (RF-LGPD-03).
- Retenção de logs de auditoria: 5 anos (RF-LGPD-02).

### 5. Cenários de Testes para o Humano
1. **Trace distribuído:** Informar produção no portal → verificar no Jaeger um trace com spans: `faturamento → fiscal → ledger`.
2. **Auditoria de emissão:** Emitir uma nota como usuário X → verificar na tabela `audit_log` o registro com `actor_id = X`, `action = 'nota.emitida'`, `cnpj_id` correto.
3. **Auditoria de repasse:** Aprovar um repasse → verificar registro com `action = 'repasse.aprovado'`.
4. **PII mascarada:** Emitir nota para paciente CPF → buscar nos logs da aplicação → CPF deve aparecer como `***`.

---

## TASK-00.6 — Setup de Cofre de Segredos (Certificados A1 por CNPJ)

### 1. Objetivo (Por quê?)
Cada CNPJ tem um certificado A1 para assinar NFS-e. Armazená-los em variável de ambiente ou arquivo em disco é inaceitável (ADR-0010). Credenciais de integração (banco, BaaS, Clicksign) também precisam do cofre.

### 2. Descrição da Solução (O quê?)
Usar HashiCorp Vault para desenvolvimento local (mapeável para AWS Secrets Manager em produção). Implementar serviço de carregamento de A1 em memória sob demanda.

**Docker Compose:**
```yaml
vault:
  image: hashicorp/vault:1.16
  environment:
    VAULT_DEV_ROOT_TOKEN_ID: dev-root-token
  ports: ["8200:8200"]
  cap_add: [IPC_LOCK]
```

**Estrutura de segredos no Vault:**
```
secret/pinsaude/cnpj/<cnpj_id>/certificado-a1   → { pfx_base64: "...", senha: "..." }
secret/pinsaude/integracoes/clicksign            → { api_key: "..." }
secret/pinsaude/integracoes/contaazul            → { client_id: "...", client_secret: "..." }
secret/pinsaude/integracoes/agregador-fiscal     → { api_key: "...", env: "sandbox" }
secret/pinsaude/db/<servico>                     → { password: "..." }
```

**Serviço de carregamento do A1 (no serviço `faturamento`):**
```java
@Service
public class CertificadoA1Service {
    // Carrega o PFX do Vault sob demanda, monta KeyStore em memória
    // Cache em memória por CNPJ com TTL de 1h (RefreshableKey)
    // NUNCA persiste em disco
    public KeyStore carregarA1(UUID cnpjId) { ... }
}
```

**Monitoramento de expiração:**
- Job `@Scheduled(cron = "0 0 8 * * MON")` que lista todos os A1 do Vault, verifica validade e envia alerta por e-mail se expirar em < 30 dias.

### 3. Critérios de Aceite
- [ ] Vault sobe em `localhost:8200`; segredos de desenvolvimento inseridos automaticamente por script de seed.
- [ ] Nenhuma senha, token ou chave privada aparece em `application.yml`, código-fonte ou variáveis de ambiente em claro.
- [ ] `CertificadoA1Service.carregarA1(cnpjId)` retorna um `KeyStore` válido com a chave do CNPJ.
- [ ] Se o Vault estiver indisponível, a aplicação loga o erro e lança exceção controlada (não trava o startup).
- [ ] Job de monitoramento de expiração envia alerta quando A1 tem < 30 dias de validade.
- [ ] Rotação de segredo no Vault reflete na próxima carga sem restart da aplicação.

### 4. Regras de Negócio
- O A1 é carregado em memória somente no momento de uso (assinatura), nunca gravado em disco.
- Cada CNPJ tem A1, IM e conta bancária próprios (premissa N3 do PRD).
- Acesso ao cofre por identidade da aplicação (IAM/role), com menor privilégio por ambiente.
- Alerta de expiração de A1 com antecedência mínima de 30 dias.

### 5. Cenários de Testes para o Humano
1. **Carregamento do A1:** Cadastrar um CNPJ com A1 de teste no Vault → acionar emissão de nota → verificar que a nota é assinada corretamente sem erro de certificado.
2. **Segredo não exposto:** Verificar que o arquivo `application.yml` e os logs de startup não contêm nenhum valor de senha ou token.
3. **Indisponibilidade do Vault:** Parar o container do Vault e tentar emitir nota → verificar que o sistema retorna erro controlado e não lança stack trace com dados sensíveis.
4. **Alerta de expiração:** Inserir um A1 com validade de 15 dias no Vault → executar o job de monitoramento → verificar e-mail de alerta recebido.

---

## TASK-00.7 — Setup da Estratégia de Testes e CI

### 1. Objetivo (Por quê?)
O domínio fiscal/financeiro tem baixíssima tolerância a erros. Sem uma estratégia de testes sólida desde o início, defeitos no motor fiscal ou ledger podem causar perda financeira real. ADR-0012 define a pirâmide de testes como obrigatória.

### 2. Descrição da Solução (O quê?)
Configurar a pirâmide de testes (unitários, integração, e2e) com as ferramentas certas em cada camada, mais property-based testing para invariantes financeiras.

**Dependências Maven (por serviço backend):**
```xml
<!-- Unitários + mocking -->
<dependency>junit-jupiter</dependency>
<dependency>mockito-junit-jupiter</dependency>
<!-- Integração com banco real -->
<dependency>testcontainers</dependency>
<dependency>testcontainers-postgresql</dependency>
<!-- Property-based testing -->
<dependency>jqwik</dependency>  <!-- ou net.jqwik:jqwik -->
<!-- Testes de contrato -->
<dependency>spring-cloud-contract-verifier</dependency>
```

**Estrutura de testes por serviço:**
```
services/fiscal/src/test/java/
├── domain/                    # unitários puros (sem Spring, sem BD)
│   ├── MotorFiscalTest.java
│   └── MotorFiscalPropertyTest.java  # PBT com jqwik
├── integration/               # com Testcontainers (PostgreSQL real)
│   ├── NotaFiscalRepositoryIT.java
│   └── TenantIsolationIT.java        # verifica RLS
└── contract/                  # testes de contrato do adapter
    └── AgregadorFiscalContractTest.java
```

**Invariantes para Property-Based Testing (obrigatórias):**
```java
// Invariante 1: médico SEMPRE recebe 85% do bruto
@Property
void medicoSempreRecebe85PorcenDoBruto(@ForAll @Positive long bruto) {
    long repasse = motorFiscal.calcularRepasse(bruto);
    assertThat(repasse).isEqualTo(bruto * 85 / 100);
}
// Invariante 2: soma débitos = soma créditos no ledger
// Invariante 3: destaque nunca ultrapassa 15% do valor bruto
```

**Testes de isolamento multi-tenant (Testcontainers):**
```java
@Test
void tenantANaoVeDadosDeTenantB() {
    // Inserir nota para tenantA e tenantB
    // Setar app.current_tenant = tenantA
    // Assert: query retorna apenas nota de tenantA
}
```

**CI com Nx (`.github/workflows/ci.yml` ou equivalente):**
- `nx affected:test` → testa apenas o que mudou.
- `nx affected:lint` → boundaries + code style.
- Gate de cobertura: núcleo fiscal e ledger ≥ 90%.
- Playwright e2e: apenas em branch `main` e PRs para `main`.

### 3. Critérios de Aceite
- [ ] `mvn test -pl services/fiscal` executa unitários e PBT sem Spring Boot (< 10s).
- [ ] Testes de integração com `@Tag("integration")` sobem PostgreSQL via Testcontainers automaticamente.
- [ ] `TenantIsolationIT` falha se a policy de RLS for removida (validação da rede de segurança).
- [ ] PBT valida 1000+ casos gerados para a invariante do repasse 85%.
- [ ] CI gate rejeita PR se cobertura do pacote `domain` cair abaixo de 90%.
- [ ] Playwright e2e roda em `apps/web/e2e/` com `npx playwright test`.

### 4. Regras de Negócio
- Testes do domínio (pacote `domain/`) NUNCA devem importar Spring ou infraestrutura.
- Testes de integração DEVEM usar banco real via Testcontainers, nunca H2.
- PBT é obrigatório para: motor fiscal, cálculo de ledger/repasse.
- Testes de isolamento de tenant DEVEM existir para cada serviço com tabelas multi-tenant.

### 5. Cenários de Testes para o Humano
1. **PBT invariante 85%:** Rodar `mvn test -pl services/fiscal -Dtest=MotorFiscalPropertyTest` → verificar que 1000+ casos passam e o relatório mostra os casos gerados.
2. **Isolamento tenant:** Remover temporariamente a policy de RLS de uma tabela, rodar `TenantIsolationIT` → o teste deve **falhar**, confirmando que o teste detecta a ausência de isolamento.
3. **Gate de cobertura:** Deletar metade dos testes do motor fiscal e rodar o CI → o build deve falhar com mensagem de cobertura insuficiente.
4. **Playwright e2e:** Rodar `npx playwright test` com a aplicação rodando → os fluxos críticos (login, informar produção, ver extrato) devem passar.
