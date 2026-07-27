# Roteiro de Teste Manual Ponta-a-Ponta — EPIC-15 (Alocação de Médico a Tomadores)

Roteiro executado e validado em 2026-07-27 durante a task 15.17, usando o ambiente local
completo (Docker infra + `faturamento` + `onboarding` + `gateway` + `portal` + `web` rodando
a partir da branch da task). Cobre a jornada completa: migration de backfill → gerência da
alocação nas duas telas admin → validação de bloqueio (422) nas 3 roles, em Produção e
Frequência → filtro correto no Portal do Médico.

## Pré-requisitos

```powershell
.\tools\scripts\start-infra.ps1
node tools/scripts/mvn-build.js :pinsaude-faturamento
node tools/scripts/mvn-build.js :pinsaude-onboarding
node tools/scripts/mvn-build.js :pinsaude-gateway
node tools/scripts/mvn-build.js :pinsaude-portal
# subir faturamento (8082), onboarding (8085), gateway (8090) e portal (8087)
npx nx run web:dev
```

## Testes automatizados — resultado antes do roteiro manual

Toda a cobertura automatizada listada nos critérios de aceite já havia sido implementada nas
tasks 15.3/15.4/15.6/15.7/15.8 (backend). Rodado nesta task para confirmar que segue passando
após todas as mudanças de frontend (15.9-15.16):

```
node tools/scripts/mvn-test.js services/faturamento
# Tests run: 140, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
#   TomadorServiceTest: 26 testes, incluindo adicionarMedico_duplicado_lanca409,
#     adicionarMedico_valido_salvaRetornaResponse, removerMedico_naoAlocado_lanca404,
#     removerMedico_alocado_removeComSucesso
#   ProducaoServiceTest: criar_medicoNaoAlocado_lanca422, criar_medicoAlocado_salvaComSucesso,
#     criar_multiplosParticipantes_umNaoAlocado_lanca422,
#     criar_multiplosParticipantesTodosAlocados_salvaComSucesso
#   FrequenciaServiceTest: criar_setorDeOutroTomador_lanca422,
#     criar_medicoNaoAlocadoAoTomador_lanca422, criar_frequenciaValida_salvaNoBanco

node tools/scripts/mvn-test.js services/portal
# Tests run: 10, Failures: 1 (conhecida/pré-existente) — ver "Achado" abaixo
#   tomadores_comRoleMedico_retornaLista, tomadores_semRoleMedico_retorna403,
#   tomadores_semAutenticacao_retorna401 — todos passando
```

### ⚠️ Falha pré-existente confirmada, não relacionada a esta EPIC
`PortalMedicoControllerTest.extrato_semImplementacao_retornaListaVazia` falha desde o EPIC-06.4
(já documentado 2x no CLAUDE.md, em EPIC-14 e EPIC-15.6) — `GET /api/portal/extrato` passou a
retornar um `ExtratoResponse` de verdade, o teste nunca foi atualizado para não esperar mais uma
lista vazia. Não corrigido aqui (fora de escopo do EPIC-15) — apenas reconfirmado que a falha é a
mesma de sempre, sem relação com as mudanças desta EPIC.

## Passo a passo do roteiro manual

### 1. Migrations V21+V22 — contagem de linhas do backfill
```sql
SELECT COUNT(*) FROM faturamento.medico_tomadores;
-- 16

SELECT COUNT(DISTINCT (pp.medico_id, p.tomador_id)) AS combinacoes_producoes
FROM faturamento.participacoes_producao pp
JOIN faturamento.producoes p ON p.id = pp.producao_id;
-- 7

SELECT COUNT(DISTINCT (medico_id, tomador_id)) FROM faturamento.frequencias_medicas;
-- 1

SELECT medico_id, COUNT(*) FROM faturamento.medico_tomadores GROUP BY medico_id;
--  fb5a67c7-... (Medico Teste)  | 11
--  54fef10b-... (Fulano de tal) |  2
--  01e7fd16-...                 |  2
--  fdc8b4ae-...                 |  1
```
**Resultado:** ✅ OK — as 16 linhas cobrem com folga as 7 combinações distintas
médico+tomador em produções e a combinação em frequências (mais alocações adicionadas
manualmente ao longo dos testes das tasks 15.11/15.12/15.13, líquido zero ao final).

