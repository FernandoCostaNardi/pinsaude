# EPIC-03 — Onboarding e KYC

> Prioridade: **P1** — Sem onboarding, nenhum médico pode faturar.
> PRD: §7.3. RFs: RF-ONB-01..09

---

## TASK-03.1 — Convite e Cadastro Inicial do Médico

### 1. Objetivo (Por quê?)
O médico entra na plataforma por convite (da operação ou indicação). Sem um fluxo de convite controlado, qualquer pessoa poderia se cadastrar, comprometendo o modelo de negócio (vínculo societário exigido).

### 2. Descrição da Solução (O quê?)
Fluxo: operação gera convite por e-mail → médico acessa link único e faz cadastro inicial → status muda para `DOCUMENTOS_PENDENTES`.

**Entidade `Convite` (migração `onboarding.V3__create_convite.sql`):**
```sql
CREATE TABLE onboarding.convite (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id      UUID NOT NULL,
  email        VARCHAR(255) NOT NULL,
  token        VARCHAR(64) NOT NULL UNIQUE,   -- token seguro (32 bytes hex)
  empresa_id   UUID NOT NULL,
  convidado_por UUID NOT NULL,               -- usuario_id do operador
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  -- PENDENTE → ACEITO → EXPIRADO
  expira_em    TIMESTAMPTZ NOT NULL,         -- now() + 72h
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
ENABLE ROW LEVEL SECURITY ON onboarding.convite;
```

**Fluxo de convite:**
```
POST /convites
  body: { "email": "medico@hospital.com", "empresa_id": "uuid" }
  role: OPERACAO, GESTAO
  → gera token seguro (SecureRandom 32 bytes hex)
  → salva convite com status PENDENTE, expira em 72h
  → envia e-mail com link: https://app.pinsaude.com.br/cadastro?token={token}

GET /cadastro/convite/{token}
  → público (sem auth)
  → valida token (existe, não expirado, PENDENTE)
  → retorna dados do convite (empresa, e-mail)

POST /cadastro/medico
  → público (sem auth, apenas com token válido)
  body: { "token": "...", "nome_completo": "...", "cpf": "...", "crm": "...", "telefone": "..." }
  → cria Medico com status PENDENTE
  → cria usuário no Keycloak (senha temporária, force-change-password)
  → atualiza convite para ACEITO
  → envia e-mail de boas-vindas com link para upload de documentos
```

### 3. Critérios de Aceite
- [ ] `POST /convites` gera token único e envia e-mail com link correto.
- [ ] Token expira após 72h — `GET /cadastro/convite/{token_expirado}` retorna 410 Gone.
- [ ] Token já usado retorna 410 Gone.
- [ ] `POST /cadastro/medico` com token válido cria médico no banco e usuário no Keycloak.
- [ ] Tentativa de cadastro com CPF já cadastrado retorna 409.
- [ ] Tentativa de cadastro com CRM já cadastrado na mesma UF retorna 409.
- [ ] E-mail de boas-vindas disparado com link para próxima etapa (documentos).

### 4. Regras de Negócio
- Entrada somente por convite (RF-ONB-01) — sem auto-cadastro.
- Token de convite tem validade de 72 horas.
- CRM deve ser único por estado (CRM + UF).
- CPF criptografado desde a criação.

### 5. Cenários de Testes para o Humano
1. **Fluxo completo:** Criar convite para e-mail válido → verificar e-mail recebido → acessar link → preencher formulário → verificar médico criado no banco com status PENDENTE.
2. **Token expirado:** Criar convite, alterar `expira_em` para data passada no banco, acessar link → deve retornar "Convite expirado".
3. **Token reutilizado:** Completar cadastro, tentar usar o mesmo token novamente → deve retornar "Convite já utilizado".
4. **CRM duplicado:** Tentar cadastrar dois médicos com mesmo CRM + UF → segundo deve retornar erro de duplicidade.

---

## TASK-03.2 — Upload e Gestão de Documentos do Médico

### 1. Objetivo (Por quê?)
O KYC exige que o médico envie documentos (CRM, diploma, CNH, comprovante de residência) antes de ser ativado. Sem validação documental, há risco legal e regulatório.

### 2. Descrição da Solução (O quê?)
Sistema de upload de arquivos com versionamento, status de análise e armazenamento em object storage (S3/MinIO).

