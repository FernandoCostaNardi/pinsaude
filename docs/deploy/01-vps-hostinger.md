# Deploy Pin Saúde — VPS Hostinger (IP: 212.85.12.228)

Guia passo a passo para colocar o sistema em produção acessível via IP.
Cobre todos os serviços do MVP: EPIC-01 ao EPIC-05.

---

## Arquitetura final

```
Navegador
    │
    ▼ porta 80
  Nginx  ──── /          ──→  arquivos estáticos React  (/home/pinsaude/frontend/)
              /api/*      ──→  Gateway :8100
    │
    │ (direto, sem Nginx)
    ▼ porta 8180
  Keycloak (Docker)  ──→  PostgreSQL nativo :5432 / banco keycloak

Gateway :8100
    ├──→  Onboarding   :8185   (/api/onboarding/**, /api/medicos/**, /api/empresas/**)
    ├──→  Gestão       :8186   (/api/gestao/**, /api/usuarios/**)
    ├──→  Faturamento  :8182   (/api/faturamento/**, /api/tomadores/**, /api/producoes/**, /api/servicos/**)
    └──→  Fiscal       :8181   (/api/fiscal/**, /api/nfse/**, /api/motor-fiscal/**, /api/parametros-fiscais/**)

RabbitMQ (Docker) :5672/:15672  ──→  Fiscal service (filas NFS-e assíncronas)
MinIO     (Docker) :9000/:9001  ──→  Onboarding (upload de documentos de médicos)
Mailhog   (Docker) :1025/:8125  ──→  Keycloak (SMTP de teste)
```

**Portas utilizadas pelo pinsaude:**

| Serviço           | Porta   | Acesso externo |
|-------------------|---------|----------------|
| Nginx (frontend)  | 80      | http://212.85.12.228 |
| Gateway           | 8100    | Interno (Nginx proxy) |
| Keycloak          | 8180    | http://212.85.12.228:8180 |
| Onboarding        | 8185    | Interno (Gateway proxy) |
| Gestão            | 8186    | Interno (Gateway proxy) |
| Faturamento       | 8182    | Interno (Gateway proxy) |
| Fiscal            | 8181    | Interno (Gateway proxy) |
| RabbitMQ AMQP     | 5672    | Interno (Fiscal service) |
| RabbitMQ UI       | 15672   | http://212.85.12.228:15672 (admin) |
| MinIO API         | 9000    | http://212.85.12.228:9000 |
| MinIO Console     | 9001    | http://212.85.12.228:9001 |
| Mailhog SMTP      | 1025    | Interno (Keycloak) |
| Mailhog UI        | 8125    | http://212.85.12.228:8125 |

---

## PARTE 1 — Preparação do Servidor (primeira vez)

### 1.1 Conectar ao servidor

```bash
ssh root@212.85.12.228
```

### 1.2 Verificar o que já existe

```bash
psql --version && systemctl status postgresql
nginx -v 2>/dev/null || echo "Nginx não instalado"
docker --version 2>/dev/null || echo "Docker não instalado"
java -version 2>/dev/null || echo "Java não instalado"
ss -tlnp | grep -E ':80|:8100|:818[0-9]|:818[0-9]'
```

### 1.3 Criar usuário pinsaude

```bash
useradd -m -s /bin/bash pinsaude
passwd pinsaude

mkdir -p /home/pinsaude/{infra,apps/onboarding,apps/gestao,apps/gateway,apps/faturamento,apps/fiscal,frontend,logs,scripts}
chown -R pinsaude:pinsaude /home/pinsaude
```

### 1.4 Instalar Java 17

```bash
java -version 2>&1 | grep -q "17" && echo "Java 17 OK" || {
    apt update && apt install -y openjdk-17-jdk
}
which java   # /usr/bin/java
```

### 1.5 Instalar Maven

```bash
mvn -version 2>/dev/null || apt install -y maven
mvn -version   # 3.6+
```

