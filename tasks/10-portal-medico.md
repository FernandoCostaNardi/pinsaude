# EPIC-10 — Portal do Médico (Frontend React)

> Prioridade: **P1** — Interface principal do cliente médico.
> ADRs: ADR-0013 (React + OpenAPI gerado). PRD: §7.12. RFs: RF-PORT-01..04
> Stack: React 18 + TypeScript, OpenAPI client gerado, design-system compartilhado.
> Responsivo (sem app nativo no MVP — PRD §3).

---

## TASK-10.1 — Setup do Frontend (React + OpenAPI Client Gerado)

### 1. Objetivo (Por quê?)
O frontend React precisa de estrutura base para consumir as APIs do backend com segurança de tipos (ADR-0013). Sem o client gerado, cada desenvolvedor cria chamadas manuais que divergem do contrato.

### 2. Descrição da Solução (O quê?)
Configurar o projeto React no monorepo (`apps/web`), a lib compartilhada (`libs/frontend/api-client`) com o client gerado a partir do OpenAPI, e a lib de design system.

**Estrutura no monorepo:**
```
apps/web/
├── src/
│   ├── pages/
│   │   ├── portal/       ← páginas do médico
│   │   └── backoffice/   ← páginas do operador/admin
│   ├── components/       ← componentes React locais
│   ├── hooks/            ← React Query hooks
│   ├── router/           ← React Router config
│   └── main.tsx
├── e2e/                  ← testes Playwright
└── package.json

libs/frontend/
├── api-client/           ← gerado pelo openapi-generator
│   └── src/              ← gerado automaticamente de contracts/openapi/*.yaml
└── ui/                   ← design system (componentes Radix/Tailwind)
```

**Geração do client OpenAPI (target Nx):**
```json
// nx.json — target para gerar o client
{
  "targets": {
    "generate-api-client": {
      "executor": "@nx/js:node",
      "options": {
        "main": "tools/generate-api-client.ts"
      }
    }
  }
}
```

```bash
# Comando de geração (via openapi-generator-cli)
npx @openapitools/openapi-generator-cli generate \
  -i contracts/openapi/faturamento.yaml \
  -g typescript-fetch \
  -o libs/frontend/api-client/src/generated/faturamento
```

**Configuração de autenticação (OAuth2/PKCE):**
```typescript
// libs/frontend/api-client/src/auth.ts
import { OidcClient } from 'oidc-client-ts';

export const oidcClient = new OidcClient({
  authority: process.env.VITE_OIDC_AUTHORITY,
  client_id: 'pinsaude-web',
  redirect_uri: `${window.location.origin}/callback`,
  response_type: 'code',
  scope: 'openid profile email',
});
```

**React Query para estado de servidor:**
```typescript
// Cada endpoint tem um hook com cache, retry e loading state
export function useExtrato(params: ExtratoParams) {
  return useQuery({
    queryKey: ['extrato', params],
    queryFn: () => ledgerApi.getExtrato(params),
    staleTime: 30_000,  // 30s de cache
  });
}
```

### 3. Critérios de Aceite
- [ ] `nx build web` compila sem erros.
- [ ] `nx generate-api-client` gera os tipos TypeScript a partir dos OpenAPI YAML.
- [ ] Erro de compilação TypeScript se o contrato do backend mudar (types fora de sincronia).
- [ ] Login via PKCE funciona com o Keycloak local.
- [ ] Header `X-Tenant-Id` enviado automaticamente em todas as chamadas autenticadas.
- [ ] `pnpm test` na lib `ui` executa testes de componentes.

### 4. Regras de Negócio
- OpenAPI como fonte da verdade (ADR-0013).
- Client gerado automaticamente — nunca escrever chamadas HTTP manuais para endpoints documentados.
- MVP é web responsivo (sem app nativo — PRD §3).
- Estados de processamento assíncrono refletidos na UI ("emitindo", "em conciliação").

### 5. Cenários de Testes para o Humano
1. **Quebra de contrato:** Renomear um campo no YAML do OpenAPI sem atualizar o frontend → `nx build web` deve falhar com erro de tipo TypeScript.
2. **Login completo:** Acessar `localhost:3000` → redirecionar para Keycloak → logar → voltar com token → header `X-Tenant-Id` nas requisições.
3. **Responsividade:** Abrir o portal no celular (viewport 375px) → layout deve ser usável (sem overflow horizontal).

---

## TASK-10.2 — Dashboard do Médico

