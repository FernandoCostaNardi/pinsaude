import { useCallback, useEffect, useState, ReactNode } from 'react'
import {
  TrendingUp, TrendingDown, DollarSign, RefreshCw,
  Download, Printer, FileDown, ArrowUpRight, ArrowDownRight,
  Receipt,
} from 'lucide-react'
import { Spinner, Alert } from '@pinsaude/ui'
import { portalApi, ExtratoPortal, ExtratoLancamento } from '../api/portalApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
}

function formatDatetime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function generateMonths(): { value: string; label: string }[] {
  const months: { value: string; label: string }[] = []
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  const now = new Date()
  for (let i = 0; i < 24; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    months.push({ value, label: `${meses[d.getMonth()]}/${d.getFullYear()}` })
  }
  return months
}

const MONTHS = generateMonths()

// ─── Categoria badge ─────────────────────────────────────────────────────────

const CAT_CFG: Record<string, { label: string; cls: string }> = {
  NFS_E:    { label: 'NFS-e',       cls: 'bg-green-50 text-green-700'   },
  ISS:      { label: 'ISS',         cls: 'bg-orange-50 text-orange-700' },
  IR:       { label: 'IR',          cls: 'bg-red-50 text-red-700'       },
  CSLL:     { label: 'CSLL',        cls: 'bg-red-50 text-red-700'       },
  PIS:      { label: 'PIS',         cls: 'bg-red-50 text-red-700'       },
  COFINS:   { label: 'COFINS',      cls: 'bg-red-50 text-red-700'       },
  TAXA_PIN: { label: 'Taxa Pin',    cls: 'bg-purple-50 text-purple-700' },
}