### 1.6 Instalar Node.js 22 + pnpm

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt install -y nodejs
npm install -g pnpm@9
pnpm -v   # 9.x.x
```

### 1.7 Instalar Docker

```bash
docker --version 2>/dev/null || {
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker && systemctl start docker
    usermod -aG docker pinsaude
}
docker compose version
```

### 1.8 Instalar Nginx

```bash
nginx -v 2>/dev/null || apt install -y nginx
# ATENÇÃO: se Nginx já roda com outros sites, verificar /etc/nginx/sites-enabled/ antes
```

### 1.9 Instalar Git

```bash
git --version 2>/dev/null || apt install -y git
```

---

## PARTE 2 — Banco de Dados PostgreSQL

> PostgreSQL nativo do servidor — NÃO usa container Docker.

### 2.1 Criar banco, usuários e schemas

```bash
sudo -u postgres psql
```

```sql
-- Banco principal
CREATE DATABASE pinsaude
    WITH ENCODING = 'UTF8'
    LC_COLLATE = 'pt_BR.UTF-8'
    LC_CTYPE = 'pt_BR.UTF-8'
    TEMPLATE = template0;

-- Se pt_BR.UTF-8 não estiver disponível:
-- CREATE DATABASE pinsaude WITH ENCODING='UTF8' TEMPLATE=template0;

-- Banco do Keycloak
CREATE DATABASE keycloak WITH ENCODING = 'UTF8' TEMPLATE = template0;

-- Usuários dos serviços
CREATE USER svc_onboarding  WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';
CREATE USER svc_gestao      WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';
CREATE USER svc_faturamento WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';
CREATE USER svc_fiscal      WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';
CREATE USER keycloak        WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';

\c pinsaude

-- Extensões (requerem superuser — executar uma única vez)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─── Onboarding ───────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS onboarding;
GRANT CONNECT, CREATE ON DATABASE pinsaude TO svc_onboarding;
GRANT CREATE, USAGE ON SCHEMA onboarding TO svc_onboarding;
ALTER USER svc_onboarding SET search_path TO onboarding, public;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding GRANT ALL ON TABLES    TO svc_onboarding;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding GRANT ALL ON SEQUENCES TO svc_onboarding;

-- ─── Gestão ───────────────────────────────────────────────────────────────────
GRANT CONNECT ON DATABASE pinsaude TO svc_gestao;

-- ─── Faturamento ──────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS faturamento;
GRANT CONNECT, CREATE ON DATABASE pinsaude TO svc_faturamento;
GRANT CREATE, USAGE ON SCHEMA faturamento TO svc_faturamento;
ALTER USER svc_faturamento SET search_path TO faturamento, public;
ALTER DEFAULT PRIVILEGES IN SCHEMA faturamento GRANT ALL ON TABLES    TO svc_faturamento;
ALTER DEFAULT PRIVILEGES IN SCHEMA faturamento GRANT ALL ON SEQUENCES TO svc_faturamento;

-- ─── Fiscal ───────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS fiscal;
GRANT CONNECT, CREATE ON DATABASE pinsaude TO svc_fiscal;
GRANT CREATE, USAGE ON SCHEMA fiscal TO svc_fiscal;
ALTER USER svc_fiscal SET search_path TO fiscal, public;
ALTER DEFAULT PRIVILEGES IN SCHEMA fiscal GRANT ALL ON TABLES    TO svc_fiscal;
ALTER DEFAULT PRIVILEGES IN SCHEMA fiscal GRANT ALL ON SEQUENCES TO svc_fiscal;

-- ─── Keycloak ─────────────────────────────────────────────────────────────────
\c keycloak
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
ALTER USER keycloak CREATEDB;

\q
```

### 2.2 Verificar conexões

```bash
psql -U svc_onboarding  -d pinsaude -h localhost -c "SELECT current_user;"
psql -U svc_faturamento -d pinsaude -h localhost -c "SELECT current_user;"
psql -U svc_fiscal      -d pinsaude -h localhost -c "SELECT current_user;"
```

---

## PARTE 3 — Keycloak, Mailhog, MinIO e RabbitMQ via Docker

### 3.1 Criar arquivo de variáveis de ambiente

```bash
# Copiar o exemplo e preencher com senhas reais
cp /home/pinsaude/projeto/tools/deploy/env-prod.example /home/pinsaude/infra/.env
nano /home/pinsaude/infra/.env
chmod 600 /home/pinsaude/infra/.env
chown pinsaude:pinsaude /home/pinsaude/infra/.env
```

Preencher todas as variáveis marcadas como `TROCAR_POR_SENHA_FORTE`:
- `KC_ADMIN_PASS`, `KC_DB_PASS`
- `DB_PASSWORD_ONBOARDING`, `DB_PASSWORD_GESTAO`, `DB_PASSWORD_FATURAMENTO`, `DB_PASSWORD_FISCAL`
- `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`
- `CRYPTO_KEY` — chave de 32+ chars usada por onboarding e faturamento para pgcrypto; **não alterar após o primeiro deploy**
- `RABBITMQ_USER`, `RABBITMQ_PASSWORD`
- `NFSE_ENABLED=false` (deixar false até ter contrato com agregador)

### 3.2 Copiar arquivos para o servidor (na sua máquina Windows)

```powershell
scp tools/keycloak/realm-export.json     root@212.85.12.228:/home/pinsaude/infra/
scp tools/deploy/docker-compose.prod.yml root@212.85.12.228:/home/pinsaude/infra/
```

### 3.3 Subir containers

```bash
cd /home/pinsaude/infra
set -a && source .env && set +a
docker compose -f docker-compose.prod.yml up -d