### 1. Objetivo (Por quê?)
O dashboard é a primeira tela que o médico vê ao logar. Precisa mostrar o que mais importa: saldo a receber, próximos repasses, status das notas recentes. Reduz a necessidade de navegar para encontrar informações críticas (RF-PORT-01).

### 2. Descrição da Solução (O quê?)
Página de dashboard com widgets de saldo, notas recentes e repasses.

**Componentes do dashboard:**
```
┌─────────────────────────────────────────────────────────────┐
│ Bom dia, Dr. João!           [Empresa: Pin Saúde Ltda ▼]    │
├──────────────┬──────────────┬──────────────────────────────┤
│  A Receber   │  Repassado   │  Último Repasse               │
│  R$ 8.500    │  R$ 42.500   │  R$ 8.500 • 10/jun ✓        │
│  (1 nota)    │  (este mês)  │                               │
├──────────────┴──────────────┴──────────────────────────────┤
│ Notas Recentes                              [Ver todas →]   │
│ ⏳ Hospital São Marcos  R$ 10.000  Emitindo...              │
│ ✅ Hospital ABC         R$ 5.000   Emitida   10/jun         │
│ ✅ Clínica XYZ          R$ 3.000   Repassada  8/jun         │
├─────────────────────────────────────────────────────────────┤
│ [+ Informar Nova Produção]                                   │
└─────────────────────────────────────────────────────────────┘
```

**Hook e endpoint:**
```typescript
// GET /portal/medico/me/dashboard
export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: () => portalApi.getDashboard(),
    refetchInterval: 30_000,  // atualiza a cada 30s
  });
}
```

**DTO do dashboard (backend):**
```java
public record DashboardDto(
    long saldoAReceber,           // centavos
    long repassadoNoMes,          // centavos
    RepasseRecenteDto ultimoRepasse,
    List<NotaResumoDto> notasRecentes,  // últimas 5
    int totalNotasEmAberto
) {}
```

**Estados de nota visíveis no dashboard:**
- `EMITINDO` → spinner "Emitindo..."
- `EMITIDA` → check verde "Emitida"
- `AGUARDANDO_REPASSE` → relógio "Aguardando pagamento do hospital"
- `REPASSE_AUTORIZADO` → check parcial "Repasse autorizado"
- `REPASSADA` → check duplo verde "Repassada"
- `REJEITADA` → X vermelho "Rejeitada — ver detalhes"

### 3. Critérios de Aceite
- [ ] Dashboard carrega em < 2s em rede local.
- [ ] Saldo "A Receber" é a soma dos repasses de notas emitidas não pagas.
- [ ] Status das notas reflete o estado real do backend (polling 30s ou SSE).
- [ ] Nota com status `EMITINDO` mostra spinner (não fica presa em estado vazio).
- [ ] Botão "Informar Nova Produção" navega para o formulário de produção.
- [ ] Seletor de empresa visível quando médico tem mais de 1 empresa.

### 4. Regras de Negócio
- Médico vê apenas dados da empresa selecionada (tenant ativo).
- Saldo em regime de caixa: só notas com recebimento confirmado aparecem como "a repassar".
- Estados assíncronos devem ter feedback visual claro (RF-PORT-01).

### 5. Cenários de Testes para o Humano
1. **Dashboard vazio:** Médico recém-ativado sem nenhuma produção → dashboard mostra zeros com mensagem "Informe sua primeira produção".
2. **Nota emitindo:** Informar produção → aguardar no dashboard → spinner aparece e desaparece quando nota é emitida (sem reload manual).
3. **Troca de empresa:** Médico vinculado a 2 empresas → trocar empresa no seletor → dados do dashboard mudam.
4. **Último repasse:** Após repasse liquidado, verificar que "Último Repasse" mostra valor e data corretos.

---

## TASK-10.3 — Formulário de Informar Produção e Solicitar Emissão

### 1. Objetivo (Por quê?)
O médico informa a produção (tomador, valor, competência, serviço) para que a nota seja emitida. É o ponto de entrada do ciclo de faturamento. A UX precisa ser simples e evitar erros (RF-PORT-02).

### 2. Descrição da Solução (O quê?)
Formulário multi-step com seleção de tomador, serviço, competência e valor, com preview do cálculo fiscal antes de confirmar.