**Migração (`onboarding.V4__create_documento.sql`):**
```sql
CREATE TABLE onboarding.documento (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id      UUID NOT NULL,
  medico_id    UUID NOT NULL REFERENCES onboarding.medico(id),
  tipo         VARCHAR(30) NOT NULL,
  -- 'CRM', 'DIPLOMA', 'CNH', 'COMPROVANTE_RESIDENCIA', 'OUTRO'
  nome_arquivo VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,   -- caminho no S3/MinIO
  content_type VARCHAR(100) NOT NULL,   -- 'application/pdf', 'image/jpeg', etc.
  tamanho_bytes BIGINT NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  -- PENDENTE → APROVADO → REJEITADO
  motivo_rejeicao VARCHAR(500),
  analisado_por UUID,                   -- usuario_id do operador
  analisado_em  TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
ENABLE ROW LEVEL SECURITY ON onboarding.documento;
```

**Docker Compose para MinIO (object storage local):**
```yaml
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: pinsaude
    MINIO_ROOT_PASSWORD: local_dev_only
  ports: ["9000:9000", "9001:9001"]
```

**Endpoints:**
```
POST /medicos/{id}/documentos
  multipart: file (max 10MB, PDF/JPG/PNG), tipo
  → upload para MinIO em: medicos/{medico_id}/{tipo}/{uuid}-{nome}
  → salva registro com status PENDENTE

GET  /medicos/{id}/documentos
  → lista documentos do médico com status e URLs assinadas (1h)

PUT  /documentos/{id}/status
  body: { "status": "APROVADO" | "REJEITADO", "motivo": "..." }
  role: OPERACAO, GESTAO
  → atualiza status e registra quem analisou

GET  /documentos/{id}/download
  → redireciona para URL assinada (pre-signed URL do MinIO/S3)
```

**Verificação de completude dos documentos:**
```java
public boolean documentosCompletos(UUID medicoId) {
    // CRM e ao menos 1 de (DIPLOMA, CNH) aprovados
    Set<String> tiposAprovados = documentoRepository
        .findByMedicoIdAndStatus(medicoId, "APROVADO")
        .stream().map(Documento::getTipo).collect(toSet());
    return tiposAprovados.contains("CRM") &&
           (tiposAprovados.contains("DIPLOMA") || tiposAprovados.contains("CNH"));
}
```

### 3. Critérios de Aceite
- [ ] Upload de PDF até 10MB funciona; arquivo maior retorna 413.
- [ ] Arquivo não-PDF/JPG/PNG retorna 400.
- [ ] URL de download é pre-signed com expiração de 1h (não expõe URL permanente).
- [ ] Médico logado só vê/faz download dos próprios documentos.
- [ ] Operação consegue aprovar/rejeitar documentos com motivo.
- [ ] Ao aprovar o documento CRM + 1 identidade, o status do médico evolui para `ANALISE_CONDUTA`.

### 4. Regras de Negócio
- Documentos obrigatórios: CRM, diploma ou CNH (RF-CAD-01).
- Documentos armazenados no object storage, nunca no banco.
- URLs de download com validade máxima de 1h (segurança).
- Rejeição exige motivo obrigatório.

### 5. Cenários de Testes para o Humano
1. **Upload e download:** Fazer upload de CRM em PDF → acessar `GET /documentos/{id}/download` → verificar que redireciona para URL do MinIO e o arquivo é o correto.
2. **Arquivo grande:** Tentar upload de arquivo de 15MB → deve retornar 413 "Arquivo muito grande".
3. **Tipo inválido:** Tentar upload de arquivo `.exe` → deve retornar 400 "Tipo de arquivo não permitido".
4. **Aprovação por operação:** Logar como OPERACAO, aprovar CRM e CNH do médico → verificar que o status do médico muda para `ANALISE_CONDUTA`.
5. **Download por médico errado:** Logar como médico A, tentar download de documento do médico B → deve retornar 403.

---

## TASK-03.3 — Checklist de Análise de Conduta

### 1. Objetivo (Por quê?)
Antes de ativar um médico, a operação da Pin realiza uma análise de conduta (verificação de CRM, registros disciplinares, processos sobre ato médico — RF-ONB-04). Este registro deve ficar auditável mesmo quando preenchido manualmente.

### 2. Descrição da Solução (O quê?)
Formulário estruturado de checklist com campos obrigatórios, status de aprovação e trilha de auditoria.

