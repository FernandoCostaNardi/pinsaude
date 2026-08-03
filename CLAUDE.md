# CLAUDE.md — Pin Saúde Monorepo

Instruções específicas deste projeto para o Claude Code.

---

## Stack e Versões

| Camada | Tecnologia | Versão |
|---|---|---|
| Orquestrador | Nx | 19.8.14 |
| Frontend | React + Vite + TypeScript | 18 / 5.x / 5.x |
| Backend | Spring Boot | 3.2.5 |
| Gateway | Spring Cloud Gateway | 4.1.x |
| Java | JDK | 17 |
| Build Java | Maven | 3.6.3 |
| Gerenciador JS | pnpm | 9.11.0 |

---

## Estrutura do Monorepo

```
pinsaude/
  apps/web/          → React 18 (porta 3000, proxy /api → 8090)
  services/fiscal/   → Spring Boot (porta 8081)
  services/faturamento/ → Spring Boot (porta 8082)
  services/ledger/   → Spring Boot (porta 8083)
  services/repasse/  → Spring Boot (porta 8084)
  services/onboarding/ → Spring Boot (porta 8085)
  services/gestao/   → Spring Boot (porta 8086)
  services/portal/   → Spring Boot (porta 8087) ← Portal do Médico (EPIC-06.1)
  gateway/           → Spring Cloud Gateway (porta 8090)
  tools/scripts/     → Scripts Node.js de build/test
  docs/              → PRD, ADR
```

---

## Regras Nx 19

**Nx 19 NÃO suporta a seção `projects` inline no `nx.json`.**
Cada projeto (app, service, gateway) deve ter seu próprio `project.json` na raiz do módulo.
Nunca adicionar `"projects": { ... }` ao `nx.json`.

Estrutura mínima de um `project.json`:
```json
{
  "$schema": "../../node_modules/nx/schemas/project-schema.json",
  "name": "nome-do-projeto",
  "sourceRoot": "caminho/src",
  "projectType": "application",
  "targets": {
    "build": {
      "executor": "nx:run-commands",
      "options": {
        "command": "tools\\scripts\\run.cmd tools/scripts/mvn-build.js :nome-do-modulo",
        "cwd": "{workspaceRoot}"
      }
    }
  }
}
```

Usar sempre `"command":` (singular) nos targets — `"commands": [...]` (array) não propaga exit codes corretamente e o Nx reporta sucesso mesmo quando o build falha.

---

## Ambiente Windows — Problemas Conhecidos

### PATH overflow no cmd.exe
O PATH do Windows tem limite de ~8.191 caracteres. Com 292+ entradas no PATH, `pnpm`, `mvn`, `npm` e outros executáveis não são encontrados pelo cmd.exe quando invocado pelo Nx.

**Solução:** O wrapper `tools/scripts/run.cmd` usa o caminho absoluto do Node.js:
```batch
"C:\Program Files\nodejs\node.exe" %*
```
Todo target Nx deve chamar scripts via `tools\scripts\run.cmd <script>`.

### WSL bash interceptando chamadas Node.js
Quando o Nx (cmd.exe) spawna um processo Node.js, e esse processo chama `spawnSync('bash', ...)`, o Windows encontra o bash do WSL — que não tem Node.js, Maven, nem acesso a caminhos Windows (`G:\...`).

**Solução:** Nunca usar `bash` em scripts Node.js neste projeto. Usar sempre:
- `process.execPath` para chamar binários Node (tsc, vite, etc.)
- `fs.readdirSync` para localizar binários dentro de `node_modules/.pnpm/`
- Caminho absoluto `.cmd` para Maven (ver seção Maven abaixo)

### Arquivos `.cmd` no spawnSync
`spawnSync(path, args, { shell: false })` falha para arquivos `.cmd` no Windows.

**Solução:** Sempre usar `shell: true` ao chamar qualquer `.cmd`:
```javascript
spawnSync(mvnPath, ['clean', 'package', ...], { shell: true, ... })
```

---

## Maven

### Localização do executável
O script `tools/scripts/mvn-build.js` e `mvn-test.js` resolvem o Maven por candidatos conhecidos:
```javascript
const candidates = [
  process.env.MAVEN_HOME && path.join(process.env.MAVEN_HOME, 'bin', 'mvn.cmd'),
  'C:\\ProgramData\\chocolatey\\lib\\maven\\apache-maven-3.6.3\\bin\\mvn.cmd',
  'C:\\Program Files\\Apache\\maven\\bin\\mvn.cmd',
  'C:\\maven\\bin\\mvn.cmd'
].filter(Boolean);
```
Se um novo caminho for necessário, adicionar aqui.

### SSL corporativo
O ambiente tem inspeção SSL corporativa que bloqueia downloads do Maven Central.
Sempre passar estas flags via `MAVEN_OPTS`:
```
-Dmaven.wagon.http.ssl.insecure=true
-Dmaven.wagon.http.ssl.allowall=true
-Dmaven.wagon.http.ssl.ignore.validity.dates=true
```
Já configurado nos scripts. Não remover.

### JAVA_HOME
Usar `C:\Program Files\Java\jdk-17.0.2` (instalado nesta máquina). Verificar com `Get-ChildItem "C:\Program Files\Java"`.
Sempre passar explicitamente no env do spawnSync:
```javascript
const javaHome = process.env.JAVA_HOME || 'C:\\Program Files\\Java\\jdk-17.0.2';
env: { ...process.env, JAVA_HOME: javaHome, MAVEN_OPTS: mavenOpts }
```

---

## Flyway — Convenções e Armadilhas

### Executar migrations sem iniciar o servidor
O serviço depende do Keycloak (issuer-uri) para subir. Para rodar migrations isoladamente:
```powershell
node tools/scripts/mvn-flyway.js :pinsaude-onboarding migrate
node tools/scripts/mvn-flyway.js :pinsaude-onboarding info
```
O script `tools/scripts/mvn-flyway.js` aceita qualquer goal do Flyway Maven Plugin.

### Extensões pgcrypto e uuid-ossp
Extensões PostgreSQL são "trusted" no PG 13+ e exigem `GRANT CREATE ON DATABASE pinsaude TO svc_<serviço>`.
Esse grant está no `tools/db/init.sql`. As extensões são instaladas pelo superuser no init e as migrations
usam `IF NOT EXISTS` para serem idempotentes.

### User Profile do Keycloak 24 bloqueia atributos customizados
O Keycloak 24 habilita User Profile por padrão. Atributos não declarados (ex: `cnpj_id`) são aceitos via Admin API mas silenciosamente ignorados no PUT.
Para adicionar um atributo: declarar em `PUT /admin/realms/{realm}/users/profile` com `permissions.view/edit: ["admin"]`.
O `realm-export.json` já declara `cnpj_id` em `userProfileConfig.attributes`.

### RLS no PostgreSQL — FORCE e padrão de bypass (implementado em EPIC-02.5)
`ALTER TABLE ... ENABLE ROW LEVEL SECURITY` sem `FORCE` faz o owner (svc_X) bypassar RLS automaticamente.
Com `FORCE ROW LEVEL SECURITY` o owner também fica sujeito às policies.
Superusers PostgreSQL bypassam RLS **mesmo com FORCE** — em produção, o app deve conectar como não-superuser.

**Padrão de policy com bypass para gestão/Flyway (tenant vazio = ver tudo):**
```sql
USING (
    COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
    OR cnpj = current_setting('app.current_tenant', TRUE)
)
```
`COALESCE(NULL, '') = ''` → bypass quando variável não está definida (Flyway, health checks).
`COALESCE('', '') = ''` → bypass quando tenant é string vazia (gestão).

**Policy em tabelas filhas** (com `empresa_id` FK, sem coluna `cnpj`):
```sql
USING (
    COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
    OR empresa_id IN (
        SELECT id FROM onboarding.empresas
        WHERE cnpj = current_setting('app.current_tenant', TRUE)
    )
)
```

### Multi-tenancy — propagação do tenant (EPIC-02.5)
A propagação do CNPJ do tenant para o PostgreSQL usa três camadas:

1. **TenantContext** (ThreadLocal estático) — armazena o CNPJ durante o request
2. **TenantFilter** — `OncePerRequestFilter` registrado na cadeia Spring Security **via `http.addFilterAfter(new TenantFilter(), BearerTokenAuthenticationFilter.class)`**, NÃO como `@Component` (evita duplo registro como servlet filter). Lê `JwtAuthenticationToken` do `SecurityContextHolder` (JWT já autenticado) e define: gestão → `""`, demais roles → `cnpj_id` do JWT. `finally` garante limpeza do ThreadLocal.
3. **TenantAwareDataSource + TenantDataSourcePostProcessor** — `BeanPostProcessor` (sem `@Autowired`) envolve o HikariCP e executa `SELECT set_config('app.current_tenant', ?, false)` a cada `getConnection()`. `is_local=false` = sessão, não transação — sempre sobrescrito no borrow seguinte. Falha silenciosa em H2 (testes).

**Testcontainers e FORCE RLS:** o usuário padrão `test` do Testcontainers é superuser → bypassa FORCE RLS. Para testar isolamento real, criar um usuário não-superuser em `@BeforeEach` e conectar via JDBC direto:
```java
conn.createStatement().execute("CREATE ROLE svc_rls_test LOGIN PASSWORD 'rls_test'");
conn.createStatement().execute("GRANT SELECT ON ALL TABLES IN SCHEMA onboarding TO svc_rls_test");
// Depois conectar como svc_rls_test para validar que as policies filtram corretamente
```

### Tipos enum em migrações Flyway
Criar enums PostgreSQL como `CREATE TYPE schema.nome_enum AS ENUM (...)` antes das tabelas que os referenciam.
Usar o schema explícito (`onboarding.regime_tributario_enum`) para evitar ambiguidade.

### Flyway com `classpath:` exige `mvn process-resources` antes de `flyway:migrate`
O `flyway-maven-plugin` configurado com `<location>classpath:db/migration</location>` lê os SQLs do diretório
`target/classes/db/migration`, **não** diretamente de `src/main/resources/db/migration`.
Ao criar um novo arquivo SQL, o Maven precisa copiar o recurso para `target/` antes que o Flyway o veja:
```powershell
$mvn = "C:\ProgramData\chocolatey\lib\maven\apache-maven-3.6.3\bin\mvn.cmd"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.2"
& $mvn process-resources -pl :pinsaude-onboarding
node tools/scripts/mvn-flyway.js :pinsaude-onboarding migrate
```
Sem esse passo, `flyway info` mostrará apenas as migrations já em `target/` e reportará "up to date" incorretamente.

### Criptografia em repouso com pgcrypto (implementado em EPIC-03.1)
CPF e chave PIX são armazenados como `bytea` usando `pgp_sym_encrypt`. A chave é fornecida pela aplicação.

**Funções criadas no schema `onboarding`:**
```sql
CREATE OR REPLACE FUNCTION onboarding.encrypt_sensitive(data TEXT, crypto_key TEXT)
    RETURNS BYTEA LANGUAGE sql SECURITY DEFINER AS
$$ SELECT pgp_sym_encrypt(data, crypto_key); $$;

CREATE OR REPLACE FUNCTION onboarding.decrypt_sensitive(data BYTEA, crypto_key TEXT)
    RETURNS TEXT LANGUAGE sql SECURITY DEFINER AS
$$ SELECT pgp_sym_decrypt(data, crypto_key); $$;
```
`SECURITY DEFINER` garante que apenas o owner execute as funções diretamente.
A variável de ambiente `CRYPTO_KEY` deve ser definida na aplicação Spring Boot.
Colunas criptografadas são do tipo `bytea` — nunca `text` ou `varchar`.

### RLS em tabelas sem coluna de tenant direto (padrão médico)
Tabelas como `medicos`, `dados_bancarios_medico`, `documentos_medico` e `checklist_conduta` não têm
uma coluna `cnpj` ou `empresa_id` direto. O isolamento resolve via join com `vinculos_medico_empresa`:
```sql
CREATE POLICY tenant_isolation ON onboarding.medicos
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR id IN (
            SELECT v.medico_id
            FROM onboarding.vinculos_medico_empresa v
            JOIN onboarding.empresas e ON e.id = v.empresa_id
            WHERE e.cnpj = current_setting('app.current_tenant', TRUE)
        )
    );
```
A tabela `vinculos_medico_empresa` é a âncora do isolamento: ela tem `empresa_id` e resolve via `empresas.cnpj`.

### RLS INSERT bloqueado por USING sem WITH CHECK (implementado em EPIC-03.2)
Policies criadas apenas com `USING (...)` usam a mesma expressão como `WITH CHECK` em INSERTs.
Quando a tabela usa lookup em outra tabela (ex: `medicos` verifica `vinculos_medico_empresa`),
o INSERT falha porque o vínculo ainda não existe no momento da inserção:
```
ERROR: new row violates row-level security policy for table "medicos"
```
**Solução:** adicionar `WITH CHECK (true)` explícito para permitir INSERTs livremente,
mantendo o `USING` apenas para SELECT/UPDATE:
```sql
ALTER POLICY tenant_isolation ON onboarding.medicos WITH CHECK (true);
-- Idem para checklist_conduta, dados_bancarios_medico, documentos_medico
```
O isolamento de leitura continua garantido pelo `USING`; a aplicação é responsável por
inserir imediatamente o vínculo na mesma transação.

---

## JPA / Hibernate 6 — Armadilhas com PostgreSQL

### CHAR(n) vs VARCHAR: mapeamento correto no Hibernate 6
O PostgreSQL armazena `CHAR(n)` como `bpchar` (tipo JDBC `Types#CHAR`).
Hibernate 6 mapeia `String` para `VARCHAR` por padrão — isso causa falha no `ddl-auto=validate`.
**Solução:** combinar `columnDefinition = "char(n)"` com `@JdbcTypeCode(SqlTypes.CHAR)`:
```java
@JdbcTypeCode(SqlTypes.CHAR)
@Column(name = "codigo_municipio_ibge", columnDefinition = "char(7)")
private String codigoMunicipioIbge;
```

### PageRequest com Sort em queries nativas — camelCase quebra no PostgreSQL (EPIC-03.2)
Ao usar `PageRequest.of(page, size, Sort.by("createdAt").descending())` com `@Query(nativeQuery=true)`,
o Hibernate 6 acrescenta `createdAt desc` ao final do SQL. PostgreSQL não reconhece camelCase:
```
ERROR: column "createdat" does not exist
```
**Solução:** passar `PageRequest.of(page, size)` sem Sort e incluir `ORDER BY created_at DESC`
diretamente na query nativa. Nunca misturar Sort do Spring Data com queries nativas que usam
nomes de colunas em snake_case.

### PostgreSQL ENUM com Hibernate 6 — @ColumnTransformer
Hibernate 6 envia enums como `character varying`, mas PostgreSQL não faz cast automático para `user-defined ENUM`.
Isso causa: `column "x" is of type schema.enum_type but expression is of type character varying`.
**Solução:** usar `@ColumnTransformer(write = "?::schema.nome_enum")` para cast explícito na escrita:
```java
@Enumerated(EnumType.STRING)
@Column(name = "regime_tributario")
@ColumnTransformer(write = "?::onboarding.regime_tributario_enum")
private RegimeTributario regimeTributario;
```
A leitura funciona sem transformer — PostgreSQL retorna o label do enum como String.

### Testcontainers com Spring Boot 3.2.5
Para testes de integração com banco real (PostgreSQL):
```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
```

Configuração do teste (sobrescreve `application.properties` de teste):
```java
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/dummy",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9999/dummy"
})
@AutoConfigureMockMvc
@Testcontainers
class MinhaIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
```
- `@ServiceConnection` sobrescreve automaticamente datasource (URL, user, password, driver) — não conflita com H2 do `application.properties` de teste.
- O `test` user do Testcontainers PostgreSQL é superuser — extensões (`pgcrypto`, `uuid-ossp`) são instaladas sem problemas.
- Para JWT mockado no teste, usar `SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("ROLE_xxx"))` — não precisa de WireMock.

---

## Dependências Maven — Armadilhas Conhecidas

### flyway-database-postgresql
**NÃO existe** como artefato separado no Flyway 9.22.3 (versão gerenciada pelo BOM do Spring Boot 3.2.5).
Nunca adicionar `flyway-database-postgresql` como dependência nos POMs dos serviços.
Usar apenas `flyway-core` + `postgresql` (driver JDBC).

---

## Frontend (apps/web)

### Vite e o `root`
Ao rodar `vite build --config /caminho/absoluto/vite.config.ts`, o Vite usa o CWD como root para localizar `index.html` — não o diretório do config.

**Solução:** Sempre definir `root` explicitamente no `vite.config.ts`:
```typescript
export default defineConfig({
  root: path.resolve(__dirname, '.'),
  ...
})
```

### Localização de binários pnpm
Binários instalados pelo pnpm ficam em `node_modules/.pnpm/<pkg>@<version>/node_modules/<pkg>/bin/`.
Para localizar sem bash, usar `fs.readdirSync` em `node_modules/.pnpm/` e filtrar por prefixo de pacote:
```javascript
function resolveNodeBin(pkg, binFile) {
  const pnpmDir = path.join(workspaceRoot, 'node_modules', '.pnpm');
  const entries = fs.readdirSync(pnpmDir);
  for (const entry of entries) {
    if (!entry.startsWith(pkg + '@')) continue;
    const candidate = path.join(pnpmDir, entry, 'node_modules', pkg, 'bin', binFile);
    if (fs.existsSync(candidate)) return candidate;
  }
  return null;
}
```

### React StrictMode
Importar `StrictMode` nomeado, não via `React.StrictMode`:
```tsx
import { StrictMode } from 'react'
// não: import React from 'react' ... <React.StrictMode>
```

---

## Nx Cache

O Nx armazena resultados em `.nx/cache/`. Em caso de comportamento estranho (build reporta sucesso mas artefato não foi gerado), limpar o cache:
```powershell
Remove-Item -Recurse -Force .nx\cache
Remove-Item -Recurse -Force .nx\workspace-data
```
Se o diretório estiver bloqueado por processo Node.js em execução, matar o processo primeiro:
```powershell
Get-Process node | Stop-Process -Force
```

---

## Comandos Úteis

```powershell
# Build de todos os projetos
npx nx run-many --target=build --all

# Build de um projeto específico
npx nx run web:build
npx nx run fiscal:build

# Testes de um serviço Java
node tools/scripts/mvn-test.js :fiscal

# Dev server frontend
npx nx run web:dev

# Limpar cache Nx
Remove-Item -Recurse -Force .nx\cache
```

---

## Frontend (apps/web) — Armadilhas Conhecidas

### pnpm install precisa de `--no-frozen-lockfile` ao adicionar pacotes novos
O `CI=true` ativado pelo ambiente faz o pnpm rodar com `frozen-lockfile` por padrão.
Ao adicionar novas dependências ao `package.json`, rodar:
```powershell
$env:NODE_TLS_REJECT_UNAUTHORIZED = "0"   # SSL corporativo
& "C:\Users\Fernando\AppData\Roaming\npm\pnpm.cmd" install --no-frozen-lockfile
```

### pnpm v11: `allowBuilds` vai em `pnpm-workspace.yaml`
O campo `"pnpm"` no `package.json` foi removido no pnpm v11. Build scripts de `esbuild` e `nx` são aprovados em `pnpm-workspace.yaml`:
```yaml
allowBuilds:
  esbuild: true
  nx: true
```

### Resolução de `lucide-react` a partir de `libs/frontend` (Rollup)
Rollup não consegue resolver `lucide-react` importado em `libs/frontend/src/` durante o build, pois o pacote está somente em `apps/web/node_modules`.
**Solução:** alias explícito em `vite.config.ts`:
```typescript
'lucide-react': path.resolve(__dirname, './node_modules/lucide-react'),
```

### TypeScript paths para React e Lucide ao incluir `libs/frontend` no projeto
Quando `libs/frontend/src` está no `include` do `tsconfig.json` de `apps/web`, TypeScript não encontra `react` e `lucide-react` a partir do caminho das libs.
**Solução:** adicionar paths explícitos em `tsconfig.json`:
```json
"paths": {
  "react": ["./node_modules/@types/react"],
  "react/jsx-runtime": ["./node_modules/@types/react/jsx-runtime"],
  "lucide-react": ["./node_modules/lucide-react/dist/lucide-react.d.ts"]
}
```

### `postcss.config.js` e `tailwind.config.js` precisam de `"type": "module"` no package.json
Com Vite 5 + Node 22, configs `.js` são tratadas como CJS se não houver `"type": "module"`.
Adicionado em `apps/web/package.json`.

### Tailwind não encontra os templates quando o build roda fora de `apps/web`
O Nx executa o build com `cwd: workspaceRoot` (`G:\olisystem\pinsaude`). O PostCSS/Tailwind resolve o `tailwind.config.js` pelo CWD — que é a raiz do monorepo, não `apps/web`. Resultado: warning `content option missing or empty` e CSS de 4.8kB sem nenhuma utility class.

**Solução em duas partes:**

1. `postcss.config.js` — passar o caminho absoluto do config explicitamente:
```javascript
import { fileURLToPath } from 'url'
import path from 'path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const configPath = path.join(__dirname, 'tailwind.config.js').replace(/\\/g, '/')

export default {
  plugins: {
    tailwindcss: { config: configPath },
    autoprefixer: {},
  },
}
```

2. `tailwind.config.js` — usar paths absolutos com forward slashes (glob falha com backslash no Windows):
```javascript
import { fileURLToPath } from 'url'
import path from 'path'

const dir = path.dirname(fileURLToPath(import.meta.url)).replace(/\\/g, '/')

export default {
  content: [
    `${dir}/index.html`,
    `${dir}/src/**/*.{js,ts,jsx,tsx}`,
    `${dir}/../../libs/frontend/src/**/*.{js,ts,jsx,tsx}`,
  ],
  ...
}
```

Com essa configuração o CSS de produção sobe de 4.8kB para ~19.6kB com todas as utility classes.

---

## Infraestrutura Docker — Armadilhas Conhecidas

### Porta 5432 em conflito com PostgreSQL local
O ambiente de desenvolvimento tem um PostgreSQL nativo rodando na porta 5432.
O container Docker do pinsaude usa **porta 5433** para evitar conflito.

| Serviço | Porta Host | Porta Container |
|---|---|---|
| PostgreSQL | **5433** | 5432 |
| RabbitMQ | 5672 / 15672 | 5672 / 15672 |
| Keycloak | 8080 | 8080 |
| Vault | 8200 | 8200 |
| Jaeger | 16686 / 4317 | 16686 / 4317 |
| Mailhog | 1025 / 8025 | 1025 / 8025 |

Para se conectar ao banco via string JDBC nos serviços Spring Boot locais (fora do Docker):
```
spring.datasource.url=jdbc:postgresql://localhost:5433/pinsaude?currentSchema=<schema>
```

### Keycloak usa PostgreSQL (não dev-mem)
`KC_DB: dev-mem` causava perda de estado periodicamente (H2 in-memory). Substituído por:
```yaml
KC_DB: postgres
KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
KC_DB_USERNAME: postgres
KC_DB_PASSWORD: postgres
```
O banco `keycloak` é criado automaticamente pelo `tools/db/init.sql` via `\gexec`.

**Recriando o volume do PostgreSQL** (necessário apenas uma vez ao migrar de `dev-mem`):
```powershell
$docker = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
# Cria o banco manualmente se o volume já existir
& $docker exec pinsaude-postgres psql -U postgres -c "CREATE DATABASE keycloak;"
# Ou recria tudo do zero:
.\tools\scripts\start-infra.ps1 -Down
& $docker volume rm pinsaude_postgres_data
.\tools\scripts\start-infra.ps1
```

### Keycloak 24 não tem curl nem wget
`quay.io/keycloak/keycloak:24.0` é baseado em Red Hat UBI 9 minimal.
Não possui `curl`, `wget`, `nc` nem Python. **Possui `bash` e suporta `/dev/tcp`**.

Healthcheck correto — usar `CMD` array (não `CMD-SHELL`, que usa `sh` sem `/dev/tcp`):
```yaml
test: ["CMD", "bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /health/ready HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && timeout 3 head -1 <&3 | grep -q '200'"]
```

`KC_HEALTH_ENABLED: "true"` deve estar nos env vars do Keycloak para o endpoint `/health/ready` ser exposto.

### Docker CLI não está no PATH padrão do PowerShell
O Docker Desktop instala o binário em `C:\Program Files\Docker\Docker\resources\bin\`.
Em sessões PowerShell sem o PATH configurado, usar caminho absoluto:
```powershell
$docker = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
$env:PATH = "C:\Program Files\Docker\Docker\resources\bin;" + $env:PATH
```
O `docker-credential-desktop.exe` também fica nessa pasta — sem ele no PATH, pulls falham.

### Atributo `version:` obsoleto no docker-compose.yml
Docker Compose v2+ ignora o campo `version`. Não usar para evitar warning no `compose up`.

### Iniciar a infra local
```powershell
# Windows (recomendado)
.\tools\scripts\start-infra.ps1

# Derrubar tudo
.\tools\scripts\start-infra.ps1 -Down
```

---

## Design System Oficial — Pin Saúde (Corvi Design)

Arquivo de referência: `design_system/corvidesign_defesa_pin-saude-02.pdf`
**Sempre consultar este arquivo ao criar ou revisar componentes visuais.**

### Cores

| Token Tailwind | Hex | Uso |
|---|---|---|
| `primary` / DEFAULT | `#02A9F7` | Cor principal — azul confiança/tecnologia |
| `primary-700` | `#0069A0` | Sidebar, nav ativa (escuro) |
| `secondary` | `#8EDD65` | Verde saúde — cor opcional |
| `gray-brand-mid` | `#939598` | Textos, boa leiturabilidade |
| `gray-brand-light` | `#B7B9BC` | Elementos auxiliares |

Paleta `primary` completa no Tailwind:
```
50: #E6F6FF | 100: #BAE9FF | 200: #7DD4FF | 300: #40BEFF | 400: #17B3FF
500: #02A9F7 | 600: #0089CC | 700: #0069A0 | 800: #004C74 | 900: #002F47
```

### Tipografia

**Source Sans Pro** (Google Fonts) — font sem serifa projetada para UI.
- Black 900 → títulos / logotipo
- Semi-bold 600 → subtítulos, labels
- Regular 400 → parágrafos, textos

### Logo

`apps/web/public/logo-pinsaude.png` — baixada do site institucional.
Em sidebar escura: usar `className="brightness-0 invert"` para versão branca.

---

## CI/CD — GitHub Actions

### Estrutura dos workflows
- `.github/workflows/ci.yml` — executa em cada push para `main` e em PRs
- `.github/workflows/release.yml` — stub, ativa em tags `v*.*.*`

### Jobs do CI
| Job | O que executa |
|---|---|
| `build-web` | `node tools/scripts/build-web.js` (tsc + vite) |
| `test-web` | `node tools/scripts/build-web.js test` (vitest) |
| `lint-web` | `node tools/scripts/build-web.js lint` (eslint) |
| `build-java` | `mvn clean install --no-transfer-progress -DskipTests` |
| `affected` | Relatório Nx affected (somente PRs) |

### Por que não usar `npx nx run-many` no CI
Os `project.json` referenciam `tools\scripts\run.cmd` que é um wrapper Windows-only para contornar o overflow de PATH. Em Linux (Ubuntu runner), esse `.cmd` não existe.
**Solução:** chamar `pnpm --filter web run <script>` e `mvn` diretamente, sem passar pelo Nx executor.

### Por que não usar `node tools/scripts/build-web.js` no CI
`build-web.js` usa `resolveNodeBin` que busca em `node_modules/.pnpm/`. Com `node-linker=hoisted` no Linux, o pnpm **não cria** o diretório `.pnpm` — logo os binários (vite, vitest, eslint) não são encontrados.
**Solução:** usar `pnpm --filter web run build|test|lint` que executa os scripts do `apps/web/package.json` com os binários corretos do workspace.
`build-web.js` continua sendo usado localmente no Windows via Nx targets (funciona porque `.pnpm` existe no Windows).

### Vitest: `--passWithNoTests` no script do package.json
`apps/web/package.json` tem `"test": "vitest run --passWithNoTests"` para não falhar enquanto não há arquivos de teste.

---

## Keycloak — Configuração do Realm

### Realm `pinsaude`
- Arquivo de configuração: `tools/keycloak/realm-export.json`
- Script de setup via REST API: `tools/keycloak/setup-realm.sh`
- Auto-importado pelo Docker via `--import-realm` + volume em `/opt/keycloak/data/import/`

### Clients
| Client | Tipo | Uso |
|---|---|---|
| `pinsaude-web` | Public (PKCE S256) | SPA React — flow OIDC |
| `pinsaude-gateway` | Confidential | Spring Cloud Gateway — validação server-side |

Segredo do gateway: `pinsaude-gateway-secret` (alterar em produção)

### Roles
`medico` · `operacao` · `financeiro` · `contabil` · `gestao`

### MFA (TOTP)
- Perfis privilegiados (`operacao`, `financeiro`, `contabil`, `gestao`) têm `CONFIGURE_TOTP` como required action
- Flow `pinsaude browser` contém sub-flows CONDITIONAL com `conditional-user-role` por role
- `pinsaude-web` tem `directAccessGrantsEnabled: true` para testes (desabilitar em produção)
- Usuários com CONFIGURE_TOTP pendente recebem `invalid_grant: Account is not fully set up` no password grant

### Claim `cnpj_id`
Protocol mapper `oidc-usermodel-attribute-mapper` no client `pinsaude-web` lê o atributo `cnpj_id` do usuário e injeta no JWT. Definir o atributo no cadastro do usuário no Keycloak.

### Usuários de teste
| Email | Senha | Role | MFA |
|---|---|---|---|
| medico@pinsaude.com.br | test123 | medico | Não |
| operacao@pinsaude.com.br | test123 | operacao | Sim (CONFIGURE_TOTP) |
| gestao@pinsaude.com.br | test123 | gestao | Sim (CONFIGURE_TOTP) |

### Scope `basic` não existe no Keycloak 24
O scope `basic` foi removido em versões mais novas. Não adicionar em `defaultClientScopes` — causará warning silencioso na importação.

---

## Autenticação Frontend (React + Keycloak ROPC)

### Abordagem adotada
Fluxo ROPC (Resource Owner Password Credentials) via `fetch` direto ao endpoint `/token` do Keycloak.
NÃO usa o adapter `keycloak-js` para o login — o adapter é para redirect flow (PKCE).
ROPC permite formulário customizado sem redirect para o Keycloak.

### Estrutura dos arquivos de auth
```
apps/web/src/auth/
  keycloak.ts        → constantes (URL, realm, client, endpoints) + decodeJwt()
  AuthContext.tsx    → Context, AuthProvider, useAuth() — gerencia tokens + refresh automático
  useAuth.ts         → re-export de useAuth e AuthUser para imports limpos
```

### Armazenamento de tokens
Tokens (access + refresh + expiresAt) armazenados em `sessionStorage` com chave `pinsaude_tokens`.
Sessão é restaurada ao recarregar a página; expirada automaticamente com o refresh timer.

### Refresh automático
`setTimeout` agendado `(expires_in - 60) * 1000` ms após cada token emitido.
Usa `useRef` para o callback (evita circular dependency entre `applyTokens` e `performRefresh`).

### `import.meta.env` no TypeScript
Vite expõe variáveis de ambiente via `import.meta.env`. Para o TypeScript não reclamar,
criar `apps/web/src/vite-env.d.ts` com:
```ts
/// <reference types="vite/client" />
```
Sem esse arquivo: `error TS2339: Property 'env' does not exist on type 'ImportMeta'`.

### Variáveis de ambiente Vite (auth)
```
VITE_KC_URL    → URL base do Keycloak (default: http://localhost:8080)
VITE_KC_REALM  → Realm (default: pinsaude)
VITE_KC_CLIENT → Client ID (default: pinsaude-web)
```

### Erros ROPC tratados
| Resposta Keycloak | Tratamento |
|---|---|
| `invalid_grant` + `"Invalid user credentials"` | "E-mail ou senha incorretos" |
| `invalid_grant` + `"Account is not fully set up"` | Mensagem sobre configuração de MFA |
| Outros 4xx/5xx | Mensagem genérica com status |

### Roles no JWT — `realm_access.roles`, não `user.roles`
O `AuthUser` extende `JwtPayload`. As roles do Keycloak ficam em `realm_access.roles` (array).
**NUNCA usar `user.roles`** — essa propriedade não existe.
```typescript
const isGestao = user?.realm_access?.roles.includes('gestao') ?? false
```

### Token de acesso — ler do sessionStorage, não de export do keycloak.ts
O `keycloak.ts` exporta apenas constantes e `decodeJwt()`. O token em si fica em `sessionStorage`.
Padrão adotado em todos os módulos de API (`usersApi.ts`, `empresasApi.ts`):
```typescript
const STORAGE_KEY = 'pinsaude_tokens'
function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}
```

---

## Keycloak Admin API — Armadilhas Conhecidas (EPIC-01.5)

### Token do admin usa o realm `master`, não `pinsaude`
```
POST /realms/master/protocol/openid-connect/token
grant_type=password&client_id=admin-cli&username=admin&password=admin
```
Chamar com realm `pinsaude` retorna 401.

### GET /role-mappings/realm retorna lista flat, não objeto
```
GET /admin/realms/pinsaude/users/{id}/role-mappings/realm
→ [ { "id": "...", "name": "gestao", ... }, ... ]
```
Não é `{ mappings: [...] }`. Desserializar direto como `List<Map<String, Object>>`.

### DELETE com body requer `RestClient.method(HttpMethod.DELETE)`
`restClient.delete()` no Spring não aceita body. Para remover roles (DELETE com payload JSON):
```java
restClient.method(HttpMethod.DELETE)
    .uri(url)
    .contentType(MediaType.APPLICATION_JSON)
    .body(List.of(role))
    .retrieve()
    .toBodilessEntity();
```

### smtpServer no realm-export.json usa nome do serviço Docker
O Keycloak roda dentro do container Docker. O SMTP deve apontar para `mailhog` (nome do serviço),
não `localhost`. `localhost` funcionaria apenas fora do Docker.
```json
"smtpServer": { "host": "mailhog", "port": "1025", ... }
```

### `execute-actions-email` envia o e-mail de convite
```
PUT /admin/realms/pinsaude/users/{id}/execute-actions-email
Body: ["UPDATE_PASSWORD", "VERIFY_EMAIL"]
```
Requer que o realm tenha `smtpServer` configurado; caso contrário retorna 500 silencioso.

