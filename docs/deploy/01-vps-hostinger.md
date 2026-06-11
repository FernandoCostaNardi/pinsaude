# Deploy Pin Saúde — VPS Hostinger (IP: 212.85.12.228)

Guia passo a passo para colocar o sistema em produção acessível via IP.
Cobre EPIC-01 (autenticação) e EPIC-02 (empresas, configuração fiscal, multi-tenancy).

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
               ──→  (outros serviços futuros)

Mailhog :8025  (UI para ver e-mails de teste)
Mailhog :1025  (SMTP para o Keycloak)
```

**Portas utilizadas pelo pinsaude (acima de 8099):**

| Serviço         | Porta | Acesso externo |
|-----------------|-------|----------------|
| Nginx (frontend)| 80    | http://212.85.12.228 |
| Gateway         | 8100  | Interno (Nginx proxy) |
| Keycloak        | 8180  | http://212.85.12.228:8180 |
| Onboarding      | 8185  | Interno (Gateway proxy) |
| Mailhog UI      | 8025  | http://212.85.12.228:8025 |
| Mailhog SMTP    | 1025  | Interno (Keycloak) |

---

## Pré-requisitos locais (Windows — sua máquina)

Antes de começar, certifique-se de que tem:
- Java 17 instalado (`java -version`)
- Maven (`mvn -version`)
- pnpm (`pnpm -v`)
- Cliente SSH (PuTTY, Windows Terminal, ou WSL)

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

# Portas em uso (confirmar que 80 e 8180 estão livres)
ss -tlnp | grep -E ':80|:8100|:8180|:8185'
```

### 1.3 Criar usuário pinsaude

```bash
useradd -m -s /bin/bash pinsaude
passwd pinsaude
# Digite uma senha forte (anote — será usada só para su, não para SSH)

# Criar estrutura de diretórios
mkdir -p /home/pinsaude/{infra,apps/onboarding,apps/gateway,frontend,logs,scripts}
chown -R pinsaude:pinsaude /home/pinsaude
```

### 1.4 Instalar Java 17 (se não tiver)

```bash
# Verificar se já tem Java 17
java -version 2>&1 | grep -q "17" && echo "Java 17 OK" || {
    apt update
    apt install -y openjdk-17-jre-headless
    java -version
}

# Confirmar localização do binário (será usada nos serviços)
which java
# Deve retornar: /usr/bin/java
```

### 1.5 Instalar Docker (se não tiver)

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

### 1.6 Instalar Nginx (se não tiver)

```bash
nginx -v 2>/dev/null || apt install -y nginx

# Verificar status
systemctl status nginx
```

> **ATENÇÃO:** Se o Nginx já estiver rodando com outros sites, NÃO reinicie sem antes verificar as configs existentes em `/etc/nginx/sites-enabled/`.

---

## PARTE 2 — Banco de Dados PostgreSQL

> Usaremos o PostgreSQL nativo que já está no servidor. Não instalaremos PostgreSQL via Docker.

### 2.1 Conectar ao PostgreSQL como superuser

```bash
# Descobrir o usuário admin do PG (geralmente postgres)
sudo -u postgres psql

# Se usar socket Unix:
su - postgres
psql
```

### 2.2 Criar banco, usuários e schemas

Execute este bloco completo dentro do `psql`:

```sql
-- Banco principal do pinsaude
CREATE DATABASE pinsaude
    WITH ENCODING = 'UTF8'
    LC_COLLATE = 'pt_BR.UTF-8'
    LC_CTYPE = 'pt_BR.UTF-8'
    TEMPLATE = template0;

-- Se pt_BR.UTF-8 não estiver disponível, use:
-- TEMPLATE = template0 LC_COLLATE='C' LC_CTYPE='C';

-- Banco do Keycloak
CREATE DATABASE keycloak
    WITH ENCODING = 'UTF8'
    TEMPLATE = template0;

-- Usuário do onboarding service
CREATE USER svc_onboarding WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';

-- Usuário do Keycloak
CREATE USER keycloak WITH PASSWORD 'TROCAR_POR_SENHA_FORTE';

-- Conectar ao banco pinsaude
\c pinsaude

-- Extensões necessárias (requerem superuser)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Schema e permissões para o onboarding
CREATE SCHEMA IF NOT EXISTS onboarding;
GRANT CREATE, USAGE ON SCHEMA onboarding TO svc_onboarding;
GRANT CREATE ON DATABASE pinsaude TO svc_onboarding;
ALTER USER svc_onboarding SET search_path TO onboarding, public;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding GRANT ALL ON TABLES TO svc_onboarding;
ALTER DEFAULT PRIVILEGES IN SCHEMA onboarding GRANT ALL ON SEQUENCES TO svc_onboarding;

-- Conectar ao banco do keycloak
\c keycloak

-- Permissões para o Keycloak
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
ALTER USER keycloak CREATEDB;

\q
```