# Acompanhar Keycloak (pode levar 2-3 min na primeira vez)
docker logs -f pinsaude-keycloak
# Aguardar: "Keycloak 24.x.x on JVM ... started in X.XXXs" → Ctrl+C
```

### 3.4 Verificar containers

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Keycloak health
curl -s http://localhost:8180/health/ready

# RabbitMQ health
curl -s http://localhost:15672 -o /dev/null -w "%{http_code}"   # 200

# MinIO health
curl -s http://localhost:9000/minio/health/live -o /dev/null -w "%{http_code}"   # 200
```

### 3.5 Configurar Keycloak para produção

Acesse **http://212.85.12.228:8180/admin** → realm `pinsaude` → Clients → `pinsaude-web` → Settings:
- **Valid Redirect URIs:** `http://212.85.12.228/*`
- **Valid Post Logout Redirect URIs:** `http://212.85.12.228/*`
- **Web Origins:** `http://212.85.12.228`

Realm Settings → Email:
- Host: `localhost`, Port: `1025`, From: `noreply@pinsaude.com.br`

### 3.6 Usuários de teste (importados automaticamente)

| E-mail | Senha | Role |
|--------|-------|------|
| `gestao@pinsaude.com.br` | `test123` | gestao |
| `medico@pinsaude.com.br` | `test123` | medico |
| `operacao@pinsaude.com.br` | `test123` | operacao |

> Alterar as senhas após o primeiro acesso dos stakeholders.

---

## PARTE 4 — Clonar e Compilar na VPS

### 4.1 Clonar o repositório

```bash
su - pinsaude
cd /home/pinsaude
git clone https://github.com/FernandoCostaNardi/pinsaude.git projeto
cd projeto
git checkout main
```

> Repositório privado → configurar Deploy Key:
> ```bash
> ssh-keygen -t ed25519 -C "pinsaude@vps"
> cat ~/.ssh/id_ed25519.pub
> # Adicionar no GitHub → Settings → Deploy Keys
> ```

### 4.2 Instalar dependências Node.js

```bash
cd /home/pinsaude/projeto
pnpm install --frozen-lockfile
```

### 4.3 Compilar os serviços Java

```bash
cd /home/pinsaude/projeto
mvn clean package -DskipTests --no-transfer-progress

# JARs gerados em:
# services/onboarding/target/pinsaude-onboarding-*.jar
# services/gestao/target/pinsaude-gestao-*.jar
# services/faturamento/target/pinsaude-faturamento-*.jar
# services/fiscal/target/pinsaude-fiscal-*.jar
# gateway/target/pinsaude-gateway-*.jar
```

> Primeira compilação: 8–15 minutos (download de dependências do Maven Central).

### 4.4 Compilar o frontend

```bash
cd /home/pinsaude/projeto/apps/web

cat > .env.production << 'EOF'
VITE_KC_URL=http://212.85.12.228:8180
VITE_KC_REALM=pinsaude
VITE_KC_CLIENT=pinsaude-web
EOF

pnpm build
# Saída em: apps/web/dist/
```

### 4.5 Copiar artefatos para as pastas de deploy