---

## Configuração Fiscal e Versionamento por Competência (EPIC-02.4)

### Tabela `aliquotas_competencia` — padrão de versionamento
Alíquotas fiscais (ISS, IR, CSLL, PIS, COFINS) são armazenadas por empresa + competência (YYYY-MM).
Cada competência é independente: salvar alíquotas de julho não retroage em notas de junho.
Modelo: UNIQUE (empresa_id, competencia) → upsert por mês. NFS-e buscará sempre pela competência do mês da nota.

### `regime_presuncao` como VARCHAR (não enum PostgreSQL)
A coluna `regime_presuncao` em `aliquotas_competencia` usa `VARCHAR(10)` com `CHECK IN ('REDUZIDA', 'CHEIA')`.
Evita o problema de cast do Hibernate 6 com enums PostgreSQL (`@ColumnTransformer` não é necessário).
Java usa `@Enumerated(EnumType.STRING)` normalmente.

### Status do Certificado A1 — calculado em runtime
Não armazenar status no banco. Calculado no `ConfiguracaoFiscalResponse.from()` comparando `vencimentoCertificadoA1` com `LocalDate.now()`:
- `VALIDO` → vence em mais de 30 dias
- `EXPIRANDO` → vence em menos de 30 dias (mas ainda válido)
- `VENCIDO` → já passou da data
- `NAO_CONFIGURADO` → campo nulo

### Filtro de busca em arrays JavaScript — `includes("")` é sempre true
`anyString.includes("")` retorna `true` em JavaScript. Ao combinar filtros OR com `.includes(searchTerm)`, sempre verificar se o termo pós-processamento é não-vazio antes de aplicar o filtro:
```typescript
const qDigits = q.replace(/\D/g, '')
const match = !q
  || e.campo.toLowerCase().includes(q)
  || (qDigits.length > 0 && e.outro.replace(/\D/g, '').includes(qDigits))
```

---

## Upload de Documentos — Padrões e Armadilhas (EPIC-03.4)

### Preview de imagens autenticadas via URL pré-assinada do MinIO
Imagens armazenadas no MinIO não são diretamente acessíveis pelo `<img src>` de um endpoint protegido por JWT (o browser não inclui o header `Authorization` em requisições de imagem).

**Padrão adotado:**
- Para uploads recém-feitos na sessão atual: `URL.createObjectURL(file)` (blob local, sem requisição ao servidor)
- Para imagens de sessões anteriores: endpoint `GET /api/medicos/{id}/documentos/{docId}/url` retorna uma URL pré-assinada do MinIO. A URL pré-assinada do MinIO usa assinatura HMAC (sem JWT) e pode ser usada diretamente como `<img src>` ou `window.open()`.
- MinIO no ambiente de desenvolvimento está disponível em `localhost:9000` (host port). A URL pré-assinada usa `localhost:9000` e é acessível pelo browser.

### Replace de documento sem duplicata
Ao fazer re-upload do mesmo tipo de documento, o service deve:
1. Buscar documento existente do mesmo tipo com `findByMedicoIdAndTipo`
2. Deletar o arquivo antigo do MinIO com `StorageService.delete`
3. Deletar o registro do banco com `documentoRepo.delete`
4. Fazer upload do novo arquivo e criar novo registro

`StorageService.delete` captura todas as exceções silenciosamente (objeto órfão é aceitável; não bloqueia o re-upload).

### Gating de ativação por documentos
`MedicoService.ativar` agora verifica dois pré-requisitos antes de mudar status para ATIVO:
1. Checklist de conduta completo (existente)
2. Todos os 4 documentos obrigatórios (CRM, DIPLOMA, IDENTIDADE, RESIDENCIA) com `statusValidacao = APROVADO`

Se algum documento estiver PENDENTE ou REPROVADO, a ativação lança `422 Unprocessable Entity`.

### Revogação de blob URLs no React
Componentes que criam blob URLs com `URL.createObjectURL` devem revogar ao desmontar para evitar memory leak:
```typescript
const previewsRef = useRef(previews)
previewsRef.current = previews
useEffect(() => () => {
  Object.values(previewsRef.current).forEach(url => {
    if (url?.startsWith('blob:')) URL.revokeObjectURL(url)
  })
}, [])
```
Usar `useRef` para capturar o valor mais recente sem re-registrar o cleanup a cada render.

### Upload multipart sem Content-Type no header
Para `fetch` com `FormData` (multipart), **não incluir** `Content-Type: multipart/form-data` manualmente. O browser define o header correto com o boundary automaticamente. Se incluído manualmente, o boundary fica ausente e o Spring `MultipartResolver` rejeita com 400.

```typescript
// CORRETO — apenas Authorization, sem Content-Type
headers: { Authorization: `Bearer ${token}` },
body: formData

// ERRADO — sobrescreve o Content-Type que o browser geraria
headers: { Authorization: `...`, 'Content-Type': 'multipart/form-data' },
```

---

## Envio de E-mail com Spring Mail (EPIC-03.6)

### Dependência e configuração mínima para Mailhog
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```
```yaml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}  # porta do Mailhog
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
```
O Mailhog roda dentro do Docker mas o serviço Spring acessa em `localhost:1025` (host port mapeado).
Usar `SimpleMailMessage` para e-mails sem template HTML. Capturar exceção de envio para torná-la não-fatal:
```java
try {
    mailSender.send(msg);
    convite.setStatus("ENVIADO");
} catch (Exception e) {
    log.error("Falha ao enviar e-mail de convite", e);
    convite.setStatus("PENDENTE");
}
```

### Injeção de valores simples via @Value no construtor
Para `ConviteService`, os parâmetros `emailFrom`, `baseUrl` e `expiracaoHoras` vêm via `@Value`:
```java
public ConviteService(
    ConviteMedicoRepository repo,
    JavaMailSender mailSender,
    @Value("${app.email-from}") String emailFrom,
    @Value("${app.base-url}") String baseUrl,
    @Value("${app.convite.expiracao-horas:168}") Long expiracaoHoras
) { ... }
```

---

## Integração com Clicksign — Port/Adapter Pattern (EPIC-03.6)

### Interface + Adapter com feature flag
Usar Port/Adapter para isolar a integração de assinatura digital.
O Adapter lança `503 SERVICE_UNAVAILABLE` quando não configurado — nunca `NullPointerException`:
```java
// Port
public interface ContratoAssinaturaPort {
    ContratoAssinatura enviar(Medico medico, String emailMedico) throws Exception;
}

// Adapter
@Component
@ConfigurationProperties(prefix = "clicksign")
public class ClicksignAdapter implements ContratoAssinaturaPort {
    private boolean enabled;
    private String accessToken;
    ...
    @Override
    public ContratoAssinatura enviar(Medico medico, String emailMedico) {
        if (!enabled || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Integração Clicksign não configurada");
        }
        // chamadas reais à API Clicksign
    }
}
```
```yaml
clicksign:
  enabled: ${CLICKSIGN_ENABLED:false}
  base-url: ${CLICKSIGN_BASE_URL:https://sandbox.clicksign.com}
  access-token: ${CLICKSIGN_ACCESS_TOKEN:}
  template-key: ${CLICKSIGN_TEMPLATE_KEY:}
```
Nos testes, usar `@Mock ContratoAssinaturaPort contratoPort` — o Adapter real nunca é invocado.

### Webhook sem autenticação
O endpoint de webhook do Clicksign deve ser liberado no SecurityConfig:
```java
.requestMatchers("/api/onboarding/webhooks/**").permitAll()
```
O webhook sempre retorna `200 OK` independente do resultado (Clicksign retransmite até receber 200).

---

## Auto-ativação de Médico (EPIC-03.6)

### Quatro condições necessárias
A ativação automática ocorre quando **todas** as condições são satisfeitas simultaneamente:
1. Checklist de conduta completo (`numeroConselhoVerificado` + `registrosDisciplinares` + `processosMedicos` = true)
2. 4 documentos obrigatórios com `statusValidacao = APROVADO` (CRM, DIPLOMA, IDENTIDADE, RESIDENCIA)
3. Contrato com `status = "ASSINADO"` (via webhook Clicksign ou atualização manual)
4. Junta comercial com `statusJuntaComercial = "APROVADO"` (atualizado pelo operador)

Disparadores da verificação:
- `atualizarJuntaComercial(id, req)` quando status = "APROVADO"
- `processarWebhookClicksign(docKey, "sign")` quando evento = "sign"

```java
private void verificarAtivacaoAutomatica(Medico medico) {
    try {
        validarPreRequisitosAtivacao(medico.getId(), medico);
        medico.setStatus(StatusMedico.ATIVO);
        medicoRepo.save(medico);
        historicoRepo.save(new HistoricoMedico(medico.getId(), TipoAcaoMedico.ATIVACAO_AUTOMATICA, "..."));
    } catch (ResponseStatusException e) {
        // pré-requisitos não cumpridos — silencioso, sem log de erro
    }
}
```

---

## Mockito — Armadilhas com Múltiplos Mocks do Mesmo Tipo

### Dois `@Mock` do mesmo tipo causam injeção ambígua
Se uma classe de teste declara dois campos `@Mock` do mesmo tipo, o Mockito injeta um deles de forma
imprevisível no `@InjectMocks`. Stubs configurados no mock "errado" nunca são invocados → `UnnecessaryStubbing`.

**Regra:** nunca declarar dois `@Mock` do mesmo tipo na mesma classe de teste.

**Solução para testes que precisam testar um serviço diretamente E injeta-lo via InjectMocks:**
```java
@Mock ConviteMedicoRepository conviteRepo;  // único mock do tipo — injetado no MedicoService
@Mock JavaMailSender mailSender;            // único mock do tipo

@InjectMocks MedicoService medicoService;

@Test
void testaConviteService_diretamente() {
    // Criar ConviteService manualmente usando os MESMOS mocks
    var svc = new ConviteService(conviteRepo, mailSender, "from@x.com", "http://x", 168L);
    // ... testar svc diretamente
}
```
Isso garante que o mock injetado no MedicoService e o usado no ConviteService manual são o mesmo objeto.

---

## Produção Médica — Padrões e Armadilhas (EPIC-04.4)

### Fetch LAZY em associações ManyToOne — @Transactional(readOnly = true) obrigatório
Entidades como `Producao` usam `@ManyToOne(fetch = FetchType.LAZY)` para `tomador` e `servico`.
Acessar essas associações fora de uma transação ativa causa `LazyInitializationException`.

**Regra:** todo método de serviço que lê e projeta associações lazy DEVE ser `@Transactional(readOnly = true)`:
```java
@Transactional(readOnly = true)
public List<ProducaoResponse> listar(...) { ... }
```
Apenas os métodos de escrita usam `@Transactional` (sem readOnly).

### Preview de cálculo — lógica de centavos com BigDecimal
Todos os valores monetários estão em centavos (BIGINT). O preview usa `BigDecimal` para evitar
perda de precisão ao calcular percentuais:
```java
// aliquota vem como 5.0000 (percent) → dividir por 100 para obter 0.05 (decimal)
BigDecimal fator = aliquota.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
long resultado = valorCentavos.multiply(fator).setScale(0, RoundingMode.HALF_UP).longValue();
```
Taxa Pin Saúde = 15% do valor bruto. Retenções fiscais só aplicam quando `indicadorRetencaoIss` ou
`indicadorRetencaoFederal` do tomador = true.

### Inner records em ProducaoResponse — sem crypto no contexto de listagem
`ProducaoResponse` usa inner records `TomadorResumo` e `ServicoResumo` ao invés de reusar
`TomadorResponse` completo. Razão: `TomadorResponse` inclui `cnpjCpf` que requer o `CryptoService`
para decifrar — não disponível em métodos estáticos (`from()`). Na listagem de produções só precisamos
de nome e cidade, então os resumos são suficientes.

### Catálogo de serviços LC 116 — seed em V4 migration
A tabela `faturamento.servicos` é compartilhada entre todos os tenants (sem RLS).
Os 15 serviços médicos mais comuns foram inseridos na migration `V4__seed_servicos_lc116.sql`.
Alíquotas padrão: ISS 5%, IR 1,5%, CSLL 1%, PIS 0,65%, COFINS 3%.
Ajustes por competência/empresa são feitos via `aliquotas_competencia` (EPIC-02.4).

### Modelo de negócio — médico sempre recebe 85%, impostos saem dos 15% da Pin
**REGRA FUNDAMENTAL:** A Pin Saúde retém 15% do valor bruto **por participante**.
O médico **sempre** recebe exatamente 85% do SEU valor bruto, independente de quais impostos são retidos.
Os impostos (ISS, IR, CSLL, PIS, COFINS) são custo fiscal da **Pin**, não do médico.
Em produções multi-médico (EPIC-04.6), a regra se aplica individualmente a cada participante.

```
Valor Bruto (do tomador):        R$ 10.000
− Taxa Pin Saúde (15%):          R$  1.500
= Valor Líquido ao Médico:       R$  8.500  ← sempre 85% do SEU valor bruto

Pin Saúde retém:                 R$  1.500
− Impostos pagos/retidos:        R$  (815)
= Resultado Pin Saúde:           R$    685  ← lucro da Pin
```

O negócio da Pin é conseguir pagar menos impostos para maximizar o resultado dentro dos 15%.
Nunca deduzir tributos do valor líquido do médico — só deduzir `taxaPin` (15%):
```java
long valorLiquidoMedico = valorBruto - taxaPin;   // CORRETO — sempre 85%
long resultadoPin       = taxaPin - totalRetencoes; // resultado da Pin após tributos
// ERRADO: valorLiquidoMedico = valorBruto - taxaPin - totalRetencoes
```

### Autocomplete no frontend — debounce de preview com setTimeout/useRef
O preview de cálculo fiscal é disparado com debounce de 400ms após qualquer mudança em
`servicoId`, `tomadorId` ou `valorBruto`. O timer é armazenado em `useRef` para evitar
race conditions quando o usuário digita rápido:
```tsx
const previewTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
// cancela o timer anterior antes de agendar novo
if (previewTimerRef.current) clearTimeout(previewTimerRef.current)
previewTimerRef.current = setTimeout(() => { /* chamar API */ }, 400)
```

### Máscara de moeda em centavos no React — pattern correto
Para campos de valor monetário, sempre armazenar em centavos e exibir com máscara:
```tsx
// input: armazena apenas os dígitos e recomputa centavos
const raw = e.target.value.replace(/\D/g, '')
const cents = parseInt(raw || '0', 10)
setValorStr(maskBRL(cents))  // exibe "1.500,00"

// parsear de volta: mesma estratégia
function parseBRL(str: string): number {
  return parseInt(str.replace(/\D/g, '') || '0', 10)
}
```
Isso evita problemas com separadores de milhar e vírgula decimal do pt-BR.

---

## Consulta e Exportação de Produção — Padrões (EPIC-04.5)

### Filtros combinados via stream — nunca if-branches mutuamente exclusivos
Quando o service precisa suportar múltiplos filtros opcionais simultaneamente, usar stream com predicados independentes.
**Atenção:** a partir do EPIC-04.6, o filtro por `medicoId` é feito via `participacoes_producao`, não por `p.getMedicoId()` (que é null para produções multi-médico). Ver padrão em EPIC-04.6.

Comparação lexicográfica de `String` funciona corretamente para competência no formato `YYYY-MM` (ordem ISO = ordem cronológica).

### Export CSV pt-BR no browser — BOM + ponto-e-vírgula
Para abrir corretamente no Excel em pt-BR, o CSV deve usar ponto-e-vírgula como separador e incluir o BOM UTF-8:
```typescript
const csv = rows.map(r => r.map(c => `"${c}"`).join(';')).join('\n')
const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })  // '﻿' = BOM
const url = URL.createObjectURL(blob)
const a = document.createElement('a'); a.href = url; a.download = 'arquivo.csv'; a.click()
URL.revokeObjectURL(url)
```
Sem o BOM, caracteres especiais (acentos) aparecem errados no Excel Windows.

### Totalizadores condicionais — mostrar só com filtro ativo
O painel de totalizadores (quantidade, bruto, líquido estimado) deve aparecer apenas quando um filtro está ativo,
para não confundir com as stats globais do header:
```typescript
const temFiltroAtivo = q || filtroStatus || filtroMedico || filtroTomador || periodoInicio || periodoFim
{temFiltroAtivo && (
  <div className="bg-primary-50 ...">
    <p>Registros: {filtered.length}</p>
    <p>Valor Bruto: {formatBRL(totalFiltradoBruto)}</p>
    <p>Estimativa Líquido (−15%): {formatBRL(Math.round(totalFiltradoBruto * 0.85))}</p>
  </div>
)}
```

### Enriquecimento do DTO para modal de detalhe — adicionar alíquotas ao ServicoResumo
Para exibir o breakdown fiscal completo no modal sem fazer uma chamada extra ao servidor,
adicionar as alíquotas ao inner record `ServicoResumo` do `ProducaoResponse`:
```java
public record ServicoResumo(UUID id, String codigoLc116, String descricaoPadrao,
                             BigDecimal aliquotaIss, BigDecimal aliquotaIr,
                             BigDecimal aliquotaCsll, BigDecimal aliquotaPis,
                             BigDecimal aliquotaCofins) { ... }
```
O cálculo fiscal no frontend repete a lógica do backend:
```typescript
const issRetido = tomador.retencaoIss ? Math.round(valorBruto * servico.aliquotaIss / 100) : 0
```

---

## Produção Multi-Médico — Padrões e Armadilhas (EPIC-04.6)

### Modelo: uma NFS-e para N médicos via `participacoes_producao`
Uma produção pode envolver N médicos somados (ex.: hospital com 2, 4, 10 médicos em uma nota).
A emissão é **uma única NFS-e pelo total** (valor somado dos participantes). O detalhamento
por médico fica em `faturamento.participacoes_producao`, que é a tabela âncora do isolamento
multi-médico.

```
producoes (uma por nota)
  ├── tomador_id
  ├── servico_id
  ├── valor_total (soma dos participantes)
  ├── medico_id NULL (preenchido apenas em single-médico por compat. histórica)
  └── participacoes_producao (N linhas)
        ├── medico_id
        ├── valor_bruto (contribuição deste médico)
        └── taxaPin = Math.round(valor_bruto * 0.15)
```

### API contract — `ProducaoRequest` agora usa lista de participantes
```json
// ANTES (single-médico):
{ "medicoId": "uuid", "valorBruto": 1000000, "tomadorId": "...", ... }

// DEPOIS (EPIC-04.6):
{
  "tomadorId": "...", "servicoId": "...", "competencia": "2026-06",
  "participantes": [
    { "medicoId": "uuid-medico-1", "valorBruto": 600000 },
    { "medicoId": "uuid-medico-2", "valorBruto": 400000 }
  ]
}
```
`@NotEmpty List<@Valid ParticipacaoRequest> participantes` — obrigatório, mínimo 1 item.
`valorTotal` da produção = soma dos `valorBruto` de cada participante (calculado no service).

### `ProducaoResponse` — campos de compatibilidade
- `medicoId` retorna `participantes[0].medicoId` para produções de 1 médico, `null` para multi.
- `valorBruto` = soma total de todos os participantes.
- `participantes: List<ParticipacaoResponse>` — lista completa sempre presente.

```java
public record ParticipacaoResponse(UUID id, UUID medicoId, long valorBruto, long taxaPin, long valorLiquido) {
    static ParticipacaoResponse from(ParticipacaoProducao p) {
        long taxaPin = Math.round(p.getValorBruto() * 0.15);
        return new ParticipacaoResponse(p.getId(), p.getMedicoId(), p.getValorBruto(),
                                        taxaPin, p.getValorBruto() - taxaPin);
    }
}
```

### Filtro por médico — batch para evitar N+1
O filtro de `listar(medicoId)` no `ProducaoService` não usa `p.getMedicoId()` (é null para multi).
Usa-se dois queries em sequência sem N+1:
```java
// 1. busca IDs das produções onde o médico participa
List<UUID> ids = participacaoRepo.findProducaoIdsByMedicoId(medicoId);

// 2. carrega todas as participações dessas produções em batch
Map<UUID, List<ParticipacaoProducao>> partsMap =
    participacaoRepo.findByProducaoIdIn(ids)
        .stream().collect(groupingBy(ParticipacaoProducao::getProducaoId));
```
```java
// JPQL — retorna só os IDs (não as entidades)
@Query("SELECT p.producaoId FROM ParticipacaoProducao p WHERE p.medicoId = :medicoId")
List<UUID> findProducaoIdsByMedicoId(@Param("medicoId") UUID medicoId);
```

### RLS em `participacoes_producao` — subquery para `producoes`
A tabela `participacoes_producao` não tem coluna `cnpj_id_tenant` diretamente.
O isolamento é por subquery:
```sql
CREATE POLICY tenant_isolation ON faturamento.participacoes_producao
USING (
    COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
    OR producao_id IN (
        SELECT id FROM faturamento.producoes
        WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE)
    )
);
```
Bypass via `COALESCE = ''` para gestão e portal (sem tenant no JWT).

### Migration V5 — auto-migra produções existentes
`V5__add_participacoes_producao.sql`:
1. Cria a tabela `participacoes_producao` com RLS e UNIQUE `(producao_id, medico_id)`
2. Migra todas as produções existentes automaticamente (single-médico → 1 participação)
3. Remove NOT NULL de `producoes.medico_id` (passa a ser nullable)

O campo `medico_id` em `producoes` é mantido por compatibilidade histórica com as notas fiscais já emitidas.

### NfseService — `medicoId` nullable para multi-médico
A lógica de "primeira nota" (que determina se vai para `AGUARDANDO_VALIDACAO` antes de emitir)
só se aplica quando `medicoId != null`. Produções multi-médico vão direto para `PENDENTE`:
```java
boolean primeiraNotaMedico = req.medicoId() != null
    && !notaRepo.existsByMedicoIdAndStatus(req.medicoId(), StatusNota.EMITIDA);
String statusInicial = primeiraNotaMedico ? "AGUARDANDO_VALIDACAO" : "PENDENTE";
```

### Portal — backward compat com notas antigas (CASE WHEN)
O `PortalService` usa CASE WHEN para suportar notas emitidas antes e depois do EPIC-04.6:
```sql
LEFT JOIN faturamento.participacoes_producao pp
       ON pp.producao_id = nf.producao_id AND pp.medico_id = ?

WHERE (nf.medico_id = ? OR pp.medico_id IS NOT NULL)

-- valores por médico:
CASE WHEN nf.medico_id IS NOT NULL
     THEN nf.valor_bruto            -- nota single-médico legada
     ELSE pp.valor_bruto            -- nota multi-médico: usa a participação deste médico
END AS valor_bruto,
CASE WHEN nf.medico_id IS NOT NULL
     THEN nf.valor_liquido_medico
     ELSE pp.valor_bruto * 0.85
END AS valor_liquido_medico
```

### Frontend — `filtroMedico` via `participantes.some()`
A listagem `ProducoesPage` filtra por médico usando a lista de participantes, nunca `medicoId` (que é null para multi):
```typescript
.filter(p => !filtroMedico || p.participantes.some(pt => pt.medicoId === filtroMedico))
```

No CSV export, participantes de uma mesma produção são concatenados:
```typescript
participantes.map(pt => medicoNomeMap[pt.medicoId] ?? pt.medicoId).join(' / ')
```

### `NfseEmissaoPage` — `medicoId` null para multi-médico
```typescript
const medicoId = producao.participantes.length === 1
    ? producao.participantes[0].medicoId
    : null   // multi-médico: nota não vinculada a um único médico
```

---

## Parâmetros Fiscais — IBS/CBS Reforma Tributária 2027 (TASK-04.5)

### Convenção de alíquotas: fiscal service usa FRAÇÕES DECIMAIS
O `fiscal service` (`services/fiscal/`) armazena alíquotas como **frações decimais**:
- `0.0200` = 2% (ISS municipal)
- `0.0100` = 1% (alíquota base IBS/CBS fase-teste 2027)

O `faturamento service` usa **percentual inteiro com 4 casas**:
- `5.0000` = 5% (ISS)
- `1.5000` = 1,5% (IR)

**Crítico ao integrar os dois serviços** — nunca tratar os valores da mesma forma.

### Seleção de regime por competência — findFirst...LessThanEqual...Desc
Para selecionar o parâmetro fiscal vigente em uma competência específica:
```java
Optional<ParametroFiscal> findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
        String cnpjId, LocalDate dataReferencia);
```
- `dataReferencia` = `LocalDate.of(ano, mes, 1)` a partir de "YYYY-MM"
- Retorna o parâmetro mais recente com `vigencia_inicio <= dataReferencia`
- Um parâmetro de 2027-01-01 só é selecionado para competências >= 2027-01

### Alíquota efetiva IBS/CBS saúde
Calculada em `ParametroFiscalResponse.from()`:
```java
aliqIbsCbsEfetiva = aliqIbsCbs.multiply(BigDecimal.ONE.subtract(reducaoIbsCbsSaude))
```
Exemplo: 1% × (1 − 60%) = 0,4% efetivo — serviços de saúde têm redução de 60% (NBS 200029).

### Homologação como fluxo de aprovação contábil
Parâmetros criados via `POST /api/parametros-fiscais/ibs-cbs` começam com `homologado=false` (rascunho).
A contabilidade deve chamar `PUT /api/parametros-fiscais/{id}/homologar` antes do go-live.
O frontend exibe alerta de governança quando há parâmetros não homologados.

### flyway-maven-plugin no fiscal service
Configurado no `pom.xml` do `services/fiscal/` com `svc_fiscal / fiscal_dev / porta 5433`.
Sempre rodar `mvn process-resources` antes de `mvn-flyway.js` para copiar os SQLs para `target/classes/`.

---

## Motor Fiscal — Cenários e Invariantes (EPIC-05.2)

### Os 4 cenários do motor fiscal
Derivados dos flags `tomadorPj`, `indicadorRetencaoFederal`, `equiparacaoHospitalar`:

| Cenário | Condição | Retenções |
|---------|----------|-----------|
| A | PJ + retencaoFederal=true | IR/CSLL/PIS/COFINS retidos pelo tomador |
| B | PJ + retencaoFederal=false | Pin paga todos por guia própria |
| C | PF + equiparacaoHospitalar=true | Nota com tributos zerados |
| D | PF + equiparacaoHospitalar=false | IR na fonte pelo tomador PF |

**Invariante absoluta:** `valorLiquidoMedico = valorBruto − taxaPin` em QUALQUER cenário.
Tributos saem dos 15% da Pin, nunca do repasse do médico.

### Hierarquia de alíquotas (fallback)
```
1. parametros_fiscais (V2, EPIC-05.1) — per-tributo com competencia_inicio/fim
   ↓ se não encontrar:
2. parametro_fiscal (V1, TASK-04.5) — regime geral por vigencia_inicio
   ↓ se não encontrar:
3. Zeros (calcula, tributos = 0)
```

### jqwik — Property-based tests com Mockito
**Import correto:** `@BeforeProperty` está em `net.jqwik.api.lifecycle.BeforeProperty`, não em `net.jqwik.api`.

**Stubs lenient no setUp:** se um teste individual redefine um stub do `@BeforeEach`, Mockito strict mode lança `UnnecessaryStubbing`. Usar `Mockito.lenient().when(...)` no setUp para stubs que podem ser sobrepostos:
```java
org.mockito.Mockito.lenient()
    .when(repo.findFirst...(anyString(), any()))
    .thenReturn(Optional.of(padrao));
```

**Compatibilidade:** jqwik 1.8.3 + Spring Boot 3.2.5 (JUnit 5.10.x) — funciona via SPI sem configuração extra.
Maven: `<dependency><groupId>net.jqwik</groupId><artifactId>jqwik</artifactId><version>1.8.3</version><scope>test</scope></dependency>`

### ISS vs IBS/CBS na transição 2027
No cenário A (PJ com retenção), ISS permanece separado mesmo quando `ibsCbsAtivo=true`:
- `valorIss = aliqIss * valorBruto` (ISS municipal persiste na transição)
- `valorIr = aliqIbsCbsEfetiva * valorBruto` (IBS/CBS substitui IR/CSLL/PIS/COFINS)
- `valorCsll = valorPis = valorCofins = 0`

---

## Configuração Fiscal Backoffice — Padrões (EPIC-05.3)

### Tabela `parametros_fiscais` V2 — append-only com sobreposição validada
Alíquotas por tributo/competência são append-only: nunca atualizar registros existentes.
O controller faz `requireCnpj()` para POST (requer tenant), mas `listar()` retorna `[]` quando
não há tenant (gestao superuser) — evita 400 no RBAC test e nas telas cross-tenant.

### `not()` do Mockito não aceita matchers de enum — usar `argThat`
`not(eq(TipoTributo.ISS))` não compila; usar `argThat(t -> t != TipoTributo.ISS)`.
Quando o service faz short-circuit (retorna ao achar ISS faltando), o stub do `argThat`
nunca é invocado — marcar como `lenient()` para evitar `UnnecessaryStubbing`.

### Histórico de alterações por campo (audit trail leve)
Cada campo alterado em `RegraEquiparacao` gera uma linha em `historico_regras_equiparacao`
com `campo_alterado`, `valor_anterior`, `valor_novo`, `alterado_por`, `alterado_em`.
Padrão: compare via `!Objects.equals(antes, depois)` antes de salvar o histórico.

### Entidades sem `setId()` em testes — usar reflection
JPA entities com `@GeneratedValue` não expõem `setId()`. Para simular o mock do
`repo.save()` retornando um objeto com ID, usar reflection no `thenAnswer`:
```java
when(repo.save(any())).thenAnswer(inv -> {
    var r = inv.getArgument(0, MinhaEntidade.class);
    var f = MinhaEntidade.class.getDeclaredField("id");
    f.setAccessible(true); f.set(r, UUID.randomUUID()); return r;
});
```

### Alíquotas no fiscal service são frações decimais (não percentual)
`0.0200` = 2%. O frontend recebe `valorAliquota` (0.0200) e `valorAliquotaPct` (2.00).
Na tela, o usuário digita em % e o `createParametroFiscal` divide por 100 antes de enviar.
Ao exibir, usar `valorAliquota × 100` para mostrar em %.

### Verificação da próxima competência — retorna false quando sem tenant
`GET /api/fiscal/parametros/verificar-proxima-competencia` retorna
`{ proximaCompetenciaConfigurada: false }` quando o usuário não tem CNPJ no JWT
(gestao superuser). O frontend exibe o alerta amarelo nesse caso.

---

## Integração NFS-e — Padrões e Armadilhas (EPIC-05.4)

### Resilience4j 2.x — `registry.retry("name")` usa config DEFAULT, não a config nomeada
`RetryRegistry.of(Map.of("minha-config", config))` armazena como NAMED config.
`retryRegistry.retry("instancia")` cria com DEFAULT config (Resilience4j built-in), ignorando a config nomeada.
**Solução:** usar a sobrecarga de dois argumentos: `retryRegistry.retry("instancia", "minha-config")`.
Idem para CircuitBreaker: `cbRegistry.circuitBreaker("instancia", "minha-config")`.
O mapa de configs deve usar "default" como chave para ser usado automaticamente pelo `retry("name")`.

### Resilience4j 2.x — `enableExponentialBackoff()` não existe; usar `intervalFunction`
Em Resilience4j 2.x, o builder de `RetryConfig` não tem `enableExponentialBackoff()` nem `exponentialBackoffMultiplier()`.
**Solução:** usar `intervalFunction(IntervalFunction.ofExponentialBackoff(Duration, multiplier))`:
```java
import io.github.resilience4j.core.IntervalFunction;
RetryConfig config = RetryConfig.custom()
    .maxAttempts(3)
    .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2.0))
    .retryExceptions(IOException.class, HttpServerErrorException.class)
    .build();
```

### CircuitBreaker + Retry aninhados — CB vê um evento por chamada (não por tentativa)
```java
CircuitBreaker.decorateCheckedSupplier(cb,
    Retry.decorateCheckedSupplier(retry, () -> doEmitir(dados)));
```
O CB registra **um** evento por `decorated.get()` (sucesso ou falha final após retries).
Com `slidingWindowSize=5, minimumNumberOfCalls=5, failureRateThreshold=100%`: o CB abre após 5 chamadas todas falhando.
Nas chamadas seguintes, `CallNotPermittedException` é lançada sem executar nenhuma tentativa.

### `onStatus` com `RestClientException` não é capturada pelo Retry
`RestClient.retrieve().onStatus(pred, (req,res) -> { throw new RestClientException(...); })` —
`RestClientException` é a superclasse, mas não está na lista de `retryExceptions`.
**Regra:** não usar `onStatus` customizado quando retries são necessários para 5xx.
Sem `onStatus`, o `RestClient` lança `HttpServerErrorException` para 5xx automaticamente — que JÁ está no `retryExceptions`.

### ArgumentCaptor com mesmo objeto entre saves — capturar estado no momento da chamada
Quando o service chama `repo.save(entity)` duas vezes com o mesmo objeto (status muda entre saves),
o `ArgumentCaptor` captura a REFERÊNCIA, não o estado. Na hora de verificar, o objeto já foi mutado.
**Solução:** usar `thenAnswer` para capturar o estado imediatamente:
```java
List<StatusNota> statusNaSalva = new ArrayList<>();
when(repo.save(any())).thenAnswer(inv -> {
    statusNaSalva.add(inv.getArgument(0, MinhaEntidade.class).getStatus());
    return inv.getArgument(0);
});
// depois:
assertThat(statusNaSalva).containsExactly(StatusNota.PROCESSANDO, StatusNota.EMITIDA);
```

### Flyway: DROP TYPE falha quando coluna tem DEFAULT que referencia o enum
`ALTER COLUMN status TYPE varchar(40)` tem sucesso, mas `DROP TYPE status_enum` falha com:
`cannot drop type status_nota_enum because other objects depend on it — default value for column status`
**Solução:** remover o DEFAULT antes, converter, remover o tipo, restaurar DEFAULT:
```sql
ALTER TABLE t ALTER COLUMN status DROP DEFAULT;
ALTER TABLE t ALTER COLUMN status TYPE varchar(40) USING status::text;
DROP TYPE meu_enum;
ALTER TABLE t ALTER COLUMN status SET DEFAULT 'VALOR';
ALTER TABLE t ADD CONSTRAINT t_status_check CHECK (status IN (...));
```

### Spring Cloud Vault — `optional:vault://` sem bootstrap.yml (Config Data API)
```yaml
spring:
  config:
    import: "optional:vault://"
  cloud:
    vault:
      uri: ${VAULT_ADDR:http://localhost:8200}
      token: ${VAULT_TOKEN:root}
      kv:
        enabled: true
        application-name: pinsaude/fiscal  # path no Vault KV
      fail-fast: false  # não falha se Vault indisponível
```
Chaves no Vault em `pinsaude/fiscal` (ex: `nfse.api-token`) são injetadas como properties Spring.
`optional:` evita falha de startup quando Vault não está disponível (dev local).

