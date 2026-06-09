# EPIC-11 — Backoffice / Admin (Frontend React)

> Prioridade: **P2** — Operação da Pin depende do backoffice.
> ADRs: ADR-0013. PRD: §7.13. RFs: RF-ADM-01..04
> Perfis: OPERACAO, FINANCEIRO, CONTABIL, GESTAO.

---

## TASK-11.1 — Gestão de Cadastros no Backoffice

### 1. Objetivo (Por quê?)
A operação precisa de uma interface centralizada para gerenciar médicos, empresas, tomadores e serviços fiscais — sem depender de chamadas diretas ao banco (RF-ADM-01).

### 2. Descrição da Solução (O quê?)
Módulo de gestão de cadastros com CRUDs, busca e filtros.

**Telas a implementar:**

**1. Gestão de Médicos:**
```
/backoffice/medicos
  - Lista paginada com: nome, CRM, empresa, status
  - Filtros: status (PENDENTE, ATIVO, INATIVO), empresa, busca por nome/CRM
  - Ações: Ver detalhes, Aprovar documentos, Enviar contrato, Atualizar status Junta

/backoffice/medicos/{id}
  - Dados completos do médico
  - Timeline de status (PENDENTE → DOCUMENTOS → CONDUTA → CONTRATO → JUNTA → ATIVO)
  - Tab: Documentos (lista + aprovação/rejeição)
  - Tab: Checklist de Conduta
  - Tab: Contrato (status + link Clicksign)
  - Tab: Vínculos com Empresas
```

**2. Gestão de Tomadores:**
```
/backoffice/tomadores
  - Lista com: nome/razão social, tipo (PJ/PF), município, status
  - Criação e edição
  - Campo "retenção federal" editável

/backoffice/servicos
  - Lista de serviços por empresa
  - Criação e edição (role: CONTABIL, GESTAO)
  - Campos: código, LC116, CNAE, equiparado, alíquotas
```

**3. Gestão de Empresas:**
```
/backoffice/empresas
  - Lista de CNPJs cadastrados com status
  - Detalhes: dados, A1 (status + validade), conta bancária mascarada
  - Ação: Upload de novo A1
  - Ação: Ativar/desativar empresa
```

### 3. Critérios de Aceite
- [ ] Lista de médicos paginada com filtros funcionando.
- [ ] Aprovação/rejeição de documento atualiza status em tempo real.
- [ ] Timeline do onboarding mostra etapa atual do médico.
- [ ] Apenas CONTABIL e GESTAO criam/editam serviços fiscais.
- [ ] Upload de A1 na tela de empresa funciona com validação de certificado.
- [ ] Busca de médico por CRM funciona.

### 4. Regras de Negócio
- RBAC: OPERACAO pode gerenciar médicos e tomadores. CONTABIL gerencia serviços fiscais. GESTAO tem acesso a tudo.
- Dados bancários exibidos mascarados.
- CPF/CNPJ exibidos mascarados (somente 4 últimos dígitos).

### 5. Cenários de Testes para o Humano
1. **Lista de médicos:** Logar como OPERACAO, acessar `/backoffice/medicos` → lista carrega com médicos e status.
2. **Aprovação de documento:** Clicar em um médico → aba documentos → aprovar CRM → status do médico muda para próxima etapa.
3. **Busca por CRM:** Digitar CRM no campo de busca → médico filtrado aparece.
4. **Restrição de acesso:** Logar como OPERACAO, tentar acessar tela de serviços fiscais → deve mostrar "Acesso negado".

---

## TASK-11.2 — Filas de Validação de Notas e Aprovação de Repasse

### 1. Objetivo (Por quê?)
A operação valida as primeiras notas manualmente e o financeiro aprova repasses. Essas filas são críticas para o funcionamento diário da operação (RF-ADM-02).

### 2. Descrição da Solução (O quê?)
Telas de fila com ações de aprovação/rejeição e indicadores de volume.

**Fila de validação de notas:**
```
/backoffice/notas/fila-validacao

┌──────────────────────────────────────────────────────────────────┐
│ Fila de Validação de Notas              [3 pendentes] [Atualizar]│
├────────┬───────────────┬──────────────┬──────────┬───────────────┤
│ Data   │ Médico        │ Tomador      │ Valor    │ Ações         │
├────────┼───────────────┼──────────────┼──────────┼───────────────┤
│ 08/jun │ Dr. João S.   │ Hosp. ABC    │ R$10.000 │ [Ver] [Aprov] │
│ 08/jun │ Dra. Maria L. │ Clín. XYZ    │ R$ 5.000 │ [Ver] [Aprov] │
│ 07/jun │ Dr. Pedro M.  │ Novo tomador │ R$ 3.000 │ [Ver] [Aprov] │
└────────┴───────────────┴──────────────┴──────────┴───────────────┘
```

