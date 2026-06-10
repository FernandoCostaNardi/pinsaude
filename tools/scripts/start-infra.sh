#!/usr/bin/env bash
# Sobe toda a infraestrutura local do Pin Saúde e aguarda os healthchecks

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$ROOT_DIR"

echo "==> Subindo infraestrutura Pin Saúde..."
docker compose up -d

echo ""
echo "==> Aguardando healthchecks (máx 120s)..."

services=(postgres rabbitmq keycloak vault jaeger mailhog)
timeout=120
elapsed=0
interval=5

while [ $elapsed -lt $timeout ]; do
  all_healthy=true
  for svc in "${services[@]}"; do
    status=$(docker inspect --format='{{.State.Health.Status}}' "pinsaude-$svc" 2>/dev/null || echo "unknown")
    if [ "$status" != "healthy" ]; then
      all_healthy=false
      break
    fi
  done

  if [ "$all_healthy" = true ]; then
    echo ""
    echo "✅  Todos os containers saudáveis!"
    break
  fi

  echo -n "."
  sleep $interval
  elapsed=$((elapsed + interval))
done

echo ""
echo "==> Status final:"
docker compose ps

echo ""
echo "==> URLs dos serviços:"
echo "  PostgreSQL  : localhost:5432  (postgres/postgres)"
echo "  RabbitMQ    : http://localhost:15672  (guest/guest)"
echo "  Keycloak    : http://localhost:8080   (admin/admin)"
echo "  Vault       : http://localhost:8200   (token: root)"
echo "  Jaeger      : http://localhost:16686"
echo "  Mailhog     : http://localhost:8025"