### RabbitMQ DLQ — rejeição após 3 tentativas de consumer
Configurar no queue args `x-dead-letter-exchange` + `spring.rabbitmq.listener.simple.retry.*`:
```yaml
spring.rabbitmq.listener.simple:
  default-requeue-rejected: false
  retry:
    enabled: true
    max-attempts: 3
```
Após 3 falhas de consumer, a mensagem vai para a DLQ (não reencaminhada).
Este é o retry de **mensagem** (AMQP), diferente do retry de **HTTP** (Resilience4j).

### Outbox Pattern — save + publish na mesma transação `@Transactional`
```java
@Transactional
public void emitir(Request req) {
    var nota = notaRepo.save(buildNota(req));
    producer.enviar(new NfseMessage(nota.getId())); // dentro da mesma transação
}
```
Se o publish falhar (RabbitMQ down), a transação faz rollback e a nota NÃO é salva.
Se o save falhar, a mensagem NÃO é publicada. Consistência garantida sem saga.

---

## Listagem de Notas com Painel Lateral — Padrões (EPIC-05.6)

### Layout de painel lateral deslizante em React/Tailwind
Para implementar uma tabela + painel lateral que abrem ao clicar numa linha:
```tsx
{/* Container body — NUNCA usar overflow-auto aqui */}
<div className="flex-1 overflow-hidden flex">
  {/* Conteúdo principal — scroll vertical independente */}
  <div className="flex-1 overflow-auto p-5">
    ...table ou cards...
  </div>
  {/* Painel lateral — largura fixa, scroll interno próprio */}
  {selecionado && (
    <div className="w-96 bg-white border-l border-ds-border flex flex-col h-full">
      <div className="flex-1 overflow-y-auto">...detalhe...</div>
      <div className="p-4 border-t">...ações...</div>
    </div>
  )}
</div>
```
O `overflow-hidden` no container impede que o conteúdo principal vaze; cada filho controla seu próprio scroll.

### TypeScript: `as const` com objetos de formas diferentes
`[{ id: 'a', count: 1 }, { id: 'b', count: 2, alert: true }] as const` cria um tipo union onde
TypeScript não consegue narrowar `alert` (não existe em todos). Solução: mover o valor variant para
um campo presente em todos os objetos (ex: `badgeCls`):
```tsx
// ERRADO — alert não narrowa
tabs.map(tab => tab.alert && ...)

// CORRETO — campo normalizado presente em todos
[
  { id: 'todas',    label: 'Todas',  badgeCls: 'bg-ds-surface text-ds-light' },
  { id: 'excecoes', label: 'Fila',   badgeCls: 'bg-yellow-100 text-yellow-700' },
].map(tab => <span className={tab.badgeCls}>...</span>)
```

### Denormalização de nome do tomador em NFS-e
`notas_fiscais` armazena `tomador_nome VARCHAR(200)` para exibição em listagens sem join cross-service.
O nome é passado pelo frontend no momento da emissão via `EmitirNfseRequest.tomadorNome`.
Notas antigas (antes do EPIC-05.6) terão `tomadorNome = null` — o frontend exibe o UUID truncado nesse caso.

### Endpoints de cancelamento e rejeição com motivo
- `PUT /api/nfse/{id}/cancelar` — EMITIDA → CANCELADA; requer role `gestao|contabil|financeiro`
- `PUT /api/nfse/{id}/rejeitar` — AGUARDANDO_VALIDACAO → CANCELADA; requer role `gestao|contabil`
- Ambos persistem o motivo em `observacoes` com prefixo ("Cancelada: " / "Rejeitada: ")
- Sem coluna `motivo_cancelamento` separada — `observacoes` já supre para auditoria leve

---

## Processamento em Lote — Padrões (EPIC-05.7)

### Lock distribuído via banco — existsByCompetenciaAndStatus
Para garantir que apenas um lote por competência esteja EM_ANDAMENTO, verificar antes de criar:
```java
if (loteRepo.existsByCompetenciaAndStatus(competencia, "EM_ANDAMENTO")) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Lote já em andamento...");
}
```
Não usar UNIQUE constraint na tabela pois permite reprocessar a mesma competência (CONCLUIDO → novo lote).

### Contadores atômicos com native SQL + GREATEST()
Para evitar race condition em processamento paralelo (consumer RabbitMQ processa múltiplas notas em paralelo):
```java
@Modifying
@Query(value = """
    UPDATE fiscal.lotes_emissao
       SET emitidas = emitidas + 1,
           em_processamento = GREATEST(em_processamento - 1, 0)
     WHERE competencia = :comp AND status = 'EM_ANDAMENTO'
    """, nativeQuery = true)
int incrementarEmitida(@Param("comp") String competencia);
```
`GREATEST(em_processamento - 1, 0)` evita valores negativos sem lock de aplicação.
Chamar `loteRepo.incrementarEmitida()` / `incrementarFalha()` no final de `NfseService.processarEmissao()`.

### Finalização lazy do lote
O status do lote (CONCLUIDO/FALHA) é atualizado on-demand quando `getProgresso()` é chamado:
```java
if ("EM_ANDAMENTO".equals(lote.getStatus()) && lote.getTotal() > 0
        && lote.getEmitidas() + lote.getFalhas() >= lote.getTotal()) {
    lote.setStatus(lote.getFalhas() > 0 ? "FALHA" : "CONCLUIDO");
    lote.setConcluidoEm(OffsetDateTime.now());
    loteRepo.save(lote);
}
```
Evita complexidade de verificar conclusão após cada nota processada.

### Resetar ERRO para PENDENTE no início do lote
Notas que falharam em lotes anteriores devem ser resetadas antes de enfileirar:
```java
if (nota.getStatus() == StatusNota.ERRO) {
    nota.setStatus(StatusNota.PENDENTE);
    notaRepo.save(nota);
}
producer.enviar(new NfseEmissaoMessage(nota.getId()));
```
Garante que o consumer (`processarEmissao()`) aceite a nota (exige status == PENDENTE).

### Job agendado — YearMonth para competência anterior
```java
@Scheduled(cron = "0 0 2 1 * *")  // Dia 1 de cada mês às 02:00
public void executarEmissaoMensal() {
    String competencia = YearMonth.now().minusMonths(1).toString(); // "2026-05"
    // ...
}
```
`@EnableScheduling` já está em `FiscalApplication.java`.

### Percentual calculado no DTO — não armazenar no banco
```java
double pct = l.getTotal() > 0
    ? Math.min(100.0, (double)(l.getEmitidas() + l.getFalhas()) / l.getTotal() * 100)
    : 0.0;
```
`Math.min(100.0, ...)` evita exibir >100% em edge cases de race condition.

---

## Multi-empresa para Médicos — Padrões (EPIC-03.8)

### Vínculos médico-empresa — gerenciamento via endpoints dedicados
A tabela `vinculos_medico_empresa` é N:N. Ao editar dados pessoais de um médico (`PUT /api/medicos/{id}`),
os vínculos NÃO devem ser tocados — `MedicoService.atualizar()` não chama nenhum método de vínculo.
O gerenciamento de vínculos é feito exclusivamente pelos endpoints:
- `GET /api/medicos/{id}/vinculos` — lista com dados enriquecidos da empresa
- `POST /api/medicos/{id}/vinculos` — body `{ "empresaId": "uuid" }`
- `DELETE /api/medicos/{id}/vinculos/{empresaId}`

**Regra de negócio:** não é possível remover o único vínculo de um médico (422).

### TenantFilter — CNPJ deve ser propagado SEM strip de formatação
O claim `cnpj_id` do JWT contém o CNPJ com formatação (`XX.XXX.XXX/XXXX-XX`).
A coluna `cnpj` da tabela `empresas` também armazena o CNPJ formatado.
O RLS compara `cnpj = current_setting('app.current_tenant')` — portanto os valores devem bater.

**NUNCA** fazer `replaceAll("\\D", "")` no CNPJ dentro do `TenantFilter`:
```java
// CORRETO
String cnpj = jwtToken.getToken().getClaimAsString("cnpj_id");
return cnpj != null ? cnpj : "";

// ERRADO — quebra o RLS
return cnpj != null ? cnpj.replaceAll("\\D", "") : "";
```

### MedicoResponse — backward compat com empresaId
O campo `empresaId` (UUID do primeiro vínculo) é mantido para não quebrar clientes existentes.
O campo `empresas` (lista completa) é a forma correta de consumir os vínculos:
```java
UUID primeiraEmpresaId = empresas.isEmpty() ? null : empresas.get(0).empresaId();
```

### Mock de EmpresaRepository em testes do MedicoService
Sempre que um teste usa `@InjectMocks MedicoService`, o `EmpresaRepository` deve estar presente
como `@Mock` — mesmo que o teste não chame métodos de empresa diretamente:
```java
@Mock EmpresaRepository empresaRepo; // obrigatório — usado por toFullResponse()
```
Quando `vinculoRepo.findByIdMedicoId()` retorna lista vazia, o `empresaRepo.findAllById(emptyList)`
é chamado mas retorna lista vazia por padrão do Mockito (sem stub explícito necessário).

---

## Portal do Médico — Padrões e Armadilhas (EPIC-06.1)

### Serviço portal: leitura cross-schema sem JPA/Flyway
O `services/portal/` é um serviço Spring Boot leve (porta 8087 dev / 8187 prod) que agrega dados
de `onboarding.medicos`, `fiscal.notas_fiscais` e `faturamento.producoes` usando **JdbcTemplate**.
Não tem JPA, Hibernate, nem Flyway — não é dono de nenhum schema, apenas lê de outros.

Usar `spring-boot-starter-jdbc` em vez de `spring-boot-starter-data-jpa`:
```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
```

### Usuário svc_portal — cross-schema read-only
O `svc_portal` precisa de `USAGE` nos schemas e `SELECT` em todas as tabelas.
Para instâncias já existentes (init.sql não recria), executar como superuser:
```sql
CREATE USER svc_portal WITH PASSWORD 'portal_dev';
GRANT USAGE ON SCHEMA onboarding, fiscal, faturamento TO svc_portal;
GRANT SELECT ON ALL TABLES IN SCHEMA onboarding TO svc_portal;
GRANT SELECT ON ALL TABLES IN SCHEMA fiscal TO svc_portal;
GRANT SELECT ON ALL TABLES IN SCHEMA faturamento TO svc_portal;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding  GRANT SELECT ON TABLES TO svc_portal;
ALTER DEFAULT PRIVILEGES IN SCHEMA fiscal      GRANT SELECT ON TABLES TO svc_portal;
ALTER DEFAULT PRIVILEGES IN SCHEMA faturamento GRANT SELECT ON TABLES TO svc_portal;
```
`ALTER DEFAULT PRIVILEGES` cobre tabelas criadas no futuro; `GRANT SELECT ON ALL TABLES` cobre as já existentes.

### Identificação do médico via JWT email
O JWT do médico contém `email` mas **não** `medico_db_id` (não mapeado no Keycloak).
O portal resolve o médico via: `SELECT id FROM onboarding.medicos WHERE email = ?`
A coluna `email` é texto plano (não criptografada), lookup por email é seguro e eficiente.

Para o teste funcionar: o usuário `medico@pinsaude.com.br` no Keycloak deve ter um registro
correspondente em `onboarding.medicos` com o mesmo e-mail.

### RLS bypass no portal (sem TenantFilter)
O portal não usa TenantFilter. O médico JWT não tem `cnpj_id`, então `current_setting('app.current_tenant', TRUE)` retorna NULL.
`COALESCE(NULL, '') = ''` → TRUE → bypass do RLS em todos os schemas.
Isolamento de dados garante-se via filtro de aplicação: `WHERE medico_id = ?`.

### @WebMvcTest + @EnableMethodSecurity exige @Import(SecurityConfig.class)
`@WebMvcTest` não aplica o interceptor AOP do `@PreAuthorize` automaticamente.
Sem `@Import(SecurityConfig.class)`, testes que esperam 403 recebem 200 (controller executado sem guard):
```java
@WebMvcTest(PortalMedicoController.class)
@Import(SecurityConfig.class)  // obrigatório para @PreAuthorize funcionar no slice de teste
class PortalMedicoControllerTest { ... }
```

### UUID e OffsetDateTime com JdbcTemplate + PostgreSQL JDBC
- UUID: `rs.getObject("id", UUID.class)` — suportado nativo pelo driver PostgreSQL
- Timestamp: `rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC)` — converter Timestamp para OffsetDateTime
- Colunas nullable timestamp: verificar `ts != null` antes de converter

---

## Extrato Financeiro — Lançamentos Virtuais (EPIC-06.4)

### Extrato derivado de NFS-e quando Ledger não existe
Quando o serviço de Ledger (EPIC-08.2) ainda não existe, o extrato financeiro
pode ser gerado como **lançamentos virtuais** a partir das `fiscal.notas_fiscais`:

```
Cada NFS-e EMITIDA gera automaticamente:
  1. CREDITO  NFS_E     — valor_bruto da nota
  2. DEBITO   ISS       — valor_iss       (se > 0)
  3. DEBITO   IR        — valor_ir        (se > 0)
  4. DEBITO   CSLL      — valor_csll      (se > 0)
  5. DEBITO   PIS       — valor_pis       (se > 0)
  6. DEBITO   COFINS    — valor_cofins    (se > 0)
  7. DEBITO   TAXA_PIN  — taxa_pin (15%)
```

O saldo running é calculado do mais antigo para o mais recente, depois a lista
é revertida antes de retornar (mais recentes primeiro = leitura natural de extrato).

```java
// Saldo running acumulado — gerado antes do reverse:
saldo += nota.valorBrutoCentavos();  // crédito
saldo -= nota.valorIss();            // débito por tributo
// ... outros tributos ...
saldo -= nota.taxaPinCentavos();     // taxa Pin
lancamentos.add(new ExtratoLancamentoResponse(..., saldoApos: saldo, ...));
Collections.reverse(lancamentos);   // mais recentes primeiro
```

Quando o Ledger for criado (EPIC-08.2), substituir `getExtrato()` no PortalService
por uma consulta à tabela `ledger.lancamentos` — a interface do endpoint e do frontend
não mudam.

### Export PDF sem dependência externa
Usar `window.open()` + `window.print()` para PDF do extrato. A URL do logo deve
usar `window.location.origin` para funcionar no contexto da nova janela:
```typescript
const logoUrl = `${window.location.origin}/logo-pinsaude.png`
```
O logo em `/public/logo-pinsaude.png` é servido pelo Vite dev server e pelo build
de produção como arquivo estático — a URL absoluta com `origin` funciona em ambos.

### `KpiCard.sub` como ReactNode (padrão DashboardMedicoPage)
O `KpiCard` do DashboardMedicoPage aceita `sub: ReactNode` (não `string`) para
permitir links como "Ver extrato →". Importar `ReactNode` de `react`:
```tsx
import { ReactNode } from 'react'
// ...
sub: ReactNode  // NOT sub: string
```

---

## Notificações por E-mail — Fila RabbitMQ + Thymeleaf (EPIC-06.6)

### Arquitetura: fire-and-forget via fila email.envio
O envio de e-mail é 100% assíncrono. O serviço que origina o evento publica na fila
`email.envio` (durable, JSON) e retorna imediatamente — nunca aguarda o envio do e-mail:
```java
private void publicar(EmailEnvioMessage msg) {
    try {
        rabbitTemplate.convertAndSend("email.envio", msg);
    } catch (Exception e) {
        log.warn("Falha ao enfileirar — tipo={}: {}", msg.tipo(), e.getMessage());
        // silencioso: não propaga para o caller
    }
}
```
O consumer (`EmailEventConsumer` no onboarding) é assíncrono por natureza (`@RabbitListener`
roda em thread pool separado) — não precisa de `@Async`.

### Cross-service medicoId resolution (sem HTTP)
O fiscal não sabe o email do médico — só tem o UUID. O fiscal publica com `destinatario=null`
e `medicoId=<uuid>`. O onboarding consumer resolve localmente via `MedicoRepository`:
```java
@RabbitListener(queues = EmailRabbitConfig.EMAIL_QUEUE)
public void consumir(EmailEnvioMessage message) {
    EmailEnvioMessage resolved = resolverDestinatario(message);
    emailTemplateService.enviar(resolved);
}
// resolverDestinatario: se destinatario nulo, busca email por medicoId no MedicoRepository
```
Isso evita chamada HTTP cross-service e mantém o onboarding como dono dos dados do médico.

### EmailEnvioMessage — record compartilhado (duplicado por serviço)
```java
public record EmailEnvioMessage(
    String tipo,         // "NOTA_FISCAL_EMITIDA", "CONVITE_CADASTRO", etc.
    String destinatario, // email direto, ou null quando medicoId é usado
    String medicoId,     // UUID como string, null quando destinatario é direto
    String assunto,
    Map<String, Object> dados  // variáveis do template Thymeleaf
) {}
```
Cada serviço tem sua cópia do record (onboarding e fiscal). Sem módulo compartilhado.
A serialização JSON é consistente desde que os campos sejam idênticos.

### Tipos de notificação suportados
| tipo | Origem | Destinatário |
|------|--------|-------------|
| `CONVITE_CADASTRO` | onboarding (ConviteService) | email direto do convite |
| `DOCUMENTO_REPROVADO` | onboarding (NotificacaoService) | email do médico (direto) |
| `MEDICO_ATIVADO` | onboarding (NotificacaoService) | email do médico (direto) |
| `NOTA_FISCAL_EMITIDA` | fiscal (NfseService) | medicoId → resolve no consumer |
| `ALERTA_TETO_FISCAL` | fiscal (EmailNotificacaoProducer) | email direto do gestor |
| `REPASSE_EFETUADO` | repasse (futuro) | email direto do médico |

### Templates Thymeleaf — localização e convenção
Templates em `services/onboarding/src/main/resources/templates/email/<tipo>.html`.
Referenciados no TIPO_TEMPLATE map de `EmailTemplateService`:
```java
"NOTA_FISCAL_EMITIDA" → "email/nota-fiscal-emitida"   // sem .html
```
Design: table-based HTML com inline CSS (máxima compatibilidade de cliente de e-mail).
Paleta Pin Saúde: header `#02A9F7`, verde sucesso `#15803d`, alerta laranja `#b45309`.

### Fila declarada em ambos os serviços — idempotente
Tanto `onboarding/EmailRabbitConfig.java` quanto `fiscal/RabbitConfig.java` declaram
`email.envio` como Queue durable. O RabbitMQ ignora re-declaração com mesmos parâmetros.
Garante que a fila existe independente de qual serviço iniciar primeiro.

### Thymeleaf com SpringTemplateEngine — não processa views web
Adicionar `spring-boot-starter-thymeleaf` em um `@RestController` não afeta endpoints REST.
O Thymeleaf é usado apenas via `SpringTemplateEngine.process(template, ctx)` no `EmailTemplateService`.
NÃO remover nem adicionar `spring.thymeleaf.check-template: false` — templates existem e devem ser verificados.

### Nota: multi-médico sem e-mail individual
Produções com múltiplos médicos (`medicoId = null` na `NotaFiscal`) não disparam e-mail.
```java
public void notificarNotaEmitida(NotaFiscal nota) {
    if (nota.getMedicoId() == null) return; // multi-médico: sem e-mail individual
    ...
}
```

---

## API Upload de Extrato Bancário — Padrões (EPIC-07.2)

### Parsers sem dependência externa (OFX4J descartado)
Os parsers CSV e OFX foram implementados sem biblioteca externa (OFX4J descartado por risco de SSL corporativo).
O `OfxParser` detecta OFX 1.x SGML vs OFX 2.x XML e usa `javax.xml.parsers.DocumentBuilderFactory` para XML.
Para SGML, converte inline com regex para transformar `<TAG>valor\n` em `<TAG>valor</TAG>` antes de parsear.

### Interface `ExtratoBancarioParser` — seleção por banco + nome de arquivo
```java
boolean suporta(BancoEnum banco, String nomeArquivo);
```
`ExtratoService` injeta `List<ExtratoBancarioParser>` (todos os @Component) e usa `findFirst` filtrando por `suporta`.
Para `BancoEnum.OUTRO`, os parsers usam o nome do arquivo como fallback (ex: `nomeArquivo.contains("inter")`).
`OfxParser` suporta qualquer banco desde que o arquivo termine em `.ofx` ou `.qfx`.

### Detecção de duplicatas — escopo tenant
O check de duplicata usa `(nomeArquivo, periodoInicio, periodoFim, cnpjIdTenant)` — nunca cross-tenant.
Retorna HTTP 409 imediatamente sem processar o arquivo.

### Deduplicação interna de lançamentos — `identificadorExterno`
Para OFX: usa o campo `FITID` (único por transação bancária).
Para CSV: gera sintético `<BANCO>-<data>-<idx>-<valor>` — idempotente para reimportação do mesmo arquivo.
O check `existsByExtratoIdAndIdentificadorExterno` previne duplicatas no mesmo extrato.

### Ciclo de vida do extrato durante importação
1. Salva `ExtratoBancario` com `statusImportacao = PROCESSANDO`
2. Parseia o arquivo — em caso de erro: atualiza para `ERRO`, lança 400
3. Salva lançamentos com `statusConciliacao = PENDENTE`
4. Atualiza extrato para `OK` com `totalLancamentos = salvos`
5. Publica `MatchingMessage(extratoId, cnpjTenant)` na fila `conciliacao.matching`

### RabbitMQ — fila `conciliacao.matching`
Declarada em `RabbitConciliacaoConfig` como queue durable. Formato JSON via `Jackson2JsonMessageConverter`.
O producer captura exceções silenciosamente — falha de RabbitMQ não impede a resposta HTTP da importação.

### Encoding CSV — BOM e ISO-8859-1
Arquivos CSV de bancos brasileiros podem ter BOM UTF-8 (`EF BB BF`) ou encoding ISO-8859-1.
Ambos os parsers removem o BOM e detectam ISO-8859-1 procurando o character replacement `�` no resultado UTF-8.

### V14 migration — coluna `banco` adicionada pós-V13
A coluna `banco VARCHAR(20)` foi esquecida na V13 inicial e adicionada na V14.
Padrão documentado: ao esquecer coluna em uma migration já aplicada, criar nova migration (`ALTER TABLE ADD COLUMN`).
**Nunca editar** uma migration já aplicada — o checksum do Flyway mudaria e causaria falha de validação.

---

## Conciliação Bancária — Padrões e Armadilhas (EPIC-07.1)

### Schema em `faturamento` service — não em serviço separado
As tabelas de conciliação ficam no schema `faturamento` (migration V13), pois a conciliação
associa lançamentos bancários às `fiscal.notas_fiscais` do serviço fiscal. Manter no faturamento
evita dependência cross-service e simplifica queries de matching.

### Hierarquia de isolamento multi-tenancy
```
extratos_bancarios      → cnpj_id_tenant (coluna direta)
lancamentos_extrato     → subquery em extratos_bancarios por cnpj_id_tenant
conciliacoes            → subquery dupla: lancamentos_extrato → extratos_bancarios → cnpj_id_tenant
```
Padrão já estabelecido para tabelas filhas sem coluna de tenant direta (ver EPIC-03.2).

### `nota_id` em `conciliacoes` — FK lógica cross-service
A coluna `nota_id` referencia `fiscal.notas_fiscais(id)` mas **não tem FK explícita** no banco.
Motivo: FK cross-schema com schemas de serviços diferentes quebra independência de deploy.
O serviço de conciliação valida existência da nota via chamada HTTP antes de gravar.

### `score_match` e `score_confianca` — convenção 0-100
- `0` = nenhum candidato encontrado / conciliação manual sem score calculado
- `100` = match perfeito (valor, data e CNPJ idênticos)
- `score_match` fica em `lancamentos_extrato` (calculado durante matching automático)
- `score_confianca` fica em `conciliacoes` (herdado do `score_match` no momento da conciliação)
- Ambos têm `CHECK (BETWEEN 0 AND 100)` no banco

### `identificador_externo` em `lancamentos_extrato` — idempotência de reimportação
Código único do banco (ex: número do documento OFX). Índice parcial `WHERE identificador_externo IS NOT NULL`.
O serviço de upload deve verificar duplicata antes de inserir para evitar reimportação do mesmo arquivo.

### `status_importacao` no extrato — ciclo de vida
`PROCESSANDO` → `OK` (todos os lançamentos parseados) ou `ERRO` (falha no parse).
`total_lancamentos` é atualizado pelo serviço após processar todo o arquivo.

### RLS `WITH CHECK (true)` em todas as tabelas filhas
Seguindo o padrão estabelecido em EPIC-03.2: `WITH CHECK (true)` em todas as políticas para
permitir INSERT sem restrição de tenant, mantendo `USING` apenas para SELECT/UPDATE.

---

## Motor de Matching Automático — Padrões (EPIC-07.3)

### Matching contra `faturamento.producoes` (não contra `fiscal.notas_fiscais`)
O motor de matching correlaciona lançamentos bancários com **produções confirmadas/emitidas**
(`faturamento.producoes`), não diretamente com notas fiscais.
Motivo: `notas_fiscais` está no schema `fiscal` (serviço diferente); `svc_faturamento` só tem acesso
ao schema `faturamento`. A `producao.id` é armazenada em `conciliacoes.nota_id` (FK lógica, sem constraint explícita).

### Algoritmo de scoring — pontuação aditiva (max 100)
Três dimensões avaliadas por par (lançamento, produção):

| Dimensão | Pontuação | Condição |
|---|---|---|
| Valor exato | +40 | `|lancamento.valor - producao.valorBruto| == 0` |
| Valor tolerância ±1% | +20 | diferença percentual ≤ 1% |
| Valor fora de 1% | 0 (skip) | par descartado — não entra no max |
| CNPJ do tomador na descrição | +40 | dígitos CNPJ presentes nos dígitos da descrição |
| Nome/razão social na descrição | +20 | descrição contém nome em uppercase (exclusivo com CNPJ) |
| Data dentro de 7 dias | +20 | `|dataLancamento - firstDayCompetencia| ≤ 7 dias` |
| Data dentro de 30 dias | +10 | `|dataLancamento - firstDayCompetencia| ≤ 30 dias` |

Score ≥ 90 → auto-conciliação (`CONCILIADO` + cria `conciliacoes` com `AUTOMATICO`)
Score 50-89 → sugestão (`scoreMatch` atualizado, status continua `PENDENTE`)
Score < 50 → sem ação

### Pré-descriptografia para evitar N×M chamadas
O `MatchingService` descriptografa o CNPJ de **todos os candidatos uma vez**, antes do loop de scoring:
```java
Map<UUID, String> cnpjDigitosMap = pré_decriptografar(candidatas);
// Reduz chamadas CryptoService de N×M para M (número de produções candidatas)
```

### Tenant vazio (gestão) — guard no início do método
```java
if (cnpjTenant == null || cnpjTenant.isBlank()) return; // gestão sem CNPJ = sem matching
```
Evita busca de produções com `cnpjIdTenant = ''` (não existem no schema).

### Idempotência do consumer
Antes de criar cada conciliação, verifica `conciliacaoRepo.existsByLancamentoExtratoId(id)`.
O constraint `UNIQUE (lancamento_extrato_id)` na tabela garante unicidade no nível de banco.
Se o consumer reprocessar a mesma mensagem (retry RabbitMQ), lançamentos já CONCILIADOS são pulados.

### Um candidato por lançamento — remoção da pool
Após auto-conciliar um par, a produção é removida da lista `candidatas` para não ser associada
a um segundo lançamento no mesmo batch. Garantia de 1:1 por execução (sem duplicata intra-batch).

### `calcularScore` é `public` — testabilidade direta
O método `calcularScore(LancamentoExtrato, Producao, String cnpjDigitosPlain)` é `public` para
permitir testes unitários diretos da função de pontuação sem mock de repositórios.
O terceiro argumento é o CNPJ já descriptografado em dígitos — `null` quando o tomador não tem CNPJ.

### Consumer propaga exceção para DLQ
`MatchingAutomaticoJob.consumir` relança exceções do `MatchingService`. Com a config padrão do
RabbitMQ listener (`max-attempts: 3`, `default-requeue-rejected: false`), após 3 falhas a mensagem
vai para a DLQ sem loop infinito.

---

## Tela de Upload de Extrato Bancário — Padrões (EPIC-07.4)

### Gateway: rota `/api/conciliacao/**` em faturamento service
O `ConciliacaoController` vive no `faturamento` service (porta 8082). A rota no gateway
usa o prefixo `/api/conciliacao/**` → `http://localhost:8082`. Sem essa rota, todas as
chamadas da tela retornam 404 mesmo com o backend respondendo corretamente.

### Parsers client-side — somente para preview, não substituem o backend
O frontend implementa parsers em TypeScript (Inter CSV, BTG CSV, OFX SGML/XML) para exibir
prévia dos 10 primeiros lançamentos antes do upload. O parsing real e o armazenamento são
feitos pelo backend. Diferenças de interpretação entre os parsers client-side e backend são
aceitáveis — apenas o preview é afetado.

**Convenções dos parsers client-side:**
- Remover BOM UTF-8 (`text.charCodeAt(0) === 0xFEFF ? text.slice(1) : text`)
- Ler com `FileReader.readAsText(file, 'ISO-8859-1')` para compatibilidade com arquivos pt-BR
- Inter CSV: separador `;`, tipo "Entrada"/"Saída", valor `"1.500,00"` (remover pontos, substituir vírgula)
- BTG CSV: separador `,` com aspas, valor `1500.00` ou `-500.00` (sinal indica tipo)
- OFX: detectar XML vs SGML pelo presença de `<STMTTRN>...</STMTTRN>` com fechamento

### `DragEvent<HTMLDivElement>` — import nomeado de 'react'
Para tipar handlers de drag-and-drop sem `React.DragEvent` (que exige `import React`):
```tsx
import type { DragEvent, ElementType } from 'react'
// ...
const handleDrop = useCallback((e: DragEvent<HTMLDivElement>) => { ... }, [dep])
```

### Barra de progresso simulada sem polling
Para uploads sem server-sent events, simular progresso com `setInterval`:
```tsx
progressTimerRef.current = setInterval(() => {
  pct = Math.min(pct + 7, 82)   // nunca passa de 82% antes da resposta
  setProgresso(pct)
}, 350)
// Ao receber resposta: clearInterval → setProgresso(95) → setTimeout → setProgresso(100)
```
O timer é limpo no `clearInterval` do `try/catch` e também no cleanup do `useEffect` com `[]`.

### Detecção de duplicata no frontend — compara banco + periodoInicio + periodoFim
O alerta de possível duplicata é exibido quando o extrato já importado tem o mesmo
`banco`, `periodoInicio` e `periodoFim` detectados pelo parser client-side:
```tsx
const dup = extratos.find(e =>
  e.banco === bancoSel &&
  e.periodoInicio === pv.periodoInicio &&   // "YYYY-MM-DD"
  e.periodoFim === pv.periodoFim,
)
if (dup) setAlerta('Atenção: já existe um extrato importado...')
```
O backend ainda realiza a verificação formal e retorna 409 se detectar duplicata real.

### `ExtratoResponse.periodoInicio` / `periodoFim` — LocalDate serializado como "YYYY-MM-DD"
Spring Boot + jackson-datatype-jsr310 auto-configurado serializa `LocalDate` como string ISO
`"YYYY-MM-DD"` (não como array `[year, month, day]`). Tipos TypeScript: `periodoInicio: string`.

---

## Tela de Conciliação Assistida — Padrões (EPIC-07.5)

### Arquitetura da página — lista + painel lateral
A `ConciliacaoAssistidaPage` segue o mesmo padrão de layout do EPIC-05.6 (duas colunas com scroll independente):
```tsx
{/* Container body — overflow-hidden no pai */}
<div className="flex-1 overflow-hidden flex">
  {/* Lista de lançamentos — scroll próprio */}
  <div className="flex-1 overflow-auto">...</div>
  {/* Painel lateral — largura fixa, só aparece quando lançamento selecionado */}
  {selecionado && (
    <div className="w-80 xl:w-96 bg-white border-l ... flex flex-col h-full shrink-0">
      <DetalhePanel ... />
    </div>
  )}
</div>
```

### Candidatos — lazy load por lançamento com cache local
Candidatos de match são carregados apenas quando o usuário clica em um lançamento PENDENTE.
O estado `candidatesMap: Record<string, CandidatoMatchResponse[] | null>` guarda por `lancamentoId`.
`undefined` = ainda não carregado; `[]` = carregado e vazio; lista = carregado com candidatos.
O loading spinner usa `candidatesMap[id] === undefined` para detectar "ainda não tentou".

### Conciliação manual — modal com busca client-side
O modal "Buscar produção manualmente" carrega todas as producoes na primeira abertura (lazy, cached em `useState`).
Filtro via `.includes(ql)` nos campos `tomadorNome`, `competencia`, `formatBRL(valorBruto)`.
A seleção chama `conciliarLancamento(lancId, prodId, 'Conciliação manual')`.

### `recarregarLancamentos` — atualiza lista e painel sem perder seleção
Após qualquer mutação (conciliar, ignorar, desfazer), recarrega `listarLancamentos(extratoId)`
e atualiza `selecionado` para o item atual (preservando o painel aberto):
```typescript
const atualizado = data.find((l) => l.id === selecionado.id)
setSelecionado(atualizado ?? null)
```

### Desfazer lançamento IGNORADO — usa `desfazerConciliacao`
O endpoint `DELETE /api/conciliacao/lancamentos/{id}/conciliacao` (originalmente para desfazer conciliação)
é reutilizado para reativar lançamentos ignorados — o service faz um check mais permissivo para IGNORADO:
- Para CONCILIADO: remove a `Conciliacao` do banco + volta para PENDENTE
- Para IGNORADO: não tem `Conciliacao` no banco, apenas volta o status para PENDENTE

