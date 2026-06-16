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

## Convenções de Commit e Branch

- **Branch:** `feature/pinsaude-<numero>`
- **Commit:** `#PINSAUDE-<NUMERO> - <descrição em português>`