function CategoriaBadge({ cat }: { cat: string }) {
  const { label, cls } = CAT_CFG[cat] ?? { label: cat, cls: 'bg-gray-100 text-gray-600' }
  return (
    <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-bold ${cls}`}>{label}</span>
  )
}

// ─── Export helpers ───────────────────────────────────────────────────────────

function exportCSV(extrato: ExtratoPortal, periodo: string) {
  const cabecalho = ['Data/Hora', 'Descrição', 'Categoria', 'Competência', 'Referência',
    'Crédito (R$)', 'Débito (R$)', 'Saldo Após (R$)']
  const linhas = extrato.lancamentos.map((l) => [
    formatDatetime(l.dataRef),
    l.descricao,
    CAT_CFG[l.categoria]?.label ?? l.categoria,
    l.competencia,
    l.referencia,
    l.tipo === 'CREDITO' ? (l.valor / 100).toFixed(2).replace('.', ',') : '',
    l.tipo === 'DEBITO'  ? (l.valor / 100).toFixed(2).replace('.', ',') : '',
    (l.saldoApos / 100).toFixed(2).replace('.', ','),
  ])
  const csv = [cabecalho, ...linhas].map((r) => r.map((c) => `"${c}"`).join(';')).join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `extrato-financeiro${periodo ? '-' + periodo : ''}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function exportPDF(extrato: ExtratoPortal, periodo: string) {
  const logoUrl = `${window.location.origin}/logo-pinsaude.png`
  const now = new Date().toLocaleDateString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })

  const rows = [...extrato.lancamentos].reverse().map((l) => {
    const isCredito = l.tipo === 'CREDITO'
    return `
      <tr>
        <td>${formatDate(l.dataRef)}</td>
        <td class="desc">${l.descricao}</td>
        <td class="cat">${CAT_CFG[l.categoria]?.label ?? l.categoria}</td>
        <td class="num credito">${isCredito ? formatBRL(l.valor) : ''}</td>
        <td class="num debito">${!isCredito ? formatBRL(l.valor) : ''}</td>
        <td class="num saldo ${l.saldoApos >= 0 ? 'pos' : 'neg'}">${formatBRL(l.saldoApos)}</td>
      </tr>`
  }).join('')

  const html = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <title>Extrato Financeiro — Pin Saúde</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 11px; color: #333; padding: 20px; }
    .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; border-bottom: 2px solid #02A9F7; padding-bottom: 12px; }
    .logo { height: 36px; }
    .title { text-align: right; }
    .title h1 { font-size: 16px; font-weight: 700; color: #0069A0; }
    .title p { font-size: 10px; color: #888; margin-top: 2px; }
    .summary { display: flex; gap: 12px; margin-bottom: 18px; }
    .card { flex: 1; border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px 12px; }
    .card .lbl { font-size: 9px; text-transform: uppercase; font-weight: 700; color: #888; letter-spacing: .04em; }
    .card .val { font-size: 13px; font-weight: 700; margin-top: 2px; }
    .card.verde .val { color: #15803d; }
    .card.vermelho .val { color: #dc2626; }
    .card.azul .val { color: #0069A0; }
    .card.roxo .val { color: #7c3aed; }
    table { width: 100%; border-collapse: collapse; font-size: 10px; }
    th { background: #f3f4f6; font-size: 9px; text-transform: uppercase; font-weight: 700; color: #6b7280; letter-spacing: .05em; padding: 6px 8px; text-align: left; border-bottom: 1px solid #e5e7eb; }
    td { padding: 5px 8px; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    td.num { text-align: right; font-variant-numeric: tabular-nums; }
    td.desc { color: #374151; max-width: 220px; }
    td.cat { white-space: nowrap; }
    td.credito { color: #15803d; font-weight: 600; }
    td.debito  { color: #dc2626; }
    td.saldo.pos { color: #0069A0; font-weight: 700; }
    td.saldo.neg { color: #dc2626; font-weight: 700; }
    tr:last-child td { border-bottom: none; }
    .footer { margin-top: 14px; font-size: 9px; color: #9ca3af; text-align: right; }
    @media print { body { padding: 0; } @page { margin: 12mm; size: A4 portrait; } }
  </style>
</head>
<body>
  <div class="header">
    <img src="${logoUrl}" alt="Pin Saúde" class="logo">
    <div class="title">
      <h1>Extrato Financeiro</h1>
      <p>Período: ${periodo || 'Todos os lançamentos'}</p>
      <p>Emitido em: ${now}</p>
    </div>
  </div>
  <div class="summary">
    <div class="card verde">
      <div class="lbl">Total Créditos</div>
      <div class="val">${formatBRL(extrato.totalCreditos)}</div>
    </div>
    <div class="card vermelho">
      <div class="lbl">Retenções</div>
      <div class="val">${formatBRL(extrato.totalRetencoes)}</div>
    </div>
    <div class="card roxo">
      <div class="lbl">Taxa Pin (15%)</div>
      <div class="val">${formatBRL(extrato.totalTaxaPin)}</div>
    </div>
    <div class="card azul">
      <div class="lbl">Saldo do Período</div>
      <div class="val">${formatBRL(extrato.saldoPeriodo)}</div>
    </div>
  </div>
  <table>
    <thead>
      <tr>
        <th>Data</th>
        <th>Descrição</th>
        <th>Categoria</th>
        <th style="text-align:right">Crédito</th>
        <th style="text-align:right">Débito</th>
        <th style="text-align:right">Saldo</th>
      </tr>
    </thead>
    <tbody>${rows}</tbody>
  </table>
  <div class="footer">Pin Saúde — Sistema de Gestão Médica • Total de ${extrato.lancamentos.length} lançamentos</div>
</body>
</html>`

  const w = window.open('', '_blank', 'width=900,height=1100')
  if (!w) return
  w.document.write(html)
  w.document.close()
  w.focus()
  let printed = false
  const doPrint = () => {
    if (printed) return
    printed = true
    clearInterval(poll)
    setTimeout(() => w.print(), 300)
  }
  const poll = setInterval(() => {
    if (w.document.readyState === 'complete') doPrint()
  }, 50)
  setTimeout(doPrint, 2000)
}

// ─── Componente principal ────────────────────────────────────────────────────

export function ExtratoPage() {
  const [extrato, setExtrato]     = useState<ExtratoPortal | null>(null)
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState<string | null>(null)

  // Filtro por período: mês selecionado (YYYY-MM) ou intervalo livre
  const [filtroModo, setFiltroModo] = useState<'mes' | 'periodo'>('mes')
  const [filtroMes,  setFiltroMes]  = useState(MONTHS[0].value) // mês atual
  const [dtInicio,   setDtInicio]   = useState('')
  const [dtFim,      setDtFim]      = useState('')

  const periodoLabel = (): string => {
    if (filtroModo === 'mes') {
      const m = MONTHS.find(m => m.value === filtroMes)
      return m?.label ?? filtroMes
    }
    const parts = []
    if (dtInicio) parts.push(new Date(dtInicio + 'T00:00:00').toLocaleDateString('pt-BR'))
    if (dtFim)    parts.push(new Date(dtFim    + 'T00:00:00').toLocaleDateString('pt-BR'))
    return parts.length === 2 ? `${parts[0]} a ${parts[1]}` : parts[0] ?? 'Período'
  }

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      let inicio: string | undefined
      let fim: string | undefined
      if (filtroModo === 'mes' && filtroMes) {
        const [ano, mes] = filtroMes.split('-').map(Number)
        inicio = `${ano}-${String(mes).padStart(2, '0')}-01`
        const ultimo = new Date(ano, mes, 0).getDate()
        fim = `${ano}-${String(mes).padStart(2, '0')}-${ultimo}`
      } else {
        inicio = dtInicio || undefined
        fim    = dtFim    || undefined
      }
      const data = await portalApi.getExtrato({ dtInicio: inicio, dtFim: fim })
      setExtrato(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar extrato')
    } finally {
      setLoading(false)
    }
  }, [filtroModo, filtroMes, dtInicio, dtFim])

  useEffect(() => { carregar() }, [carregar])

  const temDados = extrato && extrato.lancamentos.length > 0

  return (
    <div className="flex flex-col h-full -m-6">
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="px-6 pt-6 pb-4 bg-white border-b border-ds-border shrink-0">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-xl font-black text-ds-text">Extrato Financeiro</h1>
            <p className="text-sm text-ds-light mt-0.5">
              Lançamentos derivados das NFS-e emitidas em seu nome
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={() => extrato && exportCSV(extrato, periodoLabel())}
              disabled={!temDados}
              title="Exportar CSV"
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors"
            >
              <Download size={13} /> CSV
            </button>
            <button
              onClick={() => extrato && exportPDF(extrato, periodoLabel())}
              disabled={!temDados}
              title="Imprimir / Exportar PDF"
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors"
            >
              <Printer size={13} /> PDF
            </button>
            <button
              onClick={carregar}
              disabled={loading}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors"
            >
              <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
              Atualizar
            </button>
          </div>
        </div>

        {/* Filtros */}
        <div className="mt-3 flex flex-wrap items-center gap-3">
          {/* Toggle modo filtro */}
          <div className="flex rounded-lg border border-ds-border overflow-hidden text-xs">
            <button
              onClick={() => setFiltroModo('mes')}
              className={`px-3 py-1.5 font-medium transition-colors ${
                filtroModo === 'mes'
                  ? 'bg-primary text-white'
                  : 'bg-white text-ds-mid hover:bg-ds-input'
              }`}
            >
              Por mês
            </button>
            <button
              onClick={() => setFiltroModo('periodo')}
              className={`px-3 py-1.5 font-medium transition-colors ${
                filtroModo === 'periodo'
                  ? 'bg-primary text-white'
                  : 'bg-white text-ds-mid hover:bg-ds-input'
              }`}
            >
              Período livre
            </button>
          </div>

          {filtroModo === 'mes' ? (
            <select
              value={filtroMes}
              onChange={e => setFiltroMes(e.target.value)}
              className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white"
            >
              {MONTHS.map(m => (
                <option key={m.value} value={m.value}>{m.label}</option>
              ))}
            </select>
          ) : (
            <div className="flex items-center gap-2">
              <input
                type="date"
                value={dtInicio}
                onChange={e => setDtInicio(e.target.value)}
                className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <span className="text-xs text-ds-light">até</span>
              <input
                type="date"
                value={dtFim}
                onChange={e => setDtFim(e.target.value)}
                className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>
          )}
        </div>

        {/* KPI cards */}
        {extrato && (
          <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3">
            <KpiCard
              label="Total Créditos"
              value={extrato.totalCreditos}
              icon={<TrendingUp size={16} />}
              cls="text-green-700"
              bg="bg-green-50"
            />
            <KpiCard
              label="Retenções Fiscais"
              value={extrato.totalRetencoes}
              icon={<TrendingDown size={16} />}
              cls="text-red-700"
              bg="bg-red-50"
            />
            <KpiCard
              label="Taxa Pin Saúde"
              value={extrato.totalTaxaPin}
              icon={<FileDown size={16} />}
              cls="text-purple-700"
              bg="bg-purple-50"
            />
            <KpiCard
              label="Saldo do Período"
              value={extrato.saldoPeriodo}
              icon={<DollarSign size={16} />}
              cls={extrato.saldoPeriodo >= 0 ? 'text-primary-700' : 'text-red-700'}
              bg={extrato.saldoPeriodo >= 0 ? 'bg-primary-50' : 'bg-red-50'}
              destaque
            />
          </div>
        )}
      </div>

      {/* ── Body — tabela de lançamentos ────────────────────────────────── */}
      <div className="flex-1 overflow-auto p-6">
        {error && (
          <Alert variant="error" onClose={() => setError(null)} className="mb-4">
            {error}
          </Alert>
        )}

        {loading ? (
          <div className="flex items-center justify-center h-48">
            <Spinner size="lg" />
          </div>
        ) : !temDados ? (
          <div className="flex flex-col items-center justify-center h-48 text-ds-light">
            <Receipt size={36} className="mb-3 opacity-25" />
            <p className="text-sm font-semibold">Nenhum lançamento encontrado</p>
            <p className="text-xs mt-1">
              Lançamentos aparecem quando NFS-e são emitidas em seu nome
            </p>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-ds-border bg-white shadow-sm">
              <table className="w-full text-sm min-w-[680px]">
                <thead>
                  <tr className="bg-ds-surface border-b border-ds-border">
                    {[
                      { label: 'Data',          cls: 'text-left'  },
                      { label: 'Descrição',      cls: 'text-left'  },
                      { label: 'Cat.',           cls: 'text-center hidden sm:table-cell' },
                      { label: 'Crédito',        cls: 'text-right text-green-700' },
                      { label: 'Débito',         cls: 'text-right text-red-700'   },
                      { label: 'Saldo',          cls: 'text-right' },
                    ].map(({ label, cls }) => (
                      <th key={label}
                        className={`px-4 py-3 text-[10px] font-bold uppercase tracking-wider text-ds-light ${cls}`}
                      >
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-ds-border">
                  {extrato.lancamentos.map((l, i) => (
                    <LancamentoRow key={i} lancamento={l} />
                  ))}
                </tbody>
                {/* Rodapé com totais */}
                <tfoot className="border-t-2 border-ds-border">
                  <tr className="bg-ds-surface">
                    <td colSpan={3}
                      className="px-4 py-3 text-xs font-bold text-ds-text">
                      Total do período ({extrato.lancamentos.length} lançamentos)
                    </td>
                    <td className="px-4 py-3 text-right text-xs font-bold tabular-nums text-green-700">
                      {formatBRL(extrato.totalCreditos)}
                    </td>
                    <td className="px-4 py-3 text-right text-xs font-bold tabular-nums text-red-700">
                      ({formatBRL(extrato.totalDebitos)})
                    </td>
                    <td className={`px-4 py-3 text-right text-xs font-black tabular-nums ${
                      extrato.saldoPeriodo >= 0 ? 'text-primary-700' : 'text-red-700'
                    }`}>
                      {formatBRL(extrato.saldoPeriodo)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>

            <p className="mt-3 text-[11px] text-ds-light text-center">
              Cada NFS-e emitida gera um crédito (valor bruto) e débitos por cada tributo retido + taxa Pin (15%).
              O saldo = créditos − débitos. Tributos são custo fiscal da Pin Saúde.
            </p>
          </>
        )}
      </div>
    </div>
  )
}

// ─── Sub-componentes ─────────────────────────────────────────────────────────

function KpiCard({ label, value, icon, cls, bg, destaque }: {
  label: string; value: number; icon: ReactNode
  cls: string; bg: string; destaque?: boolean
}) {
  return (
    <div className={`rounded-xl border border-ds-border p-3 ${destaque ? 'ring-1 ring-primary/20' : ''}`}>
      <div className="flex items-center gap-2 mb-1">
        <span className={`p-1 rounded-md ${bg} ${cls}`}>{icon}</span>
        <p className="text-[10px] font-bold text-ds-light uppercase tracking-wide">{label}</p>
      </div>
      <p className={`text-base font-black tabular-nums ${cls}`}>
        {formatBRL(value)}
      </p>
    </div>
  )
}

function LancamentoRow({ lancamento: l }: { lancamento: ExtratoLancamento }) {
  const isCredito = l.tipo === 'CREDITO'

  return (
    <tr className={`hover:bg-ds-surface/50 transition-colors ${
      isCredito ? 'bg-green-50/30' : ''
    }`}>
      {/* Data */}
      <td className="px-4 py-2.5 whitespace-nowrap">
        <p className="text-xs font-semibold text-ds-text">{formatDate(l.dataRef)}</p>
        <p className="text-[10px] text-ds-light">{l.competencia}</p>
      </td>

      {/* Descrição + ícone tipo */}
      <td className="px-4 py-2.5">
        <div className="flex items-center gap-2">
          <span className={`shrink-0 p-0.5 rounded ${
            isCredito ? 'text-green-600' : 'text-red-400'
          }`}>
            {isCredito
              ? <ArrowUpRight size={13} />
              : <ArrowDownRight size={13} />}
          </span>
          <span className="text-xs text-ds-text leading-snug">{l.descricao}</span>
        </div>
      </td>

      {/* Categoria */}
      <td className="px-4 py-2.5 text-center hidden sm:table-cell">
        <CategoriaBadge cat={l.categoria} />
      </td>

      {/* Crédito */}
      <td className="px-4 py-2.5 text-right">
        {isCredito ? (
          <span className="text-xs font-bold tabular-nums text-green-700">
            + {formatBRL(l.valor)}
          </span>
        ) : (
          <span className="text-xs text-ds-light">—</span>
        )}
      </td>

      {/* Débito */}
      <td className="px-4 py-2.5 text-right">
        {!isCredito ? (
          <span className="text-xs tabular-nums text-red-600">
            ({formatBRL(l.valor)})
          </span>
        ) : (
          <span className="text-xs text-ds-light">—</span>
        )}
      </td>

      {/* Saldo após */}
      <td className="px-4 py-2.5 text-right">
        <span className={`text-xs font-bold tabular-nums ${
          l.saldoApos >= 0 ? 'text-primary-700' : 'text-red-700'
        }`}>
          {formatBRL(l.saldoApos)}
        </span>
      </td>
    </tr>
  )
}