**Atenção:** ao implementar o service, usar `@Transactional` e verificar status antes de deletar.

### `ExtratoService` — injeção de `ConciliacaoRepository` + `ProducaoRepository`
O construtor do `ExtratoService` ganhou dois novos parâmetros. Em testes que usam
`@InjectMocks ExtratoService`, adicionar os respectivos `@Mock`:
```java
@Mock ConciliacaoRepository conciliacaoRepo;
@Mock ProducaoRepository producaoRepo;
```

### `MatchingService.getSugestoes` — usa `ExtratoBancarioRepository`
Para resolver o tenant do lançamento, `getSugestoes(lancamentoId)` agora injeta
`ExtratoBancarioRepository` e faz lookup `lancamento.extratoId → extrato.cnpjIdTenant`.
Em testes do `MatchingService`, adicionar `@Mock ExtratoBancarioRepository extratoRepo`.

### `LancamentoExtratoResponse` — campo `ConciliacaoResumo` nullable
O campo `conciliacao: ConciliacaoResumo | null` foi adicionado ao record.
O factory `from(LancamentoExtrato l)` (sem conciliacao) delega para `from(l, null, null, 0L, null)`.
O `listarLancamentos` no service faz dois batch loads (conciliações + producoes) para preencher o campo.

### `ConciliacaoRepository` — métodos adicionais necessários para EPIC-07.5
```java
Optional<Conciliacao> findByLancamentoExtratoId(UUID lancamentoExtratoId);
List<Conciliacao> findByLancamentoExtratoIdIn(List<UUID> lancamentoIds);

@Transactional
void deleteByLancamentoExtratoId(UUID lancamentoExtratoId);
```

### Sidebar — dois itens de conciliação
```
Upload Extrato  → /conciliacao/upload     (ícone Upload)
Conciliação     → /conciliacao/assistida  (ícone ArrowLeftRight)
```
A rota `/conciliacao` também aponta para `ConciliacaoAssistidaPage` (redirect implícito por ordem de rota).

---

## Outbox Pattern — Race Condition com RabbitMQ (EPIC-07.6 / fiscal NfseService)

### Sintoma
Consumer recebe mensagem e lança `IllegalArgumentException: Nota não encontrada: <uuid>`.
A nota existe no banco, mas o consumer a busca antes da transação do producer ter commitado.

### Causa
Publicar mensagem RabbitMQ dentro de um método `@Transactional` antes do commit:
```java
@Transactional
public void emitir(Request req) {
    var nota = notaRepo.save(buildNota(req));
    producer.enviar(new NfseEmissaoMessage(nota.getId())); // ERRADO — tx ainda aberta
}
```
O consumer (em outra thread/processo) recebe a mensagem e faz `notaRepo.findById(id)` — mas a
transação ainda não foi commitada, então o SELECT retorna vazio.

### Solução — `TransactionSynchronizationManager.afterCommit()`
```java
@Transactional
public void emitir(Request req) {
    var nota = notaRepo.save(buildNota(req));
    final UUID notaId = nota.getId();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            producer.enviar(new NfseEmissaoMessage(notaId));
        }
    });
}
```
O `afterCommit()` só executa após o commit bem-sucedido da transação. Se a transação fizer rollback,
a mensagem não é publicada. Imports: `org.springframework.transaction.support.TransactionSynchronization`
e `TransactionSynchronizationManager`.

### Também se aplica a
Qualquer método `@Transactional` que publica eventos (RabbitMQ, Kafka, webhooks) baseados em
entidades recém-salvas. Padrão obrigatório no NfseService: `emitir()`, `aprovar()`, `reprocessarNota()`.

---

## Posição de Caixa — Padrões (EPIC-07.6)

### `PosicaoCaixaService` — queries analíticas com JdbcTemplate
Para queries de agregação cross-table sem JPA complexo, usar `JdbcTemplate` diretamente:
```java
@Service
public class PosicaoCaixaService {
    private final JdbcTemplate jdbc;
    // ...
    private long calcularAReceber() {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(p.valor_bruto), 0)
            FROM faturamento.producoes p
            WHERE p.status = 'EMITIDA'
              AND NOT EXISTS (
                  SELECT 1 FROM faturamento.conciliacoes c WHERE c.nota_id = p.id
              )
            """, Long.class);
    }
}
```

### `nota_id` em `conciliacoes` armazena `producoes.id`
A coluna `faturamento.conciliacoes.nota_id` referencia `faturamento.producoes(id)`, NÃO `fiscal.notas_fiscais(id)`.
O nome é historicamente confuso — FK lógica sem constraint explícita (cross-service).

### Gráfico de barras CSS sem biblioteca
Implementar gráfico de barras como `flex divs` com altura percentual. Tooltip via `group-hover:opacity-100`:
```tsx
<div className="flex items-end gap-1.5 h-40 px-1">
  {data.map((d) => {
    const pct = Math.max((d.valor / max) * 100, 2)
    return (
      <div key={d.semanaKey} className="flex-1 flex flex-col items-center gap-1 group relative">
        {/* Tooltip */}
        <div className="absolute bottom-full mb-2 ... opacity-0 group-hover:opacity-100">
          {formatBRL(d.valor)}
        </div>
        {/* Barra */}
        <div className="w-full flex-1 flex items-end">
          <div className="w-full bg-primary rounded-t" style={{ height: `${pct}%` }} />
        </div>
        <span className="text-[9px] text-ds-light">{d.semanaLabel}</span>
      </div>
    )
  })}
</div>
```
`Math.max(pct, 2)` garante que barras com valor muito pequeno ainda apareçam visivelmente.

### Gráfico semanal — `DATE_TRUNC('week', ...)` retorna segunda-feira (ISO)
PostgreSQL `DATE_TRUNC('week', data)` usa semana ISO (segunda = início).
Usar `TO_CHAR(DATE_TRUNC('week', data), 'IYYY-"W"IW')` como key e `'DD/MM'` como label.

### `repassadoNoMes = 0L` até EPIC-09
EPIC-09 (repasses) não implementado. Hardcoded: `long repassado = 0L; // EPIC-09 pendente`.
Quando EPIC-09 for implementado, substituir por query em `repasse.repasses WHERE status = 'LIQUIDADO'
AND date_trunc('month', data_liquidacao) = date_trunc('month', CURRENT_DATE)`.

### Destaque de dias em aberto — convensão de cores
```typescript
function diasEmAbertoClass(dias: number): string {
  if (dias > 30) return 'text-red-600 font-bold'    // alerta crítico
  if (dias > 15) return 'text-yellow-600 font-semibold' // atenção
  return 'text-ds-mid'                               // normal
}
// Em >30 dias: adicionar AlertTriangle size={12} inline
```

### Mock NFS-e — `@ConditionalOnProperty` + `@Primary`
Para testar o fluxo completo sem integrador real:
```java
@Primary
@Component
@ConditionalOnProperty(name = "nfse.mock.enabled", havingValue = "true")
public class MockEmissaoNfseAdapter implements EmissaoNfsePort {
    @Override
    public ResultadoEmissao emitir(DadosEmissaoNfse dados) {
        return ResultadoEmissao.sucesso("MOCK-" + System.currentTimeMillis(), "PROTOCOLO-MOCK");
    }
}
```
Ativar em `application.yml` com `nfse.mock.enabled: true`. `@Primary` garante que sobrepõe o adapter real.

---

## Ledger — Schema de Partidas Dobradas (EPIC-08.1)

O serviço `services/ledger/` (porta 8083) mantém o livro-razão financeiro com **partidas
dobradas** (double-entry). Três tabelas no schema `ledger`: `contas_ledger` (plano de contas,
catálogo compartilhado sem RLS), `lancamentos_ledger` (cabeçalho imutável, RLS por tenant) e
`partidas_ledger` (débitos/créditos, RLS via subquery no lançamento pai).

### Constraint de equilíbrio — CONSTRAINT TRIGGER DEFERRABLE INITIALLY DEFERRED
A validação `SUM(DEBITO) = SUM(CREDITO)` **não pode** ser um trigger `AFTER INSERT` normal:
ele dispararia após a primeira partida (quando débito≠crédito ainda) e bloquearia a construção
do lançamento. A solução é uma **constraint trigger diferida**, validada apenas no `COMMIT`:
```sql
CREATE CONSTRAINT TRIGGER trg_equilibrio_partidas
    AFTER INSERT OR UPDATE OR DELETE ON ledger.partidas_ledger
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger.fn_verifica_equilibrio();
```
**Consequência para o app:** o lançamento e TODAS as suas partidas devem ser inseridos na
**mesma transação**. Inserir partidas em transações separadas (autocommit) falha na primeira,
pois cada COMMIT valida o equilíbrio. A função usa `SUM(...) FILTER (WHERE tipo='DEBITO')`.

### Imutabilidade — trigger BEFORE UPDATE OR DELETE (append-only)
`lancamentos_ledger` e `partidas_ledger` são append-only. Um trigger `BEFORE UPDATE OR DELETE`
levanta exceção (`ERRCODE = 'restrict_violation'`). Correções são feitas via novo lançamento de
`AJUSTE`, nunca alterando o histórico.

**Armadilha de limpeza:** como o `DELETE` é bloqueado pelo trigger, para limpar dados de teste
no dev use `TRUNCATE ledger.partidas_ledger, ledger.lancamentos_ledger;` — o `TRUNCATE` dispara
apenas triggers `BEFORE TRUNCATE` (que não existem aqui), **não** o `BEFORE DELETE`. `TRUNCATE`
também ignora RLS. `DELETE` normal jamais funcionará nessas tabelas.

### Idempotência — correlation_id NOT NULL UNIQUE
Cada lançamento tem `correlation_id VARCHAR(120) NOT NULL UNIQUE` (ex.: `"NOTA:<uuid-nfse>"`).
Reprocessar o mesmo evento (retry de fila, replay) viola a constraint única e não duplica.
Escolhido `NOT NULL UNIQUE` (não índice parcial) para garantir idempotência em 100% dos lançamentos.

### gen_random_uuid() é nativo no PostgreSQL 13+
O ledger **não** precisa de `CREATE EXTENSION pgcrypto`/`uuid-ossp` (diferente do faturamento/
onboarding, que usam pgcrypto para criptografia). `gen_random_uuid()` é função core do PG 13+.
Por isso `svc_ledger` **não** recebe `GRANT CREATE ON DATABASE` no `init.sql` — não cria extensões.

### flyway-maven-plugin no ledger
Adicionado ao `pom.xml` do `services/ledger/` com `svc_ledger / ledger_dev / porta 5433`.
Rodar sempre `mvn process-resources -pl :pinsaude-ledger` antes de `mvn-flyway.js` para copiar os
SQLs para `target/classes/` (o plugin lê de `classpath:db/migration`).

### Valores em centavos e enums no schema
`valor_centavos BIGINT CHECK (> 0)` — o sinal contábil vem do enum `tipo_partida_enum`
(DEBITO/CREDITO), nunca do sinal do número. Enums: `tipo_conta_enum`
(ATIVO/PASSIVO/RECEITA/DESPESA/INTERMEDIARIO), `tipo_origem_enum`
(NOTA/CONCILIACAO/REPASSE/AJUSTE), `tipo_partida_enum` (DEBITO/CREDITO).

**Nota (EPIC-08.2):** os enums nativos foram convertidos para VARCHAR+CHECK na `V2` — ver abaixo.

---

## Ledger — API REST e Consultas (EPIC-08.2)

Endpoints em `services/ledger/` (porta 8083): `GET /api/ledger/lancamentos` (paginado, filtros
médico/tipo_origem/datas), `GET /api/ledger/lancamentos/{id}` (detalhe com partidas),
`GET /api/ledger/saldo/{medicoId}`, `GET /api/ledger/extrato/{medicoId}` e
`POST /api/ledger/lancamentos` (criação — só service token).

### Enums nativos PG → VARCHAR+CHECK quando a API filtra por eles (V2)
A `V1` criou enums nativos (`tipo_origem_enum` etc.). A API filtra lançamentos por `tipo_origem`
e o Hibernate 6 envia enums como `character varying` no WHERE → `operator does not exist: enum = varchar`.
A `V2__convert_enums_to_varchar.sql` converte as 3 colunas para `VARCHAR(n) + CHECK` e dá `DROP TYPE`
nos enums. Mesmo precedente do fiscal (`V6`). As triggers comparam com literais (`tipo = 'DEBITO'`),
que continuam válidos com varchar — nenhuma trigger muda. Com colunas varchar, a camada JPA fica
limpa: `@Enumerated(EnumType.STRING)` sem `@ColumnTransformer`, filtros JPQL diretos e `Pageable+Sort`
por propriedade da entidade (sem native query com CAST).

### Ordem de `@Valid` (400) vs `@PreAuthorize` (403) em POST
Na resolução do `@RequestBody`, o `@Valid` dispara `MethodArgumentNotValidException` (400) **antes**
do interceptor de método `@PreAuthorize` (403). Logo, um POST com corpo inválido de um usuário sem
permissão retorna **400, não 403**. Para testar autorização de POST, envie um **corpo válido** — aí o
único gate é o `@PreAuthorize`.

### Service token — POST interno protegido por `ROLE_service`
`POST /api/ledger/lancamentos` exige `hasRole('service')`; leituras exigem `financeiro/gestao/contabil`.
Tokens de usuário final (medico/operacao/financeiro/gestao) **não** têm `ROLE_service` → 403.
O realm role `service` foi declarado em `realm-export.json` (para service accounts / client credentials).

### Saldo/extrato do médico = posição da conta de repasse (2.1.02), não a soma global
Num ledger de partidas dobradas todo lançamento é balanceado, então
`SUM(créditos) − SUM(débitos)` sobre **todas** as partidas do médico é sempre 0 (inútil). O saldo do
médico é a **posição da conta "Repasses a Médicos a Pagar" (2.1.02)**: quanto a Pin ainda deve a ele.
`SaldoCalculator.CONTA_REPASSE_MEDICO = "2.1.02"`. O extrato acumula o saldo running a partir do
efeito líquido de cada lançamento nessa conta, em ordem cronológica (`data_lancamento`, `created_at`).
O extrato bruto usa constructor expression JPQL com `SUM(CASE WHEN tipo=:credito ...)` + `GROUP BY`.

### Centavos internamente, R$ nas respostas
Armazenamento e cálculo em centavos (long/BIGINT). As respostas convertem para reais com 2 casas:
`Money.reais(centavos) = BigDecimal.valueOf(centavos, 2)`. Requests de criação recebem `valorCentavos`.

### Cache de saldo por médico
`SaldoCalculator.saldoCentavos` é `@Cacheable("ledgerSaldo", key=medicoId)`; `LancamentoService.criar`
chama `invalidarSaldo(medicoId)` (`@CacheEvict`) após persistir. `@EnableCaching` no `LedgerApplication`
(ConcurrentMapCacheManager default). A evicção cross-bean funciona pois service → calculator são beans distintos (proxy AOP).

### Idempotência e equilíbrio na criação
`criar()` valida o equilíbrio em Java (`SUM(débitos)=SUM(créditos)` e total>0) → **422** antes de tocar
o banco (a constraint diferida do banco é backstop). Idempotência: se `correlation_id` já existe,
retorna o lançamento existente sem duplicar.

### Multi-tenancy replicado do faturamento
O ledger ganhou `TenantContext/TenantFilter/TenantAwareDataSource/TenantDataSourcePostProcessor`
(idênticos ao faturamento) + `TenantFilter` registrado no `SecurityConfig` via `addFilterAfter(...,
BearerTokenAuthenticationFilter.class)`. Nos testes Testcontainers, o usuário `test` (superuser)
bypassa FORCE RLS, então os dados ficam visíveis independentemente do tenant propagado.

---

## Ledger — Lançamentos via Eventos RabbitMQ (EPIC-08.3)

O ledger consome 4 filas e gera lançamentos automáticos, reusando `LancamentoService.criar`
(equilíbrio validado + idempotência por `correlation_id`):
`ledger.nota.emitida`, `ledger.recebimento.conciliado`, `ledger.repasse.efetuado`, `ledger.ajuste.manual`.

### Mapeamento evento → partidas (sempre balanceado)
- **NotaEmitida** → DR "Honorários a Receber" (bruto) · CR "Repasses a Médicos a Pagar" (85% do médico)
  · CR "Receita de Honorários" (margem da Pin = bruto − líquido − retenções) · CR uma retenção por imposto.
  Credita 2.1.02 → o saldo do médico (EPIC-08.2) reflete o que a Pin passa a dever.
- **RecebimentoConciliado** → DR "Caixa e Bancos" · CR "Honorários a Receber" (baixa do recebível).
- **RepasseEfetuado** → DR "Repasses a Médicos a Pagar" · CR "Caixa e Bancos" (reduz o saldo do médico).
- **AjusteManual** → exige **autorização dupla** (dois aprovadores distintos e não nulos); partidas
  informadas no evento. Sem a dupla autorização, o consumer lança exceção → DLQ.

A conta `1.1.02 Caixa e Bancos` foi adicionada na `V3` (necessária para recebimento/repasse).

### Idempotência — correlation_id determinístico
Cada evento vira um lançamento com `correlation_id` fixo: `"NOTA:<id>"`, `"CONCILIACAO:<id>"`,
`"REPASSE:<id>"`, `"AJUSTE:<id>"`. Reprocessar o mesmo evento não duplica (índice único +
`LancamentoService.criar` devolve o existente). É o "outbox / idempotent consumer": o `@Transactional`
no listener liga a persistência ao processamento da mensagem; uma redelivery pós-commit é neutralizada
pela idempotência.

### DLQ com retry aplicado a factory customizado
Filas principais têm `x-dead-letter-exchange` → `ledger.dlx` → `ledger.dlq` (uma DLQ única, routing key =
nome da fila). O retry (`max-attempts=3`, `default-requeue-rejected=false`) vem do `application.yml`. Como
há um `SimpleRabbitListenerContainerFactory` **customizado** (para o converter JSON), é preciso aplicar o
yml a ele com `SimpleRabbitListenerContainerFactoryConfigurer.configure(factory, cf)` — senão o retry/DLQ
não são aplicados. Após 3 tentativas, a mensagem é rejeitada e roteada à DLQ.

### Jackson converter com TypePrecedence.INFERRED (eventos cross-service)
Produtores em OUTROS serviços (fiscal, faturamento, repasse) publicam com o header `__TypeId__` da
classe DELES. Para o ledger desserializar no contrato local, o `Jackson2JsonMessageConverter` usa
`DefaultJackson2JavaTypeMapper` com `TypePrecedence.INFERRED` (usa o tipo do parâmetro do
`@RabbitListener`, ignorando o header) + `setTrustedPackages("*")`.

### Consumers rodam fora do request HTTP → sem TenantFilter
Threads do listener não passam pelo `TenantFilter` (servlet filter). `TenantContext` fica nulo →
`app.current_tenant` vazio → RLS bypass. O `cnpj_id_tenant` do lançamento vem do **payload do evento**,
não do JWT. Padrão idêntico ao portal.

### Testcontainers RabbitMQ + PostgreSQL
`LedgerEventIntegrationTest` sobe `PostgreSQLContainer` + `RabbitMQContainer` (ambos `@ServiceConnection`).
Publica eventos com `RabbitTemplate.convertAndSend`, aguarda o processamento assíncrono por polling e
valida os lançamentos. Para a DLQ, o profile de teste reduz `retry.initial-interval` (100ms, multiplier
1.0) e consome de `ledger.dlq` com `rabbitTemplate.receive(DLQ, timeout)`.

---

## Ledger — Tela de Extrato + Ajuste com Dupla Aprovação (EPIC-08.4)

Tela `/financeiro/ledger` (`LedgerExtratoPage`, perfis financeiro/gestao/contabil): extrato
contábil por médico (débito/crédito/saldo running), links de origem, ajuste manual com dupla
aprovação e export CSV/PDF.

### Ajuste manual com dupla aprovação (backend)
Tabela `ledger.ajustes_manuais` (V4) é **mutável** (workflow) — diferente das tabelas do razão,
que são append-only. Fluxo: o solicitante cria um ajuste `PENDENTE`; um **segundo usuário, com id
E perfil diferentes**, aprova — só então o lançamento imutável é gerado (via `LancamentoService.criar`,
reuso). Regras em `AjusteManualService.aprovar`: `aprovadorId != solicitanteId` (422) e
`aprovadorPerfil != solicitantePerfil` (422); segunda decisão do mesmo ajuste → 409. O perfil vem de
`SecurityUtils.currentPerfil()` (primeiro entre financeiro/gestao/contabil) e o usuário de
`currentUserId()` (subject do JWT). O lançamento gerado usa `tipoOrigem=AJUSTE`, `origemId=ajusteId`,
`correlationId="AJUSTE:<id>"` → aparece imediatamente no extrato.

### Extrato reusa o endpoint do médico (08.2) — valores em REAIS no JSON
A tela consome `GET /api/ledger/extrato/{medicoId}` (posição da conta de repasse). Os campos
`valor` e `saldoApos` já vêm em **reais** (o backend converte de centavos com `Money.reais`,
`BigDecimal.valueOf(centavos, 2)`), então o frontend **não divide por 100** — formata direto com
`toLocaleString('pt-BR', {style:'currency'})`. As colunas Débito/Crédito derivam do **sinal de
`valor`** (negativo = débito, positivo = crédito). O `origemId` (adicionado ao `ExtratoItemResponse`
nesta task) alimenta os links da coluna Origem: NOTA→/notas, CONCILIACAO→/conciliacao/assistida,
REPASSE→/repasses (AJUSTE não tem link).

### Teste de aprovação — simular usuários distintos no MockMvc
Para exercitar a dupla aprovação, o `jwt()` post-processor customiza o token:
`jwt().jwt(j -> j.subject("user-A").claim("cnpj_id", TENANT)).authorities(...)`. Assim dá para
testar solicitante `user-A/financeiro` vs aprovadores `user-B/financeiro` (422, mesmo perfil) e
`user-C/gestao` (aprova). O teste desabilita os consumers com
`spring.rabbitmq.listener.simple.auto-startup=false` (não precisa de broker).

---

## Faturamento por Grupo — Cadastros do Tomador (EPIC-13.1)

### Três novas entidades no schema `faturamento` (migration V17)

- **`tomador_grupos_faturamento`**: um grupo = uma NFS-e. Campos: `nome`, `descricao_nota` (template com `{competencia}`), `servico_lc116_id` (FK fiscal), `ordem`, `ativo`. RLS via subquery em `tomadores.cnpj_id_tenant`.
- **`tomador_modalidades`**: tabela de preços única por tomador. Campos: `turno` (DIURNO/NOTURNO), `horario` (ex: "19:00 as 07:00"), `horas` NUMERIC(6,2), `valor_centavos`, `deslocamento_centavos`. RLS idêntico.
- **`tomador_servicos_operacionais`**: setores operacionais (Emergência Cardiológica, UTI-URCT…) vinculados a um grupo via `grupo_id`. Não confundir com `tomador_servicos` (catálogo LC116, V16).

### Endpoints aninhados em `/api/tomadores/{id}/...`

- `GET|POST|PUT|DELETE /grupos` — grupos de faturamento (com lista de setores no GET)
- `GET|POST|PUT|DELETE /modalidades` — tabela de preços
- `GET|POST|PUT|DELETE /servicos-operacionais` — setores operacionais

Leitura: roles `operacao|gestao|financeiro|contabil|medico`. Escrita: roles `operacao|gestao`.

### Padrão de teste com `@GeneratedValue` sem `setId()`

Entidades com `@GeneratedValue(strategy = GenerationType.UUID)` não expõem `setId()`. Para setar IDs em fixtures de teste, usar reflection:
```java
var f = MinhaEntidade.class.getDeclaredField("id");
f.setAccessible(true); f.set(obj, UUID.randomUUID());
```

### `listarGrupos` inclui setores aninhados — batch evita N+1

O método carrega grupos, depois em batch `servicoRepo.findAllById(ids distintos)` + um `findByGrupoIdOrderByNomeAsc` por grupo. Aceitável dado que grupos por tomador são poucos (< 10); para volumes maiores, refatorar para query única.

### `TomadorGrupoFaturamentoResponse.servicosOperacionais`

O factory method tem duas sobrecargas: `from(g, servico)` (sem setores, para POST/PUT) e `from(g, servico, setores)` (com setores, para GET lista). Evita carregar setores em mutations.

### Mocks obrigatórios em `TomadorServiceTest`

`TomadorService` tem agora 10 dependências via construtor. Qualquer teste com `@InjectMocks TomadorService` deve declarar `@Mock` para:
`TomadorGrupoFaturamentoRepository`, `TomadorModalidadeRepository`, `TomadorServicoOperacionalRepository`.
Sem esses mocks, o `@InjectMocks` falha silenciosamente injetando `null`.

---

## Frequência Médica — Padrões e Armadilhas (EPIC-13.3)

### Schema `frequencias_medicas` + `frequencia_itens` (migration V18)

Uma **frequência médica** é o documento oficial de ponto por médico+setor+competência.
Unicidade: `UNIQUE (medico_id, servico_operacional_id, competencia)` — uma folha por médico, setor e mês.
RLS: `frequencias_medicas` tem `cnpj_id_tenant` diretamente (WITH CHECK true); `frequencia_itens` via subquery por `frequencia_id`.

### Snapshot de preço obrigatório nos itens

`frequencia_itens` armazena `valor_unitario_centavos` e `deslocamento_centavos` como **snapshot** da modalidade no momento do lançamento:
```java
item.setValorUnitarioCentavos(modalidade.getValorCentavos());
item.setDeslocamentoCentavos(modalidade.getDeslocamentoCentavos());
```
Isso garante que mudanças futuras na tabela de preços não retroagem em itens já lançados. O `totalItemCentavos` do response = `valorUnitario + deslocamento`.

### Ciclo de vida de status — guard de FATURADA

Status possíveis: `RASCUNHO → PDF_GERADO → AGUARDANDO_ASSINATURA → ASSINADA_RECEBIDA → ENVIADA_TOMADOR → FATURADA`

Qualquer mutação (adicionar/editar/remover item) em frequência `FATURADA` lança **422**:
```java
if ("FATURADA".equals(f.getStatus())) {
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não é possível modificar frequência já faturada");
}
```

### Batch load em `listar()` — evitar N+1

O service `listar()` usa discriminação por filtro primário (medicoId, tomadorId, setorId ou sem filtro), depois filtra em-stream com predicados independentes. O enriquecimento usa batch loads:
1. `setorRepo.findAllById(setorIds distintos)` — um SELECT para todos os setores
2. `itemRepo.findAll()` filtrado in-stream por `freqIds` (aceitável para volumes baixos por tenant; refatorar para `findByFrequenciaIdIn(freqIds)` quando volumes crescerem)
3. `modalidadeRepo.findAllById(modalidadeIds distintos)` — um SELECT para todas as modalidades dos itens

### `FrequenciaItemResponse.from()` — modalidade nullable

O factory aceita `modalidade = null` para tolerância a orfa. Exibe `null` nos campos de nome/turno/horário quando a modalidade não for encontrada no batch — nunca lança NPE.

### Mocks obrigatórios em `FrequenciaServiceTest`

`FrequenciaService` tem 4 dependências: `FrequenciaMedicaRepository`, `FrequenciaItemRepository`, `TomadorServicoOperacionalRepository`, `TomadorModalidadeRepository`. Sempre declarar os 4 `@Mock` ao usar `@InjectMocks FrequenciaService`.

### `SecurityUtils.currentCnpjTenant()` no faturamento = dígitos limpos

O `TenantFilter` do faturamento faz `replaceAll("\\D", "")` no claim `cnpj_id`. O `cnpj_id_tenant` gravado em `frequencias_medicas` usa dígitos apenas (`VARCHAR(20)`). Nunca armazenar CNPJ com formatação neste serviço.

---

## PDF Formulário Oficial — Geração Client-Side (EPIC-13.5)

### Geração de PDF de formulário governamental via `window.open()` + HTML template

Para formulários oficiais (ex.: Relatório de Frequência Médica Individual — Governo do Estado de PE / SES-PE), a abordagem é gerar um HTML print-friendly completo no frontend e abrir em nova janela, sem dependência de biblioteca externa:
```typescript
export function abrirPdfFrequencia(params: FrequenciaPdfParams): void {
  const html = buildHtml(params)
  const win = window.open('', '_blank', 'width=900,height=1100,scrollbars=yes')
  if (!win) {
    alert('Habilite pop-ups para gerar o PDF desta frequência.')
    return
  }
  win.document.write(html)
  win.document.close()
}
```
O auto-print usa `window.onload` com delay de 300ms para o browser renderizar antes de imprimir:
```html
<script>
  window.onload = function() {
    setTimeout(function() { window.print(); }, 300);
  };
</script>
```
CSS: `@page { size: A4 portrait; margin: 0; }` + `@media print { ... }` para garantir formato A4 sem margens extras.

### Fontes de dados para o PDF — usar estado já disponível no frontend

O PDF precisa de: nome/CRM do médico, nome do tomador, CNPJ da empresa, itens da frequência.
**Nunca fazer uma chamada extra à API para montar o PDF** — os dados já estão disponíveis:
- `medicoNome` / `medicoCrm` / `medicoCrmUf`: do `medicosApi.listar()` (backoffice) ou `portalApi.getPerfil()` (portal)
- `tomadorNome`: da lista de tomadores já carregada na página
- `empresaCnpj`: do claim `cnpj_id` do JWT via `user?.cnpj_id` do `useAuth()`
- `freq` (itens, competência, setor): do estado local da página

### `gerarPdf()` no backend — regras de transição e idempotência

Status permitidos para gerar PDF (transicionam para AGUARDANDO_ASSINATURA):
- `RASCUNHO` → chama `frequenciaRepo.save(f)` com novo status
- `PDF_GERADO` → chama `frequenciaRepo.save(f)` com novo status
- `AGUARDANDO_ASSINATURA` → **idempotente**: não chama `save()`, apenas retorna o response

Status que lançam 422 (não podem gerar PDF da transição):
- `ASSINADA_RECEBIDA`, `ENVIADA_TOMADOR`, `FATURADA`

```java
@Transactional
public FrequenciaMedicaResponse gerarPdf(UUID id) {
    FrequenciaMedica f = findOrThrow(id);
    Set<String> permitidos = Set.of("RASCUNHO", "PDF_GERADO", "AGUARDANDO_ASSINATURA");
    if (!permitidos.contains(f.getStatus())) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "...");
    }
    if (!"AGUARDANDO_ASSINATURA".equals(f.getStatus())) {
        f.setStatus("AGUARDANDO_ASSINATURA");
        frequenciaRepo.save(f);
    }
    return toResponse(f);
}
```

### Botão de reimpressão — não chamar API para status avançados

No frontend, o botão "Gerar PDF" deve verificar o status antes de chamar a API:
```typescript
const handleGerarPdf = async () => {
  const statusNovos = ['RASCUNHO', 'PDF_GERADO']
  if (statusNovos.includes(freq.status)) {
    // chama API → atualiza status → abre PDF
    const updated = await frequenciasApi.gerarPdf(freq.id)
    atualizarLista(updated)
    abrirPdfFrequencia({ freq: updated, ... })
  } else {
    // reimpressão: só abre o PDF sem chamar a API
    abrirPdfFrequencia({ freq, ... })
  }
}
```
Isso garante que frequências em `ASSINADA_RECEBIDA`, `ENVIADA_TOMADOR` ou `FATURADA` ainda possam ser reimpresas sem erro 422.

### Linhas em branco no PDF para preenchimento manual

O formulário oficial exige espaço para plantões adicionados manualmente pelo médico após impressão.
Garantir mínimo de 20 linhas visíveis na tabela:
```typescript
const totalLinhas = Math.max(20, freq.itens.length + 5)
const linhasVazias = Array.from({ length: totalLinhas - freq.itens.length }, () => `
  <tr><td></td><td></td><td></td><td></td><td></td><td></td></tr>
`).join('')
```

---

## Recebimento de Documento Assinado — MinIO no Faturamento (EPIC-13.6)

### MinIO como dependência do faturamento service

O onboarding já tinha `io.minio:minio:8.5.7`. O faturamento não tinha MinIO até o EPIC-13.6.
Ao adicionar a mesma dependência no `pom.xml` do faturamento, reusar o padrão de
`StorageConfig.java` (MinioClient bean) e `StorageService.java` do onboarding — porém
com o método `upload(String prefix, MultipartFile arquivo)` genérico (sem acoplamento
a `medicoId` ou `tipoDocumento`):
```java
String objectKey = storageService.upload("frequencias/" + frequenciaId, arquivo);
```
Propriedades em `application.yml` do faturamento:
```yaml
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:pinsaude-documentos}
```

### Regras de status para `receberDocumentoAssinado`

Upload de documento assinado **só é permitido** quando `status = AGUARDANDO_ASSINATURA` → 422 caso contrário.
Após upload com sucesso: `status → ASSINADA_RECEBIDA`.
Re-upload (quando já há `documentoAssinadoKey`): o arquivo anterior é deletado do MinIO antes do novo upload.
```java
if (!"AGUARDANDO_ASSINATURA".equals(f.getStatus())) {
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "...");
}
if (f.getDocumentoAssinadoKey() != null) {
    storageService.delete(f.getDocumentoAssinadoKey());  // silencioso se órfão
}
String objectKey = storageService.upload("frequencias/" + id, arquivo);
f.setDocumentoAssinadoKey(objectKey);
f.setStatus("ASSINADA_RECEBIDA");
```

### Upload multipart sem Content-Type (mesmo padrão EPIC-03.4)

O frontend usa `FormData` com apenas `Authorization` no header — **sem** `Content-Type`:
```typescript
async uploadDocumentoAssinado(id: string, arquivo: File): Promise<FrequenciaMedicaResp> {
  const token = JSON.parse(sessionStorage.getItem('pinsaude_tokens') ?? '{}').accessToken ?? ''
  const form = new FormData()
  form.append('arquivo', arquivo)
  const res = await fetch(`/api/frequencias/${id}/documento`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },  // SEM Content-Type
    body: form,
  })
  return handleResponse<FrequenciaMedicaResp>(res)
},
```
O browser define o `Content-Type: multipart/form-data; boundary=...` automaticamente.

### MockMultipartFile em testes unitários do faturamento

