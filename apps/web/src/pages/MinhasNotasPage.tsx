import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  FileText, Download, FileDown, RefreshCw,
  CheckCircle2, Clock, XCircle, AlertTriangle, Loader2,
  ShieldCheck, X, ChevronRight, Receipt, ArrowUpRight,
} from 'lucide-react'
import { Spinner, Alert } from '@pinsaude/ui'
import { portalApi, NotaPortal } from '../api/portalApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

function generateCompetencias(): string[] {
  const comps: string[] = []
  const now = new Date()
  for (let i = 0; i < 24; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    comps.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return comps
}

// ─── Status ──────────────────────────────────────────────────────────────────

const STATUS_CFG: Record<string, { label: string; cls: string; Icon: React.ElementType }> = {
  PENDENTE:                  { label: 'Na fila',            cls: 'bg-blue-50 text-blue-700',      Icon: Clock          },
  PROCESSANDO:               { label: 'Processando',        cls: 'bg-primary-50 text-primary',    Icon: Loader2        },
  EMITIDA:                   { label: 'Emitida',            cls: 'bg-green-50 text-green-700',    Icon: CheckCircle2   },
  CANCELADA:                 { label: 'Cancelada',          cls: 'bg-gray-100 text-gray-500',     Icon: XCircle        },
  ERRO:                      { label: 'Erro',               cls: 'bg-red-50 text-red-700',        Icon: XCircle        },
  REJEITADA:                 { label: 'Rejeitada',          cls: 'bg-red-50 text-red-700',        Icon: XCircle        },
  AGUARDANDO_EMISSAO_MANUAL: { label: 'Emissão Manual',    cls: 'bg-orange-50 text-orange-700',  Icon: AlertTriangle  },
  AGUARDANDO_VALIDACAO:      { label: 'Aguard. Validação', cls: 'bg-yellow-50 text-yellow-700',  Icon: ShieldCheck    },
}

function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CFG[status] ?? { label: status, cls: 'bg-gray-100 text-gray-500', Icon: Clock }
  const { label, cls, Icon } = cfg
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold ${cls}`}>
      <Icon size={11} className={status === 'PROCESSANDO' ? 'animate-spin' : ''} />
      {label}
    </span>
  )
}

// ─── Linha no painel de detalhe ───────────────────────────────────────────────

function LinhaValor({ label, value, sub, destaque, negativo }: {
  label: string; value: number; sub?: boolean; destaque?: boolean; negativo?: boolean
}) {
  return (
    <div className={`flex justify-between items-center py-1.5 ${
      destaque ? 'border-t-2 border-ds-border mt-1 pt-2.5' : ''
    } ${sub ? 'pl-3' : ''}`}>
      <span className={`text-xs ${
        destaque ? 'font-bold text-ds-text' : sub ? 'text-ds-light' : 'text-ds-mid'
      }`}>
        {sub && <span className="text-ds-light mr-1">(−)</span>}
        {label}
      </span>
      <span className={`text-xs tabular-nums font-semibold ${
        destaque ? 'text-ds-text' : negativo ? 'text-red-600' : 'text-ds-text'
      }`}>
        {negativo ? `(${formatBRL(value)})` : formatBRL(value)}
      </span>
    </div>
  )
}

// ─── Painel lateral de detalhe ────────────────────────────────────────────────

function PainelDetalhe({ nota, onClose }: { nota: NotaPortal; onClose: () => void }) {
  const [downloadingXml, setDownloadingXml] = useState(false)
  const [downloadingPdf, setDownloadingPdf] = useState(false)
  const [downloadError, setDownloadError]   = useState<string | null>(null)

  const totalTributos = nota.valorIss + nota.valorIr + nota.valorCsll + nota.valorPis + nota.valorCofins

  async function handleDownloadXml() {
    setDownloadingXml(true)
    setDownloadError(null)
    try { await portalApi.downloadXml(nota.id) }
    catch (e) { setDownloadError(e instanceof Error ? e.message : 'Erro ao baixar XML') }
    finally { setDownloadingXml(false) }
  }

  async function handleDownloadPdf() {
    setDownloadingPdf(true)
    setDownloadError(null)
    try { await portalApi.downloadPdf(nota.id) }
    catch (e) { setDownloadError(e instanceof Error ? e.message : 'Erro ao baixar PDF') }
    finally { setDownloadingPdf(false) }
  }

  return (
    <div className="w-80 bg-white border-l border-ds-border flex flex-col h-full shrink-0">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-ds-border shrink-0">
        <div>
          <p className="text-sm font-bold text-ds-text">Detalhe da NFS-e</p>
          {nota.numeroNota ? (
            <p className="text-[11px] text-ds-light">Nº {nota.numeroNota}</p>
          ) : (
            <p className="text-[11px] text-ds-light italic">Número pendente</p>
          )}
        </div>
        <button
          onClick={onClose}
          className="p-1.5 rounded-lg text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors"
        >
          <X size={15} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* Status + competência */}
        <div className="flex items-center justify-between">
          <StatusBadge status={nota.status} />
          <span className="text-xs text-ds-light font-semibold">{formatCompetencia(nota.competencia)}</span>
        </div>

        {/* Tomador */}
        <div>
          <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider">Tomador</p>
          <p className="text-sm font-semibold text-ds-text mt-0.5 leading-snug">{nota.tomadorNome || '—'}</p>
        </div>

        {/* Protocolo */}
        {nota.protocolo && (
          <div>
            <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider">Protocolo</p>
            <p className="text-xs font-mono text-ds-mid mt-0.5 break-all">{nota.protocolo}</p>
          </div>
        )}

        {/* Datas */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider">Criada em</p>
            <p className="text-xs text-ds-mid mt-0.5">{formatDate(nota.createdAt)}</p>
          </div>
          {nota.emitidaAt && (
            <div>
              <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider">Emitida em</p>
              <p className="text-xs text-ds-mid mt-0.5">{formatDate(nota.emitidaAt)}</p>
            </div>
          )}
        </div>

        {/* Breakdown financeiro */}
        <div>
          <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider mb-2">
            Composição do Valor
          </p>
          <div className="bg-ds-surface rounded-lg px-3 py-1 divide-y divide-ds-border">
            <LinhaValor label="Valor Bruto da Nota" value={nota.valorBrutoCentavos} />
            {nota.valorIss > 0 && (
              <LinhaValor label="ISS" value={nota.valorIss} sub negativo />
            )}
            {nota.valorIr > 0 && (
              <LinhaValor label="IR Retido na Fonte" value={nota.valorIr} sub negativo />
            )}
            {nota.valorCsll > 0 && (
              <LinhaValor label="CSLL Retida" value={nota.valorCsll} sub negativo />
            )}
            {nota.valorPis > 0 && (
              <LinhaValor label="PIS Retido" value={nota.valorPis} sub negativo />
            )}
            {nota.valorCofins > 0 && (
              <LinhaValor label="COFINS Retida" value={nota.valorCofins} sub negativo />
            )}
            <LinhaValor label="Taxa Pin Saúde (15%)" value={nota.taxaPinCentavos} sub negativo />
            {totalTributos > 0 && (
              <LinhaValor label="Total Tributos" value={totalTributos} sub negativo />
            )}
            <LinhaValor label="Seu Valor Líquido" value={nota.valorLiquidoMedicoCentavos} destaque />
          </div>
          <p className="text-[10px] text-ds-light mt-1.5 leading-relaxed">
            Seu líquido = bruto − taxa Pin (15%). Tributos são custo fiscal da Pin Saúde.
          </p>
        </div>

        {/* Link extrato */}
        <div>
          <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider mb-2">Extrato</p>
          <Link
            to="/portal/extrato"
            className="flex items-center gap-2 px-3 py-2 rounded-lg border border-ds-border text-xs text-ds-mid hover:bg-ds-input transition-colors"
          >
            <Receipt size={13} className="text-primary" />
            <span>Ver lançamento no extrato</span>
            <ArrowUpRight size={12} className="ml-auto text-ds-light" />
          </Link>
        </div>

        {/* Alerta problema */}
        {(nota.status === 'ERRO' || nota.status === 'REJEITADA') && (
          <div className="rounded-lg bg-red-50 border border-red-200 px-3 py-2">
            <p className="text-xs text-red-700 font-semibold">Nota com problema</p>
            <p className="text-[11px] text-red-600 mt-0.5">
              Entre em contato com o time operacional para regularização.
            </p>
          </div>
        )}

        {downloadError && (
          <div className="rounded-lg bg-red-50 border border-red-200 px-3 py-2">
            <p className="text-xs text-red-600">{downloadError}</p>
          </div>
        )}
      </div>

      {/* Downloads — sticky bottom, só para notas EMITIDA */}
      {nota.status === 'EMITIDA' && (
        <div className="shrink-0 border-t border-ds-border p-4 space-y-2">
          <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider mb-2">Downloads</p>
          {nota.temXml ? (
            <button
              onClick={handleDownloadXml}
              disabled={downloadingXml}
              className="w-full flex items-center gap-2 px-3 py-2 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-50 transition-colors"
            >
              {downloadingXml
                ? <Loader2 size={13} className="animate-spin text-primary" />
                : <FileText size={13} className="text-primary" />}
              Baixar XML da NFS-e
            </button>
          ) : null}
          {nota.temPdf ? (
            <button
              onClick={handleDownloadPdf}
              disabled={downloadingPdf}
              className="w-full flex items-center gap-2 px-3 py-2 rounded-lg bg-primary text-white text-xs font-semibold hover:bg-primary-600 disabled:opacity-50 transition-colors"
            >
              {downloadingPdf
                ? <Loader2 size={13} className="animate-spin" />
                : <FileDown size={13} />}
              Baixar PDF da NFS-e
            </button>
          ) : null}
          {!nota.temXml && !nota.temPdf && (
            <p className="text-[11px] text-ds-light text-center py-1">
              Documentos sendo processados...
            </p>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

const COMPETENCIAS = generateCompetencias()

export function MinhasNotasPage() {
  const [notas, setNotas]         = useState<NotaPortal[]>([])
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState<string | null>(null)
  const [selecionada, setSelecionada] = useState<NotaPortal | null>(null)

  const [filtroCompetencia, setFiltroCompetencia] = useState('')
  const [filtroStatus,      setFiltroStatus]      = useState('')
  const [filtroTomador,     setFiltroTomador]     = useState('')

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await portalApi.getNotas({
        competencia: filtroCompetencia || undefined,
        status:      filtroStatus      || undefined,
      })
      setNotas(data)
      // Atualiza o painel lateral se a nota selecionada ainda está na lista
      setSelecionada(prev => prev ? (data.find(n => n.id === prev.id) ?? null) : null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar notas')
    } finally {
      setLoading(false)
    }
  }, [filtroCompetencia, filtroStatus])

  useEffect(() => { carregar() }, [carregar])

  // Filtro de tomador client-side (evita round-trip para cada digitação)
  const notasFiltradas = notas.filter(n =>
    !filtroTomador || (n.tomadorNome ?? '').toLowerCase().includes(filtroTomador.toLowerCase())
  )

  const totalBruto  = notasFiltradas.reduce((s, n) => s + n.valorBrutoCentavos, 0)
  const totalLiquid = notasFiltradas.reduce((s, n) => s + n.valorLiquidoMedicoCentavos, 0)
  const totalTrib   = notasFiltradas.reduce((s, n) =>
    s + n.valorIss + n.valorIr + n.valorCsll + n.valorPis + n.valorCofins, 0)

  const temFiltro = !!(filtroCompetencia || filtroStatus || filtroTomador)

  function limparFiltros() {
    setFiltroCompetencia('')
    setFiltroStatus('')
    setFiltroTomador('')
  }

  function exportarCSV() {
    const cabecalho = [
      'Competência','Nº Nota','Tomador',
      'Valor Bruto','ISS','IR','CSLL','PIS','COFINS',
      'Taxa Pin','Valor Líquido','Status','Emissão',
    ]
    const linhas = notasFiltradas.map(n => [
      n.competencia,
      n.numeroNota ?? '—',
      n.tomadorNome ?? '—',
      (n.valorBrutoCentavos / 100).toFixed(2).replace('.', ','),
      (n.valorIss / 100).toFixed(2).replace('.', ','),
      (n.valorIr / 100).toFixed(2).replace('.', ','),
      (n.valorCsll / 100).toFixed(2).replace('.', ','),
      (n.valorPis / 100).toFixed(2).replace('.', ','),
      (n.valorCofins / 100).toFixed(2).replace('.', ','),
      (n.taxaPinCentavos / 100).toFixed(2).replace('.', ','),
      (n.valorLiquidoMedicoCentavos / 100).toFixed(2).replace('.', ','),
      STATUS_CFG[n.status]?.label ?? n.status,
      n.emitidaAt ? new Date(n.emitidaAt).toLocaleDateString('pt-BR') : '—',
    ])
    const csv = [cabecalho, ...linhas].map(r => r.map(c => `"${c}"`).join(';')).join('\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'minhas-notas-fiscais.csv'
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="flex flex-col h-full -m-6">
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="px-6 pt-6 pb-4 bg-white border-b border-ds-border shrink-0">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-xl font-black text-ds-text">Minhas Notas Fiscais</h1>
            <p className="text-sm text-ds-light mt-0.5">
              Histórico completo das NFS-e emitidas em seu nome
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={exportarCSV}
              disabled={notasFiltradas.length === 0}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors"
            >
              <Download size={13} />
              CSV
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
        <div className="mt-3 flex flex-wrap gap-2">
          <select
            value={filtroCompetencia}
            onChange={e => setFiltroCompetencia(e.target.value)}
            className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white"
          >
            <option value="">Todas as competências</option>
            {COMPETENCIAS.map(c => {
              const [ano, mes] = c.split('-')
              const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
              return <option key={c} value={c}>{meses[parseInt(mes, 10) - 1]}/{ano}</option>
            })}
          </select>

          <select
            value={filtroStatus}
            onChange={e => setFiltroStatus(e.target.value)}
            className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white"
          >
            <option value="">Todos os status</option>
            {Object.entries(STATUS_CFG).map(([k, v]) => (
              <option key={k} value={k}>{v.label}</option>
            ))}
          </select>

          <input
            type="text"
            placeholder="Buscar por tomador..."
            value={filtroTomador}
            onChange={e => setFiltroTomador(e.target.value)}
            className="border border-ds-border rounded-lg px-2.5 py-1.5 text-xs text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30 flex-1 min-w-36 max-w-64"
          />

          {temFiltro && (
            <button
              onClick={limparFiltros}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs text-red-600 hover:bg-red-50 border border-red-200 transition-colors"
            >
              <X size={11} /> Limpar filtros
            </button>
          )}
        </div>

        {/* Totalizadores */}
        {notasFiltradas.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-3 text-xs">
            <span className="bg-ds-surface rounded-lg px-3 py-1.5 text-ds-light">
              {notasFiltradas.length} nota{notasFiltradas.length !== 1 ? 's' : ''}
            </span>
            <span className="bg-ds-surface rounded-lg px-3 py-1.5 text-ds-mid">
              Bruto: <strong className="text-ds-text">{formatBRL(totalBruto)}</strong>
            </span>
            {totalTrib > 0 && (
              <span className="bg-red-50 rounded-lg px-3 py-1.5 text-red-700">
                Tributos: <strong>({formatBRL(totalTrib)})</strong>
              </span>
            )}
            <span className="bg-green-50 rounded-lg px-3 py-1.5 text-green-700">
              Seu líquido: <strong>{formatBRL(totalLiquid)}</strong>
            </span>
          </div>
        )}
      </div>

      {/* ── Body ───────────────────────────────────────────────────────── */}
      <div className="flex-1 overflow-hidden flex min-h-0">
        {/* Lista */}
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
          ) : notasFiltradas.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-48 text-ds-light">
              <FileText size={36} className="mb-3 opacity-25" />
              <p className="text-sm font-semibold">Nenhuma nota encontrada</p>
              <p className="text-xs mt-1">
                {temFiltro ? 'Tente ajustar os filtros' : 'Suas notas aparecerão aqui quando emitidas'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-ds-border bg-white shadow-sm">
              <table className="w-full text-sm min-w-[640px]">
                <thead>
                  <tr className="bg-ds-surface border-b border-ds-border">
                    {[
                      { label: 'Competência',  cls: 'text-left'  },
                      { label: 'Nº Nota',      cls: 'text-left'  },
                      { label: 'Tomador',      cls: 'text-left'  },
                      { label: 'Valor Bruto',  cls: 'text-right' },
                      { label: 'Tributos',     cls: 'text-right hidden md:table-cell' },
                      { label: 'Valor Líquido',cls: 'text-right' },
                      { label: 'Status',       cls: 'text-center'},
                      { label: 'Emissão',      cls: 'text-center hidden lg:table-cell' },
                      { label: '',             cls: '' },
                    ].map(({ label, cls }) => (
                      <th key={label}
                        className={`px-4 py-3 text-[10px] font-bold text-ds-light uppercase tracking-wider ${cls}`}
                      >
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-ds-border">
                  {notasFiltradas.map(nota => {
                    const sel = selecionada?.id === nota.id
                    const tribNota = nota.valorIss + nota.valorIr + nota.valorCsll + nota.valorPis + nota.valorCofins
                    return (
                      <tr
                        key={nota.id}
                        onClick={() => setSelecionada(sel ? null : nota)}
                        className={`cursor-pointer transition-colors ${
                          sel
                            ? 'bg-primary-50 border-l-4 border-l-primary'
                            : 'hover:bg-ds-surface'
                        }`}
                      >
                        <td className="px-4 py-3">
                          <span className="text-xs font-semibold text-ds-text">
                            {formatCompetencia(nota.competencia)}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          {nota.numeroNota
                            ? <span className="text-xs font-mono text-ds-text">{nota.numeroNota}</span>
                            : <span className="text-xs text-ds-light italic">—</span>}
                        </td>
                        <td className="px-4 py-3">
                          <span className="text-xs text-ds-mid block max-w-[180px] truncate">
                            {nota.tomadorNome || '—'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <span className="text-xs font-semibold tabular-nums text-ds-text">
                            {formatBRL(nota.valorBrutoCentavos)}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right hidden md:table-cell">
                          {tribNota > 0 ? (
                            <span className="text-xs tabular-nums text-red-600 font-medium">
                              ({formatBRL(tribNota)})
                            </span>
                          ) : (
                            <span className="text-xs text-ds-light">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <span className="text-xs font-bold tabular-nums text-green-700">
                            {formatBRL(nota.valorLiquidoMedicoCentavos)}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-center">
                          <StatusBadge status={nota.status} />
                        </td>
                        <td className="px-4 py-3 text-center hidden lg:table-cell">
                          <span className="text-xs text-ds-light">{formatDate(nota.emitidaAt)}</span>
                        </td>
                        <td className="px-2 py-3">
                          <ChevronRight
                            size={14}
                            className={`text-ds-light transition-transform ${sel ? 'rotate-180 text-primary' : ''}`}
                          />
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Painel lateral de detalhe */}
        {selecionada && (
          <PainelDetalhe
            nota={selecionada}
            onClose={() => setSelecionada(null)}
          />
        )}
      </div>
    </div>
  )
}
