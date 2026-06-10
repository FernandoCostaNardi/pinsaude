#!/usr/bin/env bash
# setup-realm.sh — configura o realm pinsaude no Keycloak via Admin REST API
# Uso: ./tools/keycloak/setup-realm.sh
# Pré-requisito: Keycloak rodando em http://localhost:8080

set -euo pipefail

KC_URL="${KC_URL:-http://localhost:8080}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin}"
REALM="pinsaude"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REALM_FILE="$SCRIPT_DIR/realm-export.json"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}   $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERR]${NC}  $*"; exit 1; }

# ─────────────────────────────────────────────
# 1. Aguarda Keycloak estar pronto
# ─────────────────────────────────────────────
wait_keycloak() {
  info "Aguardando Keycloak em $KC_URL/health/ready ..."
  local attempts=0
  until curl -sf "$KC_URL/health/ready" > /dev/null 2>&1; do
    attempts=$((attempts + 1))
    [ $attempts -ge 30 ] && error "Keycloak não ficou pronto em 150s. Verifique os containers."
    echo -n "."
    sleep 5
  done
  echo ""
  ok "Keycloak pronto."
}

# ─────────────────────────────────────────────
# 2. Obtém token de admin
# ─────────────────────────────────────────────
get_admin_token() {
  info "Obtendo token de admin..."
  ACCESS_TOKEN=$(curl -sf -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=admin-cli&username=$KC_ADMIN&password=$KC_ADMIN_PASS" \
    | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
  [ -z "$ACCESS_TOKEN" ] && error "Falha ao obter token de admin."
  ok "Token obtido."
}

# ─────────────────────────────────────────────
# 3. Importa ou atualiza o realm
# ─────────────────────────────────────────────
import_realm() {
  info "Verificando realm '$REALM'..."
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$KC_URL/admin/realms/$REALM")

  if [ "$status" = "200" ]; then
    warn "Realm '$REALM' já existe. Pulando criação."
  else
    info "Importando realm '$REALM' de $REALM_FILE ..."
    local http_code
    http_code=$(curl -s -o /tmp/kc-import-out.json -w "%{http_code}" \
      -X POST "$KC_URL/admin/realms" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -d @"$REALM_FILE")
    if [ "$http_code" = "201" ]; then
      ok "Realm '$REALM' criado com sucesso."
    else
      error "Falha ao criar realm. HTTP $http_code. Resposta: $(cat /tmp/kc-import-out.json 2>/dev/null)"
    fi
  fi
}

# ─────────────────────────────────────────────
# 4. Verifica roles
# ─────────────────────────────────────────────
verify_roles() {
  info "Verificando roles..."
  local roles
  roles=$(curl -sf \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$KC_URL/admin/realms/$REALM/roles" \
    | grep -o '"name":"[^"]*"' | cut -d'"' -f4 | tr '\n' ' ')
  for role in medico operacao financeiro contabil gestao; do
    if echo "$roles" | grep -qw "$role"; then
      ok "  Role '$role' encontrada."
    else
      error "Role '$role' NÃO encontrada. Roles disponíveis: $roles"
    fi
  done
}

# ─────────────────────────────────────────────
# 5. Verifica clients
# ─────────────────────────────────────────────
verify_clients() {
  info "Verificando clients..."
  for client_id in pinsaude-web pinsaude-gateway; do
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      "$KC_URL/admin/realms/$REALM/clients?clientId=$client_id")
    local count
    count=$(curl -sf \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      "$KC_URL/admin/realms/$REALM/clients?clientId=$client_id" \
      | grep -c '"clientId"' || true)
    if [ "$count" -ge 1 ] 2>/dev/null; then
      ok "  Client '$client_id' encontrado."
    else
      error "Client '$client_id' NÃO encontrado."
    fi
  done
}

# ─────────────────────────────────────────────
# 6. Testa autenticação e valida JWT
# ─────────────────────────────────────────────
test_authentication() {
  info "Testando autenticação com medico@pinsaude.com.br ..."
  local token_response
  token_response=$(curl -sf -X POST \
    "$KC_URL/realms/$REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=pinsaude-web&username=medico@pinsaude.com.br&password=test123&scope=openid")

  local access_token
  access_token=$(echo "$token_response" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
  [ -z "$access_token" ] && error "Falha ao obter token para medico@pinsaude.com.br"
  ok "Token JWT obtido com sucesso."

  # Decodifica payload (base64url → base64)
  local payload
  payload=$(echo "$access_token" | cut -d'.' -f2 | tr '_-' '/+' | awk '{
    n = length($0) % 4
    if (n == 2) print $0 "=="
    else if (n == 3) print $0 "="
    else print $0
  }' | base64 -d 2>/dev/null || echo "{}")

  info "Verificando claim realm_access.roles..."
  if echo "$payload" | grep -q '"realm_access"'; then
    ok "  Claim realm_access.roles presente no token."
  else
    warn "  Claim realm_access.roles não encontrado no payload."
  fi

  info "Verificando claim cnpj_id..."
  if echo "$payload" | grep -q '"cnpj_id"'; then
    local cnpj
    cnpj=$(echo "$payload" | grep -o '"cnpj_id":"[^"]*"' | cut -d'"' -f4)
    ok "  Claim cnpj_id presente: $cnpj"
  else
    warn "  Claim cnpj_id não encontrado. Verifique se o protocol mapper está configurado."
  fi

  info "Verificando role medico no token..."
  if echo "$payload" | grep -q '"medico"'; then
    ok "  Role 'medico' presente no token."
  else
    warn "  Role 'medico' não encontrada no token."
  fi
}

# ─────────────────────────────────────────────
# 7. Verifica MFA (requiredActions) dos usuários privilegiados
# ─────────────────────────────────────────────
verify_mfa() {
  info "Verificando MFA dos usuários privilegiados..."
  for user_email in operacao@pinsaude.com.br gestao@pinsaude.com.br; do
    local user_id
    user_id=$(curl -sf \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      "$KC_URL/admin/realms/$REALM/users?email=$user_email" \
      | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -z "$user_id" ]; then
      warn "  Usuário $user_email não encontrado."
      continue
    fi
    local req_actions
    req_actions=$(curl -sf \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      "$KC_URL/admin/realms/$REALM/users/$user_id" \
      | grep -o '"requiredActions":\[[^]]*\]' || echo "[]")
    if echo "$req_actions" | grep -q "CONFIGURE_TOTP"; then
      ok "  $user_email — CONFIGURE_TOTP ativo (MFA será exigido no 1º login)."
    else
      warn "  $user_email — CONFIGURE_TOTP NÃO ativo."
    fi
  done
}

# ─────────────────────────────────────────────
# Execução principal
# ─────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════╗"
echo "║   Pin Saúde — Keycloak Setup             ║"
echo "╚══════════════════════════════════════════╝"
echo ""

wait_keycloak
get_admin_token
import_realm
verify_roles
verify_clients
test_authentication
verify_mfa

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║  Setup concluído com sucesso!            ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "  Realm:   $KC_URL/realms/$REALM"
echo "  Console: $KC_URL/admin/master/console/#/$REALM"
echo ""
echo "  Usuários de teste:"
echo "    medico@pinsaude.com.br   / test123  (sem MFA)"
echo "    operacao@pinsaude.com.br / test123  (MFA no 1º login)"
echo "    gestao@pinsaude.com.br   / test123  (MFA no 1º login)"
echo ""