```bash
cd /home/pinsaude/projeto

# Serviços Java
cp services/onboarding/target/pinsaude-onboarding-*.jar   /home/pinsaude/apps/onboarding/app.jar
cp services/gestao/target/pinsaude-gestao-*.jar           /home/pinsaude/apps/gestao/app.jar
cp services/faturamento/target/pinsaude-faturamento-*.jar /home/pinsaude/apps/faturamento/app.jar
cp services/fiscal/target/pinsaude-fiscal-*.jar           /home/pinsaude/apps/fiscal/app.jar
cp gateway/target/pinsaude-gateway-*.jar                  /home/pinsaude/apps/gateway/app.jar

# Frontend
cp -r apps/web/dist/* /home/pinsaude/frontend/
chmod -R 755 /home/pinsaude/frontend
```

---

## PARTE 5 — Configurações de Produção

### 5.1 Copiar arquivos de configuração (na sua máquina Windows)

```powershell
# Serviços existentes
scp tools/deploy/onboarding-application-prod.yml  root@212.85.12.228:/home/pinsaude/apps/onboarding/application-prod.yml
scp tools/deploy/gestao-application-prod.yml      root@212.85.12.228:/home/pinsaude/apps/gestao/application-prod.yml
scp tools/deploy/gateway-application-prod.yml     root@212.85.12.228:/home/pinsaude/apps/gateway/application-prod.yml

# Serviços novos
scp tools/deploy/faturamento-application-prod.yml root@212.85.12.228:/home/pinsaude/apps/faturamento/application-prod.yml
scp tools/deploy/fiscal-application-prod.yml      root@212.85.12.228:/home/pinsaude/apps/fiscal/application-prod.yml
```

### 5.2 Proteger arquivos de configuração no servidor

```bash
chown -R pinsaude:pinsaude /home/pinsaude/apps/
chmod 640 /home/pinsaude/apps/*/application-prod.yml
```

---

## PARTE 6 — Serviços systemd

### 6.1 Copiar arquivos de serviço (na sua máquina Windows)

```powershell
# Serviços existentes
scp tools/deploy/pinsaude-onboarding.service  root@212.85.12.228:/etc/systemd/system/
scp tools/deploy/pinsaude-gestao.service      root@212.85.12.228:/etc/systemd/system/
scp tools/deploy/pinsaude-gateway.service     root@212.85.12.228:/etc/systemd/system/

# Serviços novos
scp tools/deploy/pinsaude-faturamento.service root@212.85.12.228:/etc/systemd/system/
scp tools/deploy/pinsaude-fiscal.service      root@212.85.12.228:/etc/systemd/system/
```

### 6.2 Habilitar e iniciar os serviços

```bash
systemctl daemon-reload

# 1. Onboarding — Flyway cria schema e tabelas (inclui RLS, pgcrypto)
systemctl enable pinsaude-onboarding
systemctl start pinsaude-onboarding
sleep 30
tail -50 /home/pinsaude/logs/onboarding.log
# Esperado: "Started OnboardingApplication" + "Successfully applied X migrations"

# 2. Faturamento — sem dependências externas além do PG
systemctl enable pinsaude-faturamento
systemctl start pinsaude-faturamento
sleep 20
tail -30 /home/pinsaude/logs/faturamento.log
# Esperado: "Started FaturamentoApplication" + "Successfully applied X migrations"

# 3. Fiscal — depende de RabbitMQ estar UP (verificar antes: docker ps)
systemctl enable pinsaude-fiscal
systemctl start pinsaude-fiscal
sleep 20
tail -30 /home/pinsaude/logs/fiscal.log
# Esperado: "Started FiscalApplication" + "Successfully applied X migrations"

# 4. Gestão
systemctl enable pinsaude-gestao
systemctl start pinsaude-gestao
sleep 15
tail -20 /home/pinsaude/logs/gestao.log

# 5. Gateway — último, roteia para todos os serviços acima
systemctl enable pinsaude-gateway
systemctl start pinsaude-gateway
sleep 10
tail -20 /home/pinsaude/logs/gateway.log
```

### 6.3 Verificar status e health

```bash
systemctl status pinsaude-onboarding pinsaude-gestao pinsaude-faturamento pinsaude-fiscal pinsaude-gateway

# Health checks individuais
curl -s http://localhost:8185/actuator/health   # onboarding
curl -s http://localhost:8186/actuator/health   # gestão
curl -s http://localhost:8182/actuator/health   # faturamento
curl -s http://localhost:8181/actuator/health   # fiscal
curl -s http://localhost:8100/actuator/health   # gateway
# Todos devem retornar {"status":"UP"}
```

