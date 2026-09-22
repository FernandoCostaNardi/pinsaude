# ADR-004 — Catálogo de Permissões Granulares (`perm_*`)

**Status:** Aceito
**Data:** 2026-09-22
**Contexto:** PERFIL-01 — Perfis de Acesso Customizados (bloqueio por tela)
**Task Notion:** [Definir catálogo final de permissões granulares (perm_*)](https://app.notion.com/p/3e3c45fcf7df817ab107cc808856ac39)

---

## Contexto

O RBAC atual ([ADR-002](002-rbac-matrix.md)) controla acesso por 5 papéis fixos
(`medico`, `operacao`, `financeiro`, `contabil`, `gestao`), aplicados por serviço/domínio
inteiro. Isso não permite montar um perfil customizado por cliente (ex.: "vê Tomadores e
Frequências, mas não vê Fiscal nem Usuários") — a granularidade mínima hoje é o serviço
inteiro, não a tela.

O épico "Perfis de Acesso Customizados — bloqueio por tela" (PERFIL-01 a 21) introduz um
segundo nível de RBAC: roles atômicas `perm_*` no Keycloak, uma por tela/função de negócio,
que se somam (nunca substituem) aos 5 papéis já existentes. Um perfil customizado (PERFIL-04)
é uma role composta no Keycloak que agrupa N `perm_*` como filhas; cada controller passa a
aceitar `hasRole('papel_atual') or hasRole('perm_x')` — aditivo, nunca removendo o acesso que
os 5 papéis legados já têm hoje.

Esta task fecha a lista definitiva de `perm_*`, na mesma granularidade dos itens do menu
(`apps/web/src/layouts/Sidebar.tsx:30-55`), com o mapeamento exato para os controllers que cada
uma passa a proteger. **Task de decisão/documentação — sem código de aplicação.**

---

## Decisão

15 permissões atômicas, cada uma uma **realm role simples (não composta)** no Keycloak,
nomeada `perm_<domínio>` (minúsculo, sem acento, no mesmo vocabulário da rota/tela):

| # | Permissão | Tela(s) | Serviço · Controller | Endpoints protegidos |
|---|---|---|---|---|
| 1 | `perm_medicos` | Médicos, Aprovação | onboarding · `MedicoController` | 28 |
| 2 | `perm_empresas` | Empresas | onboarding · `EmpresaController` | 8 |
| 3 | `perm_tomadores` | Tomadores | faturamento · `TomadorController` (CRUD + 8 sub-recursos: alíquotas, cnaes, empresas, grupos+setores, médicos+setores, modalidades, ocorrências, turnos-padrão) | 48 |
| 4 | `perm_producao` | Produção | faturamento · `ProducaoController` | 5 |
| 5 | `perm_frequencias` | Frequências | faturamento · `FrequenciaController` | 11 |
| 6 | `perm_fechamentos` | Fechamento | faturamento · `FechamentoController` | 6 |
| 7 | `perm_fiscal` | Fiscal | fiscal · `FiscalParametrosController`+`ParametroFiscalController`+`MotorFiscalController`+`RegraEquiparacaoController`+`FiscalController` (8) + onboarding · `ConfiguracaoFiscalController` (1) | 9 |
| 8 | `perm_notas` | Notas | fiscal · `NfseController` | 10 |
| 9 | `perm_notas_lote` | Lotes NFS-e | fiscal · `NfseBatchController` | 4 |
| 10 | `perm_conciliacao` | Upload Extrato, Conciliação | faturamento · `ConciliacaoController` (métodos: upload, extratos, lancamentos, candidatas, sugestoes, conciliar, ignorar, delete-conciliação) | 8 |
| 11 | `perm_caixa` | Posição de Caixa | faturamento · `ConciliacaoController` (método `GET /posicao-caixa`, mesmo controller de `perm_conciliacao`) | 1 |
| 12 | `perm_ledger` | Extrato Ledger | ledger · `LedgerController` | 10 |
| 13 | `perm_gestao` | Gestão (dashboard) | gestao · `GestaoController` (stub hoje) | 1 |
| 14 | `perm_usuarios` | Usuários | gestao · `UsuarioController` | 3 |
| 15 | `perm_repasses` | Repasses | repasse · `RepasseController` (stub — EPIC-09 nunca implementado) | 3 |

**Total: 15 `perm_*` cobrindo 17 telas do backoffice, com 168 `@PreAuthorize` validados
por grep contra o código real em 2026-09-22** (breakdown acima batendo 1:1 com o grep —
`Grep '@PreAuthorize' services/` → 168 ocorrências em 26 arquivos, incluindo os 2 controllers
de teste/stub genérico e o `PortalMedicoController`, ambos fora do catálogo — ver seção
"Fora do catálogo").

> **Correção (PERFIL-09, 2026-09-22):** a versão original desta tabela incluía
> `ContaBancariaController` na linha de `perm_medicos` — checando o código na hora de aplicar a
> permissão, essa entidade é `@ManyToOne Empresa`, ou seja, contas bancárias da **empresa** (Pin),
> não do médico (os dados bancários do médico já são os 4 endpoints `/{id}/dados-bancarios*`
> dentro do próprio `MedicoController`, já contados nos 28). `ContaBancariaController` não foi
> tocado em PERFIL-09; fica sinalizado para `perm_empresas` (PERFIL-10) — que hoje também não o
> reivindica explicitamente — decidir se o inclui.

Contagens conferidas via `Grep '@PreAuthorize' services/` (2026-09-22) contra o código real —
`TomadorController` (48), `MedicoController` (28) e os demais valores acima batem exatamente
com o que a task original estimava, exceto `perm_conciliacao`/`perm_caixa` (ver decisão
específica abaixo) e `perm_usuarios`/`perm_repasses`, que a task original deixou sem número —
preenchidos aqui a partir do grep real (3 e 3, respectivamente).

---

## Decisões específicas resolvidas nesta task

### `perm_caixa` é separável de `perm_conciliacao` — mesmo controller, métodos diferentes

`@PreAuthorize` no projeto é aplicado por método, não só por classe — `ConciliacaoController`
já tem 9 anotações individuais (uma por endpoint), nenhuma delas a nível de classe. Não há
nenhuma barreira técnica para dar a 8 delas `perm_conciliacao` e à nona (`GET
/api/conciliacao/posicao-caixa`) `perm_caixa` — a separação é uma decisão de dado (quais
métodos ganham qual role), não uma mudança estrutural do controller. Confirma a intenção
original do Sidebar, que já trata "Upload Extrato"/"Conciliação" e "Posição de Caixa" como
3 itens de menu distintos (2 perms, não 3, porque Upload Extrato e Conciliação sempre andam
juntos no fluxo do operador).

### `perm_repasses` — criar agora, mesmo com o serviço `repasse` ainda stub

`RepasseController` tem hoje 3 endpoints (`GET /`, `/worklist`, `/historico`), todos
retornando dado stub sob um único `@PreAuthorize` de classe (`hasRole('financeiro') or
hasRole('gestao')`) — EPIC-09 (repasses de verdade) nunca foi implementado. Decisão: criar a
role `perm_repasses` agora mesmo assim, porque:
- PERFIL-02 (criação das roles no Keycloak) e todas as tasks de backend (PERFIL-09 a 21)
  dependem do catálogo estar **fechado** nesta task — não faz sentido reabrir o catálogo só
  quando o EPIC-09 for implementado.
- O custo de uma role Keycloak não usada por enquanto é zero.
- Quando o serviço for implementado de verdade, os métodos reais herdam a role já existente —
  mesmo padrão aditivo já usado no projeto para toda a stack Clicksign/NFS-e mock
  (`MockClicksignAdapter`/`MockEmissaoNfseAdapter`): construir o contrato (aqui, a role) antes
  da implementação real existir, sem custo de retrabalho depois.

---

## Fora do catálogo

Ficam de fora desta lista — continuam usando **apenas** os 5 papéis legados do ADR-002, sem
nenhuma `perm_*` nova:

- **Dashboard** (`/`) — agrega dados de outras telas, sem operação sensível própria.
- **Portal do Médico** (4 telas, role `medico`) — não é um perfil customizável por este
  projeto; os perfis existentes (inclusive `medico`) permanecem inalterados.
- **`FaturamentoController` (`/api/faturamento`) e `OnboardingController`
  (`/api/onboarding`)** — stubs genéricos de um único endpoint cada, usados só por
  `RbacIntegrationTest`/testes de smoke de segurança; não correspondem a nenhuma tela real do
  Sidebar.
- **`ServicoController` (`/api/servicos`)** — catálogo de referência LC116, somente leitura,
  consumido como lookup por formulários de várias telas (Tomadores, Produção, Frequências).
  Sem tela própria — mantém `hasAnyRole('operacao','gestao','financeiro','contabil','medico')`
  como está.
- **`PortalMedicoController`** (11 `@PreAuthorize`) — já coberto pela exclusão do Portal do
  Médico acima.

---

## Convenção de nomenclatura

- `perm_<domínio>`, sempre minúsculo, sem acento — mesmo vocabulário da rota/tela (ex.:
  `perm_tomadores`, não `perm_tomador`; `perm_notas_lote`, não `perm_lotes_nfse`).
- Realm role **simples** no Keycloak, nunca composta — quem agrupa várias `perm_*` como
  filhas é o papel do *perfil customizado* em si (PERFIL-04), não a `perm_*` individual.
- Aplicação nos controllers é sempre **aditiva**: `hasRole('<papel_legado>') or
  hasRole('perm_x')` — nunca remove o acesso que os 5 papéis legados (ADR-002) já têm hoje.

---

## Consequências

- Desbloqueia PERFIL-02 (criação das 15 roles nos 3 realms Keycloak + `realm-export.json`) e
  todas as tasks de aplicação por controller (PERFIL-09 a 21).
- `perm_conciliacao`/`perm_caixa` exigem editar `@PreAuthorize` método a método dentro de
  `ConciliacaoController` (não dá pra usar uma anotação única de classe) — sinalizado para
  quem for implementar essa task específica.
- `perm_repasses` protege hoje só o stub de `RepasseController` — sem efeito prático até o
  EPIC-09 ser implementado, mas já desbloqueia o catálogo/Keycloak sem retrabalho futuro.
- Qualquer tela nova adicionada ao Sidebar no futuro deve avaliar se precisa de uma `perm_*`
  própria seguindo esta mesma convenção — este documento é a referência viva do catálogo até
  a próxima revisão formal.
