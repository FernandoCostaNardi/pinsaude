#!/usr/bin/env python3
"""Backup diário do Pin Saúde -> Google Drive + e-mail.

O que entra em cada execução (mesmo modelo do Full Watches, FW-00.32):
  pinsaude_<ts>.dump   pg_dump -Fc do banco da aplicação
  keycloak_<ts>.dump   pg_dump -Fc do banco do Keycloak
  minio_<ts>.tar.gz    diretório de dados do container pinsaude-minio
                       (documentos de médicos/empresas, frequências assinadas)
  config_<ts>.tar.gz   configuração de produção COM segredos (infra/.env,
                       application-prod.yml, units systemd, nginx, certificados).
                       Sem isso, restaurar exige recriar toda credencial à mão —
                       inclusive a CRYPTO_KEY, sem a qual CPF/PIX do dump ficam
                       ilegíveis.

Uso: sudo python3 backup-db-drive.py   (cron ou manual, a qualquer hora)

Escopo: só roda em pingestao.com.br (produção real) — o 212.85.12.228 não
tem essa automação (ver CLAUDE.md, seção "Backup Automático do Banco").

Autenticação Drive: OAuth 2.0 com a conta pessoal do dono do Drive, NÃO
Service Account. Service Accounts não têm cota de armazenamento própria no
Google Drive — toda tentativa de upload retorna 403 storageQuotaExceeded,
mesmo com a pasta compartilhada como Editor (só funcionaria com Google
Workspace + Shared Drive ou domain-wide delegation, indisponível numa conta
pessoal). O arquivo de credenciais (token + refresh_token) foi gerado uma
única vez via fluxo interativo local (google-auth-oauthlib) e vive só no
VPS — ver CLAUDE.md para detalhes de como gerar um novo caso o
refresh_token expire.

E-mail: reaproveita o SMTP Hostgator já usado pelo onboarding
(noreply@pinsaude.com.br). A senha NUNCA fica neste arquivo versionado —
vive só em SMTP_PASS_FILE, no VPS, chmod 600.
"""
import glob
import json
import os
import smtplib
import ssl
import subprocess
import sys
from datetime import datetime, timedelta
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

DATABASES = ["pinsaude", "keycloak"]
BACKUP_DIR = "/home/pinsaude/backups"
OAUTH_TOKEN_FILE = "/home/pinsaude/infra/gdrive-oauth-token.json"
DRIVE_SCOPES = ["https://www.googleapis.com/auth/drive.file"]
DRIVE_FOLDER_ID = "1KiVDuglnZ78gPK0BeoeRfbUrSDG-Ujd3"  # Olicode/Backups/DB_Pinsaude (BACKUP-03)
RETENTION_DAYS = 30

MINIO_CONTAINER = "pinsaude-minio"
MINIO_DATA_DEST = "/data"  # caminho do volume dentro do container
# Caminhos de configuração; os que não existirem no host são ignorados
# (cada VPS tem um layout ligeiramente diferente). Precisa existir ao menos um.
CONFIG_PATHS = [
    "/home/pinsaude/infra",
    "/home/pinsaude/apps",
    "/home/pinsaude/projeto/apps/web/.env.production",
    "/home/pinsaude/scripts",
    "/etc/systemd/system/pinsaude-*.service",
    "/etc/nginx/sites-available",
    "/etc/nginx/sites-enabled",
    "/etc/nginx/ssl/pinsaude",
    "/etc/letsencrypt",
]
# Nada de artefato de build/dados dentro de apps/ — só configuração.
CONFIG_EXCLUDES = ["*.jar", "*.jar.bak*", "*.log", "*/logs/*", "*/web/dist/*"]

SMTP_HOST = "sh-pro102.hostgator.com.br"
SMTP_PORT = 465
SMTP_USER = "noreply@pinsaude.com.br"
SMTP_PASS_FILE = "/home/pinsaude/infra/backup-smtp-password.txt"  # chmod 600, nunca no git
EMAIL_TO = "fcostanardi@gmail.com"


def dump_database(nome: str) -> str:
    os.makedirs(BACKUP_DIR, exist_ok=True)
    # O script roda como root (sudo), então os.makedirs cria o diretório
    # dono de root — mas o pg_dump abaixo roda como usuário "postgres"
    # (autenticação peer local, sem senha embutida). Sem o chmod, o
    # postgres não consegue escrever no diretório (testado manualmente:
    # "pg_dump: error: could not open output file ...: Permission denied").
    # /home/pinsaude já é drwxr-x--x (só root e grupo pinsaude entram), então
    # abrir esse subdiretório específico pra escrita não expõe nada a mais.
    os.chmod(BACKUP_DIR, 0o777)
    ts = datetime.now().strftime("%Y-%m-%d_%Hh%M")
    filepath = os.path.join(BACKUP_DIR, f"{nome}_{ts}.dump")
    result = subprocess.run(
        ["sudo", "-u", "postgres", "pg_dump", "-Fc", nome, "-f", filepath],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"pg_dump falhou para {nome}: {result.stderr}")
    return filepath


