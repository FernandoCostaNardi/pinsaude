# Sobe toda a infraestrutura local do Pin Saúde e aguarda os healthchecks
param(
    [int]$TimeoutSeconds = 120,
    [switch]$Down
)

$rootDir = Resolve-Path (Join-Path $PSScriptRoot "../../")
Set-Location $rootDir

if ($Down) {
    Write-Host "==> Derrubando infraestrutura Pin Saúde..." -ForegroundColor Yellow
    docker compose down
    Write-Host "✅  Infra derrubada." -ForegroundColor Green
    exit 0
}

Write-Host "==> Subindo infraestrutura Pin Saúde..." -ForegroundColor Cyan
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: docker compose up falhou." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "==> Aguardando healthchecks (máx ${TimeoutSeconds}s)..." -ForegroundColor Cyan

$services  = @("postgres", "rabbitmq", "keycloak", "vault", "jaeger", "mailhog")
$elapsed   = 0
$interval  = 5
$allHealthy = $false

while ($elapsed -lt $TimeoutSeconds) {
    $allHealthy = $true
    foreach ($svc in $services) {
        $status = docker inspect --format='{{.State.Health.Status}}' "pinsaude-$svc" 2>$null
        if ($status -ne "healthy") {
            $allHealthy = $false
            break
        }
    }

    if ($allHealthy) {
        Write-Host ""
        Write-Host "✅  Todos os containers saudáveis!" -ForegroundColor Green
        break
    }

    Write-Host -NoNewline "."
    Start-Sleep -Seconds $interval
    $elapsed += $interval
}

if (-not $allHealthy) {
    Write-Host ""
    Write-Host "⚠️  Timeout atingido — alguns containers podem ainda estar iniciando." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==> Status final:" -ForegroundColor Cyan
docker compose ps

Write-Host ""
Write-Host "==> URLs dos serviços:" -ForegroundColor Cyan
Write-Host "  PostgreSQL  : localhost:5432      (postgres/postgres)"
Write-Host "  RabbitMQ    : http://localhost:15672  (guest/guest)"
Write-Host "  Keycloak    : http://localhost:8080   (admin/admin)"
Write-Host "  Vault       : http://localhost:8200   (token: root)"
Write-Host "  Jaeger      : http://localhost:16686"
Write-Host "  Mailhog     : http://localhost:8025"
