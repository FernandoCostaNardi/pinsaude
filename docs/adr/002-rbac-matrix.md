# ADR-002 — Matriz de Permissões RBAC

**Status:** Aceito  
**Data:** 2026-06-10  
**Contexto:** EPIC-01.4 — Controle de Acesso por Perfil

---

## Contexto

O sistema Pin Saúde possui múltiplos perfis de usuário com acesso a diferentes domínios de negócio.
O controle é feito via `realm_access.roles` no JWT emitido pelo Keycloak.

Perfis disponíveis: `medico`, `operacao`, `financeiro`, `contabil`, `gestao`

---

## Decisão

RBAC implementado com `@PreAuthorize` nos controllers de cada serviço.  
`gestao` tem acesso irrestrito a todos os recursos (super-admin).

---

## Matriz de Permissões

| Serviço / Recurso          | medico | operacao | financeiro | contabil | gestao |
|---------------------------|--------|----------|------------|----------|--------|
| `/api/fiscal/**`          | ❌     | ❌       | ❌         | ✅       | ✅     |
| `/api/fiscal/parametros`  | ❌     | ❌       | ❌         | ✅       | ✅     |
| `/api/faturamento/**`     | ❌     | ✅       | ❌         | ❌       | ✅     |
| `/api/ledger/**`          | ❌     | ❌       | ✅         | ❌       | ✅     |
| `/api/repasse/**`         | ❌     | ❌       | ✅         | ❌       | ✅     |
| `/api/onboarding/**`      | ❌     | ✅       | ❌         | ❌       | ✅     |
| `/api/gestao/**`          | ❌     | ❌       | ❌         | ❌       | ✅     |
| `/api/portal/**`          | ✅     | ❌       | ❌         | ❌       | ✅     |
| `/actuator/health`        | ✅     | ✅       | ✅         | ✅       | ✅     |

> `/api/portal/**` — implementado no EPIC-06 (Portal do Médico)

---

## Implementação

### Serviços (Spring MVC)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // habilita @PreAuthorize
public class SecurityConfig { ... }
```

```java
@RestController
@RequestMapping("/api/fiscal")
@PreAuthorize("hasRole('contabil') or hasRole('gestao')")
public class FiscalController { ... }
```

### Utilitários

`SecurityUtils` disponível em cada serviço:

| Método                    | Retorno                                        |
|---------------------------|------------------------------------------------|
| `isGestao()`              | `true` se o usuário tem role `gestao`          |
| `isFinanceiro()`          | `true` se o usuário tem role `financeiro`      |
| `isOperacao()`            | `true` se o usuário tem role `operacao`        |
| `isContabil()`            | `true` se o usuário tem role `contabil`        |
| `isMedico()`              | `true` se o usuário tem role `medico`          |
| `currentMedicoId()`       | `sub` do JWT se médico, `null` caso contrário  |
| `currentCnpjTenant()`     | claim `cnpj_id` do JWT (multi-tenancy)         |

---

## Consequências

- Cada serviço rejeita requests com role incorreto com HTTP 403
- `gestao` é o único super-admin — não delegar essa role levianamente
- Tokens sem nenhuma role de negócio passam na autenticação (401 → 200 na infra) mas são barrados nos controllers (403)
- Futuras roles devem ser adicionadas tanto no Keycloak quanto nas anotações `@PreAuthorize`