**Migração (`onboarding.V5__create_checklist_conduta.sql`):**
```sql
CREATE TABLE onboarding.checklist_conduta (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  medico_id       UUID NOT NULL REFERENCES onboarding.medico(id) UNIQUE,
  crm_ativo       BOOLEAN NOT NULL,         -- CRM ativo e regular?
  sem_processo_cfm BOOLEAN NOT NULL,        -- Sem processo ativo no CFM?
  sem_acao_judicial BOOLEAN NOT NULL,       -- Sem ação judicial sobre ato médico?
  sem_impedimento_societario BOOLEAN NOT NULL,  -- Sem impedimento para ser sócio?
  observacoes     TEXT,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  -- PENDENTE → APROVADO → REPROVADO
  analisado_por   UUID NOT NULL,
  analisado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
ENABLE ROW LEVEL SECURITY ON onboarding.checklist_conduta;
```

**Endpoints:**
```
POST /medicos/{id}/checklist-conduta
  body: { "crm_ativo": true, "sem_processo_cfm": true, ... }
  role: OPERACAO, GESTAO
  → cria/atualiza checklist; se todos os campos true → status APROVADO; senão REPROVADO

GET  /medicos/{id}/checklist-conduta
  → detalhe do checklist (role: OPERACAO, GESTAO)
```

**Evolução de status do médico após checklist:**
- Checklist APROVADO → médico evolui para `CONTRATO_PENDENTE`.
- Checklist REPROVADO → médico vai para `REPROVADO` com notificação por e-mail.

### 3. Critérios de Aceite
- [ ] Checklist com todos os campos `true` → status `APROVADO` e médico avança para `CONTRATO_PENDENTE`.
- [ ] Qualquer campo `false` → status `REPROVADO` e médico bloqueado.
- [ ] Médico logado não vê o checklist de conduta (apenas OPERACAO e GESTAO).
- [ ] Checklist registrado com `analisado_por` e `analisado_em` (auditoria — RF-ONB-09).
- [ ] Médico reprovado recebe e-mail informando a reprovação.

### 4. Regras de Negócio
- Checklist deve ser registrado para auditoria mesmo quando preenchido manualmente (RF-ONB-09).
- Um único checklist por médico (pode ser atualizado antes de finalizar).
- Reprovação bloqueia o onboarding — operação decide por reanálise.

### 5. Cenários de Testes para o Humano
1. **Aprovação:** Preencher checklist com todos os campos `true` para médico → status muda para APROVADO e médico para CONTRATO_PENDENTE.
2. **Reprovação:** Preencher checklist com `sem_processo_cfm = false` → status REPROVADO → verificar e-mail enviado ao médico.
3. **Auditoria:** Após aprovação, consultar `audit_log` → deve ter registro com `action = 'conduta.aprovada'`.
4. **Médico não vê checklist:** Logar como médico, tentar `GET /medicos/{id}/checklist-conduta` → 403.

---

## TASK-03.4 — Assinatura de Contrato via Clicksign

### 1. Objetivo (Por quê?)
O contrato de adesão (proteção jurídica + termos de uso) deve ser assinado digitalmente pelo médico antes da ativação. A Clicksign é o meio legal para isso (RF-ONB-05).

### 2. Descrição da Solução (O quê?)
Integração com a API da Clicksign via Anti-Corruption Layer (ACL). O sistema cria o envelope com o documento modelo, adiciona o médico como signatário, e monitora o status via webhook.

**Port (interface no domínio `onboarding`):**
```java
public interface ContratoAssinaturaPort {
    String criarEnvelope(UUID medicoId, byte[] documentoPdf, String emailSignatario);
    StatusAssinatura consultarStatus(String envelopeId);
    // webhook → void processarEvento(String payload, String signature);
}
```

**Adapter Clicksign (`ClicksignAdapter.java`):**
```java
@Component
public class ClicksignAdapter implements ContratoAssinaturaPort {
    // POST /api/v1/documents → cria documento
    // POST /api/v1/lists     → adiciona signatário
    // POST /api/v1/batches   → cria envelope e solicita assinatura
    // GET  /api/v1/documents/{key} → consulta status
}
```

**Migração (`onboarding.V6__create_contrato.sql`):**
```sql
CREATE TABLE onboarding.contrato (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  medico_id       UUID NOT NULL REFERENCES onboarding.medico(id),
  envelope_id     VARCHAR(100) NOT NULL,   -- ID na Clicksign
  template_versao VARCHAR(20) NOT NULL,    -- versão do contrato
  status          VARCHAR(20) NOT NULL DEFAULT 'AGUARDANDO_ASSINATURA',
  -- AGUARDANDO_ASSINATURA → ASSINADO → CANCELADO
  url_assinatura  TEXT,                   -- link para assinar (enviado por e-mail)
  assinado_em     TIMESTAMPTZ,
  documento_url   TEXT,                   -- URL do PDF assinado (após conclusão)
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
ENABLE ROW LEVEL SECURITY ON onboarding.contrato;
```