**Modal de detalhe + aprovação:**
```
Nota para Aprovação
Médico: Dr. João Silva (CRM-SP 12345)
Tomador: Hospital ABC (CNPJ 12.345.678/0001-90)
Serviço: Consulta Médica (LC116 4.03 / CNAE 8630-5/01)
Competência: Junho/2026
Valor: R$ 10.000,00
Preview Fiscal:
  ISS: R$ 200 | IR: R$ 150 | CSLL: R$ 100 | PIS: R$ 65 | COFINS: R$ 300
  Taxa adm: R$ 685 | Repasse: R$ 8.500

Observação: [_______________]
[Rejeitar]  [Aprovar e Emitir]
```

**Fila de aprovação de repasses:**
```
/backoffice/repasses/aprovacao

[Aprovar Selecionados]  [Exportar Planilha]

☐ Dr. João S.    R$ 8.500   Chave PIX: ***.1234   [Aprovar]
☐ Dra. Maria L.  R$ 4.250   Chave PIX: ***.5678   [Aprovar]
☐ Dr. Pedro M.   R$ 2.550   Chave PIX: ***.9012   [Aprovar]

[☐ Selecionar Todos]
```

**Tela de execução do repasse:**
```
/backoffice/repasses/{id}/executar

Repasse: Dr. João Silva — R$ 8.500,00
Chave PIX: ***-1234 (clicar para revelar com step-up)
Parcelas: 1 parcela (valor abaixo do limite)

[Anexar Comprovante ↑]  [Marcar como Executado]
```

### 3. Critérios de Aceite
- [ ] Fila de validação exibe notas pendentes com dados completos.
- [ ] Aprovar nota dispara emissão (status muda para EMITINDO em tempo real).
- [ ] Rejeitar nota com motivo → e-mail enviado ao médico automaticamente.
- [ ] Aprovação em lote de repasses funciona para múltiplos repasses selecionados.
- [ ] Upload de comprovante funciona (PDF/JPG).
- [ ] Após marcar como executado, notificação enviada ao médico.
- [ ] Chave PIX mascarada; revelação exige step-up para FINANCEIRO.

### 4. Regras de Negócio
- Aprovação de nota: OPERACAO ou GESTAO.
- Aprovação de repasse: FINANCEIRO ou GESTAO.
- Execução do repasse: FINANCEIRO ou GESTAO (com step-up).
- Comprovante obrigatório para executar.
- Idempotência: não é possível executar mesmo repasse duas vezes.

### 5. Cenários de Testes para o Humano
1. **Aprovar nota:** Clicar em "Aprovar e Emitir" → loading → status na fila muda para "Emitindo" → desaparece da fila.
2. **Rejeitar com motivo:** Rejeitar nota com motivo "Valor inconsistente" → nota some da fila → médico recebe e-mail.
3. **Lote de repasses:** Selecionar 3 repasses e clicar "Aprovar Selecionados" → todos mudam para APROVADO.
4. **Executar repasse:** Fazer upload de comprovante e clicar "Marcar como Executado" → status LIQUIDADO → médico recebe notificação.
5. **Revelar PIX:** Clicar em chave PIX mascarada → step-up → chave revelada por 30s.

---

## TASK-11.3 — Conciliação e Fechamento

### 1. Objetivo (Por quê?)
O financeiro precisa de uma tela para importar extratos, fazer o matching e fechar o período de conciliação (RF-ADM-03).

### 2. Descrição da Solução (O quê?)
Interface de conciliação com importação, matching assistido e fechamento de período.

**Tela de conciliação:**
```
/backoffice/conciliacao

[Importar Extrato ↑ CSV/OFX]    Período: Jun/2026    Empresa: Pin Saúde Ltda

Entradas Não Conciliadas (12)
──────────────────────────────────────────────────────────────────────────
15/06  R$ 10.000  HOSPITAL SAO MARCOS           [Ver Sugestões]  [Ignorar]
14/06  R$  5.000  CLINICA VIDA                  [Ver Sugestões]  [Ignorar]
14/06  R$  3.000  PAGAMENTO DIVERSO             [Ver Sugestões]  [Ignorar]
──────────────────────────────────────────────────────────────────────────
              [Fechar Período de Conciliação]
```

**Modal de sugestões (matching):**
```
Entrada: R$ 10.000 — HOSPITAL SAO MARCOS — 15/jun

Sugestões (ordenadas por score):
Score: 95  NFS-e 2026/00123  Dr. João S.  R$ 10.000  Hosp. São Marcos  Emitida 10/jun  [✓ Confirmar]
Score: 45  NFS-e 2026/00119  Dra. Maria   R$ 10.000  Hosp. São Marcos  Emitida 05/jun  [Confirmar]

[Confirmar Selecionada]  [Confirmar Manual (sem sugestão)]  [Ignorar Entrada]
```

