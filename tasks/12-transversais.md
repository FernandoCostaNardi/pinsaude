# EPIC-12 — Transversais: Benefícios, Notificações e Auditoria/LGPD

> Prioridade: **P2** — Funcionalidades transversais que suportam todos os épicos.
> PRD: §7.10, §7.11, §7.14. RFs: RF-BEN-01, RF-NOT-01, RF-LGPD-01..03

---

## TASK-12.1 — Registro de Elegibilidade para Benefícios

### 1. Objetivo (Por quê?)
Médico ativo com ao menos 1 nota emitida tem direito a benefícios (Wellhub/TotalPass). No MVP, apenas o registro de elegibilidade é feito — a integração via API com as plataformas é Fase 2. Sem o registro, a gestão não sabe quais médicos são elegíveis quando a integração for feita (RF-BEN-01).

### 2. Descrição da Solução (O quê?)
Consumer do evento `NotaEmitida` que marca a elegibilidade do médico.

**Migração já contemplada em TASK-03.6** (`onboarding.beneficio_elegibilidade`).

**Consumer que marca elegibilidade (no serviço `onboarding`):**
```java
@RabbitListener(queues = "nota.emitida.q")
@Transactional
public void onNotaEmitida(NotaEmitidaEvent event) {
    if (processedEvents.exists(event.eventId())) return;

    UUID medicoId = event.medicoId();
    UUID cnpjId   = event.cnpjId();

    // Marcar elegibilidade na primeira nota emitida
    List<String> tipos = List.of("WELLHUB", "TOTALPASS", "CLUBE_DESCONTOS");
    for (String tipo : tipos) {
        beneficioRepository.marcarElegivel(cnpjId, medicoId, tipo,
            event.emitidaEm().toInstant());
    }

    processedEvents.save(event.eventId());
}
```

**Endpoints:**
```
GET /portal/medico/me/beneficios
  role: MEDICO
  → lista benefícios com elegibilidade e data
  response: [
    { "tipo": "WELLHUB", "elegivel": true, "elegivel_desde": "2026-06-15T10:00:00Z" },
    { "tipo": "TOTALPASS", "elegivel": false }
  ]

GET /backoffice/beneficios/elegiveis?empresa_id=uuid
  role: GESTAO, OPERACAO
  → lista todos os médicos elegíveis por empresa (para integração Fase 2)
```

### 3. Critérios de Aceite
- [ ] Após primeira nota emitida, `beneficio_elegibilidade` marcado como `elegivel = true`.
- [ ] Médico sem notas vê `elegivel = false` em todos os benefícios.
- [ ] Consumer idempotente: segunda nota não duplica o registro de elegibilidade.
- [ ] `GET /portal/medico/me/beneficios` retorna status correto.
- [ ] Exportação de elegíveis disponível para a gestão (para Fase 2).

### 4. Regras de Negócio
- Elegibilidade: médico ativo + ao menos 1 nota emitida (RF-BEN-01).
- Integração com Wellhub/TotalPass via API é Fase 2 (PRD §3).
- MVP apenas registra elegibilidade — sem chamada de API externa.
- Elegibilidade não é perdida se a nota for cancelada (data de conquista registrada).

### 5. Cenários de Testes para o Humano
1. **Antes da nota:** Médico recém-ativado, sem notas → `GET /portal/medico/me/beneficios` → todos `elegivel: false`.
2. **Após primeira nota:** Emitir nota para médico → verificar `elegivel: true` e `elegivel_desde` preenchido.
3. **Idempotência:** Emitir segunda nota → verificar que não criou novo registro (data de elegibilidade é a da primeira nota).
4. **Listagem para gestão:** `GET /backoffice/beneficios/elegiveis` → retorna lista com todos os médicos elegíveis da empresa.

---

## TASK-12.2 — Serviço de Notificações por E-mail

### 1. Objetivo (Por quê?)
O médico e a operação precisam ser notificados de eventos importantes (nota emitida, repasse efetuado, pendência de documento). Sem notificações, o fluxo para dependendo de o médico/operação entrar no portal para verificar (RF-NOT-01).

### 2. Descrição da Solução (O quê?)
Serviço de e-mail transacional via adapter de provedor (SendGrid/Mailgun/SES), com templates por evento.

**Port:**
```java
public interface EmailPort {
    void enviar(String destinatario, EmailTemplate template, Map<String, Object> variaveis);
}
```