> **Anotar as senhas** — serão usadas nos arquivos de configuração.

### 2.3 Verificar conexão (opcional)

```bash
psql -U svc_onboarding -d pinsaude -h localhost -c "SELECT current_user, current_database();"
# Deve exibir: svc_onboarding | pinsaude
```

---

## PARTE 3 — Keycloak

### 3.1 Copiar arquivos para o servidor

Na sua máquina Windows, dentro do projeto:

```powershell
# Copiar realm-export.json e docker-compose de produção
scp tools/keycloak/realm-export.json root@212.85.12.228:/home/pinsaude/infra/
scp tools/deploy/docker-compose.prod.yml root@212.85.12.228:/home/pinsaude/infra/
```

### 3.2 Criar arquivo de variáveis de ambiente

```bash
# No servidor, como root
cat > /home/pinsaude/infra/.env << 'EOF'
KC_ADMIN_USER=admin
KC_ADMIN_PASS=TROCAR_POR_SENHA_FORTE_KEYCLOAK
KC_DB_PASS=MESMA_SENHA_DO_PASSO_2.2_PARA_keycloak
DB_PASSWORD_ONBOARDING=MESMA_SENHA_DO_PASSO_2.2_PARA_svc_onboarding
EOF

# Proteger o arquivo
chmod 600 /home/pinsaude/infra/.env
chown pinsaude:pinsaude /home/pinsaude/infra/.env
```

### 3.3 Subir o Keycloak

```bash
cd /home/pinsaude/infra

# Carregar variáveis e subir
set -a && source .env && set +a
docker compose -f docker-compose.prod.yml up -d

# Aguardar inicialização (pode levar 2-3 minutos)
docker logs -f pinsaude-keycloak
# Aguardar a linha: "Keycloak 24.0.X on JVM ... started"
# Pressionar Ctrl+C para sair dos logs
```

### 3.4 Verificar

```bash
# Health check do Keycloak
curl -s http://localhost:8180/health/ready | grep -q '"status":"UP"' && echo "Keycloak OK" || echo "Keycloak ainda iniciando..."
```

### 3.5 Atualizar URLs do Keycloak para produção

O realm foi importado com URLs de localhost. Precisamos atualizar para o IP de produção.

Acesse: **http://212.85.12.228:8180/admin** → usuário `admin` → senha que definiu em `KC_ADMIN_PASS`

**Realm pinsaude → Clients → pinsaude-web → Settings:**
- **Valid Redirect URIs:** `http://212.85.12.228/*`
- **Valid Post Logout Redirect URIs:** `http://212.85.12.228/*`
- **Web Origins:** `http://212.85.12.228`

Clique em **Save**.

**Realm pinsaude → Realm Settings → Email:**
- Host: `localhost`
- Port: `1025`
- From: `noreply@pinsaude.com.br`
- Display name: `Pin Saúde`

Clique em **Save**.

---

## PARTE 4 — Build do Projeto (sua máquina Windows)

### 4.1 Fazer o build do onboarding

```powershell
cd G:\olisystem\pinsaude
node tools/scripts/mvn-build.js :pinsaude-onboarding

# O JAR será gerado em:
# services/onboarding/target/pinsaude-onboarding-0.0.1-SNAPSHOT.jar
```

### 4.2 Fazer o build do gateway

```powershell
node tools/scripts/mvn-build.js :pinsaude-gateway

# O JAR será gerado em:
# gateway/target/pinsaude-gateway-0.0.1-SNAPSHOT.jar
```

### 4.3 Fazer o build do frontend

