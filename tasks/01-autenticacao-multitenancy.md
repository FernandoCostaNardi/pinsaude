# EPIC-01 — Autenticação, Acesso e Multi-tenancy

> Prioridade: **P0** — Todos os endpoints e telas dependem deste épico.
> ADRs: ADR-0003, ADR-0009. PRD: §4, §7.1. RFs: RF-AUTH-01..04

---

## TASK-01.1 — Modelo de Dados de Usuários, Perfis e Vínculo com CNPJs

### 1. Objetivo (Por quê?)
Antes de qualquer operação, o sistema precisa saber quem é o usuário, qual(is) empresa(s) ele pode acessar e quais permissões ele tem. Este modelo é a fundação do RBAC e do multi-tenancy.

### 2. Descrição da Solução (O quê?)
Criar as entidades e migrações Flyway no schema `auth` para representar usuários, seus perfis e seus vínculos com CNPJs.

**Migração `auth` — `V1__create_auth_schema.sql`:**
```sql
CREATE TABLE auth.usuario (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  keycloak_id  UUID NOT NULL UNIQUE,  -- ID do subject no Keycloak
  email        VARCHAR(255) NOT NULL UNIQUE,
  nome         VARCHAR(255) NOT NULL,
  ativo        BOOLEAN NOT NULL DEFAULT true,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auth.empresa (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj         VARCHAR(14) NOT NULL UNIQUE,
  razao_social VARCHAR(255) NOT NULL,
  ativo        BOOLEAN NOT NULL DEFAULT true,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Perfis disponíveis: MEDICO, OPERACAO, FINANCEIRO, CONTABIL, GESTAO
CREATE TABLE auth.usuario_empresa_perfil (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id UUID NOT NULL REFERENCES auth.usuario(id),
  empresa_id UUID NOT NULL REFERENCES auth.empresa(id),
  perfil     VARCHAR(20) NOT NULL CHECK (perfil IN ('MEDICO','OPERACAO','FINANCEIRO','CONTABIL','GESTAO')),
  ativo      BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (usuario_id, empresa_id, perfil)
);
CREATE INDEX idx_uep_usuario ON auth.usuario_empresa_perfil (usuario_id);
CREATE INDEX idx_uep_empresa ON auth.usuario_empresa_perfil (empresa_id);
```

**Entidades Java (no serviço `onboarding` ou `auth`):**
```java
// br.com.pinsaude.auth.domain.Usuario
// br.com.pinsaude.auth.domain.Empresa
// br.com.pinsaude.auth.domain.UsuarioEmpresaPerfil
```

**Sincronização com Keycloak:** Ao criar/ativar um usuário na plataforma, o serviço chama a Admin API do Keycloak para criar o usuário e atribuir as roles correspondentes aos perfis. O claim `cnpj_ids` no token é populado com os IDs das empresas do usuário.

### 3. Critérios de Aceite
- [ ] Migração Flyway executa sem erro em banco limpo.
- [ ] `usuario_empresa_perfil` impede duplicata (usuário + empresa + perfil).
- [ ] Um usuário pode ter perfis diferentes em empresas diferentes (ex: MEDICO na empresa A, GESTAO na empresa B).
- [ ] Ao criar vínculo com empresa, o Keycloak recebe atualização do claim `cnpj_ids`.

### 4. Regras de Negócio
- Um usuário pode acessar 1..N empresas (multi-tenant).
- Um médico pode estar vinculado a mais de um CNPJ (relação N:N — PRD §5.1).
- Perfis: MEDICO, OPERACAO, FINANCEIRO, CONTABIL, GESTAO (PRD §4).
- Isolamento: usuário só vê dados de empresas em que tem vínculo ativo.

### 5. Cenários de Testes para o Humano
1. **Vínculo duplo:** Criar médico e vinculá-lo a 2 empresas. Verificar na tabela `usuario_empresa_perfil` 2 registros. Verificar que o token JWT contém ambos os `cnpj_ids`.
2. **Perfil diferente por empresa:** Vincular usuário como MEDICO na empresa A e OPERACAO na empresa B. Verificar que o sistema permite isso (sem constraint violation).
3. **Unicidade:** Tentar criar o mesmo vínculo (usuário + empresa + perfil) duas vezes → deve retornar erro de duplicidade.

---

## TASK-01.2 — Filtro de Tenant (TenantContext + RLS)

### 1. Objetivo (Por quê?)
Toda requisição autenticada precisa propagar o `cnpj_id` do tenant ativo para o PostgreSQL (`app.current_tenant`), ativando as policies de RLS. Sem isso, qualquer query pode vazar dados entre empresas.