**Endpoints:**
```
POST /medicos/{id}/contrato/enviar
  role: OPERACAO, GESTAO
  → gera PDF do contrato preenchido com dados do médico
  → chama ClicksignAdapter.criarEnvelope()
  → salva contrato com status AGUARDANDO_ASSINATURA
  → envia e-mail para o médico com link de assinatura

POST /webhooks/clicksign
  → público com validação de HMAC-SHA256 da signature do header
  → ao receber evento "document_signed" → atualiza contrato para ASSINADO
  → evolui médico para JUNTA_PENDENTE
  → dispara evento de domínio MedicoContratoAssinado
```

### 3. Critérios de Aceite
- [ ] `POST /medicos/{id}/contrato/enviar` cria envelope na Clicksign e retorna `envelope_id`.
- [ ] E-mail enviado ao médico com link de assinatura.
- [ ] Webhook com assinatura HMAC inválida retorna 401.
- [ ] Webhook válido com evento `document_signed` → status contrato = ASSINADO, médico = JUNTA_PENDENTE.
- [ ] Indisponibilidade da Clicksign retorna erro controlado (não 500 genérico).
- [ ] Template do contrato parametrizável (versionado, não hardcoded).

### 4. Regras de Negócio
- Assinatura via Clicksign cobre contrato de proteção jurídica e termos de uso (PRD §5.1).
- Não substitui a alteração contratual na Junta Comercial (RF-ONB-05).
- Webhook deve ser validado por HMAC antes de processar.
- Contrato assinado deve ser armazenado e acessível para auditoria.

### 5. Cenários de Testes para o Humano
1. **Envio do contrato:** Clicar em "Enviar contrato" no backoffice → verificar envelope criado na dashboard da Clicksign sandbox.
2. **Assinatura:** Médico acessa link do e-mail na sandbox da Clicksign, assina → webhook recebido → verificar status atualizado para ASSINADO.
3. **Webhook inválido:** Enviar POST para `/webhooks/clicksign` sem assinatura HMAC → deve retornar 401.
4. **Clicksign indisponível:** Desligar acesso à API da Clicksign (bloqueio no `/etc/hosts`), tentar enviar contrato → deve retornar 503 com mensagem "Serviço de assinatura temporariamente indisponível".

---

## TASK-03.5 — Acompanhamento de Alteração Contratual na Junta Comercial

### 1. Objetivo (Por quê?)
Para o médico receber como distribuição de lucro (isenta de IRPF), ele precisa entrar formalmente como sócio da empresa na Junta Comercial. Este processo leva 15–60 dias e é externo — a plataforma apenas acompanha o status (RF-ONB-06).

### 2. Descrição da Solução (O quê?)
Tela de gestão do status societário com atualização manual pela operação e notificação ao médico quando concluído.

**Migração (`onboarding.V7__alter_vinculo_societario.sql`):**
```sql
ALTER TABLE onboarding.vinculo_medico_empresa
  ADD COLUMN protocolo_junta VARCHAR(100),   -- número de protocolo na Junta
  ADD COLUMN data_protocolo  DATE,
  ADD COLUMN data_aprovacao  DATE,
  ADD COLUMN observacoes     TEXT;
```

**Endpoints:**
```
PATCH /vinculos/{id}/status-societario
  body: {
    "status": "JUNTA_AGUARDANDO" | "JUNTA_APROVADO",
    "protocolo_junta": "2026/12345",
    "data_protocolo": "2026-06-10",
    "data_aprovacao": "2026-07-15"   -- só quando JUNTA_APROVADO
  }
  role: OPERACAO, GESTAO
  → ao mudar para JUNTA_APROVADO: médico fica com status ATIVO
  → ao ativar: cria usuário definitivo no Keycloak com role MEDICO
  → envia e-mail de boas-vindas com acesso ao portal

GET /vinculos?status=JUNTA_AGUARDANDO
  → lista médicos aguardando aprovação da Junta (role: OPERACAO, GESTAO)
```

**Alerta de demora:**
```java
// Job semanal: médicos com status JUNTA_AGUARDANDO há mais de 60 dias
// → alerta por e-mail para a operação
```

