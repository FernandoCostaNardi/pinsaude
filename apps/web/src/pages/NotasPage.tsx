import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  FileText, CheckCircle2, Clock, XCircle, AlertTriangle, ShieldCheck,
  Loader2, Search, RefreshCw, Download, X, ChevronRight, Send,
  Ban, Filter, FileX, ThumbsUp, ThumbsDown,
} from 'lucide-react'
import { Spinner, Alert } from '@pinsaude/ui'
import {
  listarNotas, listarExcecoes, aprovarNota, cancelarNota, rejeitarNota,
  downloadComAuth, downloadXmlUrl, downloadPdfUrl,
  NotaFiscal, StatusNota,
} from '../api/nfseApi'
import { useAuth } from '../auth/useAuth'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number | null | undefined): string {
  if (centavos == null) return '—'
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_CFG: Record<StatusNota, { label: string; cls: string; Icon: React.ElementType }> = {
  PENDENTE:                  { label: 'Na fila',            cls: 'bg-blue-50 text-blue-700',      Icon: Clock          },
  PROCESSANDO:               { label: 'Processando',        cls: 'bg-primary-50 text-primary',    Icon: Loader2        },
  EMITIDA:                   { label: 'Emitida',            cls: 'bg-green-50 text-green-700',    Icon: CheckCircle2   },
  CANCELADA:                 { label: 'Cancelada',          cls: 'bg-gray-100 text-gray-500',     Icon: XCircle        },
  ERRO:                      { label: 'Erro',               cls: 'bg-red-50 text-red-700',        Icon: XCircle        },
  REJEITADA:                 { label: 'Rejeitada',          cls: 'bg-red-50 text-red-700',        Icon: XCircle        },
  AGUARDANDO_EMISSAO_MANUAL: { label: 'Emissão Manual',    cls: 'bg-orange-50 text-orange-700',  Icon: AlertTriangle  },
  AGUARDANDO_VALIDACAO:      { label: 'Aguard. Validação', cls: 'bg-yellow-50 text-yellow-700',  Icon: ShieldCheck    },
}

function StatusBadge({ status }: { status: StatusNota }) {
  const { label, cls, Icon } = STATUS_CFG[status]
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold ${cls}`}>
      <Icon size={11} className={status === 'PROCESSANDO' ? 'animate-spin' : ''} />
      {label}
    </span>
  )
}

// ─── Modal de Cancelamento / Rejeição ─────────────────────────────────────────

interface MotivoModalProps {
  title: string
  subtitle: string
  confirmLabel: string
  confirmCls: string
  onConfirm: (motivo: string) => Promise<void>
  onClose: () => void
}

function MotivoModal({ title, subtitle, confirmLabel, confirmCls, onConfirm, onClose }: MotivoModalProps) {
  const [motivo, setMotivo] = useState('')
  const [loading, setLoading] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => { inputRef.current?.focus() }, [])

  const handleSubmit = async () => {
    if (!motivo.trim()) { setErro('Informe o motivo.'); return }
    setLoading(true)
    setErro(null)
    try {
      await onConfirm(motivo.trim())
      onClose()
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao processar')
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-ds-border">
          <div>
            <h3 className="font-semibold text-ds-mid">{title}</h3>
            <p className="text-xs text-ds-light mt-0.5">{subtitle}</p>
          </div>
          <button onClick={onClose} className="text-ds-light hover:text-ds-mid p-1 rounded-lg hover:bg-ds-surface transition-colors">
            <X size={16} />
          </button>
        </div>
        <div className="px-6 py-4 space-y-3">
          <label className="block text-sm font-medium text-ds-mid">
            Motivo <span className="text-red-500">*</span>
          </label>
          <textarea
            ref={inputRef}
            rows={3}
            value={motivo}
            onChange={e => setMotivo(e.target.value)}
            placeholder="Descreva o motivo..."
            className="w-full px-3 py-2 rounded-lg border border-ds-border bg-white text-sm text-ds-mid placeholder:text-ds-light focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
          />
          {erro && <p className="text-xs text-red-600">{erro}</p>}
        </div>
        <div className="flex gap-2 justify-end px-6 pb-4">
          <button
            onClick={onClose}
            disabled={loading}
            className="px-4 py-2 rounded-lg border border-ds-border text-ds-mid text-sm hover:bg-ds-surface transition-colors disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            onClick={handleSubmit}
            disabled={loading || !motivo.trim()}
            className={`px-4 py-2 rounded-lg text-white text-sm font-medium transition-colors disabled:opacity-50 ${confirmCls}`}
          >
            {loading ? <Loader2 size={14} className="animate-spin" /> : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Painel lateral de detalhe ────────────────────────────────────────────────

interface DetalhePanelProps {
  nota: NotaFiscal
  onClose: () => void
  onAprovar: (nota: NotaFiscal) => void
  onCancelar: (nota: NotaFiscal) => void
  onRejeitar: (nota: NotaFiscal) => void
  isGestaoOrContabil: boolean
}

function DetalhePanel({ nota, onClose, onAprovar, onCancelar, onRejeitar, isGestaoOrContabil }: DetalhePanelProps) {
  const [dlErro, setDlErro] = useState<string | null>(null)

  const handleDownload = async (tipo: 'xml' | 'pdf') => {
    setDlErro(null)
    try {
      const url = tipo === 'xml' ? downloadXmlUrl(nota.notaId) : downloadPdfUrl(nota.notaId)
      await downloadComAuth(url, `nfse-${nota.numeroNota ?? nota.notaId}.${tipo}`)
    } catch (e: unknown) {
      setDlErro(e instanceof Error ? e.message : 'Erro ao baixar')
    }
  }

  return (
    <div className="w-96 bg-white border-l border-ds-border flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4 border-b border-ds-border">
        <h3 className="font-semibold text-ds-mid text-sm">Detalhe da Nota</h3>
        <button onClick={onClose} className="text-ds-light hover:text-ds-mid p-1 rounded-lg hover:bg-ds-surface transition-colors">
          <X size={15} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-5 space-y-5">
        {/* Status */}
        <div className="flex items-center justify-between">
          <span className="text-xs text-ds-light font-medium uppercase tracking-wide">Status</span>
          <StatusBadge status={nota.status} />
        </div>

        {/* Identificação */}
        <div className="bg-ds-surface rounded-xl p-4 space-y-2.5">
          {nota.numeroNota && (
            <div className="flex justify-between">
              <span className="text-xs text-ds-light">Número</span>
              <span className="text-sm font-bold text-ds-mid font-mono">{nota.numeroNota}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-xs text-ds-light">Competência</span>
            <span className="text-sm font-medium text-ds-mid">{formatCompetencia(nota.competencia)}</span>
          </div>
          {nota.protocolo && (
            <div className="flex justify-between gap-3">
              <span className="text-xs text-ds-light shrink-0">Protocolo</span>
              <span className="text-xs text-ds-mid font-mono text-right break-all">{nota.protocolo}</span>
            </div>
          )}
        </div>

        {/* Partes */}
        <div className="space-y-2">
          <p className="text-xs text-ds-light font-medium uppercase tracking-wide">Partes</p>
          <div className="space-y-1.5">
            <div className="flex justify-between gap-2">
              <span className="text-xs text-ds-light">Tomador</span>
              <span className="text-sm text-ds-mid text-right">{nota.tomadorNome ?? <span className="font-mono text-xs">{nota.tomadorId.substring(0, 8)}...</span>}</span>
            </div>
            <div className="flex justify-between gap-2">
              <span className="text-xs text-ds-light">Médico ID</span>
              <span className="text-xs text-ds-mid font-mono">{nota.medicoId.substring(0, 8)}...</span>
            </div>
          </div>
        </div>

        {/* Valores */}
        {nota.valorBruto != null && (
          <div className="space-y-2">
            <p className="text-xs text-ds-light font-medium uppercase tracking-wide">Valores</p>
            <div className="bg-ds-surface rounded-xl p-4 space-y-2">
              <div className="flex justify-between">
                <span className="text-xs text-ds-light">Bruto</span>
                <span className="text-sm font-semibold text-ds-mid">{formatBRL(nota.valorBruto)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-xs text-ds-light">Taxa Pin (15%)</span>
                <span className="text-sm text-orange-600">({formatBRL(nota.taxaPin)})</span>
              </div>
              <div className="flex justify-between border-t border-ds-border pt-2 mt-1">
                <span className="text-xs font-semibold text-ds-mid">Líquido Médico</span>
                <span className="text-sm font-bold text-green-700">{formatBRL(nota.valorLiquidoMedico)}</span>
              </div>
            </div>
          </div>
        )}

        {/* Datas */}
        <div className="space-y-1.5">
          <p className="text-xs text-ds-light font-medium uppercase tracking-wide">Datas</p>
          <div className="flex justify-between">
            <span className="text-xs text-ds-light">Criada em</span>
            <span className="text-xs text-ds-mid">{formatDate(nota.createdAt)}</span>
          </div>
          {nota.emitidaAt && (
            <div className="flex justify-between">
              <span className="text-xs text-ds-light">Emitida em</span>
              <span className="text-xs text-ds-mid">{formatDate(nota.emitidaAt)}</span>
            </div>
          )}
        </div>

        {/* Observações */}
        {nota.observacoes && (
          <div className="space-y-1.5">
            <p className="text-xs text-ds-light font-medium uppercase tracking-wide">Observações</p>
            <p className="text-sm text-ds-mid bg-ds-surface rounded-xl p-3 leading-relaxed">{nota.observacoes}</p>
          </div>
        )}

        {dlErro && <p className="text-xs text-red-600">{dlErro}</p>}
      </div>

      {/* Ações */}
      <div className="p-4 border-t border-ds-border space-y-2">
        {nota.status === 'EMITIDA' && (
          <>
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => handleDownload('xml')}
                className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs hover:bg-ds-surface transition-colors"
              >
                <Download size={13} /> XML
              </button>
              <button
                onClick={() => handleDownload('pdf')}
                className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs hover:bg-ds-surface transition-colors"
              >
                <FileText size={13} /> PDF
              </button>
            </div>
            {isGestaoOrContabil && (
              <button
                onClick={() => onCancelar(nota)}
                className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-red-200 text-red-600 text-xs hover:bg-red-50 transition-colors"
              >
                <Ban size={13} /> Cancelar Nota
              </button>
            )}
          </>
        )}
        {nota.status === 'AGUARDANDO_VALIDACAO' && isGestaoOrContabil && (
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => onRejeitar(nota)}
              className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-red-200 text-red-600 text-xs hover:bg-red-50 transition-colors"
            >
              <ThumbsDown size={13} /> Rejeitar
            </button>
            <button
              onClick={() => onAprovar(nota)}
              className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-green-600 text-white text-xs hover:bg-green-700 transition-colors"
            >
              <ThumbsUp size={13} /> Aprovar
            </button>
          </div>
        )}
        <button
          onClick={() => window.open(`/notas/emitir/${nota.producaoId}`, '_blank')}
          className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs hover:bg-ds-surface transition-colors"
        >
          <Send size={13} /> Ver emissão
        </button>
      </div>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

type AbaAtiva = 'todas' | 'excecoes'

export function NotasPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const isGestaoOrContabil = !!(user?.realm_access?.roles.some(r => r === 'gestao' || r === 'contabil'))

  // Dados
  const [notas, setNotas] = useState<NotaFiscal[]>([])
  const [excecoes, setExcecoes] = useState<NotaFiscal[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  // UI state
  const [aba, setAba] = useState<AbaAtiva>('todas')
  const [notaSelecionada, setNotaSelecionada] = useState<NotaFiscal | null>(null)
  const [q, setQ] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<StatusNota | ''>('')
  const [filtroCompInicio, setFiltroCompInicio] = useState('')
  const [filtroCompFim, setFiltroCompFim] = useState('')

  // Modais
  const [cancelarNota_, setCancelarNota] = useState<NotaFiscal | null>(null)
  const [rejeitarNota_, setRejeitarNota] = useState<NotaFiscal | null>(null)
  const [aprovando, setAprovando] = useState<string | null>(null)
  const [acaoErro, setAcaoErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      const [todas, exc] = await Promise.all([listarNotas(), listarExcecoes()])
      setNotas(todas)
      setExcecoes(exc)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao carregar notas')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { carregar() }, [carregar])

  // Atualiza nota selecionada quando os dados mudam (ex: após ação)
  useEffect(() => {
    if (!notaSelecionada) return
    const updated = notas.find(n => n.notaId === notaSelecionada.notaId)
    if (updated) setNotaSelecionada(updated)
  }, [notas]) // eslint-disable-line react-hooks/exhaustive-deps

  // ─── Filtros ─────────────────────────────────────────────────────────────────

  const filtradas = notas.filter(n => {
    if (filtroStatus && n.status !== filtroStatus) return false
    if (filtroCompInicio && n.competencia < filtroCompInicio) return false
    if (filtroCompFim && n.competencia > filtroCompFim) return false
    if (!q) return true
    const qLow = q.toLowerCase()
    return (
      n.competencia.includes(q) ||
      (n.tomadorNome ?? '').toLowerCase().includes(qLow) ||
      (n.numeroNota ?? '').toLowerCase().includes(qLow) ||
      (n.observacoes ?? '').toLowerCase().includes(qLow)
    )
  })

  // ─── Stats ────────────────────────────────────────────────────────────────────

  const stats = {
    total:    notas.length,
    emitidas: notas.filter(n => n.status === 'EMITIDA').length,
    fila:     notas.filter(n => n.status === 'PENDENTE' || n.status === 'PROCESSANDO').length,
    atencao:  notas.filter(n => ['ERRO','REJEITADA','AGUARDANDO_VALIDACAO','AGUARDANDO_EMISSAO_MANUAL'].includes(n.status)).length,
  }

  // ─── Ações ────────────────────────────────────────────────────────────────────

  const handleAprovar = async (nota: NotaFiscal) => {
    setAcaoErro(null)
    setAprovando(nota.notaId)
    try {
      await aprovarNota(nota.notaId)
      await carregar()
      setNotaSelecionada(null)
    } catch (e: unknown) {
      setAcaoErro(e instanceof Error ? e.message : 'Erro ao aprovar nota')
    } finally {
      setAprovando(null)
    }
  }

  const handleCancelar = async (motivo: string) => {
    if (!cancelarNota_) return
    await cancelarNota(cancelarNota_.notaId, motivo)
    await carregar()
    setNotaSelecionada(null)
    setCancelarNota(null)
  }

  const handleRejeitar = async (motivo: string) => {
    if (!rejeitarNota_) return
    await rejeitarNota(rejeitarNota_.notaId, motivo)
    await carregar()
    setNotaSelecionada(null)
    setRejeitarNota(null)
  }

  // ─── Render ───────────────────────────────────────────────────────────────────

  return (
    <div className="flex-1 overflow-hidden flex flex-col">
      {/* Header */}
      <div className="bg-white border-b border-ds-border px-6 py-5 shrink-0">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-ds-mid">Notas Fiscais</h1>
            <p className="text-sm text-ds-light mt-0.5">NFS-e emitidas e fila de exceções</p>
          </div>
          <button
            onClick={carregar}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-ds-border text-ds-light hover:text-ds-mid hover:bg-ds-surface transition-colors text-sm"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            Atualizar
          </button>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-4 gap-3 mt-4">
          {[
            { label: 'Total',        value: stats.total,    cls: 'text-ds-mid'    },
            { label: 'Emitidas',     value: stats.emitidas, cls: 'text-green-700' },
            { label: 'Em andamento', value: stats.fila,     cls: 'text-primary'   },
            { label: 'Atenção',      value: stats.atencao,  cls: 'text-orange-600'},
          ].map(({ label, value, cls }) => (
            <div key={label} className="bg-ds-surface rounded-xl px-4 py-3 text-center">
              <p className={`text-xl font-bold ${cls}`}>{value}</p>
              <p className="text-xs text-ds-light mt-0.5">{label}</p>
            </div>
          ))}
        </div>

        {/* Abas */}
        <div className="flex gap-1 mt-4 border-b border-ds-border">
          {([
            { id: 'todas' as AbaAtiva,    label: 'Todas',            count: notas.length,     badgeCls: 'bg-ds-surface text-ds-light'       },
            { id: 'excecoes' as AbaAtiva, label: 'Fila de Exceções', count: excecoes.length,  badgeCls: 'bg-yellow-100 text-yellow-700' },
          ]).map(tab => (
            <button
              key={tab.id}
              onClick={() => { setAba(tab.id); setNotaSelecionada(null) }}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px ${
                aba === tab.id
                  ? 'border-primary text-primary'
                  : 'border-transparent text-ds-light hover:text-ds-mid'
              }`}
            >
              {tab.label}
              {tab.count > 0 && (
                <span className={`inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full text-[10px] font-bold ${tab.badgeCls}`}>
                  {tab.count}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-hidden flex">
        {/* Conteúdo principal */}
        <div className="flex-1 overflow-auto p-5 space-y-4">
          {erro && <Alert variant="error">{erro}</Alert>}
          {acaoErro && <Alert variant="error">{acaoErro}</Alert>}

          {/* ─── ABA TODAS ─── */}
          {aba === 'todas' && (
            <>
              {/* Filtros */}
              <div className="flex flex-wrap gap-2 items-center">
                <div className="relative flex-1 min-w-48">
                  <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light" />
                  <input
                    type="text"
                    placeholder="Tomador, número, observação..."
                    value={q}
                    onChange={e => setQ(e.target.value)}
                    className="w-full pl-9 pr-3 py-2 rounded-lg border border-ds-border bg-white text-sm text-ds-mid placeholder:text-ds-light focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
                <div className="flex items-center gap-1.5">
                  <Filter size={13} className="text-ds-light" />
                  <select
                    value={filtroStatus}
                    onChange={e => setFiltroStatus(e.target.value as StatusNota | '')}
                    className="px-3 py-2 rounded-lg border border-ds-border bg-white text-sm text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30"
                  >
                    <option value="">Todos os status</option>
                    {Object.entries(STATUS_CFG).map(([k, v]) => (
                      <option key={k} value={k}>{v.label}</option>
                    ))}
                  </select>
                </div>
                <div className="flex items-center gap-1.5">
                  <input
                    type="month"
                    value={filtroCompInicio}
                    onChange={e => setFiltroCompInicio(e.target.value)}
                    title="Competência início"
                    className="px-3 py-2 rounded-lg border border-ds-border bg-white text-sm text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                  <span className="text-ds-light text-xs">até</span>
                  <input
                    type="month"
                    value={filtroCompFim}
                    onChange={e => setFiltroCompFim(e.target.value)}
                    title="Competência fim"
                    className="px-3 py-2 rounded-lg border border-ds-border bg-white text-sm text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                  {(filtroStatus || filtroCompInicio || filtroCompFim || q) && (
                    <button
                      onClick={() => { setQ(''); setFiltroStatus(''); setFiltroCompInicio(''); setFiltroCompFim('') }}
                      className="p-2 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors"
                      title="Limpar filtros"
                    >
                      <X size={13} />
                    </button>
                  )}
                </div>
              </div>

              {loading ? (
                <div className="flex items-center justify-center py-16"><Spinner /></div>
              ) : filtradas.length === 0 ? (
                <div className="text-center py-16">
                  <FileText size={40} className="mx-auto text-ds-light mb-3" />
                  <p className="text-ds-mid font-medium">Nenhuma nota encontrada</p>
                  <p className="text-ds-light text-sm mt-1">
                    {q || filtroStatus ? 'Tente ajustar os filtros.' : 'Vá em Produções e clique em "Emitir NFS-e".'}
                  </p>
                </div>
              ) : (
                <div className="bg-white rounded-2xl border border-ds-border overflow-hidden">
                  <table className="w-full text-sm">
                    <thead className="bg-ds-surface border-b border-ds-border">
                      <tr>
                        {['Tomador','Competência','Bruto','Líquido','Status','Emitida em','Nº Nota',''].map(h => (
                          <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-ds-light uppercase tracking-wide whitespace-nowrap">
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-ds-border">
                      {filtradas.map(nota => (
                        <tr
                          key={nota.notaId}
                          onClick={() => setNotaSelecionada(prev => prev?.notaId === nota.notaId ? null : nota)}
                          className={`cursor-pointer transition-colors ${
                            notaSelecionada?.notaId === nota.notaId
                              ? 'bg-primary-50'
                              : 'hover:bg-ds-surface'
                          }`}
                        >
                          <td className="px-4 py-3 font-medium text-ds-mid max-w-48 truncate" title={nota.tomadorNome ?? ''}>
                            {nota.tomadorNome ?? <span className="text-ds-light font-mono text-xs">{nota.tomadorId.substring(0, 8)}…</span>}
                          </td>
                          <td className="px-4 py-3 font-mono text-sm">{formatCompetencia(nota.competencia)}</td>
                          <td className="px-4 py-3 font-medium">{formatBRL(nota.valorBruto)}</td>
                          <td className="px-4 py-3 text-green-700 font-medium">{formatBRL(nota.valorLiquidoMedico)}</td>
                          <td className="px-4 py-3"><StatusBadge status={nota.status} /></td>
                          <td className="px-4 py-3 text-xs text-ds-light whitespace-nowrap">{formatDate(nota.emitidaAt)}</td>
                          <td className="px-4 py-3 font-mono text-xs font-semibold">{nota.numeroNota ?? '—'}</td>
                          <td className="px-4 py-3">
                            <ChevronRight size={14} className={`text-ds-light transition-transform ${notaSelecionada?.notaId === nota.notaId ? 'rotate-90 text-primary' : ''}`} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="px-4 py-2 border-t border-ds-border bg-ds-surface">
                    <p className="text-xs text-ds-light">{filtradas.length} de {notas.length} notas</p>
                  </div>
                </div>
              )}
            </>
          )}

          {/* ─── ABA FILA DE EXCEÇÕES ─── */}
          {aba === 'excecoes' && (
            <>
              {loading ? (
                <div className="flex items-center justify-center py-16"><Spinner /></div>
              ) : excecoes.length === 0 ? (
                <div className="text-center py-16">
                  <ShieldCheck size={40} className="mx-auto text-green-500 mb-3" />
                  <p className="text-ds-mid font-semibold">Fila vazia!</p>
                  <p className="text-ds-light text-sm mt-1">Nenhuma nota aguardando validação.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="bg-yellow-50 border border-yellow-200 rounded-xl px-4 py-3 flex items-start gap-2">
                    <AlertTriangle size={15} className="text-yellow-600 mt-0.5 shrink-0" />
                    <p className="text-xs text-yellow-800">
                      Essas são as <strong>primeiras notas</strong> de cada médico — requerem validação antes de serem enfileiradas para emissão.
                      Revise os dados e aprove ou rejeite com justificativa.
                    </p>
                  </div>

                  {excecoes.map(nota => (
                    <div
                      key={nota.notaId}
                      className={`bg-white rounded-2xl border p-4 cursor-pointer transition-all ${
                        notaSelecionada?.notaId === nota.notaId
                          ? 'border-primary ring-1 ring-primary/20'
                          : 'border-ds-border hover:border-primary/30'
                      }`}
                      onClick={() => setNotaSelecionada(prev => prev?.notaId === nota.notaId ? null : nota)}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1.5">
                            <StatusBadge status={nota.status} />
                            <span className="text-xs text-ds-light">{formatDate(nota.createdAt)}</span>
                          </div>
                          <p className="font-semibold text-ds-mid truncate">
                            {nota.tomadorNome ?? <span className="font-mono text-sm">{nota.tomadorId.substring(0, 8)}…</span>}
                          </p>
                          <p className="text-xs text-ds-light mt-0.5">
                            Competência {formatCompetencia(nota.competencia)} · {formatBRL(nota.valorBruto)} bruto
                          </p>
                          <p className="text-xs text-ds-light font-mono mt-1">
                            Médico: {nota.medicoId.substring(0, 8)}…
                          </p>
                        </div>
                        <div className="flex gap-2 shrink-0">
                          {isGestaoOrContabil && (
                            <>
                              <button
                                onClick={e => { e.stopPropagation(); setRejeitarNota(nota) }}
                                className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-red-200 text-red-600 text-xs hover:bg-red-50 transition-colors"
                              >
                                <FileX size={12} /> Rejeitar
                              </button>
                              <button
                                onClick={async e => { e.stopPropagation(); await handleAprovar(nota) }}
                                disabled={aprovando === nota.notaId}
                                className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-green-600 text-white text-xs hover:bg-green-700 transition-colors disabled:opacity-50"
                              >
                                {aprovando === nota.notaId
                                  ? <Loader2 size={12} className="animate-spin" />
                                  : <ThumbsUp size={12} />
                                }
                                Aprovar
                              </button>
                            </>
                          )}
                          <button
                            onClick={e => { e.stopPropagation(); navigate(`/notas/emitir/${nota.producaoId}`) }}
                            className="p-1.5 rounded-lg text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                            title="Ver emissão"
                          >
                            <Send size={13} />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>

        {/* Painel lateral de detalhe */}
        {notaSelecionada && (
          <DetalhePanel
            nota={notaSelecionada}
            onClose={() => setNotaSelecionada(null)}
            onAprovar={handleAprovar}
            onCancelar={nota => setCancelarNota(nota)}
            onRejeitar={nota => setRejeitarNota(nota)}
            isGestaoOrContabil={isGestaoOrContabil}
          />
        )}
      </div>

      {/* Modal de cancelamento */}
      {cancelarNota_ && (
        <MotivoModal
          title="Cancelar Nota Fiscal"
          subtitle={`NFS-e ${cancelarNota_.numeroNota ?? 'sem número'} · ${cancelarNota_.tomadorNome ?? ''}`}
          confirmLabel="Cancelar Nota"
          confirmCls="bg-red-600 hover:bg-red-700"
          onConfirm={handleCancelar}
          onClose={() => setCancelarNota(null)}
        />
      )}

      {/* Modal de rejeição */}
      {rejeitarNota_ && (
        <MotivoModal
          title="Rejeitar Nota — Fila de Exceções"
          subtitle={`${rejeitarNota_.tomadorNome ?? ''} · ${formatCompetencia(rejeitarNota_.competencia)}`}
          confirmLabel="Rejeitar"
          confirmCls="bg-red-600 hover:bg-red-700"
          onConfirm={handleRejeitar}
          onClose={() => setRejeitarNota(null)}
        />
      )}
    </div>
  )
}