```powershell
# Criar arquivo de variáveis de produção
Set-Content apps/web/.env.production "VITE_KC_URL=http://212.85.12.228:8180`nVITE_KC_REALM=pinsaude`nVITE_KC_CLIENT=pinsaude-web"

# Build
$env:NODE_TLS_REJECT_UNAUTHORIZED = "0"
npx nx run web:build

# Os arquivos estarão em:
# apps/web/dist/
```

---

## PARTE 5 — Deploy dos Serviços Java

### 5.1 Transferir os JARs

```powershell
# Do Windows, na raiz do projeto:
scp services/onboarding/target/pinsaude-onboarding-0.0.1-SNAPSHOT.jar root@212.85.12.228:/home/pinsaude/apps/onboarding/app.jar
scp gateway/target/pinsaude-gateway-0.0.1-SNAPSHOT.jar root@212.85.12.228:/home/pinsaude/apps/gateway/app.jar
scp tools/deploy/onboarding-application-prod.yml root@212.85.12.228:/home/pinsaude/apps/onboarding/application-prod.yml
scp tools/deploy/gateway-application-prod.yml root@212.85.12.228:/home/pinsaude/apps/gateway/application-prod.yml
```

### 5.2 Ajustar permissões no servidor

```bash
# No servidor
chown -R pinsaude:pinsaude /home/pinsaude/apps/
chmod 640 /home/pinsaude/apps/onboarding/application-prod.yml
chmod 640 /home/pinsaude/apps/gateway/application-prod.yml
```

### 5.3 Criar serviços systemd

```bash
# Copiar arquivos de serviço
cat > /etc/systemd/system/pinsaude-onboarding.service << 'EOF'
[Unit]
Description=Pin Saude - Onboarding Service
After=network.target postgresql.service

[Service]
Type=simple
User=pinsaude
Group=pinsaude
WorkingDirectory=/home/pinsaude/apps/onboarding

EnvironmentFile=/home/pinsaude/infra/.env

ExecStart=/usr/bin/java \
  -Xms256m -Xmx512m \
  -Dspring.profiles.active=prod \
  -Dspring.config.additional-location=file:/home/pinsaude/apps/onboarding/application-prod.yml \
  -jar /home/pinsaude/apps/onboarding/app.jar

Restart=on-failure
RestartSec=10
StandardOutput=append:/home/pinsaude/logs/onboarding.log
StandardError=append:/home/pinsaude/logs/onboarding.log

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/systemd/system/pinsaude-gateway.service << 'EOF'
[Unit]
Description=Pin Saude - Gateway
After=network.target pinsaude-onboarding.service

[Service]
Type=simple
User=pinsaude
Group=pinsaude
WorkingDirectory=/home/pinsaude/apps/gateway

ExecStart=/usr/bin/java \
  -Xms128m -Xmx256m \
  -Dspring.profiles.active=prod \
  -Dspring.config.additional-location=file:/home/pinsaude/apps/gateway/application-prod.yml \
  -jar /home/pinsaude/apps/gateway/app.jar

Restart=on-failure
RestartSec=10
StandardOutput=append:/home/pinsaude/logs/gateway.log
StandardError=append:/home/pinsaude/logs/gateway.log

[Install]
WantedBy=multi-user.target
EOF
```

### 5.4 Habilitar e iniciar os serviços

```bash
systemctl daemon-reload

# Iniciar o onboarding primeiro (Flyway vai executar as migrations V1-V5)
systemctl enable pinsaude-onboarding
systemctl start pinsaude-onboarding

# Aguardar ~30 segundos e verificar os logs
sleep 30
tail -50 /home/pinsaude/logs/onboarding.log
# Deve aparecer: "Started OnboardingApplication in X.X seconds"
# E as migrations Flyway: "Successfully applied X migrations"

# Depois iniciar o gateway
systemctl enable pinsaude-gateway
systemctl start pinsaude-gateway
sleep 10
tail -20 /home/pinsaude/logs/gateway.log
```

### 5.5 Verificar status

```bash
systemctl status pinsaude-onboarding
systemctl status pinsaude-gateway

# Health checks
curl -s http://localhost:8185/actuator/health | python3 -m json.tool
curl -s http://localhost:8100/actuator/health  | python3 -m json.tool
```