def _timestamp() -> str:
    return datetime.now().strftime("%Y-%m-%d_%Hh%M")


def _prepare_backup_dir():
    os.makedirs(BACKUP_DIR, exist_ok=True)
    os.chmod(BACKUP_DIR, 0o777)


def minio_data_dir() -> str:
    """Diretório do host montado em /data no container do MinIO."""
    result = subprocess.run(
        ["docker", "inspect", "-f", "{{json .Mounts}}", MINIO_CONTAINER],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"docker inspect {MINIO_CONTAINER} falhou: {result.stderr.strip()}")
    for mount in json.loads(result.stdout):
        if mount.get("Destination") == MINIO_DATA_DEST and mount.get("Source"):
            return mount["Source"]
    raise RuntimeError(f"{MINIO_CONTAINER} não tem volume montado em {MINIO_DATA_DEST}")


def backup_minio() -> str:
    _prepare_backup_dir()
    origem = minio_data_dir()
    filepath = os.path.join(BACKUP_DIR, f"minio_{_timestamp()}.tar.gz")
    # tar com o MinIO no ar: objetos só entram por upload completo, então o
    # risco de pegar um objeto pela metade é baixo (mesma escolha do Full Watches).
    result = subprocess.run(
        ["tar", "-czf", filepath, "-C", origem, "."],
        capture_output=True, text=True,
    )
    # tar sai com 1 quando um arquivo muda durante a leitura — aceitável aqui.
    if result.returncode not in (0, 1):
        raise RuntimeError(f"tar do MinIO falhou: {result.stderr.strip()}")
    os.chmod(filepath, 0o600)
    return filepath


def backup_config() -> str:
    _prepare_backup_dir()
    caminhos = []
    for padrao in CONFIG_PATHS:
        caminhos.extend(sorted(glob.glob(padrao)))
    if not caminhos:
        raise RuntimeError("nenhum caminho de configuração encontrado")
    filepath = os.path.join(BACKUP_DIR, f"config_{_timestamp()}.tar.gz")
    cmd = ["tar", "-czf", filepath]
    for exc in CONFIG_EXCLUDES:
        cmd.append(f"--exclude={exc}")
    cmd += ["-C", "/"] + [c.lstrip("/") for c in caminhos]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode not in (0, 1):
        raise RuntimeError(f"tar da configuração falhou: {result.stderr.strip()}")
    # Contém segredos de produção: só root lê.
    os.chmod(filepath, 0o600)
    return filepath


def get_drive_service():
    creds = Credentials.from_authorized_user_file(OAUTH_TOKEN_FILE, scopes=DRIVE_SCOPES)
    if creds.expired and creds.refresh_token:
        creds.refresh(Request())
        # Persiste o access_token renovado de volta no arquivo — o refresh_token
        # em si normalmente não muda, mas isso mantém o "expiry" atualizado.
        with open(OAUTH_TOKEN_FILE, "w") as f:
            f.write(creds.to_json())
        os.chmod(OAUTH_TOKEN_FILE, 0o600)
    return build("drive", "v3", credentials=creds)


def upload_to_drive(filepath: str) -> dict:
    service = get_drive_service()
    metadata = {"name": os.path.basename(filepath), "parents": [DRIVE_FOLDER_ID]}
    media = MediaFileUpload(filepath, resumable=True)
    return service.files().create(body=metadata, media_body=media, fields="id,webViewLink").execute()


def cleanup_old_backups():
    cutoff = datetime.now() - timedelta(days=RETENTION_DAYS)
    arquivos = glob.glob(os.path.join(BACKUP_DIR, "*.dump")) + glob.glob(os.path.join(BACKUP_DIR, "*.tar.gz"))
    for f in arquivos:
        if datetime.fromtimestamp(os.path.getmtime(f)) < cutoff:
            os.remove(f)


def _smtp_password() -> str:
    with open(SMTP_PASS_FILE) as f:
        return f.read().strip()


def _enviar(assunto: str, html: str):
    msg = MIMEMultipart("alternative")
    msg["From"] = f"Pin Saude <{SMTP_USER}>"
    msg["To"] = EMAIL_TO
    msg["Subject"] = assunto
    msg.attach(MIMEText(html, "html", "utf-8"))
    context = ssl.create_default_context()
    with smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT, context=context, timeout=20) as server:
        server.login(SMTP_USER, _smtp_password())
        server.sendmail(SMTP_USER, [EMAIL_TO], msg.as_string())


