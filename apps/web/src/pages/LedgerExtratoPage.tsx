import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  BookOpen, Download, Printer, RefreshCw, Search, Plus, X,
  CheckCircle2, XCircle, ExternalLink, Loader2, AlertCircle, ShieldCheck,
} from 'lucide-react'
import { Modal, Input, Button, Alert, Spinner } from '@pinsaude/ui'
import { ledgerApi, ExtratoItem, ContaLedger, Ajuste, TipoOrigemLedger } from '../api/ledgerApi'
import { Medico, medicosApi } from '../api/medicosApi'
import { useAuth } from '../auth/useAuth'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatReais(v: number): string {
  return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(iso: string): string {
  return new Date(iso + 'T00:00:00').toLocaleDateString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
}

function maskBRL(centavos: number): string {
  if (!centavos) return ''
  return (centavos / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function parseBRL(str: string): number {
  return parseInt(str.replace(/\D/g, '') || '0', 10)
}

function currentCompetencia(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

const ORIGEM_CFG: Record<TipoOrigemLedger, { label: string; cls: string; rota?: string }> = {
  NOTA:        { label: 'NFS-e',        cls: 'bg-green-50 text-green-700',   rota: '/notas' },
  CONCILIACAO: { label: 'Conciliação',  cls: 'bg-blue-50 text-blue-700',     rota: '/conciliacao/assistida' },
  REPASSE:     { label: 'Repasse',      cls: 'bg-purple-50 text-purple-700', rota: '/repasses' },
  AJUSTE:      { label: 'Ajuste',       cls: 'bg-orange-50 text-orange-700' },
}

// ─── Export ────────────────────────────────────────────────────────────────────

function exportCSV(itens: ExtratoItem[], medicoNome: string) {
  const cab = ['Data', 'Descrição', 'Origem', 'Débito (R$)', 'Crédito (R$)', 'Saldo (R$)']
  const linhas = itens.map((l) => [
    formatDate(l.dataLancamento),
    l.descricao,
    ORIGEM_CFG[l.tipoOrigem].label,
    l.valor < 0 ? Math.abs(l.valor).toFixed(2).replace('.', ',') : '',
    l.valor > 0 ? l.valor.toFixed(2).replace('.', ',') : '',
    l.saldoApos.toFixed(2).replace('.', ','),
  ])
  const csv = [cab, ...linhas].map((r) => r.map((c) => `"${c}"`).join(';')).join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `extrato-ledger-${medicoNome.replace(/\s+/g, '-').toLowerCase()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function exportPDF(itens: ExtratoItem[], medicoNome: string, totais: { deb: number; cred: number; saldo: number }) {
  const logoUrl = `${window.location.origin}/logo-pinsaude.png`
  const now = new Date().toLocaleString('pt-BR')
  const rows = itens.map((l) => `
    <tr>
      <td>${formatDate(l.dataLancamento)}</td>
      <td class="desc">${l.descricao}</td>
      <td class="cat">${ORIGEM_CFG[l.tipoOrigem].label}</td>
      <td class="num debito">${l.valor < 0 ? formatReais(Math.abs(l.valor)) : ''}</td>
      <td class="num credito">${l.valor > 0 ? formatReais(l.valor) : ''}</td>
      <td class="num saldo ${l.saldoApos >= 0 ? 'pos' : 'neg'}">${formatReais(l.saldoApos)}</td>
    </tr>`).join('')

  const html = `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8">
    <title>Extrato do Ledger — ${medicoNome}</title>
    <style>
      *{box-sizing:border-box;margin:0;padding:0}
      body{font-family:'Segoe UI',Arial,sans-serif;font-size:11px;color:#333;padding:20px}
      .header{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;border-bottom:2px solid #02A9F7;padding-bottom:12px}
      .logo{height:36px}.title{text-align:right}
      .title h1{font-size:16px;font-weight:700;color:#0069A0}.title p{font-size:10px;color:#888;margin-top:2px}
      .summary{display:flex;gap:12px;margin-bottom:18px}
      .card{flex:1;border:1px solid #e5e7eb;border-radius:6px;padding:8px 12px}
      .card .lbl{font-size:9px;text-transform:uppercase;font-weight:700;color:#888}
      .card .val{font-size:13px;font-weight:700;margin-top:2px}
      .card.vermelho .val{color:#dc2626}.card.verde .val{color:#15803d}.card.azul .val{color:#0069A0}
      table{width:100%;border-collapse:collapse;font-size:10px}
      th{background:#f3f4f6;font-size:9px;text-transform:uppercase;font-weight:700;color:#6b7280;padding:6px 8px;text-align:left;border-bottom:1px solid #e5e7eb}
      td{padding:5px 8px;border-bottom:1px solid #f3f4f6}
      td.num{text-align:right;font-variant-numeric:tabular-nums}
      td.credito{color:#15803d;font-weight:600}td.debito{color:#dc2626}
      td.saldo.pos{color:#0069A0;font-weight:700}td.saldo.neg{color:#dc2626;font-weight:700}
      @media print{body{padding:0}@page{margin:12mm;size:A4 portrait}}
    </style></head><body>
    <div class="header"><img src="${logoUrl}" class="logo" alt="Pin Saúde">
      <div class="title"><h1>Extrato do Ledger</h1><p>Médico: ${medicoNome}</p><p>Emitido em: ${now}</p></div></div>
    <div class="summary">
      <div class="card vermelho"><div class="lbl">Total Débitos</div><div class="val">${formatReais(totais.deb)}</div></div>
      <div class="card verde"><div class="lbl">Total Créditos</div><div class="val">${formatReais(totais.cred)}</div></div>
      <div class="card azul"><div class="lbl">Saldo do Período</div><div class="val">${formatReais(totais.saldo)}</div></div>
    </div>
    <table><thead><tr><th>Data</th><th>Descrição</th><th>Origem</th>
      <th style="text-align:right">Débito</th><th style="text-align:right">Crédito</th><th style="text-align:right">Saldo</th>
    </tr></thead><tbody>${rows}</tbody></table>
    <div style="margin-top:14px;font-size:9px;color:#9ca3af;text-align:right">Pin Saúde • ${itens.length} lançamentos</div>
    </body></html>`

  const w = window.open('', '_blank', 'width=900,height=1100')
  if (!w) return
  w.document.write(html); w.document.close(); w.focus()
  let printed = false
  const doPrint = () => { if (printed) return; printed = true; clearInterval(poll); setTimeout(() => w.print(), 300) }
  const poll = setInterval(() => { if (w.document.readyState === 'complete') doPrint() }, 50)
  setTimeout(doPrint, 2000)
}

// ─── Página ────────────────────────────────────────────────────────────────────

export function LedgerExtratoPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const roles = user?.realm_access?.roles ?? []
  const temAcesso = roles.some((r) => ['financeiro', 'gestao', 'contabil'].includes(r))

  const [medicos, setMedicos] = useState<Medico[]>([])
  const [medicoSel, setMedicoSel] = useState<Medico | null>(null)
  const [buscaMedico, setBuscaMedico] = useState('')
  const [dropdownAberto, setDropdownAberto] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  const [extrato, setExtrato] = useState<ExtratoItem[]>([])
  const [loading, setLoading] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const [dtInicio, setDtInicio] = useState('')
  const [dtFim, setDtFim] = useState('')
  const [filtroOrigem, setFiltroOrigem] = useState<TipoOrigemLedger | ''>('')

  const [contas, setContas] = useState<ContaLedger[]>([])
  const [ajustesPendentes, setAjustesPendentes] = useState<Ajuste[]>([])
  const [modalAberto, setModalAberto] = useState(false)

  // ─── Carregamentos ─────────────────────────────────────────────────────────

  useEffect(() => {
    if (!temAcesso) return
    medicosApi.listar(0, 1000, 'ATIVO').then((r) => setMedicos(r.content)).catch(() => setMedicos([]))
    ledgerApi.listarContas().then(setContas).catch(() => setContas([]))
    carregarPendentes()
  }, [temAcesso, carregarPendentes])

  const carregarPendentes = useCallback(() => {
    ledgerApi.listarAjustes('PENDENTE').then(setAjustesPendentes).catch(() => setAjustesPendentes([]))
  }, [])

  const carregarExtrato = useCallback(async (medicoId: string) => {
    setLoading(true); setErro(null)
    try {
      setExtrato(await ledgerApi.getExtrato(medicoId))
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Erro ao carregar extrato')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (medicoSel) carregarExtrato(medicoSel.id)
    else setExtrato([])
  }, [medicoSel, carregarExtrato])

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) setDropdownAberto(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  // ─── Filtro client-side ──────────────────────────────────────────────────────

  const filtrados = useMemo(() => extrato.filter((l) => {
    if (filtroOrigem && l.tipoOrigem !== filtroOrigem) return false
    if (dtInicio && l.dataLancamento < dtInicio) return false
    if (dtFim && l.dataLancamento > dtFim) return false
    return true
  }), [extrato, filtroOrigem, dtInicio, dtFim])

  const totais = useMemo(() => {
    let deb = 0, cred = 0
    for (const l of filtrados) {
      if (l.valor < 0) deb += Math.abs(l.valor)
      else cred += l.valor
    }
    const saldo = filtrados.length ? filtrados[filtrados.length - 1].saldoApos : 0
    return { deb, cred, saldo }
  }, [filtrados])

  const medicosFiltrados = medicos.filter((m) =>
    m.nome.toLowerCase().includes(buscaMedico.toLowerCase()) ||
    (m.crm ?? '').includes(buscaMedico),
  ).slice(0, 30)

  function abrirOrigem(l: ExtratoItem) {
    const rota = ORIGEM_CFG[l.tipoOrigem].rota
    if (rota) navigate(l.origemId ? `${rota}?origem=${l.origemId}` : rota)
  }

  if (!temAcesso) {
    return (
      <div className="flex-1 flex items-center justify-center text-ds-light">
        <div className="text-center">
          <XCircle size={40} className="mx-auto mb-2" />
          <p className="text-sm">Acesso restrito ao time financeiro.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full -m-6">
      {/* Header */}
      <div className="px-6 pt-6 pb-4 bg-white border-b border-ds-border shrink-0">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-2">
            <BookOpen size={20} className="text-primary" />
            <div>
              <h1 className="text-xl font-black text-ds-text">Extrato do Ledger</h1>
              <p className="text-sm text-ds-light mt-0.5">Razão contábil por médico — débitos, créditos e saldo</p>
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={() => setModalAberto(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-white text-xs font-semibold hover:bg-primary-600 transition-colors"
            >
              <Plus size={13} /> Lançamento de Ajuste
            </button>
            <button
              onClick={() => medicoSel && exportCSV(filtrados, medicoSel.nome)}
              disabled={!filtrados.length}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors"
            >
              <Download size={13} /> CSV
            </button>
            <button
              onClick={() => medicoSel && exportPDF(filtrados, medicoSel.nome, totais)}
              disabled={!filtrados.length}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors"
            >
              <Printer size={13} /> PDF
            </button>
            <button
              onClick={() => medicoSel && carregarExtrato(medicoSel.id)}
              disabled={loading || !medicoSel}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors"
            >
              <RefreshCw size={13} className={loading ? 'animate-spin' : ''} /> Atualizar
            </button>
          </div>
        </div>

        {/* Filtros */}
        <div className="mt-3 flex flex-wrap items-center gap-3">
          {/* Médico autocomplete */}
          <div ref={dropdownRef} className="relative w-72">
            <div className="relative">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ds-light" />
              <input
                value={medicoSel ? medicoSel.nome : buscaMedico}
                onChange={(e) => { setBuscaMedico(e.target.value); setMedicoSel(null); setDropdownAberto(true) }}
                onFocus={() => setDropdownAberto(true)}
                placeholder="Buscar médico..."
                className="w-full pl-8 pr-8 py-1.5 text-xs border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              {medicoSel && (
                <button onClick={() => { setMedicoSel(null); setBuscaMedico('') }}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-ds-light hover:text-ds-mid">
                  <X size={13} />
                </button>
              )}
            </div>
            {dropdownAberto && !medicoSel && medicosFiltrados.length > 0 && (
              <div className="absolute z-30 w-full mt-1 bg-white border border-ds-border rounded-lg shadow-lg max-h-56 overflow-y-auto">
                {medicosFiltrados.map((m) => (
                  <button key={m.id}
                    onMouseDown={(e) => { e.preventDefault(); setMedicoSel(m); setDropdownAberto(false); setBuscaMedico('') }}
                    className="w-full text-left px-3 py-2 hover:bg-primary-50 border-b border-ds-border last:border-0">
                    <p className="text-xs font-medium text-ds-text">{m.nome}</p>
                    <p className="text-[10px] text-ds-light">CRM {m.crm}{m.crmUf ? `-${m.crmUf}` : ''}</p>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Período */}
          <div className="flex items-center gap-2">
            <input type="date" value={dtInicio} onChange={(e) => setDtInicio(e.target.value)}
              className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30" />
            <span className="text-xs text-ds-light">até</span>
            <input type="date" value={dtFim} onChange={(e) => setDtFim(e.target.value)}
              className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>

          {/* Tipo origem */}
          <select value={filtroOrigem} onChange={(e) => setFiltroOrigem(e.target.value as TipoOrigemLedger | '')}
            className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white">
            <option value="">Todas as origens</option>
            <option value="NOTA">NFS-e</option>
            <option value="CONCILIACAO">Conciliação</option>
            <option value="REPASSE">Repasse</option>
            <option value="AJUSTE">Ajuste</option>
          </select>
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-auto p-6 space-y-4">
        {erro && <Alert variant="error" onClose={() => setErro(null)}>{erro}</Alert>}

        {/* Ajustes pendentes de aprovação */}
        {ajustesPendentes.length > 0 && (
          <AjustesPendentesPanel
            ajustes={ajustesPendentes}
            contas={contas}
            perfilAtual={roles.find((r) => ['financeiro', 'gestao', 'contabil'].includes(r)) ?? ''}
            userId={user?.sub ?? ''}
            onDecidido={() => { carregarPendentes(); if (medicoSel) carregarExtrato(medicoSel.id) }}
          />
        )}

        {!medicoSel ? (
          <div className="flex flex-col items-center justify-center h-48 text-ds-light">
            <Search size={36} className="mb-3 opacity-25" />
            <p className="text-sm font-semibold">Selecione um médico para ver o extrato contábil</p>
          </div>
        ) : loading ? (
          <div className="flex items-center justify-center h-48"><Spinner size="lg" /></div>
        ) : filtrados.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-48 text-ds-light">
            <BookOpen size={36} className="mb-3 opacity-25" />
            <p className="text-sm font-semibold">Nenhum lançamento no período</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-ds-border bg-white shadow-sm">
            <table className="w-full text-sm min-w-[760px]">
              <thead>
                <tr className="bg-ds-surface border-b border-ds-border">
                  {[
                    { label: 'Data', cls: 'text-left' },
                    { label: 'Descrição', cls: 'text-left' },
                    { label: 'Origem', cls: 'text-left' },
                    { label: 'Débito', cls: 'text-right text-red-700' },
                    { label: 'Crédito', cls: 'text-right text-green-700' },
                    { label: 'Saldo', cls: 'text-right' },
                  ].map(({ label, cls }) => (
                    <th key={label} className={`px-4 py-3 text-[10px] font-bold uppercase tracking-wider text-ds-light ${cls}`}>
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-ds-border">
                {filtrados.map((l) => {
                  const cfg = ORIGEM_CFG[l.tipoOrigem]
                  return (
                    <tr key={l.lancamentoId} className="hover:bg-ds-surface/50 transition-colors">
                      <td className="px-4 py-2.5 whitespace-nowrap">
                        <p className="text-xs font-semibold text-ds-text">{formatDate(l.dataLancamento)}</p>
                        <p className="text-[10px] text-ds-light">{l.competencia}</p>
                      </td>
                      <td className="px-4 py-2.5 text-xs text-ds-text max-w-[280px]">{l.descricao}</td>
                      <td className="px-4 py-2.5">
                        {cfg.rota ? (
                          <button onClick={() => abrirOrigem(l)}
                            className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold hover:underline ${cfg.cls}`}>
                            {cfg.label} <ExternalLink size={10} />
                          </button>
                        ) : (
                          <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-bold ${cfg.cls}`}>{cfg.label}</span>
                        )}
                      </td>
                      <td className="px-4 py-2.5 text-right text-xs tabular-nums text-red-600">
                        {l.valor < 0 ? `(${formatReais(Math.abs(l.valor))})` : '—'}
                      </td>
                      <td className="px-4 py-2.5 text-right text-xs font-bold tabular-nums text-green-700">
                        {l.valor > 0 ? formatReais(l.valor) : '—'}
                      </td>
                      <td className={`px-4 py-2.5 text-right text-xs font-bold tabular-nums ${l.saldoApos >= 0 ? 'text-primary-700' : 'text-red-700'}`}>
                        {formatReais(l.saldoApos)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
              <tfoot className="border-t-2 border-ds-border">
                <tr className="bg-ds-surface">
                  <td colSpan={3} className="px-4 py-3 text-xs font-bold text-ds-text">
                    Total do período ({filtrados.length} lançamentos)
                  </td>
                  <td className="px-4 py-3 text-right text-xs font-bold tabular-nums text-red-700">({formatReais(totais.deb)})</td>
                  <td className="px-4 py-3 text-right text-xs font-bold tabular-nums text-green-700">{formatReais(totais.cred)}</td>
                  <td className={`px-4 py-3 text-right text-xs font-black tabular-nums ${totais.saldo >= 0 ? 'text-primary-700' : 'text-red-700'}`}>
                    {formatReais(totais.saldo)}
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </div>

      {modalAberto && (
        <AjusteModal
          contas={contas}
          medicoSel={medicoSel}
          onClose={() => setModalAberto(false)}
          onCriado={() => { setModalAberto(false); carregarPendentes() }}
        />
      )}
    </div>
  )
}

// ─── Painel de ajustes pendentes (dupla aprovação) ─────────────────────────────

function AjustesPendentesPanel({ ajustes, contas, perfilAtual, userId, onDecidido }: {
  ajustes: Ajuste[]; contas: ContaLedger[]; perfilAtual: string; userId: string; onDecidido: () => void
}) {
  const [busyId, setBusyId] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const nomeConta = (codigo: string) => contas.find((c) => c.codigo === codigo)?.nome ?? codigo

  async function aprovar(id: string) {
    setBusyId(id); setErro(null)
    try {
      await ledgerApi.aprovarAjuste(id)
      onDecidido()
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Erro ao aprovar')
    } finally {
      setBusyId(null)
    }
  }

  async function rejeitar(id: string) {
    setBusyId(id); setErro(null)
    try {
      await ledgerApi.rejeitarAjuste(id, 'Rejeitado pelo financeiro')
      onDecidido()
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Erro ao rejeitar')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="rounded-xl border border-orange-200 bg-orange-50/50 p-4">
      <div className="flex items-center gap-2 mb-3">
        <ShieldCheck size={16} className="text-orange-600" />
        <p className="text-sm font-bold text-orange-800">Ajustes aguardando 2ª aprovação ({ajustes.length})</p>
      </div>
      {erro && <Alert variant="error" onClose={() => setErro(null)} className="mb-2">{erro}</Alert>}
      <div className="space-y-2">
        {ajustes.map((a) => {
          const mesmoPerfil = a.solicitantePerfil === perfilAtual
          const mesmoUser = a.solicitanteId === userId
          const bloqueado = mesmoPerfil || mesmoUser
          return (
            <div key={a.id} className="flex items-center justify-between gap-3 bg-white rounded-lg border border-ds-border px-3 py-2">
              <div className="min-w-0">
                <p className="text-xs font-semibold text-ds-text truncate">{a.motivo}</p>
                <p className="text-[11px] text-ds-light">
                  {a.contaDebitoCodigo} {nomeConta(a.contaDebitoCodigo)} → {a.contaCreditoCodigo} {nomeConta(a.contaCreditoCodigo)}
                  {' · '}{formatReais(a.valor)}{' · '}solicitado por <b>{a.solicitantePerfil}</b>
                </p>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                {bloqueado ? (
                  <span className="text-[10px] text-ds-light flex items-center gap-1">
                    <AlertCircle size={11} /> {mesmoUser ? 'Você solicitou' : 'Precisa de perfil diferente'}
                  </span>
                ) : (
                  <>
                    <button onClick={() => aprovar(a.id)} disabled={busyId === a.id}
                      className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-green-600 text-white text-[11px] font-semibold hover:bg-green-700 disabled:opacity-50">
                      {busyId === a.id ? <Loader2 size={12} className="animate-spin" /> : <CheckCircle2 size={12} />} Aprovar
                    </button>
                    <button onClick={() => rejeitar(a.id)} disabled={busyId === a.id}
                      className="flex items-center gap-1 px-2.5 py-1 rounded-lg border border-ds-border text-[11px] font-semibold text-ds-mid hover:bg-ds-input disabled:opacity-50">
                      <XCircle size={12} /> Rejeitar
                    </button>
                  </>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ─── Modal de lançamento de ajuste ─────────────────────────────────────────────

function AjusteModal({ contas, medicoSel, onClose, onCriado }: {
  contas: ContaLedger[]; medicoSel: Medico | null; onClose: () => void; onCriado: () => void
}) {
  const [contaDebito, setContaDebito] = useState('')
  const [contaCredito, setContaCredito] = useState('')
  const [valorStr, setValorStr] = useState('')
  const [motivo, setMotivo] = useState('')
  const [competencia, setCompetencia] = useState(currentCompetencia())
  const [loading, setLoading] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const centavos = parseBRL(valorStr)
  const podeConfirmar = contaDebito && contaCredito && contaDebito !== contaCredito && centavos > 0 && motivo.trim()

  async function submit() {
    if (!podeConfirmar) return
    setLoading(true); setErro(null)
    try {
      await ledgerApi.criarAjuste({
        medicoId: medicoSel?.id ?? null,
        competencia,
        contaDebitoCodigo: contaDebito,
        contaCreditoCodigo: contaCredito,
        valorCentavos: centavos,
        motivo: motivo.trim(),
      })
      onCriado()
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Erro ao solicitar ajuste')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal open title="Lançamento de Ajuste" onClose={onClose} size="md">
      <div className="flex flex-col gap-4">
        <div className="flex items-start gap-2 bg-blue-50 border border-blue-200 rounded-lg px-3 py-2">
          <ShieldCheck size={15} className="text-blue-600 shrink-0 mt-0.5" />
          <p className="text-[11px] text-blue-800">
            O ajuste fica <b>pendente</b> até a aprovação de um <b>segundo usuário com perfil diferente</b>.
            Só então o lançamento é gerado no razão.
          </p>
        </div>

        {erro && <Alert variant="error" onClose={() => setErro(null)}>{erro}</Alert>}

        <div>
          <label className="block text-xs font-semibold text-ds-mid mb-1">Conta de Débito *</label>
          <select value={contaDebito} onChange={(e) => setContaDebito(e.target.value)}
            className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30">
            <option value="">Selecione...</option>
            {contas.map((c) => <option key={c.id} value={c.codigo}>{c.codigo} — {c.nome}</option>)}
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-ds-mid mb-1">Conta de Crédito *</label>
          <select value={contaCredito} onChange={(e) => setContaCredito(e.target.value)}
            className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30">
            <option value="">Selecione...</option>
            {contas.map((c) => <option key={c.id} value={c.codigo}>{c.codigo} — {c.nome}</option>)}
          </select>
          {contaDebito && contaDebito === contaCredito && (
            <p className="text-[11px] text-red-500 mt-1">Débito e crédito devem ser contas diferentes.</p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-ds-mid mb-1">Valor *</label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-xs font-medium text-ds-mid">R$</span>
              <input value={valorStr}
                onChange={(e) => { const raw = e.target.value.replace(/\D/g, ''); setValorStr(maskBRL(parseInt(raw || '0', 10))) }}
                placeholder="0,00"
                className="w-full pl-8 pr-3 py-2 border border-ds-border rounded-lg text-sm text-right focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
          </div>
          <Input label="Competência" value={competencia} onChange={(e) => setCompetencia(e.target.value)} placeholder="YYYY-MM" />
        </div>

        <div>
          <label className="block text-xs font-semibold text-ds-mid mb-1">Motivo *</label>
          <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} rows={2}
            placeholder="Justificativa do ajuste contábil"
            className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none" />
        </div>

        <div className="flex justify-end gap-2 pt-1 border-t border-ds-border">
          <Button variant="ghost" onClick={onClose} disabled={loading}>Cancelar</Button>
          <Button onClick={submit} disabled={!podeConfirmar || loading} loading={loading}>Solicitar aprovação</Button>
        </div>
      </div>
    </Modal>
  )
}
