# Deploy Pin Saúde — VPS Hostinger (IP: 212.85.12.228)

Guia passo a passo para colocar o sistema em produção acessível via IP.
Cobre EPIC-01 (autenticação), EPIC-02 (empresas, configuração fiscal, multi-tenancy) e EPIC-01.5 (gestão de usuários).

---

## Arquitetura final

```
Navegador
    │
    ▼ porta 80
  Nginx  ──── /          ──→  arquivos estáticos React  (/home/pinsaude/frontend/)
             /api/*       ──→  Gateway :8100
    │
    │ (direto, sem Nginx)
    ▼ porta 8180
  Keycloak (Docker)  ──→  PostgreSQL nativo :5432 / banco keycloak

Gateway :8100  ──→  Onboarding :8185
               ──→  Gestão     :8186
               ──→  (outros serviços futuros)

Mailhog :8125  (UI para ver e-mails de teste)
Mailhog :1025  (SMTP para o Keycloak)
```

**Portas utilizadas pelo pinsaude (acima de 8099):**

| Serviço          | Porta | Acesso externo |
|------------------|-------|----------------|
| Nginx (frontend) | 80    | http://212.85.12.228 |
| Gateway          | 8100  | Interno (Nginx proxy) |
| Keycloak         | 8180  | http://212.85.12.228:8180 |
| Onboarding       | 8185  | Interno (Gateway proxy) |
| Gestão           | 8186  | Interno (Gateway proxy) |
| Mailhog UI       | 8125  | http://212.85.12.228:8125 |
| Mailhog SMTP     | 1025  | Interno (Keycloak) |

---

## PARTE 1 — Preparação do Servidor

### 1.1 Conectar ao servidor

```bash
ssh root@212.85.12.228
```

### 1.2 Verificar o que já existe

```bash
# Versão do PostgreSQL nativo
psql --version
systemctl status postgresql

# Nginx instalado?
nginx -v 2>/dev/null || echo "Nginx não instalado"

# Docker instalado?
docker --version 2>/dev/null || echo "Docker não instalado"

# Java disponível?
java -version 2>/dev/null || echo "Java não instalado"

# Portas em uso (confirmar que estão livres)
ss -tlnp | grep -E ':80|:8100|:8180|:8185|:8186'
```

### 1.3 Criar usuário pinsaude

```bash
useradd -m -s /bin/bash pinsaude
passwd pinsaude
# Digite uma senha forte

# Criar estrutura de diretórios
mkdir -p /home/pinsaude/{infra,apps/onboarding,apps/gestao,apps/gateway,frontend,logs,scripts}
chown -R pinsaude:pinsaude /home/pinsaude
```

### 1.4 Instalar Java 17

```bash
java -version 2>&1 | grep -q "17" && echo "Java 17 OK" || {
    apt update
    apt install -y openjdk-17-jdk
    java -version
}

# Confirmar localização do binário (será usada nos serviços)
which java
# Deve retornar: /usr/bin/java
```

### 1.5 Instalar Maven

```bash
mvn -version 2>/dev/null || apt install -y maven

# Verificar versão (precisa ser 3.6+)
mvn -version
```

> Se o apt trouxer versão antiga, instalar manualmente:
> ```bash
> wget https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
> tar -xf apache-maven-3.9.6-bin.tar.gz -C /opt
> ln -sf /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn
> ```

### 1.6 Instalar Node.js 22 + pnpm

```bash
# Node.js 22 via NodeSource
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt install -y nodejs

# Verificar
node -v   # v22.x.x
npm -v    # 10.x.x

# pnpm via npm
npm install -g pnpm@9

# Verificar
pnpm -v   # 9.x.x
```

### 1.7 Instalar Docker (se não tiver)

```bash
docker --version 2>/dev/null || {
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
    usermod -aG docker pinsaude
}

# Verificar
docker --version
docker compose version
```

### 1.8 Instalar Nginx (se não tiver)

```bash
nginx -v 2>/dev/null || apt install -y nginx

systemctl status nginx
```