### 2. Descrição da Solução (O quê?)
Implementar um `OncePerRequestFilter` que extrai o tenant do JWT, valida que o usuário tem acesso a ele e configura o contexto de RLS no banco antes de qualquer query.

**Header de tenant esperado na requisição:**
```
Authorization: Bearer <jwt>
X-Tenant-Id: <cnpj_id_uuid>   ← CNPJ ativo para esta requisição
```

**`TenantContextFilter.java`:**
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        String tenantId = req.getHeader("X-Tenant-Id");
        JwtAuthenticationToken jwt = (JwtAuthenticationToken) SecurityContextHolder
            .getContext().getAuthentication();

        // 1. Validar que tenantId está na lista cnpj_ids do JWT
        List<String> allowed = jwt.getToken().getClaimAsStringList("cnpj_ids");
        if (tenantId == null || !allowed.contains(tenantId)) {
            res.sendError(403, "Tenant não autorizado");
            return;
        }

        // 2. Propagar para o banco via DataSource wrapper
        TenantContext.set(tenantId);
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
```

**`TenantAwareDataSource.java`:** Wrapper do `DataSource` que, antes de retornar uma conexão, executa `SELECT set_config('app.current_tenant', ?, true)`.

**Endpoints públicos (whitelist sem tenant):**
- `POST /auth/login`, `POST /auth/refresh`, `GET /actuator/health`.

### 3. Critérios de Aceite
- [ ] Requisição sem `X-Tenant-Id` para endpoint protegido retorna 403.
- [ ] Requisição com `X-Tenant-Id` de empresa para a qual o usuário não tem acesso retorna 403.
- [ ] Após filtro, `SELECT current_setting('app.current_tenant')` na conexão retorna o UUID correto.
- [ ] Teste de integração: dois tenants com dados distintos; requisição do tenant A não retorna dados do tenant B.
- [ ] Endpoints públicos (`/actuator/health`) funcionam sem header de tenant.

### 4. Regras de Negócio
- O `cnpj_id` do header DEVE estar na lista `cnpj_ids` do token JWT.
- RLS deve ser ativado via `set_config` na conexão, não via cláusula WHERE manual.
- `TenantContext` usa `ThreadLocal` e DEVE ser limpo no `finally` do filtro.

### 5. Cenários de Testes para o Humano
1. **Isolamento real:** Inserir notas para tenant A e B. Fazer GET das notas com token de usuário do tenant A e header `X-Tenant-Id: <id_do_tenant_A>` → deve retornar apenas notas do tenant A.
2. **Tenant inválido:** Usar token válido mas `X-Tenant-Id` de um CNPJ que o usuário não tem acesso → deve retornar 403.
3. **Sem header:** Chamar endpoint protegido sem `X-Tenant-Id` → deve retornar 403 com mensagem clara.
4. **Health check sem tenant:** `GET /actuator/health` sem nenhum header → deve retornar 200.

---

## TASK-01.3 — RBAC por Perfil com Spring Security

### 1. Objetivo (Por quê?)
Cada perfil tem acesso a funcionalidades diferentes (PRD §4). Um médico não pode aprovar repasses; um operador não pode ver a apuração fiscal. Sem RBAC os dados financeiros ficam expostos a todos os usuários.

### 2. Descrição da Solução (O quê?)
Configurar Spring Security para mapear as roles do JWT para as authorities do Spring e usar `@PreAuthorize` nos controllers.

**Mapeamento de roles no `SecurityConfig.java`:**
```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
    converter.setAuthoritiesClaimName("roles");  // claim do Keycloak
    converter.setAuthorityPrefix("ROLE_");
    // resultado: ROLE_MEDICO, ROLE_OPERACAO, ROLE_FINANCEIRO, ROLE_CONTABIL, ROLE_GESTAO
}
```

**Matriz de autorização por endpoint (documentar e implementar):**

| Endpoint | MEDICO | OPERACAO | FINANCEIRO | CONTABIL | GESTAO |
|---|---|---|---|---|---|
| `GET /portal/extrato` | ✓ (próprio) | | | | ✓ |
| `POST /producao` | ✓ | ✓ | | | ✓ |
| `GET /notas/fila-excecao` | | ✓ | | | ✓ |
| `POST /repasses/aprovar` | | | ✓ | | ✓ |
| `GET /apuracao` | | | | ✓ | ✓ |
| `POST /cadastros/**` | | ✓ | | | ✓ |
| `GET /auditoria/**` | | | | | ✓ |

**Anotações nos controllers:**
```java
@PreAuthorize("hasAnyRole('OPERACAO', 'GESTAO')")
@PostMapping("/notas/{id}/aprovar")
public ResponseEntity<Void> aprovarNota(@PathVariable UUID id) { ... }

@PreAuthorize("hasRole('MEDICO') and #medicoId == authentication.principal.subject")
@GetMapping("/portal/medico/{medicoId}/extrato")
public ResponseEntity<ExtratoDto> getExtrato(@PathVariable UUID medicoId) { ... }
```

### 3. Critérios de Aceite
- [ ] Médico logado não consegue acessar `GET /apuracao` (403).
- [ ] Médico logado só vê o próprio extrato, não o de outro médico (403 para `medicoId` diferente).
- [ ] Operação consegue acessar fila de exceção de notas.
- [ ] Financeiro consegue aprovar repasses.
- [ ] Gestão tem acesso irrestrito.
- [ ] Endpoints sem anotação retornam 401 para usuário não autenticado.

### 4. Regras de Negócio
- Médico acessa APENAS os próprios dados (isolamento por `medicoId` além do tenant).
- Menor privilégio: cada perfil tem acesso mínimo necessário.
- Perfil GESTAO é superusuário dentro do tenant — não cross-tenant.

### 5. Cenários de Testes para o Humano
1. **Acesso do médico:** Logar como MEDICO e tentar `GET /apuracao` → 403. Tentar `GET /portal/medico/{proprio_id}/extrato` → 200.
2. **Médico vs médico:** Logar como médico A e tentar `GET /portal/medico/{id_do_medico_B}/extrato` → 403.
3. **Financeiro aprovando repasse:** Logar como FINANCEIRO e `POST /repasses/{id}/aprovar` → 200.
4. **Operação na fila:** Logar como OPERACAO e `GET /notas/fila-excecao` → 200 com lista.

---

## TASK-01.4 — MFA para Perfis Administrativos

### 1. Objetivo (Por quê?)
Perfis com acesso a dados financeiros e fiscais (OPERACAO, FINANCEIRO, CONTABIL, GESTAO) representam risco alto se comprometidos. MFA obrigatório é requisito do PRD §4 e RF-AUTH-02.

### 2. Descrição da Solução (O quê?)
Configurar no Keycloak autenticação condicional que exige TOTP (ou FIDO2) para os perfis administrativos.

**Configuração no Keycloak (Realm `pinsaude`):**
- Criar Authentication Flow `pinsaude-mfa-flow`:
  1. Username/Password Form (required)
  2. Conditional OTP (condição: role IN [OPERACAO, FINANCEIRO, CONTABIL, GESTAO])
- Atribuir este flow como Default Browser Flow do realm.
- OTP Policy: Algorithm TOTP, digits 6, period 30s, algorithm SHA1.

**Tela de configuração de MFA (frontend):**
- Na primeira vez, redirecionar para página de setup do TOTP com QR Code.
- Opção de gerar códigos de recuperação (backup codes).

**Step-up para alteração de dados bancários (RF-ONB-08):**
```java
// Endpoint sensível exige reautenticação recente (< 5 min)
@PostMapping("/medicos/{id}/dados-bancarios")
@PreAuthorize("hasAnyRole('MEDICO','GESTAO')")
public ResponseEntity<Void> atualizarDadosBancarios(
        @PathVariable UUID id,
        @RequestBody DadosBancariosDto dto,
        @AuthenticationPrincipal Jwt jwt) {
    Instant authTime = jwt.getClaimAsInstant("auth_time");
    if (authTime.isBefore(Instant.now().minusSeconds(300))) {
        throw new StepUpRequiredException("Reautenticação necessária para alterar dados bancários");
    }
    ...
}
```

### 3. Critérios de Aceite
- [ ] Usuário com role GESTAO não consegue completar o login sem configurar/confirmar TOTP.
- [ ] Usuário com role MEDICO completa o login sem MFA.
- [ ] Após configurar TOTP, o login exige o código do autenticador a cada sessão.
- [ ] Tentativa de alterar dados bancários com sessão > 5 min retorna 401 com `X-Step-Up-Required: true`.
- [ ] Códigos de backup funcionam como substituto do TOTP.

### 4. Regras de Negócio
- MFA obrigatório: OPERACAO, FINANCEIRO, CONTABIL, GESTAO (RF-AUTH-02).
- MFA opcional (pode ser configurado): MEDICO.
- Alteração de dados bancários exige reautenticação mesmo que MFA já tenha sido feito (RF-ONB-08).

### 5. Cenários de Testes para o Humano
1. **Setup TOTP:** Criar novo usuário GESTAO, tentar logar → deve ser redirecionado para configurar TOTP antes de acessar. Escanear QR Code com Google Authenticator, inserir código → acesso liberado.
2. **TOTP errado:** Inserir código TOTP inválido → deve retornar erro e bloquear após 5 tentativas.
3. **Médico sem MFA:** Criar usuário MEDICO, logar com usuário/senha → acesso direto sem TOTP.
4. **Step-up bancário:** Logar como médico, navegar, após 6 minutos tentar alterar dados bancários → deve pedir reautenticação.

---

## TASK-01.5 — Trilha de Auditoria de Ações Sensíveis

### 1. Objetivo (Por quê?)
LGPD e a operação da Pin exigem saber quem fez o quê, quando e em qual empresa. Toda ação fiscal/financeira deve ser rastreável (RF-AUTH-04, RF-LGPD-01).

### 2. Descrição da Solução (O quê?)
Implementar um `AuditService` centralizado que persiste na tabela `audit_log` (criada na TASK-00.5) a cada ação sensível, usando um aspecto Spring (AOP) para não poluir a lógica de negócio.

**Anotação customizada:**
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();             // ex: "nota.emitida"
    String entityType() default "";
}
```

**Aspecto AOP:**
```java
@Aspect
@Component
public class AuditAspect {
    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void audit(JoinPoint jp, Auditable auditable, Object result) {
        // Extrai actor_id do SecurityContext
        // Extrai cnpj_id do TenantContext
        // Extrai entity_id do result (se implementar Identifiable)
        // Persiste em audit_log
    }
}
```

**Uso nos services:**
```java
@Auditable(action = "nota.emitida", entityType = "NotaFiscal")
public NotaFiscalDto emitirNota(EmissaoCommand cmd) { ... }

@Auditable(action = "repasse.aprovado", entityType = "Repasse")
public RepasseDto aprovarRepasse(UUID repasseId) { ... }
```

**Ações que DEVEM ser auditadas:**
- `usuario.criado`, `usuario.ativado`, `usuario.desativado`
- `nota.emitida`, `nota.cancelada`, `nota.rejeitada`
- `repasse.aprovado`, `repasse.executado`
- `dados-bancarios.alterados`
- `apuracao.gerada`
- `certificado-a1.carregado`

### 3. Critérios de Aceite
- [ ] Toda ação anotada com `@Auditable` gera registro em `audit_log` com `actor_id`, `cnpj_id`, `action`, `occurred_at`.
- [ ] Exceção no método auditado NÃO impede a persistência do log de auditoria (o aspecto usa `@AfterThrowing` também).
- [ ] Logs de auditoria são imutáveis: não há endpoint de DELETE ou UPDATE para `audit_log`.
- [ ] `GET /auditoria?cnpj_id=X&action=nota.emitida&de=2026-01-01&ate=2026-12-31` retorna trilha filtrada (apenas para role GESTAO).
- [ ] Teste de integração verifica que emitir uma nota cria exatamente 1 registro em `audit_log`.

### 4. Regras de Negócio
- Audit log é append-only e imutável.
- Retenção mínima: 5 anos (RF-LGPD-02).
- Apenas GESTAO pode consultar a trilha de auditoria.
- CPF e dados bancários não devem aparecer em claro em `before_json`/`after_json` (mascarar com `***`).

### 5. Cenários de Testes para o Humano
1. **Auditoria de emissão:** Emitir uma nota como usuário X → `SELECT * FROM auth.audit_log WHERE action = 'nota.emitida'` → deve retornar 1 registro com `actor_id` de X.
2. **Auditoria de dados bancários:** Alterar dados bancários de médico → verificar registro com `action = 'dados-bancarios.alterados'` e dados bancários mascarados no `after_json`.
3. **Imutabilidade:** Tentar `DELETE FROM auth.audit_log WHERE id = '<id>'` como usuário da aplicação → deve retornar "permission denied".
4. **Consulta filtrada:** Logar como GESTAO, chamar `GET /auditoria?action=repasse.aprovado` → deve retornar apenas registros de repasse aprovado do tenant.
5. **Role indevida:** Logar como OPERACAO, tentar `GET /auditoria` → deve retornar 403.