Para testar o serviço com upload:
```java
import org.springframework.mock.web.MockMultipartFile;
// ...
MockMultipartFile arquivo = new MockMultipartFile(
    "arquivo", "doc.pdf", "application/pdf", new byte[10]);
service.receberDocumentoAssinado(freqId, arquivo);
```
O `spring-boot-starter-test` já inclui `spring-mock`, não precisa de dependência extra.
Mockar `StorageService` com `@Mock` e adicionar ao `@InjectMocks`:
```java
@Mock StorageService storageService;
@InjectMocks FrequenciaService service;
// O construtor de FrequenciaService recebe StorageService como 5º parâmetro
```

### URL pré-assinada — endpoint `GET /api/frequencias/{id}/documento/url`

Segue o padrão de `GET /api/medicos/{id}/documentos/{docId}/url` do onboarding:
```java
@GetMapping("/{id}/documento/url")
@PreAuthorize("hasAnyRole('operacao','gestao','medico','financeiro','contabil')")
public ResponseEntity<Map<String, String>> getDocumentoUrl(@PathVariable UUID id) {
    return ResponseEntity.ok(Map.of("url", service.getDocumentoUrl(id)));
}
```
No frontend: `window.open(url, '_blank', 'noopener')` — a URL pré-assinada é acessível
diretamente pelo browser sem JWT.

### `fileInputRef.current.value = ''` ao final do upload

Após processar o arquivo (sucesso ou erro), zerar o value do input para permitir
re-upload do mesmo arquivo sem precisar escolher outro:
```typescript
} finally {
  setUploadingDoc(false)
  if (fileInputRef.current) fileInputRef.current.value = ''
}
```
Sem isso, `onChange` não dispara se o usuário escolhe o mesmo arquivo novamente.

---

## Fechamento por Grupo — Padrões e Armadilhas (EPIC-13.8)

### Modelo: agregação de frequência_itens → grupo → uma producao por grupo
O fechamento agrega os `frequencia_itens` de todas as frequências não-FATURADA do tomador+competência:
```
frequencia_medica (setor)
  └── frequencia_itens (valor_unitario + deslocamento)
        → setor.grupo_id
            → grupo de faturamento (nome, descricao_nota, servico_lc116_id)
                → Producao (valor = Σ itens de todos os médicos do grupo)
                    └── ParticipacaoProducao (por médico: valor = Σ dos itens deste médico)
```
Grupos com total = 0 são ignorados (nenhuma `producao` criada). Frequências sem itens ainda participam do fechamento e ficam marcadas como FATURADA.

### Idempotência — UNIQUE (tomador_id, competencia) + status check
O fechamento é idempotente via duas camadas:
1. UNIQUE constraint no banco: impede dois fechamentos para a mesma competência
2. Status check em `executar()`: se já existe com `status = 'FECHADO'`, lança 409

```java
fechamentoRepo.findByTomadorIdAndCompetencia(req.tomadorId(), req.competencia())
    .filter(f -> "FECHADO".equals(f.getStatus()))
    .ifPresent(f -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "..."); });
```

### Ordem de persistência: Fechamento ABERTO primeiro, depois produções, depois FECHADO
```java
// 1. Salva o fechamento em status ABERTO para obter o UUID (referenciado pelas frequências)
fechamentoRepo.save(fechamento); // status = ABERTO

// 2. Para cada grupo: cria producao + participacoes + marca frequencias como FATURADA
// (dentro do loop, passando fechamento.getId() para freq.setFechamentoId())

// 3. Atualiza o fechamento para FECHADO ao final
fechamento.setStatus("FECHADO");
fechamento.setTotalCentavos(totalGeral);
fechamentoRepo.save(fechamento);
```
Essa ordem garante que o UUID do fechamento existe quando as frequências são atualizadas.

### Interpolação de competência em descrição de nota
`{competencia}` no template `descricao_nota` do grupo é substituído por mês por extenso + ano:
```java
public static String interpolarDescricao(String template, String competencia) {
    String[] parts = competencia.split("-");
    int mes = Integer.parseInt(parts[1]);
    String mesNome = MESES[mes - 1]; // "JULHO"
    return template.replace("{competencia}", mesNome + " DE " + parts[0]);
}
// "JULHO DE 2026"
```
Método `public static` para permitir testes unitários diretos.

### AggregationResult — record privado para compartilhar computação entre preview e executar
```java
private record AggregationResult(
    List<FrequenciaMedica> frequencias,
    Map<UUID, TomadorServicoOperacional> setoresMap,
    Map<UUID, TomadorGrupoFaturamento> gruposMap,
    Map<UUID, Map<UUID, Long>> agrupado  // grupoId → (medicoId → totalCentavos)
) {}
```
`computeAggregation()` é chamado por `preview()` e por `executar()` — sem duplicação de lógica.

### Batch load em dois níveis — evitar N+1
```java
// 1. Carrega todos os itens das frequências em um único SELECT
List<FrequenciaItem> todosItens = itemRepo.findByFrequenciaIdIn(freqIds);

// 2. Carrega todos os setores em um único SELECT
Map<UUID, TomadorServicoOperacional> setoresMap = setorRepo.findAllById(setorIds)...

// 3. Carrega todos os grupos necessários em um único SELECT (após agrupar)
Map<UUID, TomadorGrupoFaturamento> gruposMap = grupoRepo.findAllById(grupoIds)...
```
Nenhuma query dentro de loops.

### RLS em `fechamentos`: FORCE com bypass para gestão
Mesmo padrão das demais tabelas do faturamento: `FORCE ROW LEVEL SECURITY` com `WITH CHECK (true)`.
`cnpj_id_tenant` vem de `SecurityUtils.currentCnpjTenant()` (dígitos sem formatação).

### ArgumentCaptor com save() que usa thenAnswer — não redifinir stub no corpo do teste
Se o `@BeforeEach` define `when(producaoRepo.save(any())).thenAnswer(...)` (para setar UUID via reflection),
redefinir esse stub dentro de um teste corpo causa NPE: o setUp lambda (lambda$setUp$1) pode ser
invocado com argumento null quando o stub é sobrescrito.

**Solução:** usar `ArgumentCaptor` no lugar da redefinição do thenAnswer:
```java
// No teste — NÃO redefinir when(producaoRepo.save(any()))
ArgumentCaptor<Producao> captor = ArgumentCaptor.forClass(Producao.class);
verify(producaoRepo).save(captor.capture());
assertThat(captor.getValue().getDescricaoComplementar()).isEqualTo("JULHO DE 2026...");
```

---

## Discriminação da NFS-e — Propagação da Descrição do Grupo (EPIC-13.9)

### Campo `discriminacao` — cadeia completa backend + frontend

O campo `discriminacao TEXT` em `fiscal.notas_fiscais` propaga a descrição interpolada do grupo de faturamento até a nota fiscal emitida. Cadeia completa:

```
FechamentoService.executar()
  → producao.descricaoComplementar ("Prestação de serviços... JULHO de 2026")
  → NfseEmissaoPage.tsx emitirNfse({ discriminacao: producao.descricaoComplementar })
  → EmitirNfseRequest.discriminacao (campo String, nullable)
  → NfseService.emitir() → nota.setDiscriminacao(req.discriminacao())
  → DadosNota.discriminacao (14º campo do record)
  → NfseRpsRequest.from(d): descricao = d.discriminacao() ?? "Serviços médicos — competência..."
  → Nota emitida na prefeitura com a discriminação correta
```

Backward compat: `discriminacao = null` → fallback para texto genérico em toda a cadeia.

### Migration Flyway — V13 no fiscal service

Migrations do `fiscal` service têm numeração própria e independente do `faturamento`.
V13 do fiscal: `ALTER TABLE fiscal.notas_fiscais ADD COLUMN discriminacao TEXT;`
Sempre rodar `mvn process-resources -pl :pinsaude-fiscal` antes de `mvn-flyway.js` ao criar novo SQL.

### `TransactionSynchronizationManager.isSynchronizationActive()` — guard obrigatório

Métodos `@Transactional` que chamam `registerSynchronization()` falham em testes unitários sem contexto de transação ativa. Adicionar o guard:
```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() { producer.enviar(msg); }
    });
} else {
    producer.enviar(msg); // testes unitários sem transação: publica diretamente
}
```
Este padrão deve ser aplicado em todo método que usa outbox via `afterCommit()`:
`emitir()`, `aprovar()`, `reprocessarNota()` no `NfseService`.

### `NotaFiscalStatusResponse` — incluir `discriminacao` no DTO

Sempre que `NotaFiscal` ganhar um campo novo, incluir em `NotaFiscalStatusResponse.from()` e no record.
Frontend: adicionar o campo como `discriminacao?: string | null` nas interfaces TypeScript (`NotaFiscal` em `nfseApi.ts`).

### Frontend — exibir discriminação no painel lateral e no preview

- `NotasPage.tsx` `DetalhePanel`: renderizar `{nota.discriminacao && <p>...</p>}` antes das observações.
- `NfsePreviewModal.tsx`: usar `producao.descricaoComplementar` como primeira linha da discriminação quando presente, fallback para texto genérico.
- `NfseEmissaoPage.tsx`: passar `discriminacao: producao.descricaoComplementar ?? null` no `emitirNfse({...})`.

---

## Auto-cadastro Público de Médico — Schema (EPIC-14.1)

### Tabelas satélite 1:1 para dados que não cabem em `medicos`
`dados_civis_medico` e `declaracoes_lgpd_medico` seguem o mesmo padrão de `checklist_conduta`/
`dados_bancarios_medico`: PK = `medico_id`, FK com `ON DELETE CASCADE`, RLS via join em
`vinculos_medico_empresa` com `WITH CHECK (true)` **já na criação** (não precisa do fix em 2
migrations do V7, pois a tabela nasce sem o problema de INSERT bloqueado por USING).

### `origem_cadastro` em vez de novo valor em `StatusMedico`
Para diferenciar médico auto-cadastrado (formulário público) de cadastro manual (operação/gestão),
foi adicionado `medicos.origem_cadastro VARCHAR(20) DEFAULT 'MANUAL'` (valores: `MANUAL` |
`AUTO_CADASTRO`) em vez de mexer no enum `StatusMedico`. Isso mantém `MedicoService.listarFilaAprovacao()`
(que filtra por `status = RASCUNHO`) funcionando sem alteração — a fila de aprovação só ganha um
badge extra para identificar a origem.

### Array Postgres (`TEXT[]`) no Hibernate 6 — `@JdbcTypeCode(SqlTypes.ARRAY)`
Para `situacao_formacao TEXT[]` (multi-seleção de formação/titulação), o mapeamento correto no
Hibernate 6 é:
```java
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(name = "situacao_formacao", columnDefinition = "text[]")
private String[] situacaoFormacao;
```
Não precisa de conversor customizado nem de `@Type` do Hibernate 5 — `String[]` funciona direto.

### RLS sem vínculo — quem enxerga um auto-cadastro antes da triagem
Um médico criado via auto-cadastro público nasce **sem** `vinculos_medico_empresa` (a empresa é
atribuída depois, manualmente, por `gestao`). Como o RLS de `medicos`/`dados_civis_medico`/
`declaracoes_lgpd_medico` resolve isolamento via join nesse vínculo, um registro sem vínculo só é
visível para quem tem `app.current_tenant` vazio (role `gestao`, que faz bypass) — o papel
`operacao` de uma empresa específica não vê a candidatura até alguém atribuir o vínculo. Isso é
esperado por design, não é bug: `gestao` é quem faz a triagem inicial cross-empresa.

### ⚠️ Testcontainers Postgres não tem `svc_onboarding`/`svc_portal` — migrations V14+ falham sem `withInitScript`
As migrations `V14__add_taxa_pin_medico.sql` e `V15__create_documentos_empresa.sql` fazem
`GRANT ... TO svc_onboarding` / `GRANT ... TO svc_portal`. Essas roles são criadas por
`tools/db/init.sql` no Postgres real (Docker), mas **não existem** no container efêmero do
Testcontainers — isso faz o Flyway falhar com `ERROR: role "svc_onboarding" does not exist`
durante o boot do `ApplicationContext`, derrubando **qualquer** teste `@SpringBootTest` com
`spring.flyway.enabled=true` + `PostgreSQLContainer` (não é um problema pontual: afeta todos os
testes de integração do onboarding igualmente). Como o CI roda `mvn ... -DskipTests`
(ver `.github/workflows/ci.yml`), esse problema nunca foi pego automaticamente.

**Solução:** script `services/onboarding/src/test/resources/db/test-roles-init.sql` (cria as 2
roles com `CREATE ROLE ... IF NOT EXISTS`) + `.withInitScript("db/test-roles-init.sql")` no
container, **antes** do Flyway rodar:
```java
@Container
@ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
    .withInitScript("db/test-roles-init.sql");
```
Aplicado em todos os testes `@SpringBootTest` + `PostgreSQLContainer` do onboarding
(`MultitenancyIsolationTest`, `EmpresaIntegrationTest`, `ConfiguracaoFiscalIntegrationTest`,
`DadosCivisLgpdSchemaIntegrationTest`). Se uma migration futura adicionar `GRANT ... TO` uma nova
role, atualizar também esse script de teste.

### Problema conhecido (não corrigido nesta task) — datas fixas em testes fiscais
`ConfiguracaoFiscalServiceTest`/`ConfiguracaoFiscalIntegrationTest` têm 5 testes que falham com
`422 competência passada` conforme o relógio real avança (competência mínima é calculada a partir
de `LocalDate.now()`, mas os testes usam uma competência fixa que já ficou no passado). Não é
relacionado ao onboarding/EPIC-14 — sinalizado aqui para quem for mexer nesses testes no futuro.

---

## Auto-cadastro Público de Médico — API de Candidatura (EPIC-14.2)

### Novo serviço/controller isolados, nunca reaproveitar `MedicoService`/`MedicoController`
`CadastroPublicoService`/`CadastroPublicoController` são componentes **novos e separados** de
`MedicoService`/`MedicoController` — o fluxo público não deve reaproveitar os métodos existentes
porque eles assumem sempre um operador/gestão autenticado (`getCurrentUser()` via
`SecurityContextHolder`, `@PreAuthorize` em todo endpoint). O serviço público só enxerga/edita
médicos com `origemCadastro = "AUTO_CADASTRO"` — nunca cadastros manuais, mesmo sabendo o UUID.

### UUID da candidatura funciona como "capability handle" — sem tabela de token
`POST /api/onboarding/publico/candidaturas` retorna o `id` do `Medico` recém-criado; esse mesmo
UUID é usado depois em `PUT/GET .../candidaturas/{id}` para continuar o preenchimento — não existe
tabela de token separada (mesmo espírito do token opaco de `ConviteMedico`, mas reaproveitando o
próprio ID gerado). `GET` funciona em qualquer status (somente leitura); `PUT` só funciona enquanto
`status = RASCUNHO` — depois de aprovado/ativado, a candidatura fica travada para edição pública.

### Duas camadas de `SecurityConfig` precisam liberar o mesmo path
Rota pública nova precisa de **dois** ajustes, não só um:
1. `services/onboarding/.../config/SecurityConfig.java` — `.requestMatchers("/api/onboarding/publico/**").permitAll()`.
2. `gateway/.../config/SecurityConfig.java` — `.pathMatchers("/api/onboarding/publico/**").permitAll()`.

O gateway hoje faz `anyExchange().authenticated()` para tudo exceto `/actuator/health`/`/actuator/info`
— **isso bloqueia até o webhook do Clicksign** (`/api/onboarding/webhooks/**`, permitAll só no
onboarding, nunca liberado no gateway). Não corrigimos o webhook nesta task (integração externa já
em produção, fora do escopo do EPIC-14), mas documentamos aqui: qualquer novo endpoint público de
qualquer serviço só é realmente público se **ambos** os SecurityConfig liberarem o mesmo path.

### Testcontainers — `search_path` não replica `ALTER USER ... SET search_path` do Postgres real
Além das roles `svc_onboarding`/`svc_portal` (EPIC-14.1), o Postgres real tem
`ALTER USER svc_onboarding SET search_path TO onboarding, public` (`tools/db/init.sql`). Isso faz
com que `pgcrypto` (instalado no schema `onboarding` pois `spring.flyway.schemas=onboarding`) seja
resolvido também nas conexões normais da aplicação — `onboarding.encrypt_sensitive()` chama
`pgp_sym_encrypt()` sem qualificar o schema, contando com esse `search_path`. O usuário `test` do
Testcontainers não tem esse `ALTER`, então qualquer teste que exercite `CryptoService` de verdade
(não mockado) falha com `function pgp_sym_encrypt(text, text) does not exist`. Nenhum teste
anterior ao EPIC-14.2 tinha exercitado esse caminho com banco real (todos usam `CryptoService`
mockado). Corrigido incluindo no mesmo `test-roles-init.sql` (EPIC-14.1):
```sql
ALTER DATABASE test SET search_path TO onboarding, public;
```

### Gateway — testando "este path é público" sem depender de um backend real no ar
Um teste que faz round-trip completo (`WebTestClient` num `@SpringBootTest` do gateway) para um
path que tem rota configurada (`Path=/api/onboarding/**` → `localhost:8085`) pode **acidentalmente
bater num serviço onboarding real** rodando localmente (ex.: IDE com run configuration ativa) — e
nesse caso o 401 vem do *downstream* (que pode estar com código desatualizado), não do gateway.
Testar apenas `status != 401` é frágil por isso. Sinal confiável: o header `Vary` só aparece quando
o request passa da checagem de autorização e entra no roteamento reativo do WebFlux — o 401
imediato do próprio `SecurityConfig` (rota não permitida) nunca tem esse header. Ver
`gateway/src/test/java/.../SecurityIntegrationTest.cadastroPublico_semToken_naoEhBloqueadoPeloGateway`.
Tentar sobrescrever `spring.cloud.gateway.routes[N].uri` via `@DynamicPropertySource` para apontar
a uma porta morta quebrou o carregamento do `ApplicationContext` — não investigado a fundo, evitar
esse caminho.

---

## Auto-cadastro Público de Médico — Documentos, Bancário e LGPD (EPIC-14.3)

### `finalizar()` valida completude mas NÃO cria usuário Keycloak — isso é a 14.4
`POST .../candidaturas/{id}/finalizar` só valida que os documentos obrigatórios
(`CRM`, `COMPROVANTE_ENDERECO` — os únicos sem "(Opcional)" no formulário original) e as
declarações LGPD estão completos, registra histórico (`CANDIDATURA_FINALIZADA`) e dispara o
e-mail de confirmação. A criação do usuário Keycloak (`enabled=false`) é responsabilidade da
EPIC-14.4 — o ponto de extensão é `CadastroPublicoService.finalizar()`, que deve ganhar a chamada
ao futuro `KeycloakAdminService.createUser(...)` + persistir `medico.keycloakUserId` logo após a
validação de completude passar.

### `TipoAcaoMedico` é `VARCHAR` puro — adicionar valor novo não precisa de migration
Diferente de `StatusMedico`/`TipoDocumentoMedico` (enums Postgres nativos, exigem
`ALTER TYPE ... ADD VALUE` isolado), `HistoricoMedico.tipoAcao` é uma coluna `VARCHAR(50)` comum —
`TipoAcaoMedico` é só um enum Java usado via `.name()`. Adicionar `CANDIDATURA_FINALIZADA` (ou
qualquer novo tipo de ação) não exige nenhuma migration, só editar o enum.

### `@AssertTrue` do Jakarta Validation para aceites legais obrigatórios
Os 4 aceites de `DeclaracaoLgpdRequest` (veracidade, uso de dados, compartilhamento, aviso de
privacidade) usam `@AssertTrue` (não `@NotNull Boolean`) — força o valor a ser exatamente `true`,
retornando 400 automaticamente se qualquer um vier `false`. Mais direto que validar manualmente no
service para esse tipo de consentimento onde "false" nunca é uma resposta válida.

### Testando upload multipart sem depender de MinIO real — `@MockBean StorageService`
Nenhum teste do onboarding antes desta task exercitava upload de documento com Spring context real
(os existentes usam `@Mock` em testes unitários). Para testar o round-trip HTTP completo
(multipart, `permitAll`, persistência no Postgres) sem exigir um MinIO rodando — o que tornaria o
teste dependente de infraestrutura externa e diferente entre local/CI — usar `@MockBean
StorageService` no teste `@SpringBootTest`: substitui só esse bean por um mock, mantendo o resto
do contexto (JPA, RLS, Postgres via Testcontainers) real. Ver
`CadastroPublicoControllerIntegrationTest`.

### IP de origem atrás do gateway — `X-Forwarded-For` antes de `getRemoteAddr()`
Como toda requisição pública passa pelo Spring Cloud Gateway (que injeta `X-Forwarded-For` por
padrão), `HttpServletRequest.getRemoteAddr()` sozinho captura o IP do gateway, não do médico. Ler
`X-Forwarded-For` primeiro (pegando o primeiro IP da lista, caso haja múltiplos proxies) e só cair
para `getRemoteAddr()` se o header não vier — usado para popular `declaracoes_lgpd_medico.ip_origem`.

### Documentos obrigatórios para finalizar — só os sem "(Opcional)" no formulário original
Do formulário fornecido pelo usuário, só "Foto do CRM" e "Comprovante de Endereço" não têm
indicação de opcional — RQE, certidão de casamento, certificado de residência e títulos de
especialista são todos "(Opcional)". `CadastroPublicoService.DOCUMENTOS_OBRIGATORIOS` reflete só
esses dois; qualquer ajuste de regra de negócio deve mexer nessa lista, não espalhar validação
pelo controller.

---

## Auto-cadastro Público de Médico — Integração Keycloak (EPIC-14.4)

### `KeycloakAdminService` duplicado no onboarding — só o subconjunto usado
Mesmo padrão do `services/gestao/.../KeycloakAdminService.java` (mesmo mecanismo de cache de
token via `RestClient` + `/realms/master/protocol/openid-connect/token`), mas **não** é uma cópia
1:1: o onboarding só implementa `createUserDesabilitado`, `getRoleByName` e `assignRole`/
`updateUserEnabled` — não duplica `listUsers`/`removeRole`/`sendInvitationEmail`/`getUser`, que
não são usados neste fluxo. `RestClient.Builder` já vem auto-configurado pelo
`spring-boot-starter-web` (Spring Boot 3.2+), não precisa de bean extra — só
`@EnableConfigurationProperties(KeycloakAdminProperties.class)` num `@Configuration` vazio
(`KeycloakAdminConfig`), reaproveitando o mesmo `record` de properties do gestao com o prefixo
`keycloak.admin`.

### `createUserDesabilitado` sempre `enabled=false`, `cnpj_id` só se não for nulo/vazio
Diferente do `gestao` (sempre `enabled=true`, sempre seta `attributes.cnpj_id`), o onboarding
cria o usuário do médico **desabilitado** e só inclui o atributo `cnpj_id` no corpo da requisição
se vier não-nulo/não-vazio (`Map.of("cnpj_id", List.of(cnpjId))` com `cnpjId=null` lançaria NPE
em `List.of`). Hoje sempre é chamado com `cnpjId=null` — o médico não tem empresa definida no
momento do auto-cadastro (ver EPIC-14 no plano) — mas o método aceita o parâmetro para o caso de,
no futuro, o vínculo já ser conhecido nesse ponto.

### Idempotência de `finalizar()` — só cria o usuário Keycloak se `keycloakUserId` ainda for nulo
`CadastroPublicoService.finalizar()` só chama `createUserDesabilitado` quando
`medico.getKeycloakUserId() == null`; como só persistimos o ID DEPOIS de a chamada ao Keycloak
retornar com sucesso, uma falha nunca deixa o `Medico` num estado inconsistente — a transação não
tem nada para desfazer (historico/e-mail só acontecem depois) e uma nova chamada a `finalizar()`
tenta de novo. Falha na criação lança **502 Bad Gateway** (não 422/500) — sinaliza ao cliente que é
um problema transitório de infraestrutura, não um erro de validação do usuário.

### Liberação de acesso é tolerante a falha — ativação nunca é bloqueada pelo Keycloak
Em `MedicoService.ativar()`/`verificarAtivacaoAutomatica()`, `liberarAcessoKeycloak()` (novo
helper privado) só roda se `medico.getKeycloakUserId() != null` (médicos cadastrados manualmente,
sem Keycloak, não passam por aqui) e **captura qualquer exceção só com log.error**, sem propagar —
diferente da criação em `finalizar()` (que propaga como 502). Justificativa: a ativação do médico
no onboarding já é um fato consumado e correto independente do Keycloak; se a chamada
`assignRole`/`updateUserEnabled` falhar, um operador pode liberar manualmente pelo Console do
Keycloak sem precisar reverter/reprocessar a ativação. Mesmo padrão tolerante já usado em
`NotificacaoService.publicar()` para notificações por e-mail.

### Testando `finalizar()`/`ativar()` com Keycloak — `@MockBean`/`@Mock`, sem WireMock
Assim como `services/gestao` não tem teste dedicado testando as chamadas HTTP internas do seu
`KeycloakAdminService` (nenhum WireMock), o onboarding segue o mesmo padrão: `KeycloakAdminService`
é mockado na fronteira (`@Mock` nos testes unitários de `CadastroPublicoService`/`MedicoService` via
`OnboardingFluxoTest`, `@MockBean` no `CadastroPublicoControllerIntegrationTest`) — sem tentar
simular a API HTTP do Keycloak em si. Consistente com como `@MockBean StorageService` já evita
depender de MinIO real (EPIC-14.3): sem isso, `finalizar()` chamaria `http://localhost:8080` de
verdade e falharia com 502 em qualquer ambiente sem Keycloak rodando (ex.: CI).

---

## Wizard Reutilizável — StepWizard (EPIC-14.5)

### Extração de padrão triplicado em `libs/frontend/src/components/StepWizard.tsx`
`MedicoWizardModal.tsx`, `EmpresaWizardModal.tsx` e `ContaBancariaWizardModal.tsx` tinham cada um
sua própria cópia quase idêntica do indicador de progresso (`STEPS` array + função interna
`WizardSteps` com círculos numerados + linha de conexão). Extraído para um componente único e
compartilhado no pacote `@pinsaude/ui`, exportado em `libs/frontend/src/index.ts`:
```typescript
export { StepWizard }           from './components/StepWizard'
export type { StepWizardStep }  from './components/StepWizard'
```
Props: `steps: {label, icon}[]`, `current`, `maxVisited`, `onStepClick`, `className?`. **Sem
acoplamento a `Modal`** — só renderiza o indicador; navegação (`goTo`/`handleNext`) e validação por
etapa (`validateStep`) continuam responsabilidade de cada tela consumidora.

### Escolha da variante mais responsiva como base do componente compartilhado
Das 3 implementações originais, `ContaBancariaWizardModal` tinha o tratamento mobile mais completo
(`hidden sm:block` para labels + `sm:hidden` para números compactos, `flex-1` nas linhas de conexão
em vez de larguras fixas). Ao extrair, essa foi escolhida como base — as outras duas perderam
pequenas diferenças de largura/responsividade que não eram desejadas, apenas inconsistência não
intencional entre cópias.

### Consumo sem `as const` no array STEPS
Os arrays `STEPS` originais usavam `{ label, Icon } as const` (campo capitalizado). O componente
compartilhado usa a prop `icon` (minúsculo, consistente com a interface `StepWizardStep`) e não
precisa de `as const` — o tipo `ComponentType<{ className?: string }>` já é inferido corretamente
sem satisfazer literal types. Renomear `Icon` → `icon` nos 3 arquivos consumidores e remover
`as const` ao adotar o componente.

### Funciona tanto em Modal quanto full-page sem alteração
Por não ter nenhuma dependência de `Modal` (nem CSS nem contexto), o mesmo `StepWizard` é
reutilizável diretamente em uma página pública full-page (ex.: futuro wizard de auto-cadastro do
EPIC-14.6/14.7) só passando `className` para ajustar o container externo — não precisa de nenhuma
variante nova do componente.

---

## Jornada Pública de Auto-cadastro — Etapas 1-3 (EPIC-14.6)

### Módulo de API sem token — primeiro do projeto sem `authHeaders()`
`apps/web/src/api/candidaturaMedicoApi.ts` é o primeiro módulo de API do projeto que **não**
usa `getAccessToken()`/`Authorization`. Os endpoints em `/api/onboarding/publico/candidaturas/**`
são `permitAll` (onboarding e gateway, já configurado na 14.2/14.3) — incluir qualquer header
de autenticação aqui seria inofensivo (o backend ignora), mas incorreto conceitualmente: esta é
uma jornada de visitante sem sessão.

### Reordenação de campos entre "steps do ticket" e "steps da tela" — CRM/e-mail movidos para a etapa 1
O ticket original agrupava os campos como Etapa 1 = dados civis, Etapa 2 = contato (inclui e-mail),
Etapa 3 = documentos profissionais (inclui CRM+UF). Isso é **incompatível** com o contrato já
implementado do backend: `CandidaturaPublicaRequest` exige `@NotBlank` em `nome`, `cpf`, `crm`,
`crmUf` e `email` simultaneamente — não existe um POST parcial só com dados da etapa 1 literal.
Como os uploads de documento (etapa 2 em diante) exigem um `candidaturaId` já existente, e o
`id` só nasce quando o POST tem sucesso, os campos `crm`/`crmUf`/`email` foram antecipados para a
tela de etapa 1 (renomeada para cobrir identificação completa), mantendo os nomes de step do
ticket (`Dados Pessoais` → `Contato e Endereço` → `Documentos Profissionais`) mas redistribuindo
os campos internos. Sem essa mudança, não haveria como fazer upload de nenhum documento antes da
etapa 3, travando a etapa 2 (comprovante de endereço, certidão de casamento).

### "Retomar depois" via `id` em `sessionStorage` + hidratação por `GET`
Ao concluir a etapa 1 (primeiro `POST`/`PUT` bem-sucedido), o `id` retornado é persistido em
`sessionStorage` (`pinsaude_candidatura_id`). No `useEffect` de montagem da página, se esse `id`
existir, chama `candidaturaMedicoApi.buscar(id)` para restaurar o formulário inteiro — em caso de
404 (candidatura removida ou já avançou de status), limpa o `sessionStorage` e começa do zero
silenciosamente. Como a API pública não expõe listagem de documentos já enviados, o retomar não
sabe quais arquivos já foram enviados anteriormente — aceitável porque o backend permite reenvio
sem limite de quantidade por tipo (mesma decisão do EPIC-14.3).

### `StepWizard` com 6 passos declarados, só 3 navegáveis
A tela declara os 6 passos da jornada completa (`Dados Pessoais` → ... → `LGPD`) no array `STEPS`
passado ao `StepWizard` (EPIC-14.5), mas `maxVisited` nunca ultrapassa o índice 2 nesta task — os
3 passos finais aparecem visualmente (dão contexto de progresso) mas são bloqueados para clique
(`isClickable = i <= maxVisited`). A 14.7 estende essa mesma página, no mesmo arquivo, adicionando
o conteúdo das etapas 4-6 e elevando `maxVisited` até 5 — não precisa recriar o `StepWizard` nem
alterar seu array de `steps`.

### Upload simplificado nesta task — extração completa fica para a 14.7
`UploadField` (componente local em `CadastroMedicoWizardPage.tsx`) é uma versão enxuta do padrão
de `DocumentosModal.tsx` (clique ou arraste, sem preview de imagem nem múltiplos arquivos visíveis
por tipo) — suficiente para validar que os 4 tipos de documento desta etapa (`COMPROVANTE_ENDERECO`,
`CERTIDAO_CASAMENTO`, `CRM`, `RQE`) sobem corretamente sem sessão. A extração de um componente de
upload compartilhado (drag-and-drop completo, reutilizável entre `DocumentosModal` autenticado e
a jornada pública) é escopo da 14.7, que também adiciona os uploads das etapas 4-6.

### Ambiente local: onboarding/gateway rodando às vezes vêm de um branch desatualizado
Os processos Java de `onboarding` (8085) e `gateway` (8090) já em execução no ambiente podem ter
sido compilados a partir do diretório principal `G:\olisystem\pinsaude` — que é o worktree de
trabalho do usuário e pode estar em um branch/commit **anterior** aos merges recentes (ex.: ainda
sem as rotas `/api/onboarding/publico/**`). Isso se manifesta como `401 Unauthorized` em endpoints
que deveriam ser `permitAll`, mesmo com o código-fonte correto no worktree de feature. Antes de
depurar "por que o permitAll não funciona", checar a origem do `.jar`/`target/classes` do processo
em execução (`Get-CimInstance Win32_Process | Select CommandLine`) — se apontar para outro
worktree/branch, matar o processo e resubir com `mvn-build.js` + `java -jar` a partir do worktree
correto (branch atualizado).

---

## Jornada Pública de Auto-cadastro — Etapas 4-6 e Envio Final (EPIC-14.7)

### Componente de upload compartilhado — extraído sem tocar em `DocumentosModal.tsx`
`apps/web/src/components/MultiFileUploadField.tsx` extrai o padrão de drag-and-drop + preview de
status + múltiplos arquivos por tipo de `DocumentosModal.tsx`, mas como um componente **novo e
genérico** (`<T extends string>`), aceitando `onUpload: (tipo: T, file: File) => Promise<unknown>`
injetado — funciona tanto com `candidaturaMedicoApi` (sem token) quanto, no futuro, com
`medicosApi` (autenticado). **Decisão deliberada:** `DocumentosModal.tsx` (autenticado, já em
produção) não foi refatorado para consumir este componente nesta task — o ticket pedia extração
"reaproveitada nas etapas 3 e 5 do wizard público", não a substituição de uma tela autenticada já
estável. Evita risco de regressão em uma tela madura por uma tarefa cujo escopo é a jornada
pública.

### `MultiFileUploadField` com `multiplos` por tipo de documento
Prop `multiplos?: boolean` (default `true`) controla se o campo aceita reenvio após o primeiro
arquivo: `multiplos={false}` para documentos oficiais únicos (CRM, comprovante de endereço,
certidão de casamento, RQE) — mostra apenas "Enviado: nome.ext" e desabilita novo upload;
`multiplos={true}` (om ausência de prop) para os campos da Etapa 5 que decisão do usuário definiu
como "quantidade livre" (títulos de especialista) ou plausivelmente múltiplos (certificado de
residência/especialização) — mostra contador "N arquivo(s) enviado(s) — clique para adicionar
mais" + lista dos nomes.