> **ATENÇÃO:** Se o Nginx já estiver rodando com outros sites, NÃO reinicie sem antes verificar as configs existentes em `/etc/nginx/sites-enabled/`.

### 1.9 Instalar Git

```bash
git --version 2>/dev/null || apt install -y git
```

---

## PARTE 2 — Banco de Dados PostgreSQL

> Usaremos o PostgreSQL nativo que já está no servidor. Não instalaremos PostgreSQL via Docker.

### 2.1 Conectar ao PostgreSQL como superuser

```bash
sudo -u postgres psql
```

### 2.2 Criar banco, usuários e schemas

Execute este bloco dentro do `psql`:

```sql
-- Banco principal do pinsaude
CREATE DATABASE pinsaude
    WITH ENCODING = 'UTF8'
    LC_COLLATE = 'pt_BR.UTF-8'
    LC_CTYPE = 'pt_BR.UTF-8'
    TEMPLATE = template0;

-- Se pt_BR.UTF-8 não estiver disponível:
-- CREATE DATABASE pinsaude WITH ENCODING='UTF8' TEMPLATE=template0;

-- Banco do Keycloak
CREATE DATABASE keycloak
    WITH ENCODING = 'UTF8'
    TEMPLATE = template0;

-- Usuário do serviço Onboarding
CREATE USER svc_onboarding WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';

-- Usuário do serviço Gestão
CREATE USER svc_gestao WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';

-- Usuário do Keycloak
CREATE USER keycloak WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';

-- Conectar ao banco pinsaude
\c pinsaude

-- Extensões necessárias (requerem superuser)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Schema e permissões para o onboarding
CREATE SCHEMA IF NOT EXISTS onboarding;
GRANT CONNECT, CREATE ON DATABASE pinsaude TO svc_onboarding;
GRANT CREATE, USAGE ON SCHEMA onboarding TO svc_onboarding;
ALTER USER svc_onboarding SET search_path TO onboarding, public;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding GRANT ALL ON TABLES TO svc_onboarding;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding GRANT ALL ON SEQUENCES TO svc_onboarding;

-- Permissões para o gestão (conexão apenas, sem tabelas por ora)
GRANT CONNECT ON DATABASE pinsaude TO svc_gestao;

-- Banco do Keycloak
\c keycloak
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
ALTER USER keycloak CREATEDB;

\q
```

> **Anotar as senhas** — serão usadas nos arquivos `.env` e de configuração.

### 2.3 Verificar conexão

```bash
psql -U svc_onboarding -d pinsaude -h localhost -c "SELECT current_user, current_database();"
# Deve exibir: svc_onboarding | pinsaude
```

---

## PARTE 3 — Keycloak + Mailhog via Docker

### 3.1 Criar arquivo de variáveis de ambiente

```bash
cat > /home/pinsaude/infra/.env << 'EOF'
# Keycloak admin
KC_ADMIN_USER=admin
KC_ADMIN_PASS=TROCAR_POR_SENHA_FORTE_KEYCLOAK
KC_DB_PASS=TROCAR_PELA_SENHA_DO_USUARIO_keycloak

# Serviços Java
DB_PASSWORD_ONBOARDING=TROCAR_PELA_SENHA_DO_svc_onboarding
DB_PASSWORD_GESTAO=TROCAR_PELA_SENHA_DO_svc_gestao
EOF

chmod 600 /home/pinsaude/infra/.env
chown pinsaude:pinsaude /home/pinsaude/infra/.env
```

### 3.2 Copiar arquivos para o servidor

Na sua máquina Windows:

```powershell
scp tools/keycloak/realm-export.json       root@212.85.12.228:/home/pinsaude/infra/
scp tools/deploy/docker-compose.prod.yml   root@212.85.12.228:/home/pinsaude/infra/
```

### 3.3 Subir o Keycloak