**Adapter (ex: SendGridAdapter):**
```java
@Component
@ConditionalOnProperty("integracoes.email.provider", havingValue = "sendgrid")
public class SendGridAdapter implements EmailPort {
    @Retry(name = "email", fallbackMethod = "fallbackEmail")
    public void enviar(String destinatario, EmailTemplate template, Map<String, Object> variaveis) {
        // POST https://api.sendgrid.com/v3/mail/send
    }
    public void fallbackEmail(String dest, EmailTemplate tmpl, Map<String, Object> vars, Exception e) {
        // Log para DLQ de e-mails não enviados — não lançar exceção
        log.error("Falha ao enviar e-mail {} para {}", tmpl, dest, e);
    }
}
```

**Templates obrigatórios no MVP:**

| Template | Destinatário | Trigger |
|---|---|---|
| `BOAS_VINDAS` | Médico | Ativação do médico |
| `NOTA_EMITIDA` | Médico | Nota emitida com sucesso |
| `NOTA_REJEITADA` | Médico | Nota rejeitada (com motivo) |
| `REPASSE_EFETUADO` | Médico | Repasse liquidado (com comprovante) |
| `PENDENCIA_DOCUMENTO` | Médico | Documento rejeitado (com motivo) |
| `CONVITE_CADASTRO` | Futuro médico | Convite gerado |
| `ALERTA_VENCIMENTO_FISCAL` | Gestão/Contábil | Guia vencendo em 5 e 1 dia |
| `ALERTA_TETO_FISCAL` | Gestão | Teto > 70%/90% |
| `APROVACAO_CONTRATO` | Operação | Contrato assinado pelo médico |

**Estrutura dos templates (HTML + texto):**
```
templates/emails/
├── boas-vindas.html
├── nota-emitida.html
├── repasse-efetuado.html
└── ... (um arquivo por template)
```

**Consumer genérico de e-mails:**
```java
// Cada evento dispara e-mail via seu próprio consumer
// A falha no envio de e-mail NÃO deve reverter o evento de negócio
@RabbitListener(queues = "nota.emitida.q")
public void onNotaEmitida(NotaEmitidaEvent event) {
    emailPort.enviar(event.emailMedico(), NOTA_EMITIDA, Map.of(
        "nome", event.nomeMedico(),
        "numero_nota", event.numeroNota(),
        "valor", event.valorRepasse()
    ));
}
```

### 3. Critérios de Aceite
- [ ] E-mail de boas-vindas enviado ao ativar médico.
- [ ] E-mail de nota emitida enviado com número da nota e valor do repasse.
- [ ] E-mail de repasse efetuado com valor e link do comprovante (válido 7 dias).
- [ ] Falha no envio de e-mail NÃO reverte o estado do sistema (fire-and-forget).
- [ ] Retry automático em caso de indisponibilidade do provedor.
- [ ] Templates renderizados com dados corretos (sem campos `{{variable}}` vazios).
- [ ] E-mails enviados em < 60s após o evento.

### 4. Regras de Negócio
- E-mail é o canal de notificação do MVP (RF-NOT-01). WhatsApp é Fase 2.
- Falha de e-mail não afeta o fluxo financeiro.
- Template de e-mail versionado (mudança sem deploy se possível).
- Dados sensíveis (CPF, dados bancários) nunca no corpo do e-mail.

### 5. Cenários de Testes para o Humano
1. **Boas-vindas:** Ativar médico → verificar e-mail de boas-vindas na caixa de entrada (usar Mailhog local para teste).
2. **Nota emitida:** Emitir nota → verificar e-mail com número da nota e valor R$8.500.
3. **Repasse efetuado:** Liquidar repasse → verificar e-mail com valor e link do comprovante.
4. **Falha graceful:** Desligar o provedor de e-mail (Mailhog), emitir nota → nota emitida normalmente → log de falha de e-mail registrado.
5. **Sem PII:** Verificar que nenhum e-mail contém CPF, dados bancários ou senhas em claro.

---

## TASK-12.3 — Auditoria Completa e Conformidade LGPD

### 1. Objetivo (Por quê?)
Auditoria e LGPD são requisitos regulatórios não-negociáveis. A plataforma manipula dados fiscais, financeiros e pessoais de médicos e pacientes. Sem auditoria imutável e conformidade LGPD, a Pin está exposta a riscos legais e multas (RF-LGPD-01..03, ADR-0011).

### 2. Descrição da Solução (O quê?)
Consolidar a implementação de auditoria (iniciada em TASK-01.5) com políticas LGPD e retenção de dados.