**Fluxo do formulário:**
```
Passo 1: Selecionar Tomador
  ↓ busca por nome (typeahead, min 3 chars)
  ↓ ou "Novo Tomador" (vai para cadastro)

Passo 2: Selecionar Serviço
  ↓ lista dos serviços cadastrados para a empresa

Passo 3: Informar Dados da Produção
  - Competência: mês/ano (seletor mês)
  - Valor bruto: campo moeda (R$ 0,00)
  - Descrição: texto opcional (complementar à discriminação padrão)

Passo 4: Preview do Cálculo Fiscal
  ┌────────────────────────────────────────┐
  │ Resumo da Nota                          │
  │ Valor bruto:          R$ 10.000,00     │
  │ ISS (2%):             -R$    200,00    │
  │ IR (1,5%) — retido:   -R$    150,00    │
  │ CSLL (1%) — retido:   -R$    100,00    │
  │ PIS (0,65%) — retido: -R$     65,00    │
  │ COFINS (3%) — retido: -R$    300,00    │
  │ Taxa administrativa:  -R$    685,00    │
  │ ─────────────────────────────────────  │
  │ Seu repasse:           R$ 8.500,00 ✓  │
  └────────────────────────────────────────┘
  [← Voltar]  [Confirmar Emissão →]
```

**Nota especial para tomador CPF com equiparação (UX):**
```
⚠️ Nota emitida com impostos zerados
Conforme a legislação, notas para pacientes CPF com equiparação hospitalar
são emitidas sem destaque de impostos. Os tributos são calculados pela
contabilidade na apuração mensal. Seu repasse continua sendo R$ 8.500,00.
```

**API de preview (chamada no passo 3):**
```
GET /portal/producao/preview?tomador_id=uuid&servico_id=uuid&valor=1000000
→ { preview_calculo: {...}, impostos_zerados: false, aviso: null }
```

### 3. Critérios de Aceite
- [ ] Busca de tomador por nome funciona com typeahead (debounce 300ms).
- [ ] Preview fiscal calculado em tempo real ao digitar o valor.
- [ ] Valor do repasse (85%) destacado visualmente no preview.
- [ ] Aviso especial para nota CPF + equiparação.
- [ ] Formulário bloqueia envio com valor = 0.
- [ ] Confirmação exibe loading state enquanto aguarda resposta.
- [ ] Após confirmar, navega para o dashboard com status "Emitindo".
- [ ] Formulário responsivo (funcional em mobile).

### 4. Regras de Negócio
- Competência máxima: mês atual (não pode informar futuro).
- Valor mínimo: R$ 1,00 (100 centavos).
- Preview não é definitivo — cálculo definitivo ocorre no backend ao emitir.
- Tomador PF + serviço equiparado → aviso de nota zerada.

### 5. Cenários de Testes para o Humano
1. **Fluxo completo:** Médico informa produção de R$5.000 para hospital → preview mostra repasse R$4.250 → confirmar → dashboard mostra nota "Emitindo".
2. **Caso CPF zerado:** Selecionar tomador PF + serviço equiparado → aviso aparece → valor do repasse permanece 85%.
3. **Validação de valor:** Tentar confirmar com valor = R$0,00 → botão desabilitado + mensagem de erro.
4. **Responsividade:** Abrir formulário no mobile → todos os campos visíveis e utilizáveis.
5. **Novo tomador:** Clicar em "Novo Tomador" → modal/página de cadastro → após criar, volta ao formulário com tomador selecionado.

---

## TASK-10.4 — Extrato, Notas e Comprovantes

### 1. Objetivo (Por quê?)
O médico precisa de acesso a seu histórico completo: extrato de lançamentos, XML e PDF das notas, e comprovantes de repasse. Isso reduz contatos com a operação e dá autonomia ao médico (RF-PORT-03).

### 2. Descrição da Solução (O quê?)
Tela de extrato filtrada por período, com links para download de notas e comprovantes.

**Tela de extrato:**
```
Extrato                    [Período: Jan/2026 – Jun/2026 ▼]  [Exportar ▼ PDF | CSV]

Data        Descrição                Valor Bruto   Repasse    Status
────────────────────────────────────────────────────────────────────────────────
15/jun      Hospital São Marcos      R$ 10.000     R$ 8.500   ✅ Repassado
            NFS-e 2026/00123 [XML] [PDF]          Comp. [↓]
──────────────────────────────────────────────────────────────────────────────
08/jun      Hospital ABC             R$ 5.000      R$ 4.250   ⏳ Aguardando pag.
            NFS-e 2026/00122 [XML] [PDF]
──────────────────────────────────────────────────────────────────────────────
                                     Total         R$12.750
```

**Downloads disponíveis:**
- `[XML]` → download do XML da NFS-e (quando emitida).
- `[PDF]` → download do DANFSE em PDF (quando emitida).
- `[Comp. ↓]` → download do comprovante de repasse (quando liquidado).