```bash
cd /home/pinsaude/infra

# Carregar variáveis e subir os containers
set -a && source .env && set +a
docker compose -f docker-compose.prod.yml up -d

# Aguardar inicialização — pode levar 2-3 minutos na primeira vez
docker logs -f pinsaude-keycloak
# Aguardar: "Keycloak 24.x.x on JVM ... started in X.XXXs"
# Pressionar Ctrl+C para sair
```

### 3.4 Verificar

```bash
curl -s http://localhost:8180/health/ready | grep -q '"status":"UP"' \
    && echo "Keycloak OK" \
    || echo "Keycloak ainda iniciando..."
```

### 3.5 Atualizar URLs do Keycloak para produção

Acesse **http://212.85.12.228:8180/admin** com usuário `admin` e a senha definida em `KC_ADMIN_PASS`.

**Realm pinsaude → Clients → pinsaude-web → Settings:**
- **Valid Redirect URIs:** `http://212.85.12.228/*`
- **Valid Post Logout Redirect URIs:** `http://212.85.12.228/*`
- **Web Origins:** `http://212.85.12.228`

Clique em **Save**.

**Realm pinsaude → Realm Settings → Email:**
- Host: `localhost` ← *o realm-export.json tem "mailhog" (dev), trocar para "localhost"*
- Port: `1025`
- From: `noreply@pinsaude.com.br`

Clique em **Save**.

### 3.6 Usuários de teste (criados automaticamente)

O `realm-export.json` já inclui os usuários a seguir — importados automaticamente na primeira inicialização. **Nenhuma ação manual é necessária.**

| E-mail | Senha | Role | MFA |
|--------|-------|------|-----|
| `gestao@pinsaude.com.br` | `test123` | gestao | Não |
| `medico@pinsaude.com.br` | `test123` | medico | Não |
| `operacao@pinsaude.com.br` | `test123` | operacao | Não |

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

> Se o repositório for privado, configurar SSH key antes:
> ```bash
> ssh-keygen -t ed25519 -C "pinsaude@vps"
> cat ~/.ssh/id_ed25519.pub
> # Adicionar essa chave como Deploy Key no GitHub (Settings → Deploy Keys)
> ```

### 4.2 Instalar dependências Node.js

```bash
cd /home/pinsaude/projeto
pnpm install --frozen-lockfile
```

### 4.3 Compilar os serviços Java

```bash
cd /home/pinsaude/projeto

# Compila todos os módulos Maven (onboarding, gestão, gateway e parent)
mvn clean package -DskipTests --no-transfer-progress

# JARs gerados em:
# services/onboarding/target/pinsaude-onboarding-0.0.1-SNAPSHOT.jar
# services/gestao/target/pinsaude-gestao-0.0.1-SNAPSHOT.jar
# gateway/target/pinsaude-gateway-0.0.1-SNAPSHOT.jar
```

> Primeira compilação pode levar 5–10 minutos baixando dependências do Maven Central.

### 4.4 Compilar o frontend

```bash
cd /home/pinsaude/projeto/apps/web

# Variáveis de ambiente de produção
cat > .env.production << 'EOF'
VITE_KC_URL=http://212.85.12.228:8180
VITE_KC_REALM=pinsaude
VITE_KC_CLIENT=pinsaude-web
EOF

# Build
pnpm build
# Saída em: apps/web/dist/
```

### 4.5 Copiar artefatos para as pastas de deploy

```bash
cd /home/pinsaude/projeto

# Serviços Java
cp services/onboarding/target/pinsaude-onboarding-*.jar /home/pinsaude/apps/onboarding/app.jar
cp services/gestao/target/pinsaude-gestao-*.jar         /home/pinsaude/apps/gestao/app.jar
cp gateway/target/pinsaude-gateway-*.jar                /home/pinsaude/apps/gateway/app.jar

# Frontend
cp -r apps/web/dist/* /home/pinsaude/frontend/
chmod -R 755 /home/pinsaude/frontend
```

---

## PARTE 5 — Configurações de Produção

### 5.1 Copiar arquivos de configuração

