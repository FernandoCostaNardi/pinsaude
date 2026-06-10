# Pin Saúde

[![CI](https://github.com/FernandoCostaNardi/pinsaude/actions/workflows/ci.yml/badge.svg)](https://github.com/FernandoCostaNardi/pinsaude/actions/workflows/ci.yml)

Plataforma de gestão de saúde — monorepo Nx com frontend React e microserviços Spring Boot.

## Stack

| Camada      | Tecnologia              | Versão   |
|-------------|-------------------------|----------|
| Orquestrador | Nx                     | 19.8.14  |
| Frontend    | React + Vite + TypeScript | 18 / 5.x |
| Backend     | Spring Boot             | 3.2.5    |
| Gateway     | Spring Cloud Gateway    | 4.1.x    |
| Java        | JDK                     | 17       |
| JS          | pnpm                    | 9.11.0   |

## Estrutura

```
apps/web/            → React 18 (porta 3000)
services/fiscal/     → Spring Boot (porta 8081)
services/faturamento → Spring Boot (porta 8082)
services/ledger/     → Spring Boot (porta 8083)
services/repasse/    → Spring Boot (porta 8084)
services/onboarding/ → Spring Boot (porta 8085)
services/gestao/     → Spring Boot (porta 8086)
gateway/             → Spring Cloud Gateway (porta 8080)
```

## Início rápido

```powershell
# Instalar dependências JS
$env:NODE_TLS_REJECT_UNAUTHORIZED = "0"
pnpm install --no-frozen-lockfile

# Subir infra local (PostgreSQL, RabbitMQ, Keycloak, Vault…)
.\tools\scripts\start-infra.ps1

# Dev server frontend
npx nx run web:dev

# Build de todos os projetos
npx nx run-many --target=build --all
```

## CI

O pipeline roda automaticamente no GitHub Actions em cada push para `main` e em PRs:

| Job           | O que faz                      |
|---------------|-------------------------------|
| `build-web`   | TypeScript check + Vite build  |
| `test-web`    | Vitest unit tests              |
| `lint-web`    | ESLint                         |
| `build-java`  | Maven clean install (todos os módulos) |
| `affected`    | Relatório Nx affected (PRs)    |