### PhoneInput — mesmo padrão de CpfInput, sem validação de dígito verificador
`apps/web/src/components/PhoneInput.tsx` + `apps/web/src/utils/phone.ts` replicam a estrutura de
`CpfInput.tsx`/`utils/cpf.ts` (máscara aplicada em `onChange`, `forwardRef`), mas sem estado de
validade (telefone não tem dígito verificador) — só formata `(DD) NNNNN-NNNN` conforme o usuário
digita. Usado no campo "Telefone/WhatsApp" da Etapa 1 do wizard público.

### Etapa 4 (Dados Bancários) usa endpoint próprio, não o `atualizar()` geral
Diferente das etapas 1/2/3/5 (que persistem via `candidaturaMedicoApi.atualizar(id, toRequest(form))`,
substituindo o recurso inteiro), a Etapa 4 chama `atualizarDadosBancarios(id, req)` — um sub-recurso
separado no backend (`dados_bancarios_medico`, EPIC-14.3). `persistBank()` é uma função dedicada,
com sua própria validação (`validateStep(3)`), independente da validação geral de `form`.

### Envio final = 2 chamadas sequenciais, nunca uma só
`handleEnviar()` (Etapa 6 — LGPD) primeiro chama `registrarDeclaracaoLgpd(id, req)` e só then
`finalizar(id)` — são dois endpoints distintos no backend (EPIC-14.2/14.3), sem endpoint combinado.
Se `registrarDeclaracaoLgpd` falhar, `finalizar` nunca é chamado (a candidatura fica incompleta e
o usuário pode tentar de novo). Só após ambos terem sucesso o `sessionStorage` é limpo
(`STORAGE_KEY_ID` removido) — diferente das etapas 1-5, que sempre persistem o id para retomada,
aqui a candidatura está genuinamente finalizada (status avança além de `RASCUNHO`, e
`findEditavelOrThrow` no backend passa a rejeitar novas edições com 422).

### Validação de teste ponta-a-ponta: e-mail + Keycloak confirmados via API, não só a UI
Além do teste manual no navegador, a finalização foi confirmada consultando diretamente o Mailhog
(`http://localhost:8025`, e-mail "Recebemos sua candidatura" recebido) e o Keycloak Admin API
(`GET /admin/realms/pinsaude/users?email=...` retornando `enabled: false`) — validação que a
cadeia completa (candidatura → LGPD → finalizar → criar usuário Keycloak desabilitado → notificar
e-mail) funciona de ponta a ponta, não apenas que a tela de sucesso aparece.

### Armadilha de automação: viewport grande faz `left_click` por coordenada errar o alvo
Em uma sessão de teste manual com uma nova aba do Chrome cujo viewport lógico era muito maior
(2390×1142) que a screenshot retornada (1568×750, escala ~0.656), cliques por coordenada (mesmo
recalculados a partir do screenshot) erraram o campo alvo, fazendo texto ser digitado no campo
errado ou em nenhum campo. **Solução:** usar `form_input` (via `ref` do `read_page`/`find`) para
todos os campos de texto/select quando o viewport parecer desproporcional ao screenshot — `form_input`
manipula o elemento diretamente via referência, sem depender de coordenadas de tela.

---

## Remoção de Documento no Auto-cadastro Público (pós-EPIC-14)

### `MultiFileUploadField` nasceu sem opção de remover — bloqueava troca de arquivo errado
O componente `MultiFileUploadField.tsx` (EPIC-14.7) desabilitava a dropzone assim que um arquivo
era enviado em campos `multiplos={false}` (CRM, comprovante de endereço, certidão de casamento,
RQE) — sem nenhum botão de remoção, quem selecionava o arquivo errado ficava travado, sem
conseguir corrigir. Adicionado prop opcional `onRemove?: (tipo, arquivo) => Promise<unknown>`:
em `multiplos={false}` aparece um ícone de lixeira ao lado de "Enviado: nome.ext" (reabre a
dropzone ao remover); em `multiplos={true}` cada item da lista ganha seu próprio ícone de remoção
individual. `onRemove` é opcional de propósito — se omitido, o campo se comporta como antes
(nenhum breaking change nos consumidores existentes).

### Backend público não tinha `DELETE`/`GET` de documentos — só existia no fluxo autenticado
`CadastroPublicoController`/`CadastroPublicoService` (EPIC-14.2/14.3) só tinham `uploadDocumento`.
O fluxo autenticado (`MedicoController`/`MedicoService`) já tinha `listarDocumentos`/
`deletarDocumento` havia tempo — replicados 1:1 no serviço público, reusando o mesmo guard
`findEditavelOrThrow` (bloqueia remoção depois que a candidatura sai de `RASCUNHO`, mesma regra
que já protegia `uploadDocumento`). Endpoints novos: `GET/DELETE
/api/onboarding/publico/candidaturas/{id}/documentos[/{docId}]`.

### Retomada de candidatura (resume) passou a carregar os documentos já enviados
Antes desta correção, resumir uma candidatura salva (`sessionStorage` + `GET /candidaturas/{id}`)
não recuperava a lista de documentos já enviados — limitação já documentada no EPIC-14.6
("a retomada não sabe quais arquivos já foram enviados"). Como o novo endpoint `listarDocumentos`
já existia para viabilizar a remoção, o `useEffect` de restauração em `CadastroMedicoWizardPage.tsx`
passou a chamar `candidaturaMedicoApi.listarDocumentos(id)` logo após `buscar(id)` ter sucesso e
popular `docsEnviados` agrupando por tipo — sem isso, o botão de remover não apareceria após um
refresh de página (o array `arquivos` estaria vazio mesmo com documentos já no servidor).

---

## Ajustes na Tela de Aprovação de Onboarding (EPIC-14.8)

### `MedicoResponse` não expunha `origemCadastro`/dados civis/LGPD — precisou de novo DTO e novo `from()`
O endpoint autenticado `GET /api/medicos/fila-aprovacao` (usado por `AprovacaoOnboardingPage.tsx`)
retornava `MedicoResponse`, que **não** incluía `origemCadastro`, `DadosCivisMedico` nem
`DeclaracoesLgpdMedico` — só a API pública (`CandidaturaPublicaResponse`, EPIC-14.2) tinha esses
dados. Criado `DadosCivisMedicoResponse` (novo DTO, análogo aos campos civis de
`CandidaturaPublicaResponse` mas sem duplicar nome/cpf/crm, que já existem no `MedicoResponse`
pai) e reaproveitado `DeclaracaoLgpdResponse` (já existia, criado na 14.3 para a API pública —
sem acoplamento a "público", reutilizável como está). `MedicoResponse.from(...)` ganhou 2 novos
parâmetros (`dadosCivis`, `declaracoesLgpd`); `MedicoService.toFullResponse()` faz mais dois
`findById(medico.getId())` (em `DadosCivisMedicoRepository`/`DeclaracoesLgpdMedicoRepository`)
com `.orElse(null)` — médicos `MANUAL` não têm essas linhas, então os campos vêm `null` no JSON
sem quebrar nada no frontend.

### Só `OnboardingFluxoTest` precisou dos 2 novos `@Mock` — não todos os testes de `MedicoService`
`MedicoServiceDadosBancariosTest`/`MedicoServiceDocumentosTest` **não** foram alterados: eles só
exercitam `atualizarDadosBancarios`/`validarDocumento`, que nunca chamam `toFullResponse()` (o
único método que agora usa os 2 repositórios novos). Só `OnboardingFluxoTest` (que testa
`ativar()`/`atualizarJuntaComercial()` com sucesso, ambos retornando `toFullResponse()`) precisou
ganhar `@Mock DadosCivisMedicoRepository` e `@Mock DeclaracoesLgpdMedicoRepository`. Sem stub
explícito, o comportamento padrão do Mockito para métodos que retornam `Optional` já é
`Optional.empty()` — suficiente para os testes passarem sem setup adicional. Confirma o padrão já
documentado no projeto: nem todo teste com `@InjectMocks MedicoService` precisa de todos os mocks,
só os que exercitam o caminho de sucesso que efetivamente toca aquele campo.

### Frontend — badge, seções novas e nota de liberação automática, tudo condicional a `dadosCivis`/`origemCadastro`
`AprovacaoOnboardingPage.tsx`: badge "Auto-cadastro" (ícone `Sparkles`) no cabeçalho do
`DetalhePanel` e em cada item da lista lateral quando `medico.origemCadastro === 'AUTO_CADASTRO'`.
Duas novas seções no `DetalhePanel` — "Dados Civis e Profissionais" e "Declarações LGPD" — só
renderizam quando `medico.dadosCivis`/`medico.declaracoesLgpd` existem (nulos para médicos
manuais). Nota de texto abaixo do botão "Ativar Médico" explicando a liberação automática do
Keycloak (sem botão novo — só um parágrafo informativo), visível apenas quando
`origemCadastro === 'AUTO_CADASTRO'` e o médico ainda não está `ATIVO`.

### RLS de auto-cadastro sem vínculo — comportamento confirmado manualmente, banner só para `operacao`
Testado ao vivo com os dois papéis: logado como `gestao`, a fila mostra os auto-cadastros sem
vínculo normalmente (bypass de RLS por tenant vazio, ver EPIC-14 plano). Logado como `operacao`,
os mesmos registros **não aparecem** — confirma o comportamento de RLS já esperado por design
(ver seção "RLS sem vínculo" em EPIC-14.1). Um `Alert variant="info"` foi adicionado no topo da
página, visível só para `operacao` (`isOperacao && !isGestao`), explicando a limitação. Não é bug
nem requer mudança de política — é o comportamento correto documentado, apenas tornado visível
para não confundir o operador.

---

## Testes e Roteiro de Teste Manual Ponta-a-Ponta (EPIC-14.9)

