# Pin Saúde — Monorepo

Plataforma de gestão fiscal e financeira para clínicas médicas.
Monorepo orquestrado por **Nx**, com backend **Java 17 / Spring Boot 3** e frontend **React 18 / TypeScript**.

## Estrutura

```
pin-saude/
├── apps/web/            # React SPA (portal do médico + backoffice)
├── services/
│   ├── fiscal/          # Motor fiscal parametrizável (porta 8081)
│   ├── faturamento/     # Emissão NFS-e + integração prefeitura (porta 8082)
│   ├── ledger/          # Ledger financeiro append-only (porta 8083)
│   ├── repasse/         # Repasse ao médico (porta 8084)
│   ├── onboarding/      # Onboarding de médicos (porta 8085)
│   └── gestao/          # Apuração, DRE, relatórios (porta 8086)
├── gateway/             # API Gateway / BFF Spring Cloud Gateway (porta 8080)
├── libs/frontend/       # Libs JS/TS compartilhadas
├── contracts/
│   ├── openapi/         # Contratos OpenAPI por serviço
│   └── events/          # Schemas de eventos de domínio
├── tools/
│   ├── db/              # Scripts SQL de inicialização
│   └── scripts/         # Scripts de CI e utilitários
├── docs/adr/            # Architecture Decision Records
├── docker-compose.yml   # Infraestrutura local completa
├── nx.json              # Configuração Nx
├── pom.xml              # POM-pai Maven (reactor)
└── pnpm-workspace.yaml  # Workspace pnpm
```

## Pré-requisitos

- Java 17 (`JAVA_HOME` apontando para JDK 17)
- Maven 3.9+
- Node.js 20+
- pnpm 9+
- Docker e Docker Compose

## Como rodar localmente

### 1. Subir infraestrutura

```bash
docker compose up -d
```

Serviços disponíveis:
| Serviço | URL |
|---|---|
| PostgreSQL | `localhost:5432` |
| RabbitMQ Management | http://localhost:15672 (pinsaude/local_dev_only) |
| Keycloak | http://localhost:8080 (admin/local_dev_only) |
| HashiCorp Vault | http://localhost:8200 (token: dev-root-token) |
| Jaeger UI | http://localhost:16686 |
| Mailhog (e-mail local) | http://localhost:8025 |

### 2. Backend — compilar todos os serviços

```bash
mvn clean package -DskipTests
```

### 3. Backend — compilar serviço isolado

```bash
mvn clean package -pl services/fiscal -am -DskipTests
```

### 4. Backend — rodar testes de um serviço

```bash
mvn test -pl services/fiscal
```

### 5. Frontend — instalar dependências

```bash
pnpm install
```

### 6. Frontend — rodar em desenvolvimento

```bash
pnpm --filter web dev
# abre em http://localhost:3000
```

### 7. Nx — build apenas o que mudou

```bash
nx affected:build
nx affected:test
```

## Portas dos serviços

| Serviço | Porta |
|---|---|
| Gateway | 8080 |
| fiscal | 8081 |
| faturamento | 8082 |
| ledger | 8083 |
| repasse | 8084 |
| onboarding | 8085 |
| gestao | 8086 |
| web (dev) | 3000 |

## ADRs

Decisões arquiteturais documentadas em `docs/adr/` e no arquivo `adr-pin-saude.md`:

- ADR-0001: Monorepo (Nx + Maven + pnpm)
- ADR-0002: Microsserviços Java 17 / Spring Boot
- ADR-0003: Multi-tenancy pooled + RLS PostgreSQL
- ADR-0004: PostgreSQL + Flyway (ddl-auto=validate)
- ADR-0005: Transactional Outbox + RabbitMQ
- ADR-0006: ACL ports/adapters para integrações externas
- ADR-0007: Motor fiscal parametrizável
- ADR-0008: Ledger append-only com partidas dobradas
- ADR-0009: OAuth2/OIDC + RBAC + MFA (Keycloak)
- ADR-0010: HashiCorp Vault para certificados A1
- ADR-0011: OpenTelemetry + audit_log append-only + LGPD
- ADR-0012: Pirâmide de testes (unitários, Testcontainers, Playwright, jqwik PBT)
- ADR-0013: React + TypeScript + OpenAPI gerado
- ADR-0014: CQRS read models via RabbitMQ

## Regras de módulo (Nx boundaries)

- `services/X` **não pode importar** `services/Y` diretamente (sem FK cross-schema, sem chamada HTTP síncrona entre serviços de domínio diferente)
- Comunicação entre serviços: apenas via eventos RabbitMQ com envelope padrão
- Frontend: `apps/web` pode importar `libs/frontend/*`, mas não vice-versa