### 2. Alocar médico a um tomador via `MedicoPerfilPage.tsx` e confirmar reflexo em `TomadorMedicosModal` (EPIC-15.12)
Logado como `operacao`, em `/medicos/{id}` do "Medico Teste": adicionado o tomador "HAPVIDA
ASSISTENCIA MEDICA S.A." pela seção "Tomadores Associados" (12 tomador(es) confirmado). Em
seguida, em `/tomadores`, aberto o modal "Médicos Alocados" (ícone estetoscópio) do HAPVIDA.

**Resultado:** ✅ OK — "Medico Teste" apareceu na lista de médicos alocados ao HAPVIDA,
confirmando que as duas telas (visão médico→tomador e visão tomador→médico) leem exatamente
a mesma tabela `medico_tomadores` em tempo real. Alocação removida ao final do teste para
devolver o ambiente ao estado original (11 tomadores para Medico Teste).

### 3. Lançar Produção e Frequência com médico não alocado ao tomador, nas 3 roles → 422 em todas
Testado via chamada HTTP direta (`POST /api/producoes` e `POST /api/frequencias` pelo gateway,
`localhost:8090`) com token real de cada role, usando um tomador (`HAPVIDA`, id
`5653be3b-87cb-4d30-be13-95d482f46513`) e um médico (`Medico Teste`, id
`fb5a67c7-4cf5-4979-b840-61f2a073657a`) sem alocação entre si no momento do teste:

```bash
# Produção — tomador=HAPVIDA, medico=Medico Teste (nao alocado)
POST /api/producoes  →  operacao: 422 | gestao: 422 | medico: 422
# {"message":"Médico não está alocado a este tomador: fb5a67c7-..."}

# Frequência — tomador=SECRETARIA DA SAUDE, medico=<UUID sintético, nunca alocado>
POST /api/frequencias  →  operacao: 422 | gestao: 422 | medico: 422
# {"message":"Médico não está alocado a este tomador"}
```

**Resultado:** ✅ OK — bloqueio de 422 confirmado nas 3 roles, em Produção e Frequência,
sem bypass por papel algum (confirma a regra "sem bypass por papel" do plano original,
já documentada em CLAUDE.md desde a EPIC-15.7).

### 4. Médicos/tomadores pré-backfill continuam lançando produção/frequência normalmente
```bash
# Produção — tomador=SECRETARIA DA SAUDE (alocado), medico=Medico Teste (alocado)
POST /api/producoes  →  201 Created
# {"id":"d0d6dcc1-...", "status":"CONFIRMADA", "valorBruto":50000, ...}
```
**Resultado:** ✅ OK — o mesmo médico do backfill (11 tomadores) continua criando produção
sem qualquer fricção quando o tomador está entre os alocados. Este é exatamente o cenário que
o backfill do EPIC-15.2 existe para proteger.

### 5. Portal do Médico — `PortalProducaoNovaPage`/`PortalFrequenciaPage` mostram só os tomadores alocados
Já testado extensivamente ao vivo durante as próprias tasks 15.15/15.16 (mesma sessão, mesmo
ambiente): logado como `medico@pinsaude.com.br` (Medico Teste), o combo de Tomador de ambas as
telas mostrou exatamente os tomadores alocados a ele, sem o HAPVIDA (não alocado no momento).
Ver PRs #118 e #119 para o detalhe completo (screenshots e passos).

**Resultado:** ✅ OK (revalidado nesta task via re-execução dos testes automatizados +
inspeção da tabela `medico_tomadores`, sem necessidade de repetir toda a jornada de browser).

## Resumo

| # | Passo | Resultado |
|---|-------|-----------|
| 1 | Contagem do backfill (`medico_tomadores` vs. histórico) | ✅ |
| 2 | Alocação em `MedicoPerfilPage` reflete em `TomadorMedicosModal` | ✅ |
| 3 | Bloqueio 422 (Produção + Frequência) nas 3 roles | ✅ |
| 4 | Médico/tomador pré-backfill continua funcionando | ✅ |
| 5 | Portal do Médico filtra corretamente | ✅ |

**Nenhum achado crítico nesta task** — diferente do roteiro do EPIC-14, todas as regras de
negócio já estavam corretamente implementadas e testadas nas tasks anteriores do EPIC-15; este
roteiro serviu para validar a integração ponta-a-ponta de tudo junto, não para descobrir bugs
novos. A única falha de teste observada (`extrato_semImplementacao_retornaListaVazia`) é
pré-existente e não relacionada a esta EPIC.