Na sua máquina Windows:

```powershell
scp tools/deploy/onboarding-application-prod.yml root@212.85.12.228:/home/pinsaude/apps/onboarding/application-prod.yml
scp tools/deploy/gestao-application-prod.yml     root@212.85.12.228:/home/pinsaude/apps/gestao/application-prod.yml
scp tools/deploy/gateway-application-prod.yml    root@212.85.12.228:/home/pinsaude/apps/gateway/application-prod.yml
```

### 5.2 Proteger arquivos de configuração no servidor

```bash
chown -R pinsaude:pinsaude /home/pinsaude/apps/
chmod 640 /home/pinsaude/apps/*/application-prod.yml
```

---

## PARTE 6 — Serviços systemd

### 6.1 Copiar arquivos de serviço

Na sua máquina Windows:

```powershell
scp tools/deploy/pinsaude-onboarding.service root@212.85.12.228:/etc/systemd/system/
scp tools/deploy/pinsaude-gestao.service     root@212.85.12.228:/etc/systemd/system/
scp tools/deploy/pinsaude-gateway.service    root@212.85.12.228:/etc/systemd/system/
```

### 6.2 Habilitar e iniciar os serviços

```bash
systemctl daemon-reload

# Onboarding primeiro — o Flyway cria as tabelas (schemas, RLS, policies)
systemctl enable pinsaude-onboarding
systemctl start pinsaude-onboarding

# Aguardar subir (Flyway pode levar ~30s na primeira execução)
sleep 30
tail -50 /home/pinsaude/logs/onboarding.log
# Deve aparecer: "Started OnboardingApplication in X.X seconds"
# E as migrations: "Successfully applied X migrations"

# Gestão (depende do Keycloak estar rodando)
systemctl enable pinsaude-gestao
systemctl start pinsaude-gestao
sleep 15
tail -20 /home/pinsaude/logs/gestao.log

# Gateway
systemctl enable pinsaude-gateway
systemctl start pinsaude-gateway
sleep 10
tail -20 /home/pinsaude/logs/gateway.log
```

### 6.3 Verificar status e health

```bash
systemctl status pinsaude-onboarding pinsaude-gestao pinsaude-gateway

# Health checks
curl -s http://localhost:8185/actuator/health   # onboarding
curl -s http://localhost:8186/actuator/health   # gestão
curl -s http://localhost:8100/actuator/health   # gateway
# Todos devem retornar {"status":"UP"}
```

---

## PARTE 7 — Frontend (Nginx)

### 7.1 Configurar Nginx

```bash
# Verificar conflitos antes de criar o config
grep -r "listen 80" /etc/nginx/sites-enabled/ 2>/dev/null

cat > /etc/nginx/sites-available/pinsaude << 'EOF'
server {
    listen 80;
    server_name 212.85.12.228;

    root /home/pinsaude/frontend;
    index index.html;

    # Frontend React — SPA: qualquer rota não encontrada → index.html
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
# Verificar status atual
ufw status verbose

# Portas do pinsaude a liberar externamente
ufw allow 80/tcp    comment "Nginx - pinsaude frontend"
ufw allow 8180/tcp  comment "Keycloak - pinsaude"
ufw allow 8125/tcp  comment "Mailhog UI - pinsaude (remover em producao real)"

# NÃO expor externamente: 8100, 8185, 8186, 1025, 5432
# Eles ficam acessíveis apenas via localhost
```

---

## PARTE 9 — Verificação Final

### 9.1 Testar cada componente

```bash
# PostgreSQL — tabelas criadas pelo Flyway
psql -U svc_onboarding -d pinsaude -h localhost \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'onboarding';"
# Deve retornar 5

# Keycloak
curl -s http://localhost:8180/realms/pinsaude/.well-known/openid-configuration \
    | python3 -c "import sys,json; print('Keycloak OK:', json.load(sys.stdin)['issuer'])"

# Serviços Java
curl -s http://localhost:8185/actuator/health   # onboarding
curl -s http://localhost:8186/actuator/health   # gestão
curl -s http://localhost:8100/actuator/health   # gateway

# Nginx
curl -s -o /dev/null -w "%{http_code}" http://localhost/   # deve retornar 200

# Mailhog
curl -s -o /dev/null -w "%{http_code}" http://localhost:8125/   # deve retornar 200
```