---

## PARTE 6 — Frontend (Nginx)

### 6.1 Transferir os arquivos do frontend

```powershell
# Do Windows — usar scp ou rsync para a pasta dist/
# Opção 1: compactar e transferir
Compress-Archive -Path apps/web/dist/* -DestinationPath apps/web/dist.zip
scp apps/web/dist.zip root@212.85.12.228:/home/pinsaude/frontend/

# No servidor, descompactar:
# cd /home/pinsaude/frontend && unzip dist.zip && rm dist.zip
```

```bash
# Ou usar rsync (mais eficiente para re-deploys):
# rsync -avz --delete apps/web/dist/ root@212.85.12.228:/home/pinsaude/frontend/
```

No servidor:
```bash
# Se usou zip:
cd /home/pinsaude/frontend
unzip dist.zip
rm dist.zip

# Permissões para o Nginx ler
chmod -R 755 /home/pinsaude/frontend
chown -R pinsaude:pinsaude /home/pinsaude/frontend
```

### 6.2 Configurar Nginx

```bash
# Verificar se já existe um site no Nginx na porta 80 com esse IP
grep -r "212.85.12.228\|server_name" /etc/nginx/sites-enabled/ 2>/dev/null

# Criar config do pinsaude
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

# Ativar e testar
ln -s /etc/nginx/sites-available/pinsaude /etc/nginx/sites-enabled/
nginx -t
# Deve mostrar: "configuration file ... test is successful"

# Recarregar o Nginx (não reinicia — não derruba outros sites)
systemctl reload nginx
```

> **ATENÇÃO:** Se já existir um `default` em `/etc/nginx/sites-enabled/` com `server_name _` (catch-all) ouvindo na porta 80, pode haver conflito. Verifique com `nginx -t` e ajuste o `server_name` conforme necessário.

---

## PARTE 7 — Firewall (UFW)

```bash
# Verificar status atual do UFW
ufw status verbose

# Se UFW estiver ativo, liberar as portas do pinsaude
ufw allow 80/tcp    comment "Nginx - pinsaude frontend"
ufw allow 8180/tcp  comment "Keycloak - pinsaude"
ufw allow 8025/tcp  comment "Mailhog UI - pinsaude (remover em producao real)"

# IMPORTANTE: NÃO expor as portas internas (8100, 8185, 1025, 5432) externamente
# Elas só precisam ser acessíveis via localhost

ufw status
```

---

## PARTE 8 — Verificação Final

### 8.1 Testar cada componente

```bash
# 1. PostgreSQL
psql -U svc_onboarding -d pinsaude -h localhost -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'onboarding';"
# Deve retornar 5 (tabelas criadas pelo Flyway)

# 2. Keycloak
curl -s http://localhost:8180/realms/pinsaude/.well-known/openid-configuration | python3 -c "import sys,json; d=json.load(sys.stdin); print('Keycloak OK:', d['issuer'])"

# 3. Onboarding
curl -s http://localhost:8185/actuator/health
# {"status":"UP"}

# 4. Gateway
curl -s http://localhost:8100/actuator/health
# {"status":"UP"}

# 5. Nginx
curl -s -o /dev/null -w "%{http_code}" http://localhost/
# 200

# 6. Mailhog
curl -s -o /dev/null -w "%{http_code}" http://localhost:8025/
# 200
```

### 8.2 Teste de acesso completo (no seu navegador)

1. Abrir **http://212.85.12.228** → deve aparecer a tela de login do Pin Saúde
2. Fazer login com `gestao@pinsaude.com.br` / `test123`
3. Navegar para a tela de Empresas
4. Criar uma empresa de teste

> **Usuários pré-configurados no realm:**
>
> | Email | Senha | Role | MFA |
> |---|---|---|---|
> | medico@pinsaude.com.br | test123 | medico | Não |
> | operacao@pinsaude.com.br | test123 | operacao | Sim (TOTP pendente) |
> | gestao@pinsaude.com.br | test123 | gestao | Sim (TOTP pendente) |
>
> Para o gestor testar sem MFA: no Keycloak Admin → Users → gestao → Required Actions → remover `CONFIGURE_TOTP`.

