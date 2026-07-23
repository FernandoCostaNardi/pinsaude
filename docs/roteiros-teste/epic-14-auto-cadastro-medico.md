# Roteiro de Teste Manual Ponta-a-Ponta — EPIC-14 (Auto-cadastro Público de Médico)

Roteiro executado e validado em 2026-07-23 durante a task 14.9, usando o ambiente local
(Docker infra + `onboarding` + `gateway` + `web` rodando a partir do branch da task).
Cobre a jornada completa: candidatura pública → triagem/aprovação → liberação de acesso →
login do médico.

## Pré-requisitos

```powershell
.\tools\scripts\start-infra.ps1
node tools/scripts/mvn-build.js :pinsaude-onboarding
node tools/scripts/mvn-build.js :pinsaude-gateway
# subir onboarding (porta 8085) e gateway (porta 8090) com o JAVA_HOME do projeto
npx nx run web:dev
```

## Passo a passo

### 1. Acessar `/cadastro-medico` sem estar logado
Confirmado: a rota é acessível sem token/sessão. Preenchido o wizard completo (6 etapas) com
CPF/CRM fictícios válidos (CPF gerado com dígitos verificadores corretos — ver
`utils/cpf.ts`), incluindo:
- Etapa 1-3: dados pessoais, endereço + upload de comprovante, documentos profissionais (CRM)
- Etapa 4: dados bancários (PIX)
- Etapa 5: formação, áreas de atuação — **6 arquivos** enviados no campo de títulos de
  especialista (ver passo 10)
- Etapa 6: declarações LGPD + assinatura eletrônica

**Resultado:** ✅ OK.

### 2. Refresh no meio do preenchimento (rascunho incremental)
Preenchida a Etapa 1 completa → avançado para a Etapa 2 → preenchidos (sem salvar) campos de
endereço → `F5` na mesma aba.

**Resultado:** ✅ OK — a página exibiu "Encontramos uma candidatura salva — seus dados foram
restaurados" e todos os campos da Etapa 1 (já persistidos via `POST`) voltaram preenchidos.
Os campos da Etapa 2 preenchidos mas não submetidos foram perdidos, como esperado (só o que
foi persistido via `POST`/`PUT` sobrevive a um reload — não é autosave de cada tecla).

### 3. Confirmar no banco que os dados batem e `origem_cadastro = 'AUTO_CADASTRO'`

```sql
SELECT id, nome, crm, crm_uf, origem_cadastro, status, keycloak_user_id
FROM onboarding.medicos WHERE crm = 'E2E888';
-- origem_cadastro = AUTO_CADASTRO ✅, status = RASCUNHO ✅, keycloak_user_id preenchido ✅

SELECT cidade, uf, estado_civil, canal_origem, situacao_formacao
FROM onboarding.dados_civis_medico WHERE medico_id = '<id>';
-- todos os campos batendo com o formulário ✅

SELECT aceite_declaracao_veracidade, autorizacao_uso_dados, autorizacao_compartilhamento,
       aviso_privacidade_lido, assinatura_nome, ip_origem
FROM onboarding.declaracoes_lgpd_medico WHERE medico_id = '<id>';
-- todos true, assinatura e IP registrados ✅

SELECT tipo, COUNT(*) FROM onboarding.documentos_medico
WHERE medico_id = '<id>' GROUP BY tipo;
-- CRM: 1, COMPROVANTE_ENDERECO: 1, ESPECIALIDADES: 6 ✅
```

**Resultado:** ✅ OK — todos os dados batem exatamente com o preenchido no formulário.

### 4. Confirmar no Keycloak que o usuário foi criado com `enabled=false`

```bash
curl .../admin/realms/pinsaude/users/<keycloak_user_id>
# {"username": "...", "enabled": false}
```

**Resultado:** ✅ OK.

### 5. Logar como `gestao`, abrir `/medicos/aprovacao`, localizar a candidatura
Badge "Auto-cadastro" visível tanto na lista lateral quanto no cabeçalho do detalhe. Seções
"Dados Civis e Profissionais" e "Declarações LGPD" exibindo todos os dados preenchidos.
Aprovados os 8 documentos (COMPROVANTE_ENDERECO, Registro CRM, 6× Certificado de
Especialidades) — todos "Aguardando" → "Aprovado" sem erro.

**Resultado:** ✅ OK.

### ⚠️ Achado durante o roteiro — checklist de conduta nunca era criado para auto-cadastro
Ao tentar avançar para "Ativar Médico", o requisito "Checklist de conduta completo" nunca
saía do estado pendente — e a seção de Checklist simplesmente não aparecia em nenhuma tela
(`MedicoPerfilPage.tsx`/`AprovacaoOnboardingPage.tsx` só renderizam o `ChecklistEditor` quando
`medico.checklist != null`).

**Causa raiz:** `MedicoService.criar()` (cadastro manual, operação/gestão) sempre semeava uma
linha vazia em `checklist_conduta` na criação do médico — mas `CadastroPublicoService.criar()`
(EPIC-14.2, auto-cadastro público) nunca fazia isso. Resultado: **nenhum médico vindo do
auto-cadastro público teria uma linha de checklist criada em algum momento**, e portanto a
tela de Aprovação nunca teria como marcá-lo como completo — bloqueando a ativação de qualquer
auto-cadastro permanentemente.

**Correção aplicada nesta task:** `CadastroPublicoService` passou a receber
`ChecklistCondutaRepository` e a semear `new ChecklistConduta(medico.getId())` em `criar()`,
mirando exatamente o padrão já usado em `MedicoService.criar()`. Cobertura de teste adicionada
em `CadastroPublicoServiceTest` (verifica a chamada ao `checklistRepo.save`) e em
`CadastroPublicoControllerIntegrationTest` (verifica a linha persistida no Postgres real via
Testcontainers). Ver `CLAUDE.md` para o detalhe técnico completo.