---

## PARTE 7 — Frontend (Nginx)

### 7.1 Configurar Nginx

```bash
grep -r "listen 80" /etc/nginx/sites-enabled/ 2>/dev/null

cat > /etc/nginx/sites-available/pinsaude << 'EOF'
server {
    listen 80;
    server_name 212.85.12.228;

    root /home/pinsaude/frontend;
    index index.html;

    # Frontend React — SPA
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API — proxy para o gateway
    location /api/ {
        proxy_pass         http://127.0.0.1:8100;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
EOF

ln -sf /etc/nginx/sites-available/pinsaude /etc/nginx/sites-enabled/pinsaude
nginx -t && systemctl reload nginx
```

---

## PARTE 8 — Firewall (UFW)

```bash
ufw status verbose

ufw allow 80/tcp    comment "Nginx - pinsaude frontend"
ufw allow 8180/tcp  comment "Keycloak"
ufw allow 8125/tcp  comment "Mailhog UI (remover em produção real)"
ufw allow 15672/tcp comment "RabbitMQ Management UI (restringir em produção real)"

# NÃO expor externamente: 8100, 8181, 8182, 8185, 8186, 5672, 5432, 9000, 9001
# Acessíveis apenas via localhost (serviços internos)
```

> Em produção real: remover acesso externo ao Mailhog e RabbitMQ Management UI, e configurar HTTPS.

---

## PARTE 9 — Verificação Final

```bash
# PostgreSQL — schemas criados pelo Flyway
psql -U svc_onboarding  -d pinsaude -h localhost \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'onboarding';"

psql -U svc_faturamento -d pinsaude -h localhost \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'faturamento';"

psql -U svc_fiscal -d pinsaude -h localhost \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'fiscal';"

# Keycloak
curl -s http://localhost:8180/realms/pinsaude/.well-known/openid-configuration \
    | python3 -c "import sys,json; print('Keycloak OK:', json.load(sys.stdin)['issuer'])"

# Todos os serviços
curl -s http://localhost:8185/actuator/health   # onboarding
curl -s http://localhost:8186/actuator/health   # gestão
curl -s http://localhost:8182/actuator/health   # faturamento
curl -s http://localhost:8181/actuator/health   # fiscal
curl -s http://localhost:8100/actuator/health   # gateway

# Nginx
curl -s -o /dev/null -w "%{http_code}" http://localhost/   # 200
```

**Teste no navegador:**
1. Abrir **http://212.85.12.228** → tela de login
2. Login com `gestao@pinsaude.com.br` / `test123` → dashboard com todos os menus visíveis
3. Cadastrar uma empresa → confirmar persistência
4. Cadastrar um tomador → confirmar na listagem
5. Registrar uma produção → confirmar cálculo fiscal
6. Emitir NFS-e → verificar status na tela de Notas

---

## PARTE 10 — Atualizações Futuras

### Atualizar serviços existentes

```bash
ssh root@212.85.12.228
su - pinsaude
cd /home/pinsaude/projeto

# Puxar mudanças
git pull origin main

# Recompilar Java (todos os módulos)
mvn clean package -DskipTests --no-transfer-progress

# Copiar JARs
cp services/onboarding/target/pinsaude-onboarding-*.jar   /home/pinsaude/apps/onboarding/app.jar
cp services/gestao/target/pinsaude-gestao-*.jar           /home/pinsaude/apps/gestao/app.jar
cp services/faturamento/target/pinsaude-faturamento-*.jar /home/pinsaude/apps/faturamento/app.jar
cp services/fiscal/target/pinsaude-fiscal-*.jar           /home/pinsaude/apps/fiscal/app.jar
cp gateway/target/pinsaude-gateway-*.jar                  /home/pinsaude/apps/gateway/app.jar

# Reiniciar serviços (como root)
exit
systemctl restart pinsaude-onboarding pinsaude-gestao pinsaude-faturamento pinsaude-fiscal pinsaude-gateway

# Atualizar frontend (se houver mudanças)
su - pinsaude
cd /home/pinsaude/projeto/apps/web
pnpm build
cp -r dist/* /home/pinsaude/frontend/
exit
systemctl reload nginx
```

### Reiniciar apenas um serviço específico

```bash
systemctl restart pinsaude-fiscal    # ou onboarding, gestao, faturamento, gateway
journalctl -u pinsaude-fiscal -f     # seguir os logs em tempo real
```