---

## PARTE 9 — Script de Re-deploy

Para futuras atualizações, crie este script no servidor:

```bash
cat > /home/pinsaude/scripts/redeploy.sh << 'SCRIPT'
#!/bin/bash
# Uso: ./redeploy.sh [onboarding|gateway|frontend|all]
set -e

COMPONENTE=${1:-all}

if [[ "$COMPONENTE" == "onboarding" || "$COMPONENTE" == "all" ]]; then
    echo ">>> Parando onboarding..."
    systemctl stop pinsaude-onboarding
    echo ">>> Copiando novo JAR..."
    # cp /tmp/pinsaude-onboarding-SNAPSHOT.jar /home/pinsaude/apps/onboarding/app.jar
    echo "!!! Coloque o novo app.jar em /home/pinsaude/apps/onboarding/ e execute novamente"
    systemctl start pinsaude-onboarding
    echo ">>> Onboarding reiniciado"
fi

if [[ "$COMPONENTE" == "gateway" || "$COMPONENTE" == "all" ]]; then
    echo ">>> Reiniciando gateway..."
    systemctl restart pinsaude-gateway
fi

if [[ "$COMPONENTE" == "frontend" || "$COMPONENTE" == "all" ]]; then
    echo ">>> Frontend: descompactar novo dist.zip em /home/pinsaude/frontend/"
    # unzip -o /tmp/dist.zip -d /home/pinsaude/frontend/
    systemctl reload nginx
fi

echo ">>> Deploy concluído"
SCRIPT

chmod +x /home/pinsaude/scripts/redeploy.sh
chown pinsaude:pinsaude /home/pinsaude/scripts/redeploy.sh
```

---

## PARTE 10 — Monitoramento de Logs

```bash
# Logs em tempo real
tail -f /home/pinsaude/logs/onboarding.log
tail -f /home/pinsaude/logs/gateway.log
tail -f /var/log/nginx/access.log
docker logs -f pinsaude-keycloak

# Status geral
systemctl status pinsaude-onboarding pinsaude-gateway nginx
docker compose -f /home/pinsaude/infra/docker-compose.prod.yml ps
```

---

## Resumo das URLs de acesso

| URL | O que é |
|-----|---------|
| http://212.85.12.228 | Sistema Pin Saúde (frontend) |
| http://212.85.12.228:8180/admin | Keycloak Admin Console |
| http://212.85.12.228:8025 | Mailhog (ver e-mails de teste) |

---

## Problemas comuns

### Onboarding não sobe — erro de SSL no Maven/Flyway

O servidor pode ter inspeção SSL corporativa. Adicionar ao onboarding.service:
```
-Dmaven.wagon.http.ssl.insecure=true \
```
Não se aplica ao ambiente de produção Hostinger (sem proxy corporativo).

### Keycloak retorna "Invalid issuer"

O JWT contém `http://localhost:8180/...` mas o serviço espera `http://212.85.12.228:8180/...`.
Isso acontece se o Keycloak não sabe seu IP público. Verificar:
```bash
# Dentro do container Keycloak, a URL interna
docker exec pinsaude-keycloak curl -s http://localhost:8180/realms/pinsaude | python3 -c "import sys,json; print(json.load(sys.stdin)['issuer'])"
```
Se retornar `localhost`, adicionar `KC_HOSTNAME_URL=http://212.85.12.228:8180` no `docker-compose.prod.yml` e reiniciar.

### CORS ao fazer login

No Keycloak Admin → Realm pinsaude → Clients → pinsaude-web → Settings → Web Origins:
Adicionar `http://212.85.12.228` e salvar.

### Nginx conflito com outro site

```bash
nginx -t 2>&1
# Verificar o erro específico
grep -r "listen 80" /etc/nginx/sites-enabled/
```
Se houver conflito, alterar `server_name` no config do pinsaude para o IP específico e garantir que o outro site usa `server_name _` ou `default_server`.

### Flyway falha ao criar extensões

Se `CREATE EXTENSION pgcrypto` falhar (permissão negada):
```sql
-- Como superuser do PostgreSQL
\c pinsaude
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```
As extensões precisam ser criadas uma vez pelo superuser antes do Flyway rodar.
