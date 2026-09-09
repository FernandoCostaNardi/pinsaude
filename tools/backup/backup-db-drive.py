#!/usr/bin/env python3
"""Backup diário dos bancos pinsaude + keycloak -> Google Drive + e-mail.
Uso: sudo python3 backup-db-drive.py   (cron ou manual, a qualquer hora)

Escopo: só roda em pingestao.com.br (produção real) — o 212.85.12.228 não
tem essa automação (ver CLAUDE.md, seção "Backup Automático do Banco").

Autenticação: OAuth 2.0 com a conta pessoal do dono do Drive, NÃO Service
Account. Service Accounts não têm cota de armazenamento própria no Google
Drive — toda tentativa de upload retorna 403 storageQuotaExceeded, mesmo com
a pasta compartilhada como Editor (só funcionaria com Google Workspace +
Shared Drive ou domain-wide delegation, indisponível numa conta pessoal).
O arquivo de credenciais (token + refresh_token) foi gerado uma única vez
via fluxo interativo local (google-auth-oauthlib) e vive só no VPS — ver
CLAUDE.md para detalhes de como gerar um novo caso o refresh_token expire.
"""
import subprocess, os, sys, glob
from datetime import datetime, timedelta

from google.oauth2.credentials import Credentials
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

DATABASES = ["pinsaude", "keycloak"]
BACKUP_DIR = "/home/pinsaude/backups"
OAUTH_TOKEN_FILE = "/home/pinsaude/infra/gdrive-oauth-token.json"
DRIVE_SCOPES = ["https://www.googleapis.com/auth/drive.file"]
DRIVE_FOLDER_ID = "1KiVDuglnZ78gPK0BeoeRfbUrSDG-Ujd3"  # Olicode/Backups/DB_Pinsaude (BACKUP-03)
RETENTION_DAYS = 30


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
    for f in glob.glob(os.path.join(BACKUP_DIR, "*.dump")):
        if datetime.fromtimestamp(os.path.getmtime(f)) < cutoff:
            os.remove(f)


def main():
    resultados = []
    try:
        for db in DATABASES:
            print(f"[backup] Gerando dump de '{db}'...")
            path = dump_database(db)
            print(f"[backup] Dump gerado: {path} ({os.path.getsize(path) / 1024 / 1024:.2f} MB) — enviando ao Drive...")
            drive_file = upload_to_drive(path)
            resultados.append({
                "banco": db,
                "arquivo": os.path.basename(path),
                "tamanho_mb": round(os.path.getsize(path) / 1024 / 1024, 2),
                "drive_link": drive_file.get("webViewLink"),
            })
            print(f"[backup] '{db}' enviado com sucesso: {drive_file.get('webViewLink')}")
        cleanup_old_backups()
        print(f"[backup] Concluído. {len(resultados)}/{len(DATABASES)} bancos enviados.")
        # enviar_email_sucesso(resultados) — ver BACKUP-06
    except Exception as e:
        print(f"[backup] ERRO: {e}", file=sys.stderr)
        # enviar_email_falha(str(e), resultados) — ver BACKUP-06
        sys.exit(1)


if __name__ == "__main__":
    main()