---

## Arquivos de Suporte em `tools/deploy/`

| Arquivo | Descrição |
|---------|-----------|
| `docker-compose.prod.yml` | Keycloak, Mailhog, MinIO, RabbitMQ |
| `env-prod.example` | Modelo do arquivo `.env` (copiar e preencher) |
| `onboarding-application-prod.yml` | Config Spring Boot — Onboarding (porta 8185) |
| `gestao-application-prod.yml` | Config Spring Boot — Gestão (porta 8186) |
| `faturamento-application-prod.yml` | Config Spring Boot — Faturamento (porta 8182) |
| `fiscal-application-prod.yml` | Config Spring Boot — Fiscal (porta 8181) |
| `gateway-application-prod.yml` | Config Spring Cloud Gateway (porta 8100) |
| `pinsaude-onboarding.service` | Systemd unit — Onboarding |
| `pinsaude-gestao.service` | Systemd unit — Gestão |
| `pinsaude-faturamento.service` | Systemd unit — Faturamento |
| `pinsaude-fiscal.service` | Systemd unit — Fiscal |
| `pinsaude-gateway.service` | Systemd unit — Gateway |
| `nginx-pinsaude.conf` | Config Nginx (porta 80, SPA + proxy `/api/`) |

---

## URLs de Acesso

| URL | O que é |
|-----|---------|
| http://212.85.12.228 | Sistema Pin Saúde (frontend) |
| http://212.85.12.228:8180/admin | Keycloak Admin Console |
| http://212.85.12.228:8125 | Mailhog (ver e-mails enviados) |
| http://212.85.12.228:15672 | RabbitMQ Management UI (guest/guest ou credenciais do .env) |
| http://212.85.12.228:9001 | MinIO Console (admin de documentos) |

---

## Troubleshooting

### Serviço não sobe — verificar logs

```bash
journalctl -u pinsaude-fiscal --since "5 minutes ago" --no-pager
tail -100 /home/pinsaude/logs/fiscal.log
```

| Erro | Causa | Solução |
|------|-------|---------|
| `Connection refused` (5432) | PostgreSQL não aceita conexões locais | Verificar `pg_hba.conf`, reiniciar PG |
| `password authentication failed` | Senha errada no `.env` | Corrigir `.env` e reiniciar serviço |
| `Could not validate credentials` | Keycloak inacessível | `docker ps`, logs do Keycloak |
| `Flyway found non-empty schema` | Migration rodou parcialmente | Verificar `flyway_schema_history` no PG |
| `Connection refused` (5672) | RabbitMQ não subiu | `docker ps`, `docker logs pinsaude-rabbitmq` |
| Porta já em uso | Conflito com outro serviço | `ss -tlnp \| grep 818X` |
| `Flyway: relation not found` | Extensão pgcrypto/uuid-ossp ausente | Executar passo 2.1 como superuser |

### Keycloak retorna "Invalid issuer"

```bash
docker exec pinsaude-keycloak \
    bash -c 'exec 3<>/dev/tcp/127.0.0.1/8180 && printf "GET /realms/pinsaude HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && cat <&3' \
    | tail -1 | python3 -c "import sys,json; print(json.load(sys.stdin).get('issuer','?'))"
```

Se retornar `localhost`, adicionar no `docker-compose.prod.yml` e reiniciar:
```yaml
KC_HOSTNAME_URL: "http://212.85.12.228:8180"
```

### RabbitMQ — fiscal não conecta

```bash
# Verificar se RabbitMQ está UP
docker ps | grep rabbitmq
docker logs pinsaude-rabbitmq | tail -20

# Testar porta AMQP
nc -zv localhost 5672

# Verificar credenciais no .env
grep RABBITMQ /home/pinsaude/infra/.env
```

### MinIO — upload de documentos falha

```bash
docker logs pinsaude-minio | tail -20
# Verificar MINIO_ENDPOINT no .env (deve ser IP público, não localhost)
grep MINIO /home/pinsaude/infra/.env
```

### Nginx conflito com outro site

```bash
nginx -t 2>&1
grep -r "listen 80" /etc/nginx/sites-enabled/
```

### Flyway falha ao criar extensões

```sql
-- Como superuser do PostgreSQL
\c pinsaude
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```