### 3. Critérios de Aceite
- [ ] `PATCH /vinculos/{id}/status-societario` atualiza status com rastreabilidade.
- [ ] Ao mudar para `JUNTA_APROVADO` + `data_aprovacao`, médico muda para `ATIVO` e recebe e-mail de boas-vindas.
- [ ] Médico `ATIVO` consegue logar no portal.
- [ ] Listagem de `JUNTA_AGUARDANDO` filtra apenas médicos nesse status.
- [ ] Job de alerta identifica médicos com > 60 dias aguardando.

### 4. Regras de Negócio
- Alteração contratual na Junta é externa — o sistema acompanha mas não controla (PRD §7.3, §14).
- Prazo típico: 15–60 dias (conforme autarquia).
- Médico só pode emitir notas após status `ATIVO`.
- Ativação dispara liberação de benefícios (RF-BEN-01).

### 5. Cenários de Testes para o Humano
1. **Atualização de status:** Logar como OPERACAO, acessar médico em JUNTA_AGUARDANDO, informar protocolo e data → verificar atualização no banco.
2. **Ativação:** Mudar status para JUNTA_APROVADO → verificar que médico muda para ATIVO e recebe e-mail de boas-vindas.
3. **Médico acessa portal:** Após ativação, médico usa credenciais recebidas no e-mail para logar → deve acessar o portal.
4. **Alerta de demora:** Criar médico com data de protocolo de 65 dias atrás → executar job de alerta → verificar e-mail enviado à operação.

---

## TASK-03.6 — Ativação e Liberação de Benefícios

### 1. Objetivo (Por quê?)
A ativação marca o início da relação comercial real. O médico só pode informar produção e receber repasses após estar ativo e ter ao menos 1 nota emitida com sucesso (RF-ONB-07, RF-BEN-01).

### 2. Descrição da Solução (O quê?)
Automação da ativação após aprovação na Junta + registro de elegibilidade para benefícios.

**Migração (`onboarding.V8__create_beneficio.sql`):**
```sql
CREATE TABLE onboarding.beneficio_elegibilidade (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id     UUID NOT NULL,
  medico_id   UUID NOT NULL REFERENCES onboarding.medico(id),
  tipo        VARCHAR(50) NOT NULL,     -- 'WELLHUB', 'TOTALPASS', 'CLUBE_DESCONTOS'
  elegivel    BOOLEAN NOT NULL DEFAULT false,
  elegivel_desde TIMESTAMPTZ,          -- data da primeira nota emitida
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ENABLE ROW LEVEL SECURITY ON onboarding.beneficio_elegibilidade;
```

**Evento de domínio publicado ao ativar médico:**
```json
{
  "type": "MedicoAtivado",
  "version": "1",
  "aggregate_id": "<medico_id>",
  "cnpj_id": "<cnpj_id>",
  "payload": {
    "medico_id": "uuid",
    "empresa_id": "uuid",
    "ativado_em": "2026-07-15T10:00:00Z"
  }
}
```

**Consumidor do evento `NotaEmitida` para elegibilidade:**
```java
// Ao receber primeira NotaEmitida de um médico:
// → marca elegibilidade WELLHUB e TOTALPASS como true
// → registra data da elegibilidade
```

**Endpoint:**
```
GET /portal/medico/me/beneficios
  → médico logado vê seus benefícios e elegibilidade
  role: MEDICO
```

### 3. Critérios de Aceite
- [ ] Ao ativar médico, usuário é criado no Keycloak com role MEDICO e acesso ao portal.
- [ ] Médico não-ativo não consegue logar no portal.
- [ ] Após primeira nota emitida com sucesso, `beneficio_elegibilidade` marcado como elegível.
- [ ] `GET /portal/medico/me/beneficios` retorna status de elegibilidade.
- [ ] Evento `MedicoAtivado` publicado no RabbitMQ ao ativar.

### 4. Regras de Negócio
- Elegibilidade de benefícios: ativado + ao menos 1 nota emitida (RF-BEN-01).
- Integração com Wellhub/TotalPass via API é Fase 2 — MVP apenas registra elegibilidade.
- Notificação por e-mail ao médico na ativação (RF-NOT-01).

### 5. Cenários de Testes para o Humano
1. **Acesso ao portal:** Médico recém-ativado acessa `app.pinsaude.com.br` → consegue logar e ver dashboard.
2. **Benefícios sem nota:** Médico ativado mas sem notas → verificar `elegivel = false` em benefícios.
3. **Benefícios com nota:** Emitir primeira nota para médico ativo → verificar `elegivel = true` e `elegivel_desde` preenchido.
4. **Médico inativo tenta logar:** Médico em status JUNTA_AGUARDANDO tenta logar → deve ser bloqueado pelo Keycloak.