### 9.2 Teste de acesso completo (no navegador)

1. Abrir **http://212.85.12.228** → tela de login do Pin Saúde
2. Login com `gestao@pinsaude.com.br` / `test123` → dashboard com menu **Gestão de Usuários** visível
3. Login com `medico@pinsaude.com.br` / `test123` → menu de gestão **não** aparece
4. Criar uma empresa de teste e confirmar persistência

---

## PARTE 10 — Atualizações Futuras

```bash
su - pinsaude
cd /home/pinsaude/projeto

# Puxar mudanças
git pull origin main

# Recompilar Java
mvn clean package -DskipTests --no-transfer-progress

# Copiar JARs
cp services/onboarding/target/pinsaude-onboarding-*.jar /home/pinsaude/apps/onboarding/app.jar
cp services/gestao/target/pinsaude-gestao-*.jar         /home/pinsaude/apps/gestao/app.jar
cp gateway/target/pinsaude-gateway-*.jar                /home/pinsaude/apps/gateway/app.jar

# Reiniciar serviços (como root)
exit
systemctl restart pinsaude-onboarding pinsaude-gestao pinsaude-gateway

# Atualizar frontend (se houver mudanças)
su - pinsaude
cd /home/pinsaude/projeto/apps/web
pnpm build
cp -r dist/* /home/pinsaude/frontend/
```

---

## Arquivos de Suporte em `tools/deploy/`

| Arquivo | Descrição |
|---------|-----------|
| `docker-compose.prod.yml` | Keycloak + Mailhog (PostgreSQL nativo) |
| `onboarding-application-prod.yml` | Config Spring Boot — Onboarding (porta 8185) |
| `gestao-application-prod.yml` | Config Spring Boot — Gestão (porta 8186) |
| `gateway-application-prod.yml` | Config Spring Cloud Gateway (porta 8100) |
| `pinsaude-onboarding.service` | Systemd unit — Onboarding |
| `pinsaude-gestao.service` | Systemd unit — Gestão |
| `pinsaude-gateway.service` | Systemd unit — Gateway |
| `nginx-pinsaude.conf` | Config Nginx (porta 80, SPA + proxy `/api/`) |

---

## URLs de Acesso

| URL | O que é |
|-----|---------|
| http://212.85.12.228 | Sistema Pin Saúde (frontend) |
| http://212.85.12.228:8180/admin | Keycloak Admin Console |
| http://212.85.12.228:8125 | Mailhog (ver e-mails enviados) |

---

## Troubleshooting

### Serviço não sobe — verificar logs

```bash
journalctl -u pinsaude-onboarding --since "5 minutes ago" --no-pager
tail -100 /home/pinsaude/logs/onboarding.log
```

| Erro | Causa | Solução |
|------|-------|---------|
| `Connection refused` (5432) | PG não aceita conexões locais | Verificar `pg_hba.conf`, reiniciar PG |
| `password authentication failed` | Senha no `.env` errada | Corrigir `.env` e reiniciar o serviço |
| `Could not validate credentials` | Keycloak inacessível (8180) | Verificar `docker ps`, logs do Keycloak |
| `Flyway found non-empty schema` | Migration rodou parcialmente | Verificar `flyway_schema_history` no PG |
| Porta já em uso | Conflito com outro serviço | `ss -tlnp \| grep 818X` |

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

### CORS ao fazer login

No Keycloak Admin → Realm pinsaude → Clients → pinsaude-web → Settings → Web Origins:
Adicionar `http://212.85.12.228` e salvar.

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

As extensões precisam ser criadas uma vez pelo superuser antes do Flyway rodar.
