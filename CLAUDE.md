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

## Convenções de Commit e Branch

- **Branch:** `feature/pinsaude-<numero>`
- **Commit:** `#PINSAUDE-<NUMERO> - <descrição em português>`