**Ações auditadas (lista completa):**
```
auth:
  usuario.criado, usuario.ativado, usuario.desativado
  login.sucesso, login.falha, logout
  token.revogado

cadastros:
  medico.criado, medico.atualizado, medico.desativado
  dados-bancarios.alterados
  empresa.criada, empresa.atualizada
  certificado-a1.carregado, certificado-a1.expirado
  tomador.criado, tomador.atualizado
  servico.criado, servico.atualizado

onboarding:
  convite.enviado, convite.aceito
  documento.aprovado, documento.rejeitado
  conduta.aprovada, conduta.reprovada
  contrato.enviado, contrato.assinado
  medico.ativado

fiscal/faturamento:
  producao.registrada
  nota.emitida, nota.rejeitada, nota.cancelada, nota.emissao-manual
  parametro-fiscal.criado, parametro-fiscal.atualizado

ledger:
  lancamento.criado, lancamento.estorno

recebimento:
  extrato.importado
  conciliacao.confirmada, conciliacao.ignorada
  periodo.fechado

repasse:
  repasse.criado, repasse.aprovado, repasse.executado, repasse.falhou

gestao:
  apuracao.gerada, apuracao.homologada
  dre.gerada
  calendario.item-pago
```

**Políticas LGPD implementadas:**

**1. Minimização de dados:**
- Notas para paciente PF (CPF/nome): tratar como dado pessoal (RF-LGPD-03).
- CPF criptografado em repouso (TASK-02.2).
- Logs de aplicação não contêm CPF, dados bancários ou senhas em claro.

**2. Retenção de dados:**
```sql
-- Job mensal de verificação de retenção
-- Documentos fiscais: retenção mínima 5 anos (RF-LGPD-02)
-- Logs operacionais: 1 ano
-- Audit_log: 5 anos (alinhado à retenção fiscal)
```

**3. Acesso a dados de paciente:**
```java
// Nome/CPF de paciente em NFS-e: acesso restrito a CONTABIL e GESTAO
// Mascarado para OPERACAO e FINANCEIRO na visualização
// Ex: "João S***" e "***-789"
```

**Endpoint de consulta de auditoria (já implementado em TASK-01.5):**
```
GET /auditoria
  role: GESTAO
  query: ?cnpj_id=uuid&action=nota.emitida&de=2026-01-01&ate=2026-12-31&actor_id=uuid
  → lista paginada de eventos de auditoria
  → exportável em CSV
```

**Relatório de auditoria para conformidade:**
```
GET /auditoria/relatorio?tipo=fiscal&competencia=2026-06
  → PDF com todas as ações fiscais do período
  → Por médico: emissões, cancelamentos, repasses
  → Assinado digitalmente (para uso regulatório)
```

### 3. Critérios de Aceite
- [ ] Todas as ações da lista acima geram registro em `audit_log`.
- [ ] Logs de aplicação não contêm CPF, CNPJ ou dados bancários em claro.
- [ ] `GET /auditoria` filtrável por ação, ator, período e tenant.
- [ ] Audit log imutável: nenhum endpoint de DELETE ou UPDATE.
- [ ] Job de retenção identifica registros a arquivar/apagar após 5 anos.
- [ ] Nome/CPF de paciente PF mascarado para OPERACAO e FINANCEIRO.
- [ ] Relatório de auditoria fiscal exportável em PDF.

### 4. Regras de Negócio
- Retenção de documentos fiscais: mínimo 5 anos (RF-LGPD-02).
- Dados clínicos de paciente: fora do MVP (PRD §3).
- Dados pessoais de paciente (CPF/nome nas notas): tratar conforme LGPD (RF-LGPD-03).
- Audit log: append-only, sem exceção.
- Base legal para tratamento de dados: documentada (contrato de prestação de serviços).

### 5. Cenários de Testes para o Humano
1. **Cobertura de auditoria:** Executar fluxo completo (convite → onboarding → emissão → conciliação → repasse). Verificar que cada etapa gerou pelo menos 1 registro em `audit_log`.
2. **Imutabilidade:** Tentar `UPDATE audit_log SET action = 'xxx' WHERE id = '...'` → deve retornar "permission denied".
3. **Mascaramento de PII:** Emitir nota para paciente CPF. Logar como OPERACAO e visualizar a nota → nome e CPF do paciente devem aparecer mascarados.
4. **Filtro de auditoria:** Logar como GESTAO, filtrar auditoria por `action = 'repasse.aprovado'` → apenas repasses aprovados aparecem.
5. **Log sem PII:** Emitir nota para paciente "João Silva CPF 123.456.789-09" → verificar no Jaeger/logs que CPF não aparece em nenhum span ou log.
6. **Exportação:** `GET /auditoria/relatorio?tipo=fiscal&competencia=2026-06` → verificar PDF gerado com todas as ações fiscais do período.