### 3. Critérios de Aceite
- [ ] Extrato exibe lançamentos do período selecionado.
- [ ] Filtro de período funciona (mês/ano, com range).
- [ ] Download de XML abre arquivo `.xml` da NFS-e.
- [ ] Download de PDF abre o DANFSE em PDF.
- [ ] Download de comprovante disponível somente para repasses liquidados.
- [ ] Exportação PDF gera arquivo com header, tabela e total do período.
- [ ] Exportação CSV abre corretamente no Excel/Sheets.
- [ ] Médico sem notas vê mensagem "Nenhuma produção no período".

### 4. Regras de Negócio
- Médico vê apenas seus próprios lançamentos.
- XML e PDF disponíveis somente para notas `EMITIDA` (não rascunhos).
- Comprovante visível somente após repasse `LIQUIDADO`.

### 5. Cenários de Testes para o Humano
1. **Extrato com notas:** Logar como médico com 5 notas em junho → filtrar por junho/2026 → 5 registros.
2. **Download XML:** Clicar em `[XML]` de uma nota emitida → arquivo `.xml` baixado com conteúdo válido.
3. **Download PDF:** Clicar em `[PDF]` → DANFSE em PDF aberto/baixado.
4. **Comprovante indisponível:** Nota no status "Aguardando repasse" → `[Comp.]` não disponível (desabilitado ou oculto).
5. **Exportação:** Clicar em "Exportar PDF" → PDF gerado com todas as notas do período e total.

---

## TASK-10.5 — Edição de Cadastro e Dados Bancários

### 1. Objetivo (Por quê?)
O médico pode precisar atualizar telefone, e-mail ou a chave PIX para recebimento. A atualização de dados bancários é sensível e exige reautenticação (RF-PORT-04, RF-ONB-08).

### 2. Descrição da Solução (O quê?)
Tela de perfil do médico com edição limitada e confirmação reforçada para dados bancários.

**Campos editáveis pelo médico:**
- Telefone, e-mail de contato (sem reautenticação adicional).
- Dados bancários (chave PIX principal + CPFs adicionais para split) → exige step-up.

**Fluxo de step-up para dados bancários:**
```
1. Médico clica em "Alterar dados bancários"
2. Frontend verifica age da sessão via token JWT (claim auth_time)
3. Se sessão > 5 min:
   → Redireciona para página de reautenticação do Keycloak
   → Após confirmar senha (+ TOTP se obrigatório), volta com token novo
4. Formulário de dados bancários liberado
5. Confirmar → PATCH /medicos/{id}/dados-bancarios
```

**Componente de step-up:**
```typescript
function DadosBancariosStep({ medicoId }: { medicoId: string }) {
  const { isSessionRecent } = useSessionAge(5 * 60); // 5 minutos

  if (!isSessionRecent) {
    return (
      <Alert type="warning">
        Para alterar dados bancários, você precisa confirmar sua senha.
        <Button onClick={requestStepUp}>Confirmar Identidade</Button>
      </Alert>
    );
  }

  return <DadosBancariosForm medicoId={medicoId} />;
}
```

### 3. Critérios de Aceite
- [ ] Médico edita telefone/e-mail sem step-up.
- [ ] Ao tentar editar dados bancários com sessão > 5min, step-up é solicitado.
- [ ] Após step-up, formulário de dados bancários é liberado.
- [ ] Confirmação de dados bancários envia `PATCH` com os novos dados.
- [ ] Sucesso exibe toast de confirmação.
- [ ] Chave PIX é mascarada (exibida como `****.1234` apenas os últimos 4 dígitos).

### 4. Regras de Negócio
- Dados bancários exigem reconfirmação (RF-ONB-08).
- Chave PIX mascarada na visualização (segurança).
- Médico não pode alterar CRM, CPF ou dados societários pela interface.
- Alteração gera auditoria automática (`dados-bancarios.alterados`).

### 5. Cenários de Testes para o Humano
1. **Edição de telefone:** Logar, ir para perfil, alterar telefone → salva sem step-up.
2. **Step-up para PIX:** Logar, aguardar 6 min, tentar alterar chave PIX → modal de step-up → confirmar senha → formulário liberado.
3. **Mascaramento:** Na tela de perfil, chave PIX aparece como `***@gmail.com` ou `*****-1234` (não o valor completo).
4. **Auditoria:** Após alterar dados bancários, verificar registro em `audit_log` com `action = 'dados-bancarios.alterados'`.