Após o fix (rebuild + restart do `onboarding`), o checklist foi marcado como completo via
`PUT /api/medicos/{id}/checklist` (mesmo endpoint que o `ChecklistEditor` chama) e a seção
passou a aparecer normalmente em `MedicoPerfilPage.tsx` com o badge "Completo".

### 6. Marcar contrato/junta comercial e confirmar ativação automática
Como o Clicksign está desabilitado por padrão neste ambiente (`CLICKSIGN_ENABLED=false`), o
envio real ao Clicksign não foi exercitado aqui (comportamento correto e já testado em
EPIC-03.6/05.2 — fora do escopo desta task). Para validar o fluxo de ativação, um registro de
`contratos_assinatura` com `status='ASSINADO'` foi inserido diretamente (simulando o que o
webhook do Clicksign faria em produção), e o status da Junta Comercial foi alterado para
"Aprovado" via UI (`Onboarding` tab → "Atualizar status" → "Aprovado" → "Confirmar").

**Resultado:** ✅ OK — assim que o último requisito pendente foi satisfeito, o médico foi
**ativado automaticamente** (badge mudou de "Rascunho" para "Ativo" sem precisar clicar em
nenhum botão "Ativar Médico") — confirma o comportamento de `verificarAtivacaoAutomatica()`
documentado desde EPIC-03.6, agora também disparado corretamente a partir de
`atualizarJuntaComercial()` para médicos de auto-cadastro.

### 7. Confirmar no Keycloak que o usuário virou `enabled=true` e ganhou a role `medico`

```bash
curl .../admin/realms/pinsaude/users/<id>              # {"enabled": true}
curl .../admin/realms/pinsaude/users/<id>/role-mappings/realm
# [{"name": "default-roles-pinsaude"}, {"name": "medico"}]
```

**Resultado:** ✅ OK.

### 8. Login com o usuário do médico e confirmar acesso ao portal
Senha definida via Admin API (`reset-password`, simulando o fluxo `UPDATE_PASSWORD` que o
médico completaria via e-mail em produção). Login em `/login` com o e-mail da candidatura.

**Nota:** o primeiro login falhou com "Sua conta requer configuração de MFA" — mensagem
genérica do frontend (`AuthContext.tsx`) para qualquer `error_description` do Keycloak
contendo "not fully set up"/"actions", **não exclusivamente MFA**. Causa real: o usuário
ainda tinha `VERIFY_EMAIL` pendente (`requiredActions` setado por
`KeycloakAdminService.createUserDesabilitado`). Em produção o médico limparia isso clicando no
link do e-mail de verificação; neste teste local, o required action foi limpo diretamente via
Admin API (`emailVerified: true, requiredActions: []`) — fluxo de verificação de e-mail em si
não é escopo do EPIC-14.

Após isso, login bem-sucedido: sidebar do papel `medico` (Meu Portal, Minhas Notas, Extrato,
Informar Produção, Frequências), `/portal/dashboard` carregou com saudação "Olá, Dra.!" e
seções do portal renderizando (mensagens "Erro 500" nas seções que dependem de dados fiscais
são esperadas neste teste — os serviços `fiscal`/`faturamento`/`portal` não foram subidos nesta
sessão, fora do escopo desta task).

**Resultado:** ✅ OK (acesso e autorização por role confirmados; dados agregados de outros
serviços não testados por não fazerem parte do escopo do EPIC-14).

### 9. Repetir o cadastro com o mesmo CPF/CRM → 409
```bash
curl -X POST .../api/onboarding/publico/candidaturas -d '{"cpf": "<mesmo cpf>", ...}'
# HTTP 409 — "Já existe uma candidatura ou cadastro com este CPF. Entre em contato com
#            falecom@pinsaude.com.br para mais informações."
```

**Resultado:** ✅ OK.

### 10. Enviar mais de 5 arquivos em títulos de especialista
6 arquivos enviados sequencialmente no campo "Títulos de especialista (envie quantos tiver)"
durante a Etapa 5. A UI mostrou o contador incrementando ("6 arquivo(s) enviado(s)") com todos
os 6 listados e nenhum bloqueio/erro. Confirmado também no banco (`documentos_medico`: 6 linhas
com `tipo = ESPECIALIDADES` para o mesmo médico).

**Resultado:** ✅ OK — sem limite de quantidade, conforme decisão de escopo do EPIC-14.

## Resumo

| # | Passo | Resultado |
|---|-------|-----------|
| 1 | Acessar `/cadastro-medico` sem login | ✅ |
| 2 | Refresh mid-preenchimento (rascunho incremental) | ✅ |
| 3 | Dados no banco batem + `origem_cadastro=AUTO_CADASTRO` | ✅ |
| 4 | Keycloak: usuário criado com `enabled=false` | ✅ |
| 5 | Fila de aprovação exibe candidatura com badge + dados civis/LGPD | ✅ |
| 6 | Ativação automática ao cumprir todos os requisitos | ✅ (após corrigir gap do checklist) |
| 7 | Keycloak: `enabled=true` + role `medico` | ✅ |
| 8 | Login do médico + acesso ao portal | ✅ |
| 9 | Duplicidade de CPF/CRM → 409 | ✅ |
| 10 | Upload de >5 títulos de especialista sem bloqueio | ✅ |

**Achado corrigido nesta task:** `CadastroPublicoService.criar()` não semeava
`checklist_conduta`, impedindo a ativação de qualquer auto-cadastro. Corrigido e coberto por
testes automatizados (ver PR da task 14.9).