### Cobertura de testes automatizados já estava completa desde 14.2/14.3/14.4 — só faltava um teste
Ao auditar os critérios de aceite ("testes unitários da criação de candidatura, duplicidade
409, upload sem limite, LGPD, finalizar() com Keycloak mockado" + "testes de
`ativar()`/`verificarAtivacaoAutomatica()` chamando `assignRole`+`updateUserEnabled`"), todos já
existiam em `CadastroPublicoServiceTest`/`CadastroPublicoControllerIntegrationTest`/
`OnboardingFluxoTest` (implementados nas tasks 14.2-14.4, seguindo o padrão do time de TDD já
documentado). Só faltava um teste para o **segundo gatilho** de ativação automática —
`processarWebhookClicksign(docKey, "sign")` — que só tinha teste para o caminho de pré-requisitos
incompletos, não para o caminho de sucesso com liberação de Keycloak (o outro gatilho,
`atualizarJuntaComercial`, já tinha ambos). Adicionado
`webhook_sign_todosRequisitosJaCumpridos_ativaAutomaticamenteEliberaKeycloak` em
`OnboardingFluxoTest.java`.

### 🐛 Achado crítico do roteiro manual: `CadastroPublicoService.criar()` nunca semeava o checklist de conduta
Executando o roteiro E2E completo (candidatura → aprovação → ativação), a ativação do médico
**nunca saía do estado bloqueado** por "Checklist de conduta incompleto" — e pior, não havia
NENHUMA tela onde criar esse checklist pela primeira vez, porque `ChecklistEditor` (tanto em
`MedicoPerfilPage.tsx` quanto na leitura do médico em `AprovacaoOnboardingPage.tsx`) só renderiza
quando `medico.checklist != null`. Causa raiz: `MedicoService.criar()` (cadastro manual,
operação/gestão) sempre chama `checklistRepo.save(new ChecklistConduta(medico.getId()))` na
criação (ver EPIC-03.2) — mas `CadastroPublicoService.criar()` (EPIC-14.2, auto-cadastro
público) nunca fazia isso, por ser um serviço espelhado mas escrito do zero. **Consequência: TODO
médico vindo do auto-cadastro público estava permanentemente impossibilitado de ser ativado.**
Esse é exatamente o tipo de gap que só aparece testando a jornada de ponta a ponta — nenhum teste
unitário isolado pegaria isso, porque cada serviço em separado se comporta corretamente (o
checklist nunca existir é um estado "válido" do ponto de vista de cada chamada individual).

**Correção:** `CadastroPublicoService` passou a receber `ChecklistCondutaRepository` no
construtor e semear `new ChecklistConduta(medico.getId())` em `criar()`, no mesmo padrão do
cadastro manual. Cobertura adicionada em `CadastroPublicoServiceTest` (verifica a chamada ao
mock) e `CadastroPublicoControllerIntegrationTest` (verifica a persistência real via
Testcontainers). Nenhuma mudança de frontend foi necessária — o `ChecklistEditor` já existente
passou a renderizar corretamente assim que o backend parou de retornar `checklist: null`.

### Mensagem de erro "requer configuração de MFA" no login é genérica para qualquer required action pendente
`AuthContext.tsx` (`login()`) mapeia QUALQUER `error_description` do Keycloak contendo
`"not fully set up"` ou `"actions"` para a mensagem fixa "Sua conta requer configuração de MFA" —
não é exclusiva de `CONFIGURE_TOTP`. Um usuário recém-criado por `createUserDesabilitado()` tem
`requiredActions: ["UPDATE_PASSWORD", "VERIFY_EMAIL"]`; mesmo depois de definir senha via
Admin API (`reset-password` com `temporary:false`, que limpa `UPDATE_PASSWORD`), o
`VERIFY_EMAIL` pendente sozinho já dispara essa mesma mensagem de "MFA" — o texto do frontend
é enganoso para depuração. Ao testar login de um médico recém-ativado sem ter passado pelo
fluxo real de e-mail, limpar manualmente via Admin API:
```bash
curl -X PUT .../admin/realms/pinsaude/users/{id} \
  -d '{"requiredActions":[],"emailVerified":true}'
```

### Contrato Clicksign — `MockClicksignAdapter` substitui o workaround via SQL direto (pós-EPIC-14.9)
A abordagem original (inserir manualmente um registro em `onboarding.contratos_assinatura` com
`status='ASSINADO'` via SQL) foi substituída por um adapter mock, no mesmo padrão de
`MockEmissaoNfseAdapter` do fiscal (`@Primary` + `@ConditionalOnProperty`):
`services/onboarding/.../adapter/MockClicksignAdapter.java`, ativado por
`clicksign.mock.enabled` (`CLICKSIGN_MOCK_ENABLED`, **default `true`** — funciona sem nenhuma
configuração extra em dev). Quando ativo, sobrepõe o `ClicksignAdapter` real e faz
`enviarContrato()` criar um registro `ENVIADO` com `documentoKey`/`signatarioKey` fake (sem chamar
a API do Clicksign) — isso desbloqueia o botão "Marcar como Assinado" (`assinarContratoManual`,
já existente na tela) que antes nunca aparecia porque `enviarContrato()` retornava 503 sem
Clicksign configurado. Fluxo completo na tela de Aprovação: clicar "Enviar Clicksign" (mock) →
"Marcar como Assinado" → se checklist/documentos/junta já estiverem OK, `verificarAtivacaoAutomatica`
ativa o médico automaticamente. Para desligar o mock e forçar o comportamento real (503 sem
Clicksign configurado): `CLICKSIGN_MOCK_ENABLED=false`. Para o checklist de conduta (já semeado na
criação desde o fix do EPIC-14.9), usar `PUT /api/medicos/{id}/checklist` diretamente se precisar
ajustar manualmente — mesmo endpoint que a tela usa.

### Roteiro de teste manual documentado em `docs/roteiros-teste/`
Novo padrão de repositório: roteiros de teste E2E ficam em `docs/roteiros-teste/<epic>.md`
(não existia essa pasta antes). Cada roteiro documenta os passos executados de verdade nesta
sessão (não um roteiro genérico) — com resultado real de cada passo e achados encontrados
durante a execução, para servir de referência de regressão em EPICs futuros que toquem o mesmo
fluxo.

---

## Tela de Usuários — gestão deve ver todos, não só quem compartilha seu cnpj_id (pós-EPIC-14.9)

### Bug: `GET /api/usuarios` filtrava pelo cnpj_id de quem está logado
`UsuarioController` é `@PreAuthorize("hasRole('gestao')")` na classe inteira — só gestão chama
esse endpoint. Mesmo assim, `KeycloakAdminService.listUsers(cnpjId)` (gestao) filtrava no
Keycloak com `q=cnpj_id:{valor}`, usando o `cnpj_id` do PRÓPRIO usuário logado. Isso só faz
sentido se cada empresa tivesse seu próprio "gestão" — não é o modelo do projeto (gestão é o
único papel cross-tenant, com bypass de RLS em todos os outros serviços). Resultado: um médico
(ou qualquer usuário) com `cnpj_id` diferente do `cnpj_id` do usuário `gestao@pinsaude.com.br`
nunca aparecia na lista, mesmo estando ativo e corretamente provisionado no Keycloak.

**Correção:** `KeycloakAdminService.listAllUsers()` (sem filtro `q`) substitui `listUsers(cnpjId)`;
`UsuarioService.listar()` (sem parâmetro) e `UsuarioController.listar()` não resolvem mais
`currentCnpjId()` para este endpoint (só `convidar()` continua precisando, pois define o `cnpj_id`
do usuário sendo criado). Um filtro novo em `UsuarioService.listar()` exclui contas sem nenhuma
role de negócio (`medico/operacao/financeiro/contabil/gestao`) — necessário porque listar TODOS os
usuários do realm agora inclui o service-account do client `pinsaude-gateway`
(`serviceAccountsEnabled: true` no `realm-export.json`), que não é um usuário gerido por essa tela.

A armadilha do PUT parcial de `attributes` zerando `firstName`/`lastName` no Keycloak 24 (achado
enquanto testava esta mesma tela) está documentada na seção seguinte, "Sincronização de cnpj_id no
Keycloak ao Atribuir Vínculo" — é onde o código do fix realmente vive (onboarding).

---

## Sincronização de cnpj_id no Keycloak ao Atribuir Vínculo (pós-EPIC-14.9)

### Sintoma: médico auto-cadastrado ativo não aparece na tela de Usuários
`GET /api/usuarios` (services/gestao) lista usuários do Keycloak filtrando por
`q=cnpj_id:{valor}`, onde `{valor}` é o **próprio `cnpj_id` de quem está logado** (não é uma
listagem cross-tenant, nem para `gestao`). Os 3 usuários seed (`medico@`, `operacao@`,
`gestao@pinsaude.com.br`) compartilham `cnpj_id: "11.222.333/0001-81"` no `realm-export.json` —
por isso só eles aparecem por padrão. Médicos de auto-cadastro (EPIC-14) nascem no Keycloak com
`cnpj_id` vazio (`KeycloakAdminService.createUserDesabilitado(..., cnpjId=null)`, correto na
criação — o médico ainda não tem vínculo com nenhuma empresa) e **nada sincronizava esse atributo
depois**, nem quando um operador atribuía manualmente um vínculo médico↔empresa. Resultado: um
médico auto-cadastrado, mesmo `ATIVO`, nunca aparecia em `/usuarios` para ninguém.

### Correção — sincroniza no primeiro vínculo, não sobrescreve depois
`MedicoService.adicionarVinculo()` agora chama `sincronizarCnpjIdKeycloak(medico, empresa)` **só
quando é o primeiro vínculo do médico** (`vinculoRepo.findByIdMedicoId(medicoId).isEmpty()` antes
do save) — vínculos adicionais com outras empresas não sobrescrevem, mesmo critério de "primeira
empresa define" já usado em `MedicoResponse.empresaId` (compat). `KeycloakAdminService` ganhou
`updateUserAttributeCnpjId(userId, cnpjId)`. Só roda se `medico.getKeycloakUserId() != null`
(médicos cadastrados manualmente, sem Keycloak vinculado pelo onboarding, não são afetados — o
deles é gerenciado só pela tela de Usuários). Falha na chamada ao Keycloak é tolerada (log apenas),
mesmo padrão de `liberarAcessoKeycloak` — não bloqueia a criação do vínculo em si.

### ⚠️ Armadilha crítica: PUT parcial só com `attributes` ZERA firstName/lastName (Keycloak 24 User Profile)
Diferente do que se poderia assumir pelo padrão já usado em `updateUserEnabled` (PUT parcial só com
`{"enabled": ...}`, que é seguro), um PUT parcial contendo **apenas** `{"attributes": {...}}`
**apaga `firstName`/`lastName`** do usuário. Confirmado empiricamente com `curl` direto na Admin
API nesta sessão — dois usuários reais tiveram o nome zerado até serem restaurados manualmente.
Causa: o realm tem User Profile habilitado (Keycloak 24) e `cnpj_id` está declarado em
`userProfileConfig.attributes` (ver seção "User Profile do Keycloak 24 bloqueia atributos
customizados") — qualquer atualização de `attributes` é tratada como submissão completa do
formulário de perfil, zerando os campos do perfil ausentes do corpo da requisição. O bug é
**específico de enviar `attributes`**, não de PUT parcial em geral.

**`updateUserAttributeCnpjId` corrigido para fazer GET antes do PUT** e reenviar
`firstName`/`lastName` no mesmo corpo:
```java
Map<String, Object> atual = restClient.get().uri(...).retrieve().body(...);
Map<String, Object> body = new LinkedHashMap<>();
body.put("firstName", atual.get("firstName"));
body.put("lastName", atual.get("lastName"));
body.put("attributes", Map.of("cnpj_id", List.of(cnpjId)));
restClient.put().uri(...).body(body)...
```
Qualquer código futuro que precise atualizar `attributes` de um usuário Keycloak neste realm deve
seguir o mesmo padrão — nunca enviar `attributes` isolado. Se encontrar um usuário com
`firstName`/`lastName` nulos no Admin Console sem explicação (consultar o nome de origem no banco:
`onboarding.medicos.nome` para médicos), essa é a causa mais provável.

### Limitação conhecida: vínculos criados antes da correção não são retroativos
Médicos que já tinham um vínculo atribuído **antes** deste fix não têm o `cnpj_id` sincronizado
automaticamente — a sincronização só dispara no evento de criação de um novo vínculo. Para um
médico legado nessa situação, remover e reatribuir o vínculo (`DELETE` + `POST
/api/medicos/{id}/vinculos`) dispara a sincronização retroativamente.

### Verificação direta via Keycloak Admin API (sem depender da tela)
Para confirmar o atributo sem logar como o usuário certo (que pode não existir em dev):
```bash
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
curl -s "http://localhost:8080/admin/realms/pinsaude/users/{keycloakUserId}" \
  -H "Authorization: Bearer $TOKEN"
# ou simular a query exata do gestao:
curl -s -G "http://localhost:8080/admin/realms/pinsaude/users" \
  --data-urlencode "q=cnpj_id:{cnpj-da-empresa}" -H "Authorization: Bearer $TOKEN"
```

### Senha do médico auto-cadastrado — não é definida na criação
`createUserDesabilitado` cria o usuário com `requiredActions: [UPDATE_PASSWORD, VERIFY_EMAIL]` e
sem senha. O caminho de primeiro acesso é o link "Esqueceu a senha?" em `LoginPage.tsx`
(`KC_RESET_PASSWORD_URL`, fluxo nativo `reset-credentials` do Keycloak — `resetPasswordAllowed:
true` no realm) — não é um passo manual que falta fazer, é o mecanismo pretendido. Em dev, o
e-mail de redefinição cai no Mailhog (`http://localhost:8025`).

---

## Alocação de Médico a Tomadores — RLS em tabela-filha nova (EPIC-15.1)

### ⚠️ FORCE ROW LEVEL SECURITY é obrigatório em toda tabela nova do faturamento — o padrão do V17 (sem FORCE) está incorreto
Testado empiricamente ao criar `faturamento.medico_tomadores`: o app do `faturamento` conecta
como `svc_faturamento` (`application.yml`), que é o **mesmo usuário dono da tabela** (Flyway roda
com o datasource da própria aplicação). Sem `FORCE ROW LEVEL SECURITY`, o owner **bypassa a
política de RLS automaticamente** — confirmado na prática: com `ALTER TABLE ... ENABLE ROW LEVEL
SECURITY` (sem FORCE), uma query como `svc_faturamento` com `app.current_tenant` de outro tenant
ainda retornou a linha, ou seja, a política virou letra morta para o próprio serviço.

Isso significa que `tomador_grupos_faturamento`, `tomador_modalidades` e
`tomador_servicos_operacionais` (criadas na V17, EPIC-13.1) **não têm FORCE** e sofrem do mesmo
bypass — inconsistente com o padrão mais usado no schema (`producoes`, `participacoes_producao`,
`conciliacoes`, `frequencias_medicas`, `tomadores` — todas com `FORCE ROW LEVEL SECURITY`,
confirmado via `pg_class.relforcerowsecurity`). O padrão correto e testado (usado por
`participacoes_producao`/`conciliacoes`, tabelas-filhas via subquery para uma tabela pai) é:
```sql
ALTER TABLE faturamento.minha_tabela ENABLE ROW LEVEL SECURITY;
ALTER TABLE faturamento.minha_tabela FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON faturamento.minha_tabela
    USING (
        COALESCE(current_setting('app.current_tenant', TRUE), '') = ''
        OR pai_id IN (SELECT id FROM faturamento.tabela_pai WHERE cnpj_id_tenant = current_setting('app.current_tenant', TRUE))
    )
    WITH CHECK (true);
```
`WITH CHECK (true)` evita bloquear o INSERT (a linha pai já existe e já pertence ao tenant no
momento da inserção — sem o problema de "USING sem WITH CHECK" de tabelas com `cnpj_id_tenant`
própria, documentado na seção de RLS do onboarding). **Nunca copiar o padrão da V17 sem adicionar
FORCE + WITH CHECK** — não corrigimos V17 retroativamente nesta task (fora de escopo), mas
qualquer nova tabela-filha do faturamento deve seguir o padrão com FORCE, não o da V17.

### Teste funcional de RLS deve simular o usuário real da aplicação, não o superuser
`docker exec ... psql -U postgres` (superuser) sempre bypassa RLS **mesmo com FORCE** — não serve
para validar isolamento. O teste correto conecta como o usuário de verdade do serviço
(`svc_faturamento`, senha em `tools/db/init.sql`) e faz `SET app.current_tenant = '...'` antes da
query, comparando tenant correto (retorna linha) vs. tenant errado (deve retornar vazio) vs. tenant
vazio (bypass esperado para gestão/portal).

### Migration de backfill funciona normalmente mesmo com FORCE ROW LEVEL SECURITY (EPIC-15.2)
Migrations Flyway rodam como `svc_faturamento` (o mesmo usuário/owner das tabelas) e **sem**
`app.current_tenant` definido na sessão. Pela policy padrão (`COALESCE(current_setting(...), '')
= ''`), sessão sem a variável definida se comporta como tenant vazio → **bypass total do RLS**,
mesmo em tabelas com `FORCE`. Ou seja: um `INSERT ... SELECT` de backfill cross-tenant dentro de
uma migration enxerga e grava linhas de **todos** os tenants normalmente — não precisa (e não deve)
setar `app.current_tenant` manualmente na migration. Confirmado ao criar `V22__backfill_medico_tomadores.sql`.

### Padrão de backfill idempotente a partir de múltiplas fontes sobrepostas
Quando o backfill precisa juntar dados de N tabelas-fonte que podem gerar a mesma combinação de
chave (ex.: o mesmo médico+tomador aparece tanto em produções quanto em frequências), usar um
`INSERT ... SELECT DISTINCT ... ON CONFLICT (colunas_unique) DO NOTHING` por fonte, na tabela de
destino já com `UNIQUE` — dispensa `UNION`/deduplicação manual entre as fontes, e a migration fica
idempotente por natureza (reexecutar os mesmos `INSERT`s não duplica nem falha).

### ⚠️ `mvn test` no faturamento NUNCA valida o mapeamento JPA contra o schema real (EPIC-15.3)
`services/faturamento/src/test/resources/application.properties` força
`spring.flyway.enabled=false` + `spring.jpa.hibernate.ddl-auto=none` com H2 em memória
(`jdbc:h2:mem:testdb`) — ou seja, a suíte de testes padrão nunca cria nem valida schema nenhum. Os
testes `@SpringBootTest` existentes (`RbacIntegrationTest`, `SecurityIntegrationTest`) só validam
autorização (401/403 antes de qualquer query tocar o banco); todo o resto da suíte usa `@Mock`
nos repositories. **Rodar `mvn test` com sucesso não prova que uma entidade JPA nova bate com o
schema real** — descoberto ao criar `MedicoTomador.java`, cuja mapeamento só foi de fato validado
apontando o teste para o Postgres local de verdade (override via `@SpringBootTest(properties =
{...})`, nunca commitado — não existe precedente de teste de repository isolado em nenhum serviço
do monorepo):
```java
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5433/pinsaude",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.datasource.username=svc_faturamento",
    "spring.datasource.password=faturamento_dev",
    "spring.jpa.properties.hibernate.default_schema=faturamento",
    "spring.flyway.enabled=false",   // schema já migrado de verdade, não precisa recriar
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional  // rollback automático — não suja o banco de dev
```
Ao escrever esse teste ad-hoc contra dados reais (tomadores já existentes por causa do `FK`),
**cuidado com dados pré-existentes** (ex.: backfill do EPIC-15.2): `findByTomadorId(tomadorReal)`
pode retornar mais linhas do que as inseridas no próprio teste — usar `medicoId` aleatório (nunca
colide com dado real) e `assertThat(lista).extracting(...).contains(...)` em vez de `hasSize(N)`
fixo quando a chave de busca é uma entidade que já tem histórico real no banco.

### ⚠️ `grep -o '"id":"[^"]*"'` conta demais em DTOs com listas aninhadas (EPIC-15.5)
Ao testar manualmente via `curl` + `grep` quantos registros um endpoint de listagem retorna,
**nunca** usar `grep -o '"id":"[^"]*"' | wc -l` em respostas cujo DTO tenha coleções aninhadas com
seu próprio campo `id` — é exatamente o caso de `TomadorResponse` (`aliquotas`, `cnaes`,
`servicos` cada um tem `id` próprio). O grep casa TODOS os `"id":"..."` da resposta inteira,
não só os do objeto de topo, inflando a contagem (chegou a mostrar 32 "tomadores" onde só
existiam 12 de verdade — quase 1h perdida investigando uma "duplicação de dados" que não
existia, incluindo reiniciar o serviço e comparar contagens via `psql` direto). **Solução:**
parsear o JSON de verdade (`node -e "JSON.parse(...).length"` ou um script `.js` no scratchpad)
para contar só os elementos do array de nível superior — nunca grep ingênuo em JSON com
estrutura aninhada desconhecida.

### Portal nunca descriptografa o CNPJ do tomador — só usa razao_social_nome/municipio (EPIC-15.6)
`GET /api/portal/tomadores` (novo endpoint, EPIC-15.6) segue o precedente já estabelecido em
`PortalService.getProducoes()` (EPIC-06.5): ao fazer `JOIN faturamento.tomadores`, só seleciona
`razao_social_nome`/`municipio` — **nunca** `cnpj_cpf_tomador_criptografado`. Descriptografar
exigiria chamar `faturamento.decrypt_sensitive(bytea, crypto_key)` via SQL nativo (a função é
`SECURITY DEFINER` e tem EXECUTE liberado para PUBLIC por padrão, então funcionaria), mas o
`services/portal` não tem `crypto.key` configurado em nenhum lugar hoje — nenhum endpoint do
portal jamais precisou do CNPJ do tomador, só do nome para exibição em listas/dropdowns. Antes de
adicionar `crypto.key` ao portal só para isso, confirmar que a tela realmente precisa mostrar o
CNPJ (não precisou até agora).

### Teste manual do portal — resolver o medico_id real primeiro, evita massa de dados fake
Para testar `GET /api/portal/tomadores` (ou qualquer endpoint que dependa de alocações reais),
buscar o `medico_id` de `onboarding.medicos` pelo e-mail do usuário de teste
(`medico@pinsaude.com.br`) e checar `faturamento.medico_tomadores` para esse ID — o backfill do
EPIC-15.2 normalmente já populou vínculos reais o suficiente para testar sem precisar criar dado
fake. `SELECT id FROM onboarding.medicos WHERE email = '...'` seguido de `SELECT * FROM
faturamento.medico_tomadores WHERE medico_id = '<id>'`.

### ⚠️ Teste pré-existente quebrado no portal — não relacionado a esta task
`PortalMedicoControllerTest.extrato_semImplementacao_retornaListaVazia` falha desde o EPIC-06.4
(quando `GET /api/portal/extrato` passou a retornar um `ExtratoResponse` de verdade, não mais uma
lista vazia) — o teste nunca foi atualizado e ainda espera `$` como array vazio. `git log` confirma
que o arquivo de teste só foi tocado no commit inicial do EPIC-06.1, nunca depois. Não corrigido
nesta task (fora de escopo) — sinalizado aqui para quem for mexer nesse arquivo de teste no futuro.

### ⚠️ `ProducaoService` nunca teve testes unitários antes do EPIC-15.7
Ao adicionar a validação de bloqueio em `ProducaoService.criar()`, descobri que **não existia
nenhum arquivo de teste** para esse serviço (`ProducaoController`/`ProducaoService` são código do
EPIC-04.4, bem antigo) — nenhum `ProducaoServiceTest.java` em lugar nenhum do módulo. Criado
`producao/ProducaoServiceTest.java` cobrindo a nova validação (médico não alocado → 422, médico
alocado → sucesso, múltiplos participantes com um não alocado → 422) mais alguns testes de
sanidade do caminho já existente (tomador inexistente → 404, valor total zero → 400). **Não é
cobertura completa do serviço** (cálculo de preview, listagem com filtros, etc. continuam sem
teste) — só o suficiente para validar com segurança a mudança desta task. Sinalizado aqui para
quem for ampliar a cobertura no futuro.

### Validação de bloqueio vale para TODAS as roles que podem criar produção, incluindo `medico`
`POST /api/producoes` aceita `hasAnyRole('operacao','gestao','medico')` — o médico pode lançar a
própria produção diretamente (não só via portal). Testado manualmente com token real de médico:
tentar criar produção com um `medicoId` (dele mesmo ou de terceiro) não alocado ao tomador
retorna 422 igual para qualquer role, sem bypass — confirma o requisito do plano ("sem bypass por
papel") na prática, não só na intenção do código.

### Teste manual do cenário crítico do backfill — médico com histórico real não pode ficar bloqueado
Antes de validar o bloqueio, testei o caso mais importante: um médico **real** (`medico@pinsaude.com.br`,
já com 11 alocações vindas do backfill do EPIC-15.2) consegue criar uma **nova** produção no mesmo
tomador onde já tem histórico → 201 normalmente. Esse é exatamente o cenário que o backfill existe
para proteger — se esse teste falhasse, seria sinal de que o backfill não rodou ou está incompleto
no ambiente.

### `FrequenciaService.criar()` — ordem das validações importa ao testar manualmente (EPIC-15.8)
A ordem é: (1) duplicidade `medico+setor+competência` (409) → (2) setor existe (404) → (3) setor
pertence ao tomador informado (422) → (4) médico alocado ao tomador (422). Ao testar manualmente o
cenário 3 (setor de outro tomador) reusando o mesmo médico+setor+competência de um teste anterior,
a checagem de duplicidade (1) dispara primeiro e mascara a validação que se quer testar — retorna
409 em vez do 422 esperado, parecendo (por engano) que a nova validação não está funcionando.
Sempre usar uma competência nova a cada cenário de teste manual deste endpoint.

---

## E-mails Nativos do Keycloak em Inglês — Faltava Internacionalização no Realm

### Sintoma: "Reenviar Convite" (tela de Usuários) envia e-mail em inglês, genérico
`UsuarioService.reenviarConvite()`/`convidar()` (services/gestao) chamam
`KeycloakAdminService.sendInvitationEmail()`, que aciona o endpoint nativo do Keycloak
`PUT /users/{id}/execute-actions-email` — o Keycloak quem renderiza e envia esse e-mail (não é
um template Thymeleaf nosso), usando seu tema `email` embutido. Sem internacionalização habilitada
no realm, o Keycloak sempre usa o locale padrão do tema (inglês): assunto "Update Your Account",
corpo "Your administrator has just requested...". Mesmo problema afeta o e-mail nativo de "Esqueci
minha senha" (`resetPasswordAllowed`, acionado pelo link `KC_RESET_PASSWORD_URL`/`primeiroAcessoUrl`)
e o de verificação de e-mail — todos usam o mesmo tema `email` do Keycloak.

### Correção — habilitar internacionalização com pt-BR (sem precisar de tema customizado)
O Keycloak 24 já traz traduções pt-BR embutidas no tema base `email` (mensagens como
`executeActionsSubject`/`executeActionsBody`, `passwordResetSubject`/`Body` etc. já têm bundle
`messages_pt_BR.properties` no tema padrão) — não foi necessário criar um tema customizado nem
montar volume novo no container. Bastou habilitar i18n no realm, em `tools/keycloak/realm-export.json`:
```json
"internationalizationEnabled": true,
"supportedLocales": ["pt-BR"],
"defaultLocale": "pt-BR",
```
Como o projeto é 100% em português, `supportedLocales` só tem `pt-BR` — não precisa de seletor de
idioma nem lógica de detecção. Resultado confirmado no Mailhog: assunto muda de "Update Your
Account" para "Atualização de conta", corpo inteiro traduzido.

### Aplicar em um Keycloak já rodando (sem esperar reimport do realm)
`--import-realm` no `docker-compose.yml` só importa o `realm-export.json` na **criação** do realm
— não reaplica em um realm que já existe (o caso comum em dev, onde o container já rodou antes).
Para aplicar imediatamente sem recriar o container/volume, usar a Admin API direto:
```bash
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
curl -s -X PUT "http://localhost:8080/admin/realms/pinsaude" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"internationalizationEnabled": true, "supportedLocales": ["pt-BR"], "defaultLocale": "pt-BR"}'
```

### ⚠️ Achado adicional: `updateUserAttributeCnpjId` ainda zerava o `email` do usuário
Investigando por que "Reenviar Convite" retornava `400 "User email missing"` para médicos que já
tinham passado pela sincronização de `cnpj_id` (ver seção "Sincronização de cnpj_id no Keycloak"),
descobri que a correção anterior daquele bug (reenviar `firstName`/`lastName` no mesmo PUT que
`attributes`) era **incompleta** — o Keycloak 24 com User Profile trata QUALQUER PUT com
`attributes` como submissão completa do formulário de perfil, e `email` também é um campo desse
formulário. A correção anterior só reenviava `firstName`/`lastName`, então `email` continuava
sendo zerado silenciosamente. Dois usuários reais (`Fernando Costa Nardi`, `Dr. Roberto Almeida
Costa`) ficaram sem e-mail no Keycloak por causa disso — restaurados manualmente via Admin API.

**Correção definitiva:** `KeycloakAdminService.updateUserAttributeCnpjId()` (onboarding) agora faz
GET da representação **completa** do usuário e copia tudo para o corpo do PUT, sobrescrevendo só
`attributes` — em vez de escolher campos a dedo (`firstName`, `lastName`), que é frágil a qualquer
novo campo que o Keycloak passe a gerenciar no futuro:
```java
Map<String, Object> atual = restClient.get().uri(...).retrieve().body(...);
Map<String, Object> body = new LinkedHashMap<>(atual);
body.put("attributes", Map.of("cnpj_id", List.of(cnpjId)));
restClient.put().uri(...).body(body)...
```
Essa é a lição geral: **qualquer PUT parcial para `/admin/realms/{realm}/users/{id}` que inclua
`attributes` deve sempre partir de um GET completo e sobrescrever só o campo desejado** — nunca
montar o corpo do zero escolhendo campos manualmente.

---

## Tema Customizado de E-mail do Keycloak — Branding Pin Saúde (pós-EPIC-14.9)

### De i18n pt-BR (texto correto) para tema visual (logo, cor, card) — dois problemas diferentes
Habilitar `internationalizationEnabled`/pt-BR (seção anterior) resolveu o **idioma** dos e-mails
nativos do Keycloak, mas o layout continuava sendo o HTML mínimo do tema `base` (`<html><body>
<#nested></body></html>`, sem nenhum estilo) — bem diferente dos e-mails Thymeleaf do onboarding
(`templates/email/*.html`, com header azul, card branco centralizado e rodapé). Corrigido criando
um tema de e-mail customizado (`tools/keycloak/themes/pinsaude/email/`) com `parent=base` no
`theme.properties` — herda todos os templates de conteúdo (`executeActions.ftl`,
`password-reset.ftl`, `email-verification.ftl` etc.), só sobrescrevendo:

1. **`html/template.ftl`** — o macro `emailLayout` que TODOS os templates de conteúdo chamam via
   `<@layout.emailLayout>...</@layout.emailLayout>`. Sobrescrever só este arquivo já aplica o
   header azul (`#02A9F7`) + "Pin Saúde" + subtítulo, o card branco centralizado (`width:600`,
   `border-radius`, `box-shadow`) e o rodapé cinza claro a **todos** os e-mails nativos de uma vez
   — sem precisar duplicar cada template de conteúdo individualmente.
2. **`messages/messages_pt_BR.properties`** — sobrescreve só as chaves `*BodyHtml`
   (`executeActionsBodyHtml`, `passwordResetBodyHtml`, `emailVerificationBodyHtml`) para trocar o
   link `<a href="{0}">texto</a>` simples por um botão de CTA estilizado (`<table><tr><td
   style="background:#02A9F7;border-radius:8px;"><a href="{0}" style="...">Texto →</a></td></tr>
   </table>`), consistente com os botões dos e-mails Thymeleaf. As versões plaintext e os assuntos
   continuam herdados do tema base (já corretos em pt-BR). **`kcSanitize` (o sanitizador do
   Keycloak sobre o HTML de `msg()`) permite `style` inline em `table`/`td`/`a`** — testado e
   confirmado, o botão azul renderiza corretamente no Mailhog, não foi preciso usar CSS externo.
3. `"emailTheme": "pinsaude"` no realm (`realm-export.json` + aplicado via Admin API na sessão).

### Descobrindo a estrutura de templates do tema `base` sem documentação local
Os `.ftl`/`.properties` do tema padrão não existem como arquivos soltos — vêm empacotados dentro
de `/opt/keycloak/lib/lib/main/org.keycloak.keycloak-themes-<versão>.jar` dentro do container. Sem
`jar`/`unzip` disponíveis no container (imagem mínima), o caminho usado foi:
```powershell
docker cp pinsaude-keycloak:/opt/keycloak/lib/lib/main/org.keycloak.keycloak-themes-24.0.5.jar ./kc-themes.jar
```
e então extrair localmente (`unzip` no Git Bash funciona direto, já que um `.jar` é um `.zip`) para
inspecionar `theme/base/email/html/*.ftl` (estrutura/macros) e `theme/base/email/messages/
messages_pt_BR.properties` (textos e nomes/ordem exata dos parâmetros `{0}`, `{1}`... de cada
`msg(...)`, que **variam por template** — `executeActionsBodyHtml` tem 5 parâmetros
`(link, linkExpiration, realmName, requiredActionsText, linkExpirationFormatter(...))` enquanto
`passwordResetBodyHtml`/`emailVerificationBodyHtml` têm só 4, sem `requiredActionsText`).

### Aplicar tema customizado num Keycloak já rodando (sem recriar o container)
Igual à seção de i18n: `--import-realm` não reaplica em realm já existente, e um volume novo no
`docker-compose.yml` só é montado na **criação** do container. Para testar imediatamente:
```powershell
docker exec pinsaude-keycloak mkdir -p /opt/keycloak/themes/pinsaude/email/html /opt/keycloak/themes/pinsaude/email/messages
docker cp tools/keycloak/themes/pinsaude/email/theme.properties pinsaude-keycloak:/opt/keycloak/themes/pinsaude/email/theme.properties
docker cp tools/keycloak/themes/pinsaude/email/html/template.ftl pinsaude-keycloak:/opt/keycloak/themes/pinsaude/email/html/template.ftl
docker cp tools/keycloak/themes/pinsaude/email/messages/messages_pt_BR.properties pinsaude-keycloak:/opt/keycloak/themes/pinsaude/email/messages/messages_pt_BR.properties
```
Depois setar `emailTheme` via Admin API (`PUT /admin/realms/pinsaude` com `{"emailTheme":
"pinsaude"}`) — Keycloak em modo `start-dev` não cacheia temas/templates, então o efeito é
imediato, sem reiniciar o container. Para um container **recriado do zero** (`docker compose up`
depois desta mudança), o volume `./tools/keycloak/themes/pinsaude:/opt/keycloak/themes/pinsaude:ro`
já monta os arquivos automaticamente e o `emailTheme` já vem do `realm-export.json`.

### Onde estender no futuro (novos tipos de e-mail nativo do Keycloak)
Qualquer novo fluxo que dispare e-mail nativo do Keycloak (ex.: `email-verification-with-code`,
eventos de segurança como `event-update_password.ftl`) já herda automaticamente o layout
`template.ftl` — só precisa de uma entrada nova em `messages_pt_BR.properties` se quiser o botão de
CTA estilizado; sem override, o texto plaintext do tema `base` (já em pt-BR) continua funcionando,
só sem o botão colorido.

---

## E-mail de Ativação — Faltava no Caminho de Auto-ativação (pós-EPIC-14.9)

### Bug: `verificarAtivacaoAutomatica()` nunca disparava o e-mail "MEDICO_ATIVADO"
`MedicoService.ativar()` (botão manual "Ativar Médico") sempre chamou
`notificacaoService.notificarMedicoAtivado(medico)`. `verificarAtivacaoAutomatica()` (disparada
por `assinarContratoManual()` e `atualizarJuntaComercial()`/webhook Clicksign — o caminho que
médicos de auto-cadastro efetivamente percorrem, já que eles nunca passam pelo botão manual)
**nunca fazia essa chamada**. Resultado: todo médico auto-cadastrado ativado automaticamente virava
`ATIVO` silenciosamente, sem nenhum e-mail de boas-vindas — só descoberto testando o fluxo completo
(nenhum teste unitário cobria essa lacuna porque cada caminho de ativação era testado
isoladamente). Corrigido adicionando a mesma chamada em `verificarAtivacaoAutomatica()`.

### E-mail "MEDICO_ATIVADO" não tinha instrução de primeiro acesso — só o médico auto-cadastro precisa
Mesmo com o e-mail disparado, o botão "Acessar meu Portal" simplesmente linkava para
`http://localhost:3000` — inútil para um médico de auto-cadastro, que não tem senha nenhuma
definida (`createUserDesabilitado` nunca passa por `sendInvitationEmail`/`execute-actions-email` do
Keycloak, que só existe no `KeycloakAdminService` do **gestao**, usado no convite manual). A
`NotificacaoService.notificarMedicoAtivado()` (onboarding) passou a montar e incluir
`primeiroAcessoUrl` no `dados` do e-mail — o mesmo link nativo `reset-credentials` do Keycloak
usado por `KC_RESET_PASSWORD_URL` no frontend, construído a partir de
`KeycloakAdminProperties.serverUrl()/realm()` + client hardcoded `"pinsaude-web"` (duplicado do
frontend, já que o e-mail é montado sem acesso ao build do frontend). Incluído **sempre**, mesmo
para médicos cadastrados manualmente via convite do gestao (que já têm senha) — inofensivo, só uma
alternativa de redefinição de senha a mais, evita ter que diferenciar por `origemCadastro`.
Template `medico-ativado.html` ganhou uma seção destacada "🔑 Primeiro acesso?" com botão "Definir
minha senha →" antes do CTA "Acessar meu Portal".

### Testando localmente: médicos antigos podem não ter `checklist_conduta` (pré-fix EPIC-14.9)
Médicos de auto-cadastro criados **antes** do fix de seeding do checklist (EPIC-14.9) não têm
linha em `onboarding.checklist_conduta` e a tela de Aprovação não mostra nenhum editor para criá-la
retroativamente (`ChecklistEditor` só renderiza quando `medico.checklist != null`). Para destravar
um médico legado desses em teste manual, inserir direto via SQL:
```sql
INSERT INTO onboarding.checklist_conduta
  (medico_id, numero_conselho_verificado, registros_disciplinares, processos_medicos, verificado_por, verificado_em)
VALUES ('<medico-id>', true, true, true, 'teste-manual', now());
```
Médicos criados após o fix já nascem com essa linha automaticamente — não precisam desse workaround.

---

## Alocação de Médico a Tomadores — Frontend `tomadoresApi.ts` (EPIC-15.9)

### Branches de backend paralelas mescladas em `main` entre a criação e a execução da task
A task 15.9 (frontend) depende de 15.4/15.5 (backend), mas as 3 tasks foram criadas como branches
irmãs (`feature/pinsaude-15.3/15.4/15.5`), cada uma a partir de `main`, e não sequencialmente uma
sobre a outra. No momento em que a 15.9 começou a ser codada, `feature/pinsaude-15.9` (criada
alguns dias antes, também a partir de `main`) estava **desatualizada**: as PRs #106/#107/#108
(15.3/15.4/15.5) só foram mescladas em `main` minutos antes desta sessão. Sem atualizar a branch
local, o arquivo `tomadoresApi.ts` estaria sendo escrito às cegas, sem o contrato real do backend
disponível no working tree (nenhuma classe `MedicoTomador*` existia localmente até o merge).
**Lição:** antes de implementar uma task de frontend que "depende de" tasks de backend, sempre
conferir com `gh pr list --state all` se as dependências já foram de fato mescladas em `main` —
não confiar apenas no status do ClickUp ("ready to deploy" pode significar só "aprovado, PR aberta
mas não mesclada", como era o caso de 15.6/15.7/15.8 nesta mesma EPIC) — e rodar
`git merge origin/main` (ou rebase) na branch de trabalho antes de escrever qualquer código que
consuma esse contrato.

### Contrato `MedicoTomadorResponse` — sem envelope, snake→camel automático
`GET/POST /api/tomadores/{id}/medicos` retornam `{ medicoId, tomadorId, createdAt }` (sem
`id` do vínculo em si — só as duas FKs + timestamp). O tipo `MedicoTomador` no frontend espelha
exatamente esse shape. `POST` aceita `{ medicoId }` no body (schema `MedicoTomadorRequest`,
`@NotNull UUID medicoId`) e retorna `201 Created` com o mesmo shape do GET.
`DELETE /api/tomadores/{id}/medicos/{medicoId}` retorna `204 No Content` — sem corpo, mapeado por
`handleResponse` que já trata `res.status === 204` retornando `undefined as T`.

### `listar(q?, medicoId?)` — extensão por query param opcional, 100% compatível com chamadas existentes
A assinatura de `tomadoresApi.listar()` ganhou um segundo parâmetro opcional `medicoId?: string`,
que popula `?medicoId=<uuid>` via `URLSearchParams` só quando presente. Como todos os 7 call sites
existentes (`TomadoresPage`, `ProducaoNovaPage`, `FrequenciasPage`, `FechamentoPage`,
`ProducoesPage`, `PortalProducaoNovaPage`, `PortalFrequenciaPage`) chamam `listar()` sem argumentos,
a mudança não quebra nenhum consumidor — confirmado com `tsc --noEmit` + build de produção limpos.
O consumo real desse filtro (telas passando `medicoId` de fato) é escopo de 15.13/15.14/15.15/15.16,
fora desta task.

---

## Alocação de Médico a Tomadores — Frontend `portalApi.ts` (EPIC-15.10)

### Descrição da task no ClickUp ficou desatualizada em relação ao contrato real do backend
A task 15.10 (criada em 2026-07-24, antes de 15.6 ser codada) especificava o tipo
`TomadorPortal { id, razaoSocial, cnpj, municipio }` — mas o `TomadorPortalResponse` (EPIC-15.6,
mesclado em `main` dias depois) **nunca inclui `cnpj`**: `PortalService.getTomadoresDoMedico()`
retorna só `{ id, razaoSocial (razao_social_nome), municipio }`, exatamente pela decisão já
documentada em "Portal nunca descriptografa o CNPJ do tomador" (seção EPIC-15.6 acima) — o portal
não tem `crypto.key` configurado e nunca precisou expor o CNPJ do tomador em nenhuma tela. Segui o
contrato real do backend (conferido lendo `TomadorPortalResponse.java`/`PortalService.java`
diretamente), não a descrição da task, e omiti o campo `cnpj` do tipo `TomadorPortal` no frontend.
**Lição geral (reforça a de EPIC-15.9):** descrições de task no ClickUp são escritas no momento do
planejamento e podem ficar desatualizadas assim que a implementação de uma dependência (mesmo já
concluída) toma uma decisão diferente da prevista — sempre validar contra o código-fonte real da
dependência, nunca só contra o texto da task.

### `getTomadoresAlocados()` — GET simples, sem paginação nem filtro
`GET /api/portal/tomadores` (`hasRole('medico')`, resolve o médico via e-mail do JWT, mesmo padrão
de todos os outros endpoints do portal) retorna a lista completa de tomadores alocados ao médico
logado, ordenada por `razao_social_nome` no próprio SQL — sem paginação, sem query params. O método
no frontend segue o padrão mais simples já usado por `getPerfil()`/`getVinculosEmpresa()` (fetch
direto, sem `URLSearchParams`).

---

## Seção "Tomadores Associados" em MedicoPerfilPage.tsx (EPIC-15.11)

### Reuso de `listar(medicoId)` da EPIC-15.9 em vez de `listarMedicos` — evita N+1
A descrição da task citava `tomadoresApi.listarMedicos/adicionarMedico/removerMedico` como a API
consumida, mas `listarMedicos(tomadorId)` lista médicos de **um** tomador — não serve para "listar
tomadores de **um** médico" sem fazer um loop `listarMedicos` por tomador do tenant (N+1). A forma
correta e já existente para essa direção é `tomadoresApi.listar(q?, medicoId?)` (extensão da
EPIC-15.5/15.9): uma chamada com `medicoId` retorna só os tomadores já alocados, outra sem filtro
retorna todos (para montar o combo de "Adicionar"). `adicionarMedico(tomadorId, medicoId)` e
`removerMedico(tomadorId, medicoId)` continuam sendo os únicos usados para mutação, exatamente como
a task previa.

### Espelhamento exato do padrão "Empresas Associadas" — inclusive a ausência de padrão de estado por página
Estado (`todosTomadores`/`tomadoresAlocados`/`addTomadorId`/`addingTomador`/`removingTomadorId`/
`tomadorError`), efeito de carga (dentro do mesmo `useEffect` que já buscava o médico) e handlers
(`handleAdicionarTomador`/`handleRemoverTomador`) replicam 1:1 a estrutura já usada para
`empresas`/`vinculo*` — incluindo o update otimista da lista local após sucesso da API (sem
refetch), que é o padrão já estabelecido nesta página. `canEdit` (`isGestao || isOperacao`)
substitui `isGestao` sozinho, e não há a regra de "não remover o último vínculo" (perguntado
explicitamente na task — diferente do `vinculo` médico↔empresa do onboarding).

### `formatDocumentoTomador` — helper duplicado localmente, não exportado do TomadoresPage.tsx
`TomadoresPage.tsx` já tem uma função idêntica (`formatDocumento`) mas não exportada — replicada
localmente em `MedicoPerfilPage.tsx` como `formatDocumentoTomador` em vez de exportar/importar,
seguindo o mesmo precedente já estabelecido no projeto (funções helper pequenas e específicas de
tela não são extraídas para um módulo compartilhado só por aparecerem em duas páginas).

### Teste manual ponta-a-ponta confirmou o gating de permissão sem alteração de código
Testado ao vivo: como `operacao`, adicionar um tomador (`HAPVIDA ASSISTENCIA MEDICA S.A.`) e
remover, cada ação confirmada com reload de página (não é só estado otimista — persiste no
backend). Como `medico`, a mesma URL (`/medicos/{id}`) é acessível (rota SPA não bloqueia por
role) mas renderiza **somente leitura**: sem select "Adicionar tomador...", sem botão "Adicionar",
sem ícone de remover em nenhuma linha — `canEdit=false` já cobre tudo automaticamente, sem
nenhuma lógica extra necessária. Zero erros no console em ambos os cenários.

### Dado de seed com tomador duplicado (mesmo CNPJ, 2 linhas) — não é bug desta task
Ao testar com o médico `medico@pinsaude.com.br` (mesmo dos testes de EPIC-15.7/15.8), a lista de
"Tomadores Associados" mostra "SECRETARIA DA SAUDE DO ESTADO DO CEARA" duas vezes (mesmo CNPJ
`07.954.571/0001-04`). São dois registros de `tomadores` distintos (IDs diferentes) do backfill de
dados de teste, não uma duplicação introduzida pelo frontend nem pelo backend do EPIC-15.5/15.9 —
o filtro de duplicidade do backend é por `(tomador_id, medico_id)`, não por CNPJ. Não corrigido
(dado de ambiente de dev, fora do escopo desta task) — sinalizado para quem for mexer em seed de
tomadores no futuro.

---

## `tools/scripts/start-infra.ps1` sem BOM UTF-8 quebra o parser do Windows PowerShell 5.1

### Sintoma
`.\tools\scripts\start-infra.ps1` falha com `A cadeia de caracteres não tem o terminador: "` e
`'}' de fechamento ausente` em linhas que, abertas em qualquer editor, parecem perfeitamente
válidas (ex.: `Write-Host "  Mailhog     : http://localhost:8025"`).

### Causa
O arquivo estava salvo em UTF-8 **sem BOM** (confirmado com
`[System.IO.File]::ReadAllBytes($path)` — primeiros 3 bytes não eram `EF BB BF`). O Windows
PowerShell 5.1 (`powershell.exe`, diferente do PowerShell 7/`pwsh`) só interpreta um arquivo
`.ps1` como UTF-8 se ele tiver o BOM explícito; sem BOM, o parser usa o codepage ANSI ativo do
sistema para decodificar o arquivo. Os caracteres multi-byte do script (`✅`, `⚠️`, acentos como
`á`/`ã`/`ç`, o em-dash `—`) viram sequências de bytes inválidas nesse codepage, e a corrupção
resultante confunde o tokenizer do PowerShell — não necessariamente na mesma linha do caractere
especial, o que torna a mensagem de erro enganosa (aponta para uma linha "normal" mais adiante).

### Solução
Regravar o arquivo com BOM UTF-8 explícito, preservando o conteúdo:
```powershell
$path = "tools\scripts\start-infra.ps1"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$utf8Bom = New-Object System.Text.UTF8Encoding($true)
[System.IO.File]::WriteAllText($path, $content, $utf8Bom)
```
Isso força o PowerShell 5.1 a reconhecer o arquivo como UTF-8 independente do codepage do sistema.
**Qualquer novo `.ps1` com acentos/emojis neste repositório deve ser salvo com BOM UTF-8** — bash
(`cat`/`file`) lê o arquivo normalmente sem BOM, então esse tipo de bug só aparece ao executar de
verdade no PowerShell do Windows, nunca em uma revisão de código feita fora dele.

---

## `TomadorMedicosModal.tsx` — visão inversa de "Tomadores Associados" (EPIC-15.12, opcional)

### `MedicoTomador` não tem nome do médico — join client-side com `medicosApi.listar()`
`tomadoresApi.listarMedicos(tomadorId)` retorna só `{ medicoId, tomadorId, createdAt }` (sem nome/
CRM). Para exibir "Dr. Fulano — CRM 12345/SP" no modal, é preciso um segundo fetch com
`medicosApi.listar(0, 1000)` e montar um `Map<string, Medico>` por `id` no client — mesmo padrão
já usado no `MedicoPerfilPage.tsx` (EPIC-15.11) para resolver nome de tomador a partir do
`MedicoTomador`, só que na direção oposta.

### `GET /api/medicos` não inclui a role `medico` — fallback silencioso obrigatório
Diferente de `GET /api/tomadores` (`hasAnyRole('operacao','gestao','financeiro','contabil',
'medico')`), o endpoint `GET /api/medicos` do onboarding **não** libera a role `medico`
(`hasAnyRole('gestao','operacao','financeiro','contabil')`). Como a rota SPA `/tomadores` é
alcançável por navegação direta de URL por qualquer role autenticada (mesmo achado do EPIC-15.11
— rotas do React Router não são bloqueadas por papel, só os elementos condicionais dentro da
página), um usuário `medico` que abrir o modal `TomadorMedicosModal` dispara um 403 nessa segunda
chamada. A troca é sempre feita com `.catch(() => [] as Medico[])` (nunca deixar propagar) —
com `todosMedicos` vazio, o componente já tem um fallback nativo (`medico?.nome ?? a.medicoId`)
que exibe o UUID cru em vez de travar o modal ou lançar erro no console. Testado ao vivo: logado
como `medico`, o modal abre normalmente mostrando o UUID; logado como `operacao`, mostra o nome
completo + CRM/UF normalmente. Zero erros de console em ambos os cenários.

### Botão do modal fica visível para todos os papéis — só os controles internos são gated
Igual ao ícone `Layers` (grupos de faturamento) já existente em `TomadoresPage.tsx`, o novo botão
"Médicos alocados" (ícone `Stethoscope`) aparece na linha de qualquer tomador independente de
`canWrite` — é o modal que decide, internamente, se mostra os controles de adicionar/remover
(`canWrite = isOperacao || isGestao`, já calculado na página e repassado como prop). Não há
necessidade de esconder o botão em si: um usuário só-leitura abrindo o modal vê a lista, sem
nenhuma opção de mutação.

---

## Filtro por médico em ProducaoNovaPage.tsx (EPIC-15.13)

### Fluxo real da tela é invertido em relação à leitura literal da task — filtro reage ao(s) participante(s), não o contrário
A tela pede o Tomador **antes** da lista de Participantes (médicos). A task descrevia "ao trocar o
médico selecionado, refazer `tomadoresApi.listar(medicoId)`" — implementado exatamente assim, mas
o efeito reage à lista completa de médicos já escolhidos em **todos** os participantes (chave
derivada `medicoIdsSelecionados`), não a um único médico isolado. Sem médico nenhum selecionado,
`tomadoresDisponiveis` cai no array completo já carregado no mount (comportamento anterior
preservado); assim que o primeiro participante ganha um médico, o combo de Tomador já filtra.

### Produção multi-médico — interseção via N chamadas paralelas, não client-side filtering do array já carregado
Para `N` médicos distintos selecionados nos participantes, o efeito dispara `N` chamadas
`tomadoresApi.listar(undefined, medicoId)` em paralelo (`Promise.all`) e computa a interseção por
`id` (`primeira.filter(t => resto.every(lista => lista.some(x => x.id === t.id)))`) — não reaproveita
o array `tomadores` (carregado uma vez, sem filtro, no mount) porque esse array não carrega a
informação de qual tomador pertence a qual médico; só o endpoint filtrado por `medicoId` tem essa
informação. Testado ao vivo com 2 médicos reais (`Medico Teste`: 11 tomadores; `Fulano de tal`: 1
tomador) — a interseção reduziu corretamente para exatamente o 1 tomador em comum, confirmando que
o algoritmo de interseção funciona mesmo em cenários altamente assimétricos.

### Tomador já selecionado que "sai" da interseção não é limpo automaticamente — só um aviso
Se o usuário seleciona um Tomador e depois adiciona um segundo participante cujo médico não está
alocado a esse tomador, a seleção **não é apagada** (evita surpreender o usuário limpando um campo
que ele já preencheu) — em vez disso, aparece um aviso amarelo "Atenção: o tomador selecionado não
está alocado a todos os médicos participantes." A validação definitiva (422) já existe no backend
desde a EPIC-15.7; este aviso é só uma dica de UX antes do submit, não uma trava client-side.
Testado ao vivo: o aviso apareceu corretamente no cenário de interseção quase vazia.

### `tomadorObj`/`servicosDisponiveis`/preview fiscal continuam usando o array `tomadores` completo, nunca o filtrado
O array filtrado (`tomadoresDisponiveis`) só alimenta as opções do `Autocomplete` de Tomador. Todo
o resto do formulário que precisa dos dados completos de um tomador já selecionado (CNAEs, serviços
vinculados, cálculo fiscal) continua consultando `tomadores` (a lista completa carregada uma vez no
mount) — nunca o array filtrado, que pode nem conter o tomador já selecionado no cenário de
interseção vazia descrito acima.

### Ambiente de teste sem o serviço `fiscal` (porta 8081) rodando — preview fiscal e combo de empresa vazios
Nesta sessão de teste manual só `faturamento`(8082)/`onboarding`(8085)/`gateway`(8090)/`web`(3000)
estavam de pé — sem o serviço `fiscal` nem dados de `empresas` no ambiente, o card de "Cálculo
Fiscal da Nota" nunca resolve e o select de "Empresa Emissora" fica vazio. Isso é uma limitação do
ambiente de teste (fora do escopo desta task, que só mexe no filtro de tomador por médico) — não
uma regressão introduzida por esta mudança; confirmado que o restante do formulário (tomador,
CNAE, serviço, participantes, valores, total) funciona normalmente até o ponto em que dependeria
desses dois pré-requisitos ausentes.

---

## Filtro por médico em FrequenciasPage.tsx (EPIC-15.14)

### Fluxo mais simples que EPIC-15.13 — 1 médico só, sem interseção
Diferente de `ProducaoNovaPage.tsx` (múltiplos participantes/médicos por produção), o modal
"Nova Frequência Médica" tem um único campo Médico (a frequência é sempre de 1 médico + 1 setor +
1 competência). O filtro do combo de Tomador é então uma única chamada
`tomadoresApi.listar(undefined, medico.id)` reagindo a `medico?.id`, sem necessidade do algoritmo
de interseção de N chamadas paralelas criado na EPIC-15.13. Mesmo padrão de hint/aviso (mensagem
informativa quando o filtro está ativo + aviso amarelo se o tomador já selecionado não estiver
mais na lista filtrada, sem limpar o campo automaticamente).

### `Dropdown` genérico deste arquivo não suporta prop `loading` — sem indicador de carregamento
Diferente do `Autocomplete` de `ProducaoNovaPage.tsx` (que já tinha suporte a `loading` com ícone
`Loader2`), o `Dropdown<T>` genérico usado em `FrequenciasPage.tsx` não tem essa prop. Como a
chamada de filtro é uma única requisição (não N em paralelo), o delay é imprescindível mas
pequeno o suficiente para não precisar de spinner — mantido minimalista, sem adicionar a prop ao
componente compartilhado só para este caso (evita expandir a superfície de um componente usado
em várias outras chamadas de `Dropdown` nesta mesma tela — médico, tomador, setor, modalidade).

### Teste manual confirmou exclusão correta de um vínculo já removido em sessão anterior (EPIC-15.11)
Testado com o médico `Medico Teste` (11 tomadores alocados) — o combo de Tomador do modal de nova
frequência mostrou exatamente os 11 tomadores esperados, **sem** o "HAPVIDA ASSISTENCIA MEDICA
S.A." que havia sido adicionado e depois removido durante os testes manuais da EPIC-15.11. Confirma
que o filtro lê o estado real e atual da tabela `medico_tomadores`, não uma versão em cache.

---

## Filtro no Portal — PortalProducaoNovaPage.tsx (EPIC-15.15)

### `TomadorPortal` (EPIC-15.10) não tem `cnaes`/`servicos` — usado só como safelist de IDs, não como fonte dos dados
A task pedia para usar `portalApi.getTomadoresAlocados()` no lugar de `tomadoresApi.listar()`, mas
o tipo `TomadorPortal { id, razaoSocial, municipio }` é deliberadamente enxuto (decisão de
privacidade da EPIC-15.6/15.10 — portal nunca expõe CNPJ do tomador) e **não carrega** `cnaes`
nem `servicos`, que o formulário já usava para auto-selecionar CNAE/serviço quando o tomador tem
cadastro específico. Trocar o tipo por completo quebraria essa funcionalidade existente (CNAE
sumiria sempre, serviço nunca mais auto-selecionaria).

**Solução híbrida:** buscar as duas fontes em paralelo — `tomadoresApi.listar()` (shape completo,
já usado antes) e `portalApi.getTomadoresAlocados()` (a safelist de IDs alocados ao médico) — e
filtrar o array completo pelos IDs alocados:
```typescript
const idsAlocados = new Set(alocados.map(a => a.id))
setTomadores(t.filter(tom => idsAlocados.has(tom.id)))
```
Isso fecha o gap de UX (médico só vê tomadores onde atua) **sem** perder a lógica de CNAE/serviço
por tomador já existente — o componente `SearchDropdown`/lógica de `servicosDisponiveis` continuam
inalterados, só o array de entrada agora vem filtrado.

### Estado vazio explícito quando o médico não tem nenhum tomador alocado
Antes desta mudança, um médico sem nenhuma alocação via **todos** os tomadores do tenant no combo
— o gap mais crítico apontado pelo usuário. Agora, `tomadores.length === 0` renderiza um aviso
laranja substituindo o campo ("Você ainda não está alocado a nenhum tomador. Entre em contato com
o time operacional...") em vez de um dropdown vazio e confuso — mesmo padrão visual já usado para
"Nenhuma empresa vinculada" (`empresas.length === 0`) alguns campos abaixo.

### Teste manual confirmou a correção do gap crítico com dados reais
Logado como `medico@pinsaude.com.br` (Medico Teste, 11 tomadores alocados) em
`/portal/producao/nova`: o combo de Tomador mostrou exatamente os 11 tomadores alocados —
**sem** o "HAPVIDA ASSISTENCIA MEDICA S.A." (não alocado) — confirmando que antes desta mudança
esse médico veria também o HAPVIDA e qualquer outro tomador do tenant. Selecionar
"SECRETARIA DA SAUDE DO ESTADO DO CEARA" continuou auto-selecionando CNAE (3 opções) e Serviço
(único, "4.01 — Medicina e biomedicina") normalmente — confirma que a lógica híbrida preserva
100% da funcionalidade de CNAE/serviço por tomador. Preview de valor (85%/15%) calculou
corretamente. Zero erros de console.

### Ambiente de teste precisou do `services/portal` (porta 8087) — não estava rodando por padrão
Diferente das tasks anteriores desta EPIC (que só precisavam de faturamento/onboarding/gateway),
esta é a primeira tela cujo teste manual depende do `services/portal`. Sessão anterior só tinha
web/faturamento/onboarding/gateway/keycloak de pé — precisou subir `services/portal` via
`mvn -f services/portal/pom.xml spring-boot:run` antes de testar.

---

## Filtro no Portal — PortalFrequenciaPage.tsx (EPIC-15.16)

### Duas coleções de tomadores necessárias — histórico precisa do array completo, formulário não
Diferente da EPIC-15.15, esta tela usa `tomadores` para **dois** propósitos distintos:
1. `FrequenciaCard` resolve o nome do tomador de cada frequência já lançada via
   `tomadores.find(t => t.id === freq.tomadorId)` — precisa do array **completo**, sem filtro,
   porque uma frequência antiga pode referenciar um tomador do qual o médico **já foi desalocado**
   (o vínculo pode ter sido removido depois que a frequência foi criada). Filtrar esse array
   quebraria a exibição do nome em frequências históricas, mostrando "—" em vez do nome real.
2. `NovaFrequenciaModal` precisa do array **filtrado**, restrito aos tomadores atualmente
   alocados — esse é o gap de UX real que a task pede para fechar.

**Solução:** duas states separadas — `tomadores` (completo, alimenta `FrequenciaCard` via
`tomadoresSorted`) e `tomadoresAlocados` (filtrado por `portalApi.getTomadoresAlocados()`,
alimenta só `NovaFrequenciaModal` via `tomadoresAlocadosSorted`). Mesma técnica híbrida da
EPIC-15.15 (buscar `tomadoresApi.listar()` completo + `portalApi.getTomadoresAlocados()` como
safelist de IDs, filtrar client-side), mas aqui com o cuidado extra de **não** substituir a
única fonte de dados por uma versão filtrada — precisava de duas.

### Este arquivo é código duplicado de `FrequenciasPage.tsx` (EPIC-15.14) — mesmo padrão de fix
A própria descrição da task já apontava isso ("`Dropdown<T>` local, código hoje duplicado de
`FrequenciasPage.tsx`"). O fix de UI (estado vazio laranja quando `tomadores.length === 0`) segue
exatamente o mesmo texto/estilo já usado na EPIC-15.14/15.15 — mantém consistência visual entre
as 3 telas de lançamento (admin Produção, admin Frequência, Portal Produção, Portal Frequência)
que já compartilham esse mesmo padrão de aviso.

### Teste manual — ordenação alfabética expôs a ausência do HAPVIDA de forma mais visível
Com `tomadoresAlocadosSorted` (ordenado por `razaoSocialNome`), a ausência do tomador não-alocado
fica visualmente óbvia: a lista pula de "CLINICA VIDA SAUDAVEL" direto para "HOSPITAL NOSSA SRA
APARECIDA" — "HAPVIDA" (que alfabeticamente ficaria entre os dois) simplesmente não aparece.
Confirmado ao vivo com `Medico Teste` (11 tomadores alocados, sem HAPVIDA) — mesmo padrão de teste
das EPICs 15.11/15.14/15.15. Setor Operacional habilitou normalmente após selecionar o tomador
(dependência de `listarGrupos(tomador.id)` intacta — só precisa do `.id`, não do shape completo).
Zero erros de console.

---

## Testes e Roteiro Manual E2E do EPIC-15 (EPIC-15.17)

### Épico com TDD incremental — a task final de "testes" não precisou de nenhum teste novo
Diferente do EPIC-14 (onde a 14.9 descobriu um gap real — checklist nunca semeado), o EPIC-15
já tinha 100% da cobertura automatizada exigida pelos critérios de aceite implementada nas
próprias tasks de backend (15.3 `TomadorServiceTest`, 15.7 `ProducaoServiceTest`, 15.8
`FrequenciaServiceTest`, 15.6 `PortalMedicoControllerTest`). A 15.17 rodou tudo de novo
(`node tools/scripts/mvn-test.js services/faturamento` → 140/140; `services/portal` → 9/10,
a falha é a `extrato_semImplementacao_retornaListaVazia` já conhecida desde EPIC-06.4/14/15.6)
e escreveu o roteiro manual, sem precisar adicionar nenhum teste novo. **Lição:** quando cada
task de backend do épico já inclui sua própria cobertura de teste (prática já estabelecida no
projeto), a task final de "testes" vira validação de integração ponta-a-ponta, não a primeira
oportunidade de testar — não assumir que sempre haverá gaps grandes tipo EPIC-14.9.

### Testar bloqueio 422 em 3 roles via curl+JWT direto é mais rápido e confiável que via browser
Para confirmar "sem bypass por papel" (operação/gestão/médico recebem 422 igual ao tentar
criar Produção/Frequência com médico não alocado), obter token real via ROPC do Keycloak e
chamar o endpoint direto pelo gateway é muito mais rápido e determinístico que repetir 3 logins
completos no browser:
```bash
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/pinsaude/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=pinsaude-web&username=$USER&password=test123" | \
  node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>console.log(JSON.parse(d).access_token))")
curl -s -w "HTTP %{http_code}\n" -X POST "http://localhost:8090/api/producoes" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$BODY"
```
Um `medicoId` sintético (UUID aleatório nunca inserido em `medico_tomadores`) serve tão bem
quanto um médico real não-alocado para testar o bloqueio — não precisa existir em
`onboarding.medicos`, já que `faturamento` não tem FK cruzada (mesmo padrão já usado nos testes
unitários de `ProducaoServiceTest`/`FrequenciaServiceTest`).

### Verificação cruzada de contagem do backfill — `medico_tomadores` deve ser ≥ combinações históricas
Para validar que o backfill (EPIC-15.2) e as alocações adicionadas depois cobrem o histórico
real, comparar a contagem total de `medico_tomadores` com o número de combinações **distintas**
médico+tomador em `participacoes_producao`/`producoes` e em `frequencias_medicas` — a primeira
deve ser sempre `>=` a soma das segundas (nunca poderia ser menor, ou médicos com histórico
ficariam bloqueados). `GROUP BY medico_id` na mesma tabela também serve para auditoria rápida
de quantos tomadores cada médico tem alocado, sem precisar abrir a UI.

### Aba do Chrome trava de forma recorrente após uso prolongado — abrir aba nova é o workaround
Ao longo de toda a EPIC-15, a mesma aba usada por múltiplas tasks eventualmente para de
responder a `find`/`screenshot` (`"Page still loading (executeScript waited 45000ms for
document_idle)"`), mesmo com a página aparentemente carregada e o console respondendo
normalmente — sintoma consistente com o processo de renderização da aba ficando preso após
muitas navegações/logins consecutivos na mesma sessão de automação. `tabs_create_mcp` para
uma aba nova e login do zero sempre resolve — não vale a pena tentar recuperar a aba travada.

---

## Checklist de Conduta editável também na Aprovação (pós-EPIC-15)

### `ChecklistEditor` extraído de `MedicoPerfilPage.tsx` para componente compartilhado
A tela de Aprovação (`AprovacaoOnboardingPage.tsx`) mostrava o Checklist de Conduta em modo
somente-leitura — só era possível marcar os 3 itens no perfil do médico (`MedicoPerfilPage.tsx`),
obrigando o operador a navegar entre as duas telas para concluir a aprovação. `ChecklistEditor`
(antes uma função local em `MedicoPerfilPage.tsx`) foi extraído para
`apps/web/src/components/ChecklistEditor.tsx` e importado nas duas páginas — sem duplicar a
lógica de dirty-check/save/cancel. O wrapper visual (`mt-6 pt-6 border-t`) usado no perfil do
médico ficou no local de uso (`MedicoPerfilPage.tsx`), não no componente, porque a Aprovação já
envolve o checklist num card próprio (`border rounded-xl p-4`) — o componente compartilhado não
assume nenhum wrapper externo.

### `canEdit` sempre `true` na Aprovação — não existe view somente-leitura nessa tela
Diferente do perfil do médico (onde `canEdit = isGestao || isOperacao`, pensado para o caso do
próprio médico visualizar seu perfil sem poder editar), `DetalhePanel` em
`AprovacaoOnboardingPage.tsx` só é renderizado para quem já passou pelo gate
`canAccess = isOperacao || isGestao` da página — não há cenário de acesso somente-leitura. Por
isso `<ChecklistEditor canEdit ... />` é passado sem condicional nenhuma.

### `onSaved` do componente devolve só o checklist — a página componente é quem funde no estado do médico
A assinatura de `ChecklistEditor` retorna `onSaved(updated: Checklist)`, não o `Medico` inteiro
(o endpoint `atualizarChecklist` só retorna o sub-recurso). Cada página consumidora funde esse
retorno no seu próprio estado: `MedicoPerfilPage.tsx` faz
`setMedico(m => m ? { ...m, checklist: updated } : m)`; `AprovacaoOnboardingPage.tsx` (que não
guarda o médico selecionado num `useState` próprio dentro de `DetalhePanel`, e sim recebe via
prop `medico` + callback `onRefresh(updated: Medico)`) faz
`onRefresh({ ...medico, checklist: updated })` — reaproveitando o mesmo `onRefresh` que já
atualiza a lista lateral e o `checklistOk`/`prontoPraAtivar` usados no card "Requisitos para
Ativação", sem precisar de um refetch.

---

## Ocultar Tomadores com Faturamento por Grupo em Nova Produção (PINSAUDE-13.11)

### Regra de negócio: tomador com grupo ativo não gera produção manual
Um tomador com `tomador_grupos_faturamento` ativo (EPIC-13.1/13.8) tem sua produção gerada
exclusivamente pelo fluxo Frequência Médica → Fechamento por Grupo, que agrega os itens de
frequência por grupo/médico. Lançar produção manualmente (`POST /api/producoes`) para esse
tomador cria uma segunda produção não relacionada ao fechamento, duplicando/conflitando com o
valor que o Fechamento por Grupo geraria depois. A regra vale para **todas** as roles que podem
criar produção (`operacao`, `gestao`, `medico`), sem bypass — mesmo padrão "sem bypass por papel"
já usado na alocação médico↔tomador (EPIC-15.7).

### `TomadorResponse.temGrupoFaturamento` — campo computado, sem endpoint novo
Em vez de criar um endpoint dedicado ou um query param `semGrupoFaturamento`, `TomadorResponse`
(bare record, sem `from()` — construído manualmente em `TomadorService.toResponse()`) ganhou um
campo boolean `temGrupoFaturamento`, calculado com
`grupoRepo.existsByTomadorIdAndAtivoTrue(t.getId())` — reaproveitando o repositório já injetado
no service para `listarGrupos`/`criarGrupo`. Segue o mesmo padrão de N+1 já aceito nesse método
para `aliquotas`/`cnaes`/`servicos` (uma query extra por tomador em `toResponse`, sem batch— ver
nota já existente sobre isso no service) — não vale a pena introduzir batch-loading só para um
boolean quando o método já paga esse custo para 3 outras coleções.

### `ProducaoService.criar()` — novo check antes do loop de médico alocado
```java
if (grupoRepo.existsByTomadorIdAndAtivoTrue(req.tomadorId())) {
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "Tomador possui faturamento por grupo configurado. Lance a produção pelo Fechamento por Grupo.");
}
```
Posicionado logo após carregar `tomador`/`servico` (que lançam 404) e antes do loop de
`medicoTomadorRepo.existsByTomadorIdAndMedicoId` (422 de médico não alocado, EPIC-15.7) — um
tomador-level gate deve vir antes de qualquer checagem por participante. `TomadorGrupoFaturamentoRepository`
foi injetado como 6º parâmetro do construtor de `ProducaoService`; testes com `@InjectMocks`
só precisam adicionar `@Mock TomadorGrupoFaturamentoRepository grupoRepo` — como o Mockito
retorna `false` por padrão para métodos boolean não stubados, nenhum teste existente quebrou.

### Frontend — filtro aplicado no ponto de montagem da lista, não em um novo estado
`ProducaoNovaPage.tsx`: `tomadoresDisponiveis` (já existente, EPIC-15.13 — interseção por médico
alocado) ganhou `.filter(t => !t.temGrupoFaturamento)` na mesma linha, e um hint estático
(`existeTomadorComGrupo = tomadores.some(t => t.temGrupoFaturamento)`) explica a ausência só
quando há pelo menos um tomador excluído no tenant. `PortalProducaoNovaPage.tsx`: mesmo filtro
adicionado ao `.filter(...)` que já restringia por tomador alocado ao médico (EPIC-15.15) — uma
única linha, sem estado novo.

### ⚠️ Achado ao testar manualmente — tomador duplicado (mesmo CNPJ) já documentado em EPIC-15.11 serviu de teste natural
Ao tentar validar o filtro no navegador, "SECRETARIA DA SAUDE DO ESTADO DO CEARA" aparecia no
combo de Tomador mesmo após eu confirmar (via modal "Grupos de Faturamento") que ela tinha um
grupo ativo. Investigando via `fetch('/api/tomadores?q=SECRETARIA')` no console, havia **dois**
registros com o mesmo nome/CNPJ (o duplicado de dado de seed já documentado na seção EPIC-15.11
do CLAUDE.md) — um com `temGrupoFaturamento: true` (corretamente ausente do combo) e outro com
`false` (corretamente presente). Não é um bug desta task; serviu como confirmação de que o filtro
diferencia corretamente registros por `id`, não por nome — sinalizado aqui para quem for investigar
comportamento parecido no futuro sem lembrar do duplicado.

---

## Associação Tomador ↔ Empresa Pin (PINSAUDE-13.12)

### Nova tabela `faturamento.tomador_empresas` — mesmo padrão de `medico_tomadores` (EPIC-15.1)
Tomador nunca era vinculado a uma empresa Pin específica — ao criar Produção/Nota, o operador
escolhia a empresa emissora manualmente toda vez, e não havia como saber "quais tomadores cada
empresa Pin atende" para relatórios futuros. Migration `V23__create_tomador_empresas.sql`
replica exatamente o padrão de `V21__create_medico_tomadores.sql`: `FORCE ROW LEVEL SECURITY`
obrigatório (app conecta como `svc_faturamento`, dono da tabela), `WITH CHECK (true)` para não
bloquear o INSERT, `empresa_id` sem FK (Empresa é entidade do onboarding, outro schema/serviço —
mesmo motivo de `producoes.empresa_id`/`medico_tomadores.medico_id`), `UNIQUE(tomador_id,
empresa_id)` permite N empresas por tomador (requisito explícito: "deve ser possível adicionar
mais de uma empresa Pin ao tomador").

### Duplo propósito do vínculo — DTO embutido no `TomadorResponse` **e** endpoints dedicados
Diferente de `medicos`/`grupos`/`modalidades`/`setores` (só endpoints dedicados, nunca embutidos
em `TomadorResponse`), `empresas` aparece nos dois lugares:
1. **Embutido** — `TomadorResponse.empresas: List<TomadorEmpresaResponse>`, calculado em
   `toResponse()` via `empresaTomadorRepo.findByTomadorId(t.getId())` (mesmo padrão de N+1 já
   aceito nesse método para `aliquotas`/`cnaes`/`servicos`/`temGrupoFaturamento` — uma query
   extra por tomador, sem batch). Necessário porque o frontend precisa saber, para CADA tomador
   já carregado na tela de Nova Produção, quais empresas estão vinculadas — sem isso seria um
   fetch extra por tomador (N+1 client-side) só para popular o auto-select.
2. **Endpoints dedicados** (`GET/POST/DELETE /api/tomadores/{id}/empresas`) — para o modal de
   gestão (`TomadorEmpresasModal.tsx`) e para o filtro `GET /api/tomadores?empresaId=` (relatórios
   futuros "quais tomadores pertencem à empresa X"), mesmo padrão de `?medicoId=` (EPIC-15.5).

`TomadorService.buscar(String q, UUID medicoId)` virou um overload que delega para
`buscar(q, medicoId, null)` — evita quebrar as 8 chamadas de teste existentes com 2 argumentos.

### Auto-seleção da Empresa Emissora em Nova Produção — mesmo padrão de CNAE/Serviço (EPIC-04.4)
`ProducaoNovaPage.tsx`: `empresasDisponiveis` restringe o combo às empresas vinculadas ao tomador
selecionado (fallback pro catálogo completo quando o tomador não tem vínculo — maioria dos casos
hoje). Auto-seleciona quando há exatamente 1 vínculo; limpa a seleção manual anterior só quando o
tomador TEM vínculos e a seleção atual não está entre eles (nunca limpa quando o tomador não tem
nenhum vínculo, para não quebrar o fluxo de seleção manual pré-existente — diferente do CNAE, que
é campo opcional e pode ser limpo sem problema, `empresaId` é obrigatório).

**Não confundir com o auto-select pré-existente por `cnpj_id` do JWT:** antes desta task já
existia um auto-select de empresa **ao carregar a página** (linhas ~424-431), tentando casar
`user.cnpj_id` do JWT logado com o CNPJ de alguma empresa — histórico de EPIC-04.4, roda **antes**
de qualquer tomador ser selecionado. Os dois mecanismos coexistem sem conflito porque o novo só
age depois que `tomador?.id` muda (dependência do `useEffect`); mas ao testar manualmente, um
tomador cuja empresa vinculada coincide com a do JWT (mesmo CNPJ) torna os dois mecanismos
indistinguíveis no resultado final — testar a restrição de **opções do `<select>`** (contagem de
`<option>`), não só o valor selecionado, para confirmar que o novo filtro está de fato ativo.

### ⚠️ Achado (não corrigido nesta task): `operacao` recebe 403 em `GET /api/empresas`
`EmpresaController` (onboarding) tem `@PreAuthorize("hasRole('gestao')")` **a nível de classe**
— nenhum método libera `operacao`, nem o `GET` de listagem. Isso significa que `ProducaoNovaPage.tsx`
(acessível a `operacao`) sempre teve o combo "Empresa Emissora" **vazio** para esse papel: a
chamada já tinha um fallback silencioso (`.catch(() => ({ content: [] ... }))`) que mascara o 403
como "nenhuma empresa cadastrada". Descoberto ao testar esta task como `operacao` — o combo
aparecia vazio mesmo tendo 3 empresas cadastradas no tenant. Não corrigido aqui por estar fora do
escopo (bug pré-existente, não introduzido por esta mudança) — sinalizado para abrir como bug de
produção separado. Contornado nesta sessão testando como `gestao`, que tem acesso normal.

---

## Modal "Editar Tomador" com Abas no Desktop (PINSAUDE-13.13)

### Problema: modal `size="lg"` (max-w-lg, 512px) com formulário longo empilhado verticalmente
`TomadorFormModal.tsx` tinha ~9 seções (Tipo, Documento, Dados básicos, Contato e Endereço,
Retenções na Fonte, Alíquotas Diferenciadas, CNAEs, Serviços) todas empilhadas num único scroll
vertical dentro de um modal estreito — no mobile isso é o layout correto (bottom sheet, tela
cheia), mas no desktop sobrava muito espaço horizontal não aproveitado e o formulário ficava
"espremido" e muito longo para rolar.

### Solução: abas só no desktop, mobile continua com tudo empilhado — via CSS puro, sem duplicar JSX
Em vez de renderizar duas árvores de DOM diferentes (mobile vs desktop), cada grupo de seções
("Identificação", "Contato e Endereço", "Fiscal", "CNAEs e Serviços") é envolvido numa única
`<div>` cuja classe alterna entre `'flex flex-col gap-5'` (aba ativa — sempre visível) e
`'flex flex-col gap-5 sm:hidden'` (aba inativa — visível só abaixo do breakpoint `sm` porque
`sm:hidden` é `min-width`, então nunca ativa no mobile). A barra de abas em si usa `hidden
sm:flex` (o inverso — só aparece no desktop). Resultado: no mobile, todos os grupos ficam com
`display:flex` o tempo todo (independente do estado `aba`, que é irrelevante lá porque a barra
de abas nem aparece) — comportamento anterior 100% preservado; no desktop, só o grupo da aba
ativa aparece. Mesmo padrão `hidden sm:X` / `X sm:hidden` já usado em todo o resto do projeto
(inclusive no próprio `Modal.tsx`, que já faz bottom-sheet mobile vs dialog centralizado desktop
dessa forma) — não foi inventado um mecanismo novo.

```tsx
function abaClass(key: Aba): string {
  return aba === key ? 'flex flex-col gap-5' : 'flex flex-col gap-5 sm:hidden'
}
// ...
<div className="hidden sm:flex gap-1 p-1 bg-ds-input rounded-xl border border-ds-border">
  {ABAS.map(({ key, label }) => <button onClick={() => setAba(key)} ...>{label}</button>)}
</div>
<div className={abaClass('identificacao')}>{/* Tipo, Documento, Dados básicos */}</div>
<div className={abaClass('contato')}>{/* Contato e Endereço */}</div>
<div className={abaClass('fiscal')}>{/* Retenções + Alíquotas Diferenciadas */}</div>
<div className={abaClass('servicos')}>{/* CNAEs + Serviços LC116 */}</div>
```

### Modal `size` sobe de `lg` (512px) para `2xl` (768px) — sem efeito visual no mobile
`libs/frontend/src/components/Modal.tsx` já é responsivo por natureza: no mobile, `w-full` sempre
vence porque a viewport é mais estreita que qualquer `max-w-*`; o `size` só importa a partir do
breakpoint onde `sm:p-4` entra em ação (desktop). Trocar `size="lg"` por `size="2xl"` (mesmo valor
já usado em `TomadorGruposModal.tsx`) alarga o modal só no desktop, sem tocar no mobile.

### Botões "Ações" (Cancelar/Salvar) ficam fora de qualquer aba — sempre visíveis
Intencional: o rodapé com os botões de submit não está dentro de nenhum `abaClass(...)`, então
aparece em qualquer aba/tamanho de tela — o usuário nunca precisa navegar até uma aba específica
só para salvar ou cancelar.

### Auto-troca de aba na validação — evita erro "invisível" numa aba não ativa
`validate()` só valida `cnpjCpf`/`razaoSocialNome`, ambos na aba "Identificação". Se a validação
falhar enquanto o usuário está em outra aba (ex.: preencheu CNAEs primeiro), `setAba('identificacao')`
é chamado dentro do próprio `validate()` para garantir que o erro fique visível assim que o submit
falha — sem esse auto-switch, o `<Alert>`/mensagens de erro ficariam escondidas atrás de `sm:hidden`.

### Reset da aba ativa ao trocar de tomador (ou abrir "Novo Tomador")
`setAba('identificacao')` foi adicionado ao mesmo `useEffect` que já reseta form/alíquotas/CNAEs/
serviços quando a prop `tomador` muda — evita abrir o modal de um tomador novo já numa aba que não
é a primeira (resíduo de uma edição anterior na mesma sessão do componente).

---

## Empresas Pin consolidado no Modal de Tomador (PINSAUDE-13.14)

### De modal separado + ícone próprio para 5ª aba do TomadorFormModal
A associação Tomador ↔ Empresa Pin (EPIC-13.12) nasceu como um modal dedicado
(`TomadorEmpresasModal.tsx`), aberto por um ícone próprio na tela de Tomadores — mesmo padrão de
`TomadorGruposModal`/`TomadorMedicosModal`. Assim que o modal de cadastro/edição ganhou abas
(EPIC-13.13), o usuário pediu pra consolidar ali, sem precisar de mais um ícone. `TomadorEmpresasModal.tsx`
foi **deletado** (não sobrou nenhum outro consumidor) e sua lógica foi portada como 5ª aba
("Empresas Pin") dentro de `TomadorFormModal.tsx`, seguindo o mesmo padrão de duas fontes
(pendente no cadastro, imediato na edição) já usado por CNAEs/Serviços no mesmo arquivo.

### Funciona em cadastro E edição — lista pendente igual a CNAEs/Serviços
Diferente do modal antigo (que só existia para tomador já persistido, `tomador: Tomador` sem
`| null` nas props), a aba dentro do form precisa suportar `tomador === null` (Novo Tomador).
Replicado o padrão exato já usado por `pendingCnaes`/`pendingServicos`: `pendingEmpresas:
PendingEmpresa[]` acumula vínculos localmente até o `handleSubmit` criar o tomador, e só então
`tomadoresApi.adicionarEmpresa(id, ...)` é chamado pra cada pendente. Na edição, cada
adicionar/remover já dispara a API na hora (padrão idêntico ao das outras seções).

### `vinculadas` inicializado direto de `tomador.empresas` — sem fetch extra
Como `TomadorResponse.empresas` já vem embutido na resposta de `GET /api/tomadores` (decisão da
EPIC-13.12, exatamente pra evitar esse tipo de fetch adicional), a aba não precisa de nenhuma
chamada própria pra carregar os vínculos existentes — `setVinculadas(tomador.empresas ?? [])` no
mesmo `useEffect` que já reresseta form/CNAEs/serviços ao trocar de tomador. Só o catálogo
completo de empresas (`todasEmpresas`, pro combo de "adicionar") precisa de fetch, carregado uma
vez no mount junto com `catalogoServicos`.

### Ícone "Empresas Pin vinculadas" removido de `TomadoresPage.tsx` (mobile + desktop)
Os dois blocos de botões de ação (card mobile e linha de tabela desktop) tinham o ícone
`Building2` chamando `setEmpresasTomador(t)`. Removidos junto com o state `empresasTomador`, o
import de `TomadorEmpresasModal` e o bloco de render condicional do modal — a gestão de empresas
agora só existe dentro do fluxo de Editar/Novo Tomador.

---

## Modalidades com Horas Livres e Tipo Mensal (PINSAUDE-13.17)

### Novo campo `tipo` — PLANTAO (padrão) vs MENSAL, com turno/horário/horas condicionais
A tabela de preços (`faturamento.tomador_modalidades`) só suportava um modelo: turno
(DIURNO/NOTURNO) + horário (texto livre) + horas, todos `NOT NULL`. Cliente pediu duas coisas:
(1) modalidades com quantidade de horas livre (ex: "Diária 10h", "Diarista 20h" — não só 6/12
como antes) e (2) modalidades sem turno/horário/horas, com valor fixo mensal (ex: "Coordenação
de UTI"). Resolvido com um discriminador `tipo VARCHAR(10) CHECK IN ('PLANTAO','MENSAL')`
(migration `V24__add_tipo_modalidade.sql`, mesmo padrão VARCHAR+CHECK já usado no projeto em vez
de enum nativo do Postgres — ver `regime_presuncao`), tornando `turno`/`horario`/`horas`
opcionais (`DROP NOT NULL`) com CHECK condicional garantindo consistência:
```sql
ALTER TABLE faturamento.tomador_modalidades
    ADD CONSTRAINT tomador_modalidades_tipo_campos_check CHECK (
        (tipo = 'PLANTAO' AND turno IS NOT NULL AND horario IS NOT NULL AND horas IS NOT NULL)
        OR
        (tipo = 'MENSAL' AND turno IS NULL AND horario IS NULL AND horas IS NULL)
    );
```
Os CHECKs antigos de `turno`/`horas` (que exigiam sempre não-nulo) foram recriados tolerando
`NULL` (`turno IS NULL OR turno IN (...)`, `horas IS NULL OR horas > 0`) — sem isso, o `DROP NOT
NULL` sozinho não bastaria, o CHECK antigo continuaria rejeitando linhas MENSAL.

### Validação condicional no service, não em Bean Validation declarativo
`TomadorModalidadeRequest` teve `@NotBlank`/`@NotNull` removidos de `turno`/`horario`/`horas`
(Bean Validation trata `null` como válido para a maioria dos constraints quando não combinado com
`@NotNull` — `@Pattern`/`@Size`/`@DecimalMin` continuam funcionando normalmente quando o valor
vem preenchido, só passam a tolerar `null`). A obrigatoriedade condicional (PLANTAO exige os 3
campos; MENSAL não aceita nenhum) é validada em `TomadorService.aplicarCamposPorTipo()`, chamado
por `criarModalidade`/`atualizarModalidade`: lança 422 se PLANTAO vier incompleto; se MENSAL,
**ignora** silenciosamente qualquer turno/horário/horas que vier no request (zera os 3 campos),
em vez de rejeitar — mais tolerante a um frontend que não limpe esses campos corretamente.

### Frontend — `TomadorGruposModal.tsx`, aba Modalidades
Removida a lógica de sincronização automática `syncByTurnoHoras`/`syncByHorario` (que travava
Horário↔Turno↔Horas nos 4 combos fixos de `HORARIOS_FIXOS`). Agora:
- **Seletor de Tipo** (2 botões: "Por Plantão" / "Valor Fixo Mensal") no topo do form, controla
  quais campos aparecem.
- **PLANTAO**: `HORARIOS_FIXOS` vira uma linha de "chips" de preenchimento rápido (clicar prepreenche
  turno+horas+horário, mas os 3 campos continuam livres para editar depois) — Turno continua
  `<select>`, mas **Horas agora é `<input type="number" step="0.5">` livre** (não mais limitado a
  6/12) e **Horário é `<input type="text">` livre** (não mais um `<select>` fechado).
- **MENSAL**: esconde Turno/Horário/Horas/Deslocamento por completo, mostra só Nome + "Valor
  Mensal" + Ativo.
- Tabela de listagem ganhou coluna "Tipo" (badge PLANTÃO azul / MENSAL roxo); Turno/Horário/Horas
  mostram "—" quando `null` (linhas MENSAL).

### Outras telas que exibem turno/horário — todas já toleravam `null`, só precisou ajustar o label
`FrequenciasPage.tsx`/`PortalFrequenciaPage.tsx`: o label do `<select>` de modalidade
(`` `${m.nome} — ${m.turno} · ${m.horario}` ``) virou condicional:
`m.tipo === 'MENSAL' ? \`${m.nome} — Mensal\` : ...`. O preview de valores (`modalidade.horario`)
segue o mesmo padrão. `frequenciaPdf.ts` (`gerarOcorrencia()`) **já era null-safe** (`item.
modalidadeTurno ? ... : ''`, filtrado com `.filter(Boolean)`) — uma linha MENSAL no PDF mostra só
o dia da semana, sem turno/horas, sem nenhuma mudança de código. `FechamentoPage.tsx`
(`fmtHoras`) só precisou aceitar `number | null` na assinatura (já retornava `''` para valores
falsy). `ModalidadeDetalhe` (`fechamentosApi.ts`) teve `turno`/`horas` tipados como `string |
null`/`number | null` — o backend (`FechamentoPreviewResponse.ModalidadeDetalhe`, Java) já eram
tipos de referência (`String`/`BigDecimal`), então nenhuma mudança foi necessária no Java além da
tipagem do TS no frontend.

### ⚠️ Armadilha: processo Java do faturamento rodando com código compilado ANTES da mudança
Ao testar manualmente a criação de uma modalidade MENSAL pela UI, o backend retornou 400 com
`"turno: não deve estar em branco"` — exatamente a mensagem do `@NotBlank` que **já tinha sido
removido** do código-fonte. Causa: o processo `java.exe` do `faturamento` em execução no ambiente
local (via `mvn spring-boot:run`) foi iniciado **antes** desta sessão de edição, rodando o `.class`
antigo em `target/classes` — mesmo padrão já documentado para onboarding/gateway (ver EPIC-14.6),
agora confirmado também no faturamento. **Sempre que uma mudança de backend não aparentar efeito
ao testar manualmente (comportamento antigo persistindo), verificar se o processo em execução foi
iniciado antes da edição** (`Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where
CommandLine -like "*faturamento*"`) e reiniciá-lo (`mvn compile` + `mvn spring-boot:run`) antes de
assumir que o código está errado.

### Refinamento pós-13.17 — Turno também vira opcional em modalidades PLANTAO
Cliente pediu, logo após o merge inicial: modalidades "Por Plantão" com horas específicas (ex:
"Diária 15h") às vezes **não têm turno definido** — só horário e horas importam. Migration
`V25__make_turno_opcional_plantao.sql` recria `tomador_modalidades_tipo_campos_check` exigindo
apenas `horario IS NOT NULL AND horas IS NOT NULL` para `tipo = 'PLANTAO'` (turno já era nullable
desde a V24, só o CHECK de consistência ainda o exigia). `TomadorService.aplicarCamposPorTipo()`:
`incompleto` não checa mais `req.turno()`; `m.setTurno(req.turno() != null && !req.turno().isBlank()
? req.turno() : null)` normaliza string vazia pra `null`. Frontend: `<select>` de Turno ganhou a
opção `"Não especificar"` (value `''`), `ModalidadeForm.turno` virou `'DIURNO' | 'NOTURNO' | ''`,
`emptyModalidadeForm()` passou a iniciar todos os campos de Plantão vazios (sem default 12h/DIURNO)
já que os chips de preenchimento rápido cobrem o caso comum. `getLabel` do `<select>` de modalidade
em `FrequenciasPage.tsx`/`PortalFrequenciaPage.tsx` ganhou um terceiro ramo: modalidade PLANTAO sem
turno mostra `"${nome} — ${horario}"` (sem o `turno ·` no meio) — sem isso apareceria "undefined"
no label. Tabela de listagem já mostrava "—" para turno nulo (reaproveitado do caso MENSAL, nenhuma
mudança necessária ali).

---

## Convenções de Commit e Branch

- **Branch:** `feature/pinsaude-<numero>`
- **Commit:** `#PINSAUDE-<NUMERO> - <descrição em português>`
