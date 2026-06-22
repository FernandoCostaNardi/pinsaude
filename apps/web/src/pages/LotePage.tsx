import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Layers, Play, RotateCcw, CheckCircle2, XCircle,
  Loader2, AlertTriangle, Clock, PackageCheck, ChevronRight,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import {
  emitirLote, getProgressoLote, listarLotes, listarErrosLote,
  reprocessarNota,
  type LoteProgressoResponse, type NotaFiscal,
} from '../api/nfseApi'

// ─── utils ────────────────────────────────────────────────────────────────────

function competenciaAnterior(): string {
  const now = new Date()
  const d = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const MESES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

function formatarCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  return `${MESES[parseInt(mes, 10) - 1]}/${ano}`
}

function formatarData(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function calcEta(lote: LoteProgressoResponse): string | null {
  if (lote.status !== 'EM_ANDAMENTO' || lote.emitidas === 0) return null
  const elapsedMs = Date.now() - new Date(lote.iniciadoEm).getTime()
  const msPerNota = elapsedMs / lote.emitidas
  const remaining = lote.total - lote.emitidas - lote.falhas
  if (remaining <= 0) return null
  const etaMin = Math.ceil((msPerNota * remaining) / 60000)
  return etaMin <= 1 ? 'menos de 1 min' : `~${etaMin} min`
}

// ─── Barra de Progresso ───────────────────────────────────────────────────────

function ProgressBar({ percent, status }: { percent: number; status: string }) {
  const cor =
    status === 'FALHA' ? 'bg-red-500' :
    status === 'CONCLUIDO' ? 'bg-green-500' : 'bg-primary'
  return (
    <div className="w-full bg-ds-surface rounded-full h-3 overflow-hidden">
      <div
        className={`h-full rounded-full transition-all duration-700 ${cor} ${status === 'EM_ANDAMENTO' ? 'animate-pulse' : ''}`}
        style={{ width: `${Math.min(100, percent)}%` }}
      />
    </div>
  )
}

// ─── Card de Progresso ────────────────────────────────────────────────────────

function ProgressoCard({ lote }: { lote: LoteProgressoResponse }) {
  const eta = calcEta(lote)
  const cfg = {
    EM_ANDAMENTO: { label: 'Em andamento',        cls: 'text-primary',   Ic: Loader2      },
    CONCLUIDO:    { label: 'Concluído',            cls: 'text-green-600', Ic: CheckCircle2 },
    FALHA:        { label: 'Concluído com falhas', cls: 'text-red-600',   Ic: XCircle      },
  }[lote.status] ?? { label: lote.status, cls: 'text-ds-mid', Ic: Clock }

  return (
    <div className="bg-white rounded-2xl border border-ds-border p-5">
      <div className="flex items-start justify-between mb-4">
        <div>
          <h3 className="text-base font-bold text-ds-mid">{formatarCompetencia(lote.competencia)}</h3>
          <span className={`text-xs font-semibold flex items-center gap-1 mt-0.5 ${cfg.cls}`}>
            <cfg.Ic size={12} className={lote.status === 'EM_ANDAMENTO' ? 'animate-spin' : ''} />
            {cfg.label}
          </span>
        </div>
        <span className="text-4xl font-bold text-ds-mid tabular-nums">
          {Math.round(lote.percentualConcluido)}%
        </span>
      </div>

      <ProgressBar percent={lote.percentualConcluido} status={lote.status} />

      <div className="grid grid-cols-3 gap-3 mt-4">
        <div className="text-center p-3 bg-green-50 rounded-xl">
          <p className="text-2xl font-bold text-green-700 tabular-nums">{lote.emitidas}</p>
          <p className="text-xs text-green-600 mt-0.5">Emitidas</p>
        </div>
        <div className="text-center p-3 bg-primary-50 rounded-xl">
          <p className="text-2xl font-bold text-primary tabular-nums">{lote.emProcessamento}</p>
          <p className="text-xs text-primary-700 mt-0.5">Em fila</p>
        </div>
        <div className={`text-center p-3 rounded-xl ${lote.falhas > 0 ? 'bg-red-50' : 'bg-ds-surface'}`}>
          <p className={`text-2xl font-bold tabular-nums ${lote.falhas > 0 ? 'text-red-700' : 'text-ds-mid'}`}>
            {lote.falhas}
          </p>
          <p className={`text-xs mt-0.5 ${lote.falhas > 0 ? 'text-red-600' : 'text-ds-light'}`}>
            Com erro
          </p>
        </div>
      </div>

      <div className="mt-3 flex items-center justify-between text-xs text-ds-light">
        <span>Total: {lote.total} notas · Início: {formatarData(lote.iniciadoEm)}</span>
        {eta && <span className="text-primary font-medium">ETA {eta}</span>}
        {lote.concluidoEm && <span>Fim: {formatarData(lote.concluidoEm)}</span>}
      </div>
    </div>
  )
}

// ─── Tabela de Erros ──────────────────────────────────────────────────────────

function TabelaErros({
  erros,
  onReprocessar,
}: {
  erros: NotaFiscal[]
  onReprocessar: (notaId: string) => Promise<void>
}) {
  const [processando, setProcessando] = useState<Set<string>>(new Set())

  const handleReprocessar = async (notaId: string) => {
    setProcessando(prev => new Set([...prev, notaId]))
    try {
      await onReprocessar(notaId)
    } finally {
      setProcessando(prev => {
        const next = new Set(prev)
        next.delete(notaId)
        return next
      })
    }
  }

  if (erros.length === 0) {
    return (
      <div className="text-center py-8 text-ds-light">
        <CheckCircle2 size={28} className="mx-auto mb-2 text-green-400" />
        <p className="text-sm">Nenhuma nota com erro neste lote.</p>
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-ds-border">
            {['Nota', 'Tomador', 'Status', 'Motivo', ''].map(h => (
              <th key={h} className="text-left py-2 px-3 text-[11px] font-semibold text-ds-light uppercase tracking-wide">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {erros.map(nota => {
            const isManual = nota.status === 'AGUARDANDO_EMISSAO_MANUAL'
            return (
              <tr key={nota.notaId} className="border-b border-ds-border hover:bg-ds-surface transition-colors">
                <td className="py-2.5 px-3 font-mono text-xs text-ds-light">
                  {nota.notaId.slice(0, 8)}…
                </td>
                <td className="py-2.5 px-3">
                  <span className="font-medium text-ds-mid">
                    {nota.tomadorNome ?? nota.tomadorId.slice(0, 8) + '…'}
                  </span>
                </td>
                <td className="py-2.5 px-3">
                  <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-semibold ${
                    isManual ? 'bg-orange-50 text-orange-700' : 'bg-red-50 text-red-700'
                  }`}>
                    <XCircle size={10} />
                    {isManual ? 'Emissão Manual' : 'Erro'}
                  </span>
                </td>
                <td className="py-2.5 px-3 max-w-xs">
                  <span className="text-xs text-ds-light line-clamp-2">
                    {nota.observacoes ?? '—'}
                  </span>
                </td>
                <td className="py-2.5 px-3 text-right">
                  <button
                    onClick={() => handleReprocessar(nota.notaId)}
                    disabled={processando.has(nota.notaId)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-primary-50 text-primary hover:bg-primary hover:text-white transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {processando.has(nota.notaId)
                      ? <Loader2 size={11} className="animate-spin" />
                      : <RotateCcw size={11} />
                    }
                    Reprocessar
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

// ─── Modal de Iniciar Lote ────────────────────────────────────────────────────

function ModalIniciar({
  onConfirmar,
  onFechar,
  loading,
  erro,
}: {
  onConfirmar: (comp: string) => void
  onFechar: () => void
  loading: boolean
  erro: string | null
}) {
  const [comp, setComp] = useState(competenciaAnterior())

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-50">
            <Layers size={18} className="text-primary" />
          </div>
          <div>
            <h2 className="font-bold text-ds-mid text-base">Iniciar Emissão em Lote</h2>
            <p className="text-xs text-ds-light">Selecione a competência para processar</p>
          </div>
        </div>

        <div className="mb-4">
          <label className="block text-xs font-semibold text-ds-mid mb-1.5">
            Competência (mês/ano)
          </label>
          <input
            type="month"
            value={comp}
            onChange={e => setComp(e.target.value)}
            className="w-full border border-ds-border rounded-xl px-3 py-2 text-sm text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          <p className="mt-1.5 text-xs text-ds-light">
            Todas as notas <strong>PENDENTE</strong> e <strong>ERRO</strong> desta competência serão enfileiradas.
          </p>
        </div>

        {erro && (
          <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded-xl mb-4 text-xs text-red-700">
            <AlertTriangle size={14} className="shrink-0 mt-0.5" />
            {erro}
          </div>
        )}

        <div className="flex gap-2 justify-end">
          <button
            onClick={onFechar}
            disabled={loading}
            className="px-4 py-2 text-sm font-medium text-ds-mid rounded-xl border border-ds-border hover:bg-ds-surface transition-colors disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            onClick={() => onConfirmar(comp)}
            disabled={loading || !comp}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-primary text-white rounded-xl hover:bg-primary-700 transition-colors disabled:opacity-50"
          >
            {loading ? <Loader2 size={14} className="animate-spin" /> : <Play size={14} />}
            Iniciar Lote
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── LotePage ─────────────────────────────────────────────────────────────────

export function LotePage() {
  const { user } = useAuth()
  const userRoles = user?.realm_access?.roles ?? []
  const podeIniciar = userRoles.some(r => ['gestao', 'operacao', 'contabil'].includes(r))

  const [lotes, setLotes] = useState<LoteProgressoResponse[]>([])
  const [loteAtivoId, setLoteAtivoId] = useState<string | null>(null)
  const [erros, setErros] = useState<NotaFiscal[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [iniciando, setIniciando] = useState(false)
  const [erroModal, setErroModal] = useState<string | null>(null)
  const [erroGlobal, setErroGlobal] = useState<string | null>(null)

  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const pararPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current)
      pollingRef.current = null
    }
  }, [])

  const carregarErros = useCallback(async (loteId: string) => {
    try {
      const data = await listarErrosLote(loteId)
      setErros(data)
    } catch {
      // silencioso — erros de carregamento não devem bloquear a UI
    }
  }, [])

  const atualizarProgresso = useCallback(async (loteId: string) => {
    try {
      const lote = await getProgressoLote(loteId)
      setLotes(prev => prev.map(l => l.loteId === loteId ? lote : l))
      if (lote.status !== 'EM_ANDAMENTO') {
        pararPolling()
        await carregarErros(loteId)
      }
    } catch {
      pararPolling()
    }
  }, [pararPolling, carregarErros])

  const selecionarLoteAtivo = useCallback(async (loteId: string) => {
    setLoteAtivoId(loteId)
    pararPolling()
    await carregarErros(loteId)

    // Iniciar polling só se estiver em andamento
    setLotes(prev => {
      const lote = prev.find(l => l.loteId === loteId)
      if (lote?.status === 'EM_ANDAMENTO') {
        pollingRef.current = setInterval(() => atualizarProgresso(loteId), 5000)
      }
      return prev
    })
  }, [pararPolling, carregarErros, atualizarProgresso])

  // Carregamento inicial
  useEffect(() => {
    const carregar = async () => {
      try {
        const data = await listarLotes()
        setLotes(data)
        const emAndamento = data.find(l => l.status === 'EM_ANDAMENTO')
        if (emAndamento) {
          setLoteAtivoId(emAndamento.loteId)
          await carregarErros(emAndamento.loteId)
          pollingRef.current = setInterval(() => atualizarProgresso(emAndamento.loteId), 5000)
        }
      } catch (e) {
        setErroGlobal('Erro ao carregar lotes. Verifique sua conexão.')
      } finally {
        setLoading(false)
      }
    }
    carregar()
    return pararPolling
  }, [pararPolling, carregarErros, atualizarProgresso])

  const handleIniciarLote = async (competencia: string) => {
    setIniciando(true)
    setErroModal(null)
    try {
      const lote = await emitirLote(competencia)
      setLotes(prev => [lote, ...prev])
      setLoteAtivoId(lote.loteId)
      setErros([])
      pararPolling()
      pollingRef.current = setInterval(() => atualizarProgresso(lote.loteId), 5000)
      setShowModal(false)
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Erro ao iniciar lote'
      setErroModal(msg.includes('409') || msg.toLowerCase().includes('andamento')
        ? 'Já existe um lote em andamento para esta competência.'
        : msg)
    } finally {
      setIniciando(false)
    }
  }

  const handleReprocessar = async (notaId: string) => {
    await reprocessarNota(notaId)
    setErros(prev => prev.filter(n => n.notaId !== notaId))
    if (loteAtivoId) {
      const loteAtualizado = await getProgressoLote(loteAtivoId)
      setLotes(prev => prev.map(l => l.loteId === loteAtivoId ? loteAtualizado : l))
    }
  }

  const loteAtivo = lotes.find(l => l.loteId === loteAtivoId)
  const historico = lotes.filter(l => l.loteId !== loteAtivoId)

  return (
    <div className="flex flex-col flex-1 overflow-hidden">
      {/* Header */}
      <div className="bg-white border-b border-ds-border px-6 py-4 flex items-center justify-between shrink-0">
        <div>
          <div className="flex items-center gap-2">
            <PackageCheck size={20} className="text-primary" />
            <h1 className="text-lg font-bold text-ds-mid">Emissão em Lote</h1>
          </div>
          <p className="text-xs text-ds-light mt-0.5">
            Job automático: dia 1 de cada mês às 02:00 · Competência anterior
          </p>
        </div>
        {podeIniciar && (
          <button
            onClick={() => { setErroModal(null); setShowModal(true) }}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-primary text-white rounded-xl hover:bg-primary-700 transition-colors shadow-sm"
          >
            <Play size={14} />
            Iniciar Lote Manual
          </button>
        )}
      </div>

      {/* Body */}
      <div className="flex-1 overflow-auto bg-ds-surface">
        <div className="max-w-3xl mx-auto px-6 py-6 space-y-6">

          {erroGlobal && (
            <div className="flex items-start gap-2 p-4 bg-red-50 border border-red-200 rounded-xl text-sm text-red-700">
              <AlertTriangle size={16} className="shrink-0 mt-0.5" />
              {erroGlobal}
            </div>
          )}

          {loading ? (
            <div className="text-center py-16 text-ds-light">
              <Loader2 size={28} className="mx-auto mb-3 animate-spin text-primary" />
              <p className="text-sm">Carregando lotes…</p>
            </div>
          ) : (
            <>
              {/* Lote selecionado */}
              {loteAtivo ? (
                <section>
                  <h2 className="text-xs font-semibold text-ds-light uppercase tracking-wider mb-3">
                    {loteAtivo.status === 'EM_ANDAMENTO' ? 'Processamento Atual' : 'Lote Selecionado'}
                  </h2>
                  <ProgressoCard lote={loteAtivo} />

                  {/* Lista de erros */}
                  {(loteAtivo.status !== 'EM_ANDAMENTO' || loteAtivo.falhas > 0) && (
                    <div className="mt-4 bg-white rounded-2xl border border-ds-border overflow-hidden">
                      <div className="px-5 py-3 border-b border-ds-border flex items-center justify-between">
                        <h3 className="text-sm font-semibold text-ds-mid flex items-center gap-2">
                          <XCircle size={14} className="text-red-500" />
                          Notas com Erro
                          {erros.length > 0 && (
                            <span className="bg-red-100 text-red-700 text-xs font-bold px-2 py-0.5 rounded-full">
                              {erros.length}
                            </span>
                          )}
                        </h3>
                        {loteAtivo.status === 'EM_ANDAMENTO' && (
                          <span className="text-xs text-ds-light italic">Atualizando em tempo real…</span>
                        )}
                      </div>
                      <div className="p-1">
                        <TabelaErros erros={erros} onReprocessar={handleReprocessar} />
                      </div>
                    </div>
                  )}
                </section>
              ) : !loading && lotes.length === 0 ? (
                <div className="text-center py-16 text-ds-light bg-white rounded-2xl border border-ds-border">
                  <Layers size={36} className="mx-auto mb-3 text-ds-light" />
                  <p className="text-sm font-medium text-ds-mid mb-1">Nenhum lote encontrado</p>
                  <p className="text-xs">
                    {podeIniciar
                      ? 'Clique em "Iniciar Lote Manual" para processar notas pendentes.'
                      : 'Aguarde o job automático do dia 1 de cada mês.'}
                  </p>
                </div>
              ) : null}

              {/* Histórico */}
              {historico.length > 0 && (
                <section>
                  <h2 className="text-xs font-semibold text-ds-light uppercase tracking-wider mb-3">
                    Histórico de Lotes
                  </h2>
                  <div className="bg-white rounded-2xl border border-ds-border overflow-hidden">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-ds-border">
                          {['Competência', 'Total', 'Emitidas', 'Erros', 'Status', 'Data', ''].map(h => (
                            <th key={h} className="text-left py-2.5 px-4 text-[11px] font-semibold text-ds-light uppercase tracking-wide">
                              {h}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {historico.map(lote => {
                          const cfg = {
                            CONCLUIDO:    { cls: 'bg-green-50 text-green-700', label: 'Concluído' },
                            FALHA:        { cls: 'bg-red-50 text-red-700',     label: 'Com falhas' },
                            EM_ANDAMENTO: { cls: 'bg-primary-50 text-primary', label: 'Em andamento' },
                          }[lote.status] ?? { cls: 'bg-ds-surface text-ds-mid', label: lote.status }
                          return (
                            <tr
                              key={lote.loteId}
                              className="border-b border-ds-border hover:bg-ds-surface transition-colors cursor-pointer last:border-0"
                              onClick={() => selecionarLoteAtivo(lote.loteId)}
                            >
                              <td className="py-3 px-4 font-medium text-ds-mid">
                                {formatarCompetencia(lote.competencia)}
                              </td>
                              <td className="py-3 px-4 tabular-nums">{lote.total}</td>
                              <td className="py-3 px-4 tabular-nums text-green-700 font-medium">{lote.emitidas}</td>
                              <td className="py-3 px-4 tabular-nums text-red-700 font-medium">
                                {lote.falhas > 0 ? lote.falhas : <span className="text-ds-light">—</span>}
                              </td>
                              <td className="py-3 px-4">
                                <span className={`inline-block px-2 py-0.5 rounded-md text-[11px] font-semibold ${cfg.cls}`}>
                                  {cfg.label}
                                </span>
                              </td>
                              <td className="py-3 px-4 text-xs text-ds-light">
                                {formatarData(lote.iniciadoEm)}
                              </td>
                              <td className="py-3 px-4">
                                <ChevronRight size={14} className="text-ds-light" />
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}
            </>
          )}
        </div>
      </div>

      {/* Modal */}
      {showModal && (
        <ModalIniciar
          onConfirmar={handleIniciarLote}
          onFechar={() => setShowModal(false)}
          loading={iniciando}
          erro={erroModal}
        />
      )}
    </div>
  )
}