def enviar_email_sucesso(resultados: list):
    linhas = "".join(
        f'<tr><td style="padding:8px;border-bottom:1px solid #eee;">{r["banco"]}</td>'
        f'<td style="padding:8px;border-bottom:1px solid #eee;">{r["arquivo"]}</td>'
        f'<td style="padding:8px;border-bottom:1px solid #eee;">{r["tamanho_mb"]} MB</td></tr>'
        for r in resultados
    )
    html = f"""
    <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
      <div style="background:#02A9F7;padding:24px;text-align:center;border-radius:8px 8px 0 0;">
        <h1 style="color:#fff;margin:0;font-size:20px;">Pin Saúde</h1>
        <p style="color:#e6f6ff;margin:4px 0 0;">Backup diário (banco, arquivos e configuração)</p>
      </div>
      <div style="background:#fff;padding:24px;border:1px solid #eee;border-top:none;">
        <p style="color:#15803d;font-weight:bold;">✅ Backup concluído com sucesso</p>
        <table style="width:100%;border-collapse:collapse;font-size:14px;">
          <tr style="background:#f5f5f5;"><th style="padding:8px;text-align:left;">Item</th>
              <th style="padding:8px;text-align:left;">Arquivo</th>
              <th style="padding:8px;text-align:left;">Tamanho</th></tr>
          {linhas}
        </table>
        <p style="color:#666;font-size:12px;margin-top:20px;">O arquivo de configuração contém senhas de produção — não compartilhe.<br>Enviado automaticamente pelo Pin Saúde — pingestao.com.br</p>
      </div>
    </div>"""
    # E-mail é "melhor esforço": uma falha de SMTP não pode transformar um
    # backup que já deu certo (dump + upload concluídos) em erro fatal.
    try:
        _enviar("✅ Backup diário - Pin Saúde", html)
    except Exception as e:
        print(f"[backup] AVISO: falha ao enviar e-mail de sucesso: {e}", file=sys.stderr)


def enviar_email_falha(erro: str, resultados: list):
    # Cor de alerta (âmbar, não vermelho) e assunto sem "FALHA" em caixa alta —
    # ver nota no CLAUDE.md: a versão original (header vermelho forte + emoji
    # de alerta ⚠️ + "FALHA" em caixa alta no assunto) foi enviada 3x pelo SMTP
    # sem erro nenhum, mas NUNCA chegou (nem na Caixa de Entrada, nem no Spam) —
    # tudo indica um filtro anti-phishing silencioso reagindo ao padrão visual
    # "alerta vermelho + urgência", bem comum em phishing. Uma versão mais
    # neutra (mesmo conteúdo, tom mais brando) entregou normalmente.
    html = f"""
    <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
      <div style="background:#b45309;padding:24px;text-align:center;border-radius:8px 8px 0 0;">
        <h1 style="color:#fff;margin:0;font-size:20px;">Pin Saúde</h1>
        <p style="color:#fef3e2;margin:4px 0 0;">Aviso sobre o backup diário do banco de dados</p>
      </div>
      <div style="background:#fff;padding:24px;border:1px solid #eee;border-top:none;">
        <p style="color:#b45309;font-weight:bold;">O backup de hoje não foi concluído</p>
        <p style="font-family:monospace;background:#f5f5f5;padding:12px;border-radius:4px;">{erro}</p>
        <p style="color:#666;font-size:12px;margin-top:20px;">Verifique os logs em /home/pinsaude/logs/backup-db.log</p>
      </div>
    </div>"""
    # Aqui também é melhor esforço — se o SMTP estiver fora do ar, o script
    # já vai sair com código 1 de qualquer forma (ver main()); não deixar
    # uma segunda exceção (do próprio envio de e-mail) mascarar o erro real.
    try:
        _enviar("Aviso: backup do banco não concluído - Pin Saúde", html)
    except Exception as e:
        print(f"[backup] AVISO: falha ao enviar e-mail de falha: {e}", file=sys.stderr)


def _registrar(resultados: list, item: str, path: str):
    tamanho_mb = round(os.path.getsize(path) / 1024 / 1024, 2)
    print(f"[backup] {item}: {path} ({tamanho_mb} MB) — enviando ao Drive...")
    drive_file = upload_to_drive(path)
    resultados.append({
        "banco": item,
        "arquivo": os.path.basename(path),
        "tamanho_mb": tamanho_mb,
        "drive_link": drive_file.get("webViewLink"),
    })
    print(f"[backup] {item} enviado com sucesso: {drive_file.get('webViewLink')}")


def main():
    resultados = []
    total = len(DATABASES) + 2
    try:
        for db in DATABASES:
            print(f"[backup] Gerando dump de '{db}'...")
            _registrar(resultados, db, dump_database(db))
        print("[backup] Empacotando dados do MinIO...")
        _registrar(resultados, "minio", backup_minio())
        print("[backup] Empacotando configuração...")
        _registrar(resultados, "config", backup_config())
        cleanup_old_backups()
        print(f"[backup] Concluído. {len(resultados)}/{total} itens enviados.")
        enviar_email_sucesso(resultados)
    except Exception as e:
        print(f"[backup] ERRO: {e}", file=sys.stderr)
        enviar_email_falha(str(e), resultados)
        sys.exit(1)


if __name__ == "__main__":
    main()