**Fechamento do período:**
- Somente após todas as entradas estarem Conciliadas ou Ignoradas.
- Gera relatório de conciliação do período.
- Bloqueia novas importações para o período fechado.

### 3. Critérios de Aceite
- [ ] Importação de extrato CSV/OFX mostra entradas não conciliadas.
- [ ] Sugestões ordenadas por score (0-100).
- [ ] Confirmar match com 1 clique muda status da entrada para CONCILIADO.
- [ ] Fechamento de período disponível somente quando 0 entradas PENDENTES.
- [ ] Relatório de conciliação gerado ao fechar (CSV/PDF com resumo).
- [ ] Entradas duplicadas (mesma importação) não aparecem duas vezes.

### 4. Regras de Negócio
- Confirmação de match exige que a soma das notas = valor da entrada.
- Matching é assistido (sugestão) + confirmação humana (PRD O2).
- Período fechado não permite novos matches.
- Financeiro e Gestão têm acesso à conciliação.

### 5. Cenários de Testes para o Humano
1. **Importação e matching:** Importar extrato com 5 entradas → 5 aparecem como não conciliadas → confirmar match para 3 → 2 permanecem pendentes.
2. **Confirmação por lote:** Confirmar múltiplos matches de uma vez → entradas correspondentes mudam para CONCILIADO.
3. **Fechamento:** Após conciliar ou ignorar todas as entradas → botão "Fechar Período" ativo → clicar → gerar relatório.
4. **Relatório:** Baixar relatório de conciliação → verificar PDF com entradas, notas vinculadas e totais.

---

## TASK-11.4 — Painéis de Gestão e Exportações

### 1. Objetivo (Por quê?)
A gestão da Pin precisa de visão consolidada: apuração fiscal, DRE, monitor de teto, posição de caixa e calendário fiscal. Sem esses painéis, a tomada de decisão é feita em planilhas manuais (RF-ADM-04).

### 2. Descrição da Solução (O quê?)
Dashboard de gestão com os painéis definidos no PRD §7.9 e exportações.

**Estrutura de navegação do backoffice de gestão:**
```
/backoffice/gestao/
├── apuracao          → Apuração Mensal de Tributos (TASK-09.2)
├── dre               → DRE Simplificada (TASK-09.3)
├── teto-fiscal       → Monitor de Teto Fiscal (TASK-09.4)
├── posicao-caixa     → Posição de Caixa (TASK-09.5)
└── calendario-fiscal → Calendário Fiscal (TASK-09.6)
```

**Tela de Apuração (resumo):**
```
Apuração — Junho/2026           [Gerar Rascunho]  [Exportar PDF]

Receita bruta:        R$ 100.000
Tributos apurados:
  IRPJ:               R$  1.200  (saldo)
  CSLL:               R$    360  (saldo)
  PIS/COFINS:         R$  3.650  (saldo)
  ISS:                R$  2.000
Total a recolher:     R$  7.210

[Ver Detalhamento Completo]  [Homologar]
```

**Monitor de Teto — card resumo:**
```
┌─────────────────────────────────────────┐
│ Pin Saúde Olinda          56% ⚠️         │
│ ██████████████░░░░░░  R$ 43,7mi / R$78mi│
│ Pin Saúde Eusébio          3% ✅         │
│ ███░░░░░░░░░░░░░░░░░  R$  2,3mi / R$78mi│
└─────────────────────────────────────────┘
```

**Exportações disponíveis:**
- Apuração: PDF/CSV por competência.
- DRE: PDF/CSV histórico.
- Repasses: CSV para execução bancária (contém nome, valor, PIX — acesso FINANCEIRO + step-up).

### 3. Critérios de Aceite
- [ ] Painel de apuração exibe tributos calculados para a competência selecionada.
- [ ] Monitor de teto mostra percentual de todas as empresas.
- [ ] Posição de caixa exibe valores em tempo real (atualizado via eventos).
- [ ] Calendário fiscal mostra vencimentos com status (pendente/pago/vencido).
- [ ] Exportação de repasses exige step-up antes de mostrar chaves PIX.
- [ ] Painéis acessíveis apenas por GESTAO e CONTABIL.

### 4. Regras de Negócio
- Painéis de gestão: GESTAO e CONTABIL.
- Posição de caixa: GESTAO e FINANCEIRO.
- Exportação com dados bancários exige step-up.
- Monitor de teto aciona roteamento de novos médicos quando > 90%.

### 5. Cenários de Testes para o Humano
1. **Apuração no painel:** Gerar apuração de junho/2026 → valores aparecem no painel.
2. **Teto crítico:** Empresa com 92% do teto → card aparece em vermelho com indicador CRÍTICO.
3. **Posição de caixa:** Após conciliar recebimento → painel de posição de caixa reflete nova posição sem reload.
4. **Exportação protegida:** Clicar em "Exportar Repasses" → step-up solicitado → após confirmar, CSV baixado com dados bancários.
