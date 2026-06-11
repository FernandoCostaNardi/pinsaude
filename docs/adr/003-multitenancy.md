# ADR 003 — Multi-tenancy: Isolamento de Dados por CNPJ via RLS

**Status:** Aceito  
**Data:** 2026-06-11  
**EPIC:** 02.5

---

## Contexto

O Pin Saúde é uma plataforma multi-empresa (multi-tenant). Cada empresa (identificada pelo CNPJ) deve ver apenas seus próprios dados. É necessário garantir que, mesmo em caso de bug na camada de aplicação, um usuário de uma empresa não consiga acessar dados de outra.

O JWT emitido pelo Keycloak contém o claim `cnpj_id` com o CNPJ da empresa do usuário, e `realm_access.roles` com os perfis.

---

## Decisão

Adotar **PostgreSQL Row Level Security (RLS) com FORCE** como camada de isolamento, propagando o tenant via `app.current_tenant` (GUC de sessão) antes de cada query Hibernate.

O tenant é propagado em três camadas:

### 1. TenantFilter — leitura do JWT

`TenantFilter` é registrado dentro da cadeia Spring Security (depois de `BearerTokenAuthenticationFilter`) via `http.addFilterAfter(...)`. Lê `JwtAuthenticationToken` do `SecurityContextHolder` — que já está autenticado — e define:

- **Role `gestão`:** `TenantContext.set("")` (string vazia = bypass, vê todos os dados)
- **Demais roles:** `TenantContext.set(jwt.cnpj_id)`
- **Sem autenticação:** `TenantContext.set("")`

O `finally` garante limpeza do `ThreadLocal` após cada request.

### 2. TenantAwareDataSource — propagação para o PostgreSQL

`TenantDataSourcePostProcessor` (BeanPostProcessor) envolve o DataSource auto-configurado com `TenantAwareDataSource`. A cada `getConnection()` executado:

```sql
SELECT set_config('app.current_tenant', ?, false)
```

`is_local=false` → valor persiste na sessão da conexão poolada. Como é sempre sobrescrito no início de cada borrow, o valor está sempre sincronizado com o thread atual.

### 3. RLS Policies — PostgreSQL

Cada tabela multi-tenant tem `FORCE ROW LEVEL SECURITY`, que obriga inclusive o owner (`svc_onboarding`) a passar pelas policies.

**Policy padrão:**
```sql
USING (
    COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
    OR cnpj = current_setting('app.current_tenant', TRUE)
)
```

**Policy em tabelas filhas** (sem coluna `cnpj`, só `empresa_id`):
```sql
USING (
    COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
    OR empresa_id IN (
        SELECT id FROM onboarding.empresas
        WHERE cnpj = current_setting('app.current_tenant', TRUE)
    )
)
```

**Bypass quando `app.current_tenant` é:**
- `NULL` → `COALESCE(NULL, '') = ''` → verdadeiro → todas as linhas (Flyway, health checks)
- `''` (string vazia) → `COALESCE('', '') = ''` → verdadeiro → todas as linhas (gestão)
- CNPJ válido → filtra pela empresa

---

## Tabelas com FORCE RLS (V5)

| Tabela | Policy | Coluna tenant |
|---|---|---|
| `onboarding.empresas` | `cnpj = current_setting(...)` | `cnpj` direto |
| `onboarding.configuracoes_fiscais` | subquery em empresas | `empresa_id` |
| `onboarding.contas_bancarias` | subquery em empresas | `empresa_id` |
| `onboarding.aliquotas_competencia` | subquery em empresas | `empresa_id` |
| `onboarding.usuarios_empresas` | subquery em empresas | `empresa_id` |

---

## Alternativas Consideradas

### A) Filtros na camada de serviço (WHERE empresa_id = ?)
- Descartada: depende de código correto em cada query; um bug expõe dados cross-tenant.

### B) SET app.current_tenant via AOP em @Transactional
- Descartada: `SET LOCAL` exige transação ativa; fora de transação o SET é imediatamente revertido.

### C) Armazenar `empresa_cnpj` diretamente nas tabelas filhas
- Descartada: desnormalização desnecessária; a subquery via índice `idx_empresas_cnpj` é O(log n) e aceitável.

### D) AbstractRoutingDataSource para múltiplos schemas/databases
- Descartada: over-engineering para o modelo atual. RLS é mais simples e transparente.

---

## Consequências

**Positivas:**
- Isolamento garantido no nível do banco de dados; a camada de aplicação é um reforço adicional.
- Zero impacto nas queries existentes (nenhuma alteração no código de repositório).
- Flyway e healthchecks funcionam sem `app.current_tenant` (bypass por NULL).

**Negativas/Restrições:**
- Migrations futuras para novas tabelas multi-tenant devem incluir `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY` e a policy correspondente.
- Superusers do PostgreSQL bypassam RLS mesmo com FORCE. Em produção, `svc_onboarding` deve ser criado como usuário não-superuser com permissões específicas.
- Testes com Testcontainers usam o usuário `test` (superuser); para testar isolamento de RLS, é necessária uma conexão separada como usuário não-superuser (ver `MultitenancyIsolationTest`).

---

## Nota sobre outros serviços

Os demais serviços (fiscal, faturamento, ledger, repasse, gestao) devem aplicar o mesmo padrão em suas migrations assim que tiverem dados multi-tenant. Os componentes `TenantContext`, `TenantAwareDataSource` e `TenantFilter` devem ser replicados em cada serviço Spring Boot ou extraídos para uma biblioteca compartilhada em EPIC-02.5+.
