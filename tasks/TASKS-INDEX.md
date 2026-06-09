# Pin Saúde — Índice de Tasks do MVP

> Gerado a partir do PRD v1.1 final e dos ADRs de 2026-06-08.
> Stack: Java 17 / Spring Boot · React + TypeScript · PostgreSQL · RabbitMQ · Playwright

---

## Estrutura dos Arquivos

| Arquivo | Épico | Módulo |
|---|---|---|
| [00-setup-infraestrutura.md](./00-setup-infraestrutura.md) | EPIC-00 | Monorepo, CI/CD, banco, broker, IdP, secrets |
| [01-autenticacao-multitenancy.md](./01-autenticacao-multitenancy.md) | EPIC-01 | Auth, RBAC, MFA, isolamento por CNPJ |
| [02-cadastros.md](./02-cadastros.md) | EPIC-02 | Empresa, Médico, Tomador, Serviço |
| [03-onboarding.md](./03-onboarding.md) | EPIC-03 | Convite → KYC → Clicksign → Ativação |
| [04-motor-fiscal.md](./04-motor-fiscal.md) | EPIC-04 | Equiparação, destaque, alíquotas versionadas |
| [05-emissao-nfse.md](./05-emissao-nfse.md) | EPIC-05 | NFS-e, agregador, fila, Conta Azul |
| [06-ledger.md](./06-ledger.md) | EPIC-06 | Partidas dobradas, extrato, saldo |
| [07-recebimento-conciliacao.md](./07-recebimento-conciliacao.md) | EPIC-07 | Importação extrato, matching, baixa |
| [08-repasse.md](./08-repasse.md) | EPIC-08 | Cálculo 85%, PIX manual, aprovação |
| [09-gestao-apuracao.md](./09-gestao-apuracao.md) | EPIC-09 | Apuração LP, DRE, teto, caixa, calendário |
| [10-portal-medico.md](./10-portal-medico.md) | EPIC-10 | Dashboard, produção, extrato (React) |
| [11-backoffice.md](./11-backoffice.md) | EPIC-11 | Filas, conciliação, painéis (React) |
| [12-transversais.md](./12-transversais.md) | EPIC-12 | Benefícios, e-mail, auditoria, LGPD |

---

## Convenções de Prioridade

| Label | Significado |
|---|---|
| `P0` | Bloqueante — outros épicos dependem disto |
| `P1` | Core MVP — sem isso não há produto |
| `P2` | Importante — degrada muito sem ter |
| `P3` | Desejável no MVP |
| `P4` | Fase 2 (documentado aqui apenas como referência) |

---

## Dependências entre Épicos

```
EPIC-00 (setup)
  └─► EPIC-01 (auth/tenant)
        └─► EPIC-02 (cadastros)
              ├─► EPIC-03 (onboarding)
              ├─► EPIC-04 (motor fiscal)  ──────────────┐
              │     └─► EPIC-05 (emissão NFS-e)          │
              │           └─► EPIC-06 (ledger) ◄─────────┤
              │                 └─► EPIC-07 (recebimento) │
              │                       └─► EPIC-08 (repasse)
              └─► EPIC-09 (gestão/apuração) ◄── eventos de EPIC-05/06/07/08
EPIC-10 (portal médico) ◄── EPIC-02, 03, 05, 06, 08
EPIC-11 (backoffice)    ◄── todos os serviços
EPIC-12 (transversais)  ◄── paralelo a tudo
```

---

## Resumo de Tasks por Épico

| Épico | Qtd Tasks | Prioridade Máxima |
|---|---|---|
| EPIC-00 Setup | 7 | P0 |
| EPIC-01 Auth | 5 | P0 |
| EPIC-02 Cadastros | 4 | P1 |
| EPIC-03 Onboarding | 6 | P1 |
| EPIC-04 Motor Fiscal | 5 | P0 |
| EPIC-05 Emissão NFS-e | 7 | P1 |
| EPIC-06 Ledger | 3 | P0 |
| EPIC-07 Recebimento | 3 | P1 |
| EPIC-08 Repasse | 4 | P1 |
| EPIC-09 Gestão/Apuração | 6 | P1 |
| EPIC-10 Portal Médico | 5 | P1 |
| EPIC-11 Backoffice | 4 | P2 |
| EPIC-12 Transversais | 3 | P2 |
| **Total** | **62** | |
