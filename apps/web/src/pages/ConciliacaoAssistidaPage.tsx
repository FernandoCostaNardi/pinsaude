import { useCallback, useEffect, useState } from 'react'
import {
  ArrowLeftRight,
  Bot,
  CheckCircle2,
  ChevronRight,
  EyeOff,
  Loader2,
  RefreshCw,
  Search,
  X,
  XCircle,
} from 'lucide-react'
import {
  CandidatoMatchResponse,
  ExtratoResponse,
  LancamentoExtratoResponse,
  ProducaoResumo,
  StatusConciliacao,
  conciliarLancamento,
  desfazerConciliacao,
  getSugestoes,
  ignorarLancamento,
  listarExtratos,
  listarLancamentos,
  listarProducoesParaBusca,
} from '../api/conciliacaoApi'
import { useAuth } from '../auth/useAuth'

const BANCO_LABEL: Record<string, string> = {
  INTER: 'Banco Inter',
  BTG: 'BTG Pactual',
  OUTRO: 'Outro',
}

const STATUS_CFG = {
  PENDENTE: {
    label: 'Pendente',
    cls: 'bg-yellow-50 text-yellow-700',
    icon: <span className="w-2 h-2 rounded-full bg-yellow-400 inline-block" />,
  },
  CONCILIADO: {
    label: 'Conciliado',
    cls: 'bg-green-50 text-green-700',
    icon: <CheckCircle2 size={13} className="text-green-600" />,
  },
  IGNORADO: {
    label: 'Ignorado',
    cls: 'bg-ds-surface text-ds-light',
    icon: <EyeOff size={13} className="text-ds-light" />,
  },
} as const

function formatBRL(centavos: number) {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(iso: string) {
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

function formatCompetencia(c: string | null) {
  if (!c) return '—'
  const [y, m] = c.split('-')
  return `${m}/${y}`
}

// ─── Painel de detalhes/ações ────────────────────────────────────────────────

interface DetalhePainelProps {
  lancamento: LancamentoExtratoResponse
  onConciliar: (lancId: string, prodId: string) => Promise<void>
  onIgnorar: (lancId: string) => Promise<void>
  onDesfazer: (lancId: string) => Promise<void>
  onBuscarManual: () => void
  onFechar: () => void
  candidates: CandidatoMatchResponse[] | null
  loadingCandidates: boolean
}

function DetalhePanel({
  lancamento,
  onConciliar,
  onIgnorar,
  onDesfazer,
  onBuscarManual,
  onFechar,
  candidates,
  loadingCandidates,
}: DetalhePainelProps) {
  const [busy, setBusy] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const cfg = STATUS_CFG[lancamento.statusConciliacao]

  async function handleAceitar(producaoId: string) {
    setBusy(true); setErro(null)
    try {
      await onConciliar(lancamento.id, producaoId)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao conciliar')
    } finally {
      setBusy(false)
    }
  }

  async function handleIgnorar() {
    setBusy(true); setErro(null)
    try {
      await onIgnorar(lancamento.id)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao ignorar')
    } finally {
      setBusy(false)
    }
  }

  async function handleDesfazer() {
    setBusy(true); setErro(null)
    try {
      await onDesfazer(lancamento.id)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao desfazer')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header do painel */}
      <div className="flex items-start justify-between gap-2 px-5 py-4 border-b border-ds-border shrink-0">
        <div className="min-w-0">
          <p className="text-[11px] font-semibold text-ds-light uppercase tracking-wide mb-1">
            Lançamento
          </p>
          <p className="font-semibold text-ds-text text-sm leading-snug line-clamp-2">
            {lancamento.descricao}
          </p>
          <div className="flex items-center gap-1.5 mt-2 flex-wrap">
            <span className="text-xs text-ds-light">{formatDate(lancamento.dataLancamento)}</span>
            <span
              className={`text-xs px-1.5 py-0.5 rounded font-semibold ${
                lancamento.tipo === 'CREDITO'
                  ? 'bg-blue-50 text-blue-700'
                  : 'bg-orange-50 text-orange-700'
              }`}
            >
              {lancamento.tipo === 'CREDITO' ? 'Crédito' : 'Débito'}
            </span>
            <span className={`text-xs flex items-center gap-1 px-1.5 py-0.5 rounded font-semibold ${cfg.cls}`}>
              {cfg.icon} {cfg.label}
            </span>
          </div>
          <p className="text-xl font-black text-ds-text mt-2">{formatBRL(lancamento.valorCentavos)}</p>
          {lancamento.scoreMatch > 0 && lancamento.statusConciliacao === 'PENDENTE' && (
            <p className="text-xs text-primary mt-1">✨ Score de match: {lancamento.scoreMatch}</p>
          )}
        </div>
        <button
          onClick={onFechar}
          className="shrink-0 text-ds-light hover:text-ds-mid p-1 rounded-lg hover:bg-ds-surface transition-colors"
        >
          <X size={15} />
        </button>
      </div>

      {/* Conteúdo */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {/* CONCILIADO */}
        {lancamento.statusConciliacao === 'CONCILIADO' && lancamento.conciliacao && (
          <div className="bg-green-50 border border-green-100 rounded-xl p-3.5 space-y-1.5">
            <div className="flex items-center gap-1.5 text-green-700 text-xs font-semibold uppercase tracking-wide">
              <CheckCircle2 size={13} />
              Conciliado
              {lancamento.conciliacao.tipoMatch === 'AUTOMATICO' && (
                <span className="ml-auto flex items-center gap-1 text-xs font-normal text-green-600">
                  <Bot size={12} /> Automático
                </span>
              )}
            </div>
            {lancamento.conciliacao.tomadorNome && (
              <p className="text-sm font-semibold text-ds-text">{lancamento.conciliacao.tomadorNome}</p>
            )}
            <div className="flex gap-3 text-xs text-ds-mid">
              {lancamento.conciliacao.competencia && (
                <span>{formatCompetencia(lancamento.conciliacao.competencia)}</span>
              )}
              {lancamento.conciliacao.valorBruto > 0 && (
                <span>{formatBRL(lancamento.conciliacao.valorBruto)}</span>
              )}
              {lancamento.conciliacao.scoreConfianca > 0 && (
                <span className="text-primary font-semibold">Score {lancamento.conciliacao.scoreConfianca}</span>
              )}
            </div>
          </div>
        )}

        {/* PENDENTE — sugestões */}
        {lancamento.statusConciliacao === 'PENDENTE' && (
          <>
            {loadingCandidates ? (
              <div className="flex items-center gap-2 text-ds-mid text-sm py-3">
                <Loader2 size={15} className="animate-spin" /> Buscando sugestões…
              </div>
            ) : candidates && candidates.length > 0 ? (
              <div className="space-y-2">
                <p className="text-[11px] font-semibold text-ds-light uppercase tracking-wide">
                  Sugestões ({candidates.length})
                </p>
                {candidates.map((c) => (
                  <div
                    key={c.producaoId}
                    className="border border-ds-border rounded-xl p-3 bg-white hover:border-primary transition-colors"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ds-text truncate">{c.tomadorNome}</p>
                        <div className="flex gap-2 text-xs text-ds-mid mt-0.5">
                          <span>{formatCompetencia(c.competencia)}</span>
                          <span>{formatBRL(c.valorBruto)}</span>
                          <span className="text-primary font-semibold">Score {c.score}</span>
                        </div>
                      </div>
                      <button
                        disabled={busy}
                        onClick={() => handleAceitar(c.producaoId)}
                        className="shrink-0 text-xs px-3 py-1.5 bg-primary text-white rounded-lg hover:bg-primary-700 disabled:opacity-50 transition-colors font-semibold"
                      >
                        Aceitar
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : candidates !== null ? (
              <p className="text-sm text-ds-light py-3">Nenhuma sugestão automática encontrada.</p>
            ) : null}
          </>
        )}

        {/* IGNORADO */}
        {lancamento.statusConciliacao === 'IGNORADO' && (
          <div className="bg-ds-surface border border-ds-border rounded-xl p-3.5 text-sm text-ds-mid">
            Este lançamento está marcado como ignorado.
          </div>
        )}

        {erro && (
          <div className="bg-red-50 text-red-700 text-sm rounded-lg p-2.5">{erro}</div>
        )}
      </div>

      {/* Ações */}
      <div className="p-4 border-t border-ds-border space-y-2 shrink-0">
        {lancamento.statusConciliacao === 'PENDENTE' && (
          <>
            <button
              disabled={busy}
              onClick={onBuscarManual}
              className="w-full flex items-center justify-center gap-2 py-2 border border-ds-border rounded-lg text-sm text-ds-mid hover:bg-ds-surface disabled:opacity-50 transition-colors"
            >
              <Search size={14} /> Buscar produção manualmente
            </button>
            <button
              disabled={busy}
              onClick={handleIgnorar}
              className="w-full flex items-center justify-center gap-2 py-2 border border-red-200 rounded-lg text-sm text-red-600 hover:bg-red-50 disabled:opacity-50 transition-colors"
            >
              <EyeOff size={14} /> Ignorar lançamento
            </button>
          </>
        )}
        {lancamento.statusConciliacao === 'CONCILIADO' && (
          <>
            <button
              disabled={busy}
              onClick={onBuscarManual}
              className="w-full flex items-center justify-center gap-2 py-2 border border-ds-border rounded-lg text-sm text-ds-mid hover:bg-ds-surface disabled:opacity-50 transition-colors"
            >
              <Search size={14} /> Trocar produção
            </button>
            <button
              disabled={busy}
              onClick={handleDesfazer}
              className="w-full flex items-center justify-center gap-2 py-2 border border-orange-200 rounded-lg text-sm text-orange-600 hover:bg-orange-50 disabled:opacity-50 transition-colors"
            >
              <RefreshCw size={14} /> Desfazer conciliação
            </button>
          </>
        )}
        {lancamento.statusConciliacao === 'IGNORADO' && (
          <button
            disabled={busy}
            onClick={handleDesfazer}
            className="w-full flex items-center justify-center gap-2 py-2 border border-ds-border rounded-lg text-sm text-ds-mid hover:bg-ds-surface disabled:opacity-50 transition-colors"
          >
            <RefreshCw size={14} /> Reativar para pendente
          </button>
        )}
        {busy && (
          <div className="flex justify-center pt-1">
            <Loader2 size={15} className="animate-spin text-primary" />
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Modal busca manual ──────────────────────────────────────────────────────

interface BuscaModalProps {
  producoes: ProducaoResumo[]
  loading: boolean
  onSelecionar: (producaoId: string) => void
  onFechar: () => void
}

function BuscaModal({ producoes, loading, onSelecionar, onFechar }: BuscaModalProps) {
  const [q, setQ] = useState('')
  const filtradas = producoes.filter((p) => {
    if (!q) return true
    const ql = q.toLowerCase()
    return (
      p.tomadorNome.toLowerCase().includes(ql) ||
      (p.municipio ?? '').toLowerCase().includes(ql) ||
      p.competencia.includes(ql) ||
      formatBRL(p.valorBruto).includes(ql)
    )
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 flex flex-col max-h-[80vh]">
        <div className="flex items-center justify-between px-5 py-4 border-b border-ds-border">
          <div>
            <h2 className="font-bold text-ds-text">Buscar produção</h2>
            <p className="text-xs text-ds-light mt-0.5">
              Apenas produções com NF emitida ainda não conciliadas
            </p>
          </div>
          <button onClick={onFechar} className="text-ds-light hover:text-ds-mid p-1 rounded-lg hover:bg-ds-surface transition-colors">
            <X size={18} />
          </button>
        </div>
        <div className="p-3 border-b border-ds-border">
          <div className="relative">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light" />
            <input
              autoFocus
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Buscar por tomador, município, competência ou valor…"
              className="w-full pl-9 pr-3 py-2 text-sm border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-text placeholder:text-ds-light"
            />
          </div>
        </div>
        <div className="flex-1 overflow-y-auto divide-y divide-ds-border">
          {loading ? (
            <div className="flex items-center gap-2 p-4 text-ds-mid text-sm">
              <Loader2 size={15} className="animate-spin" /> Carregando produções…
            </div>
          ) : filtradas.length === 0 && !loading ? (
            <div className="p-5 text-center">
              <p className="text-sm text-ds-mid font-medium">
                {q ? 'Nenhuma produção encontrada para esta busca.' : 'Nenhuma produção com NF emitida aguardando conciliação.'}
              </p>
              {!q && (
                <p className="text-xs text-ds-light mt-1">
                  Todas as produções emitidas já foram conciliadas.
                </p>
              )}
            </div>
          ) : (
            filtradas.map((p) => (
              <button
                key={p.id}
                onClick={() => onSelecionar(p.id)}
                className="w-full text-left px-4 py-3 hover:bg-ds-surface transition-colors group"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-ds-text truncate">{p.tomadorNome}</p>
                    <div className="flex gap-2 text-xs text-ds-mid mt-0.5 flex-wrap">
                      <span>{formatCompetencia(p.competencia)}</span>
                      {p.municipio && <span className="text-ds-light">· {p.municipio}</span>}
                    </div>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-black text-ds-text">{formatBRL(p.valorBruto)}</p>
                    <span className="text-[10px] bg-green-50 text-green-700 font-semibold px-1.5 py-0.5 rounded">
                      NF Emitida
                    </span>
                  </div>
                </div>
              </button>
            ))
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Componente principal ────────────────────────────────────────────────────

type TabKey = 'TODOS' | 'PENDENTE' | 'SUGESTAO' | 'CONCILIADO' | 'IGNORADO'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'TODOS',      label: 'Todos'        },
  { key: 'PENDENTE',   label: 'Pendentes'    },
  { key: 'SUGESTAO',   label: 'Com sugestão' },
  { key: 'CONCILIADO', label: 'Conciliados'  },
  { key: 'IGNORADO',   label: 'Ignorados'    },
]

export function ConciliacaoAssistidaPage() {
  const { user } = useAuth()

  const roles = user?.realm_access?.roles ?? []
  const temAcesso = roles.some((r) =>
    ['operacao', 'gestao', 'financeiro', 'contabil'].includes(r),
  )

  const [extratos, setExtratos] = useState<ExtratoResponse[]>([])
  const [extratoId, setExtratoId] = useState<string>('')
  const [lancamentos, setLancamentos] = useState<LancamentoExtratoResponse[]>([])
  const [loadingExtratos, setLoadingExtratos] = useState(true)
  const [loadingLanc, setLoadingLanc] = useState(false)
  const [tab, setTab] = useState<TabKey>('TODOS')
  const [selecionado, setSelecionado] = useState<LancamentoExtratoResponse | null>(null)

  const [candidatesMap, setCandidatesMap] = useState<Record<string, CandidatoMatchResponse[] | null>>({})
  const [loadingCandidates, setLoadingCandidates] = useState(false)

  const [modalAberto, setModalAberto] = useState(false)
  const [producoes, setProducoes] = useState<ProducaoResumo[]>([])
  const [loadingProducoes, setLoadingProducoes] = useState(false)

  const extratoSel = extratos.find((e) => e.id === extratoId)

  function carregarExtratos() {
    setLoadingExtratos(true)
    listarExtratos()
      .then((data) => {
        setExtratos(data.filter((e) => e.statusImportacao === 'OK'))
        setLoadingExtratos(false)
      })
      .catch(() => setLoadingExtratos(false))
  }

  useEffect(() => { if (temAcesso) carregarExtratos() }, [temAcesso])

  useEffect(() => {
    if (!extratoId) { setLancamentos([]); return }
    setLoadingLanc(true)
    setSelecionado(null)
    setCandidatesMap({})
    listarLancamentos(extratoId)
      .then((data) => { setLancamentos(data); setLoadingLanc(false) })
      .catch(() => setLoadingLanc(false))
  }, [extratoId])

  const filtrados = lancamentos.filter((l) => {
    if (tab === 'TODOS') return true
    if (tab === 'SUGESTAO') return l.statusConciliacao === 'PENDENTE' && l.scoreMatch > 0
    return l.statusConciliacao === (tab as StatusConciliacao)
  })

  const counts = {
    TODOS:      lancamentos.length,
    PENDENTE:   lancamentos.filter((l) => l.statusConciliacao === 'PENDENTE').length,
    SUGESTAO:   lancamentos.filter((l) => l.statusConciliacao === 'PENDENTE' && l.scoreMatch > 0).length,
    CONCILIADO: lancamentos.filter((l) => l.statusConciliacao === 'CONCILIADO').length,
    IGNORADO:   lancamentos.filter((l) => l.statusConciliacao === 'IGNORADO').length,
  }

  const handleSelecionarLanc = useCallback(async (l: LancamentoExtratoResponse) => {
    setSelecionado(l)
    if (l.statusConciliacao !== 'PENDENTE') return
    if (candidatesMap[l.id] !== undefined) return
    setLoadingCandidates(true)
    try {
      const cands = await getSugestoes(l.id)
      setCandidatesMap((prev) => ({ ...prev, [l.id]: cands }))
    } catch {
      setCandidatesMap((prev) => ({ ...prev, [l.id]: [] }))
    } finally {
      setLoadingCandidates(false)
    }
  }, [candidatesMap])

  async function recarregarLancamentos() {
    if (!extratoId) return
    const data = await listarLancamentos(extratoId).catch(() => lancamentos)
    setLancamentos(data)
    if (selecionado) {
      const atualizado = data.find((l) => l.id === selecionado.id)
      setSelecionado(atualizado ?? null)
    }
  }

  const handleConciliar = useCallback(async (lancId: string, prodId: string) => {
    await conciliarLancamento(lancId, prodId)
    setCandidatesMap((prev) =>
      Object.fromEntries(Object.entries(prev).filter(([k]) => k !== lancId)) as Record<string, CandidatoMatchResponse[] | null>
    )
    await recarregarLancamentos()
  }, [extratoId, selecionado, lancamentos])

  const handleIgnorar = useCallback(async (lancId: string) => {
    await ignorarLancamento(lancId)
    await recarregarLancamentos()
  }, [extratoId, selecionado, lancamentos])

  const handleDesfazer = useCallback(async (lancId: string) => {
    await desfazerConciliacao(lancId)
    setCandidatesMap((prev) =>
      Object.fromEntries(Object.entries(prev).filter(([k]) => k !== lancId)) as Record<string, CandidatoMatchResponse[] | null>
    )
    await recarregarLancamentos()
  }, [extratoId, selecionado, lancamentos])

  async function abrirModal() {
    setModalAberto(true)
    if (producoes.length === 0) {
      setLoadingProducoes(true)
      const data = await listarProducoesParaBusca().catch(() => [])
      setProducoes(data)
      setLoadingProducoes(false)
    }
  }

  async function handleSelecionarProducao(producaoId: string) {
    setModalAberto(false)
    if (!selecionado) return
    try {
      await conciliarLancamento(selecionado.id, producaoId, 'Conciliação manual')
      setCandidatesMap((prev) =>
        Object.fromEntries(Object.entries(prev).filter(([k]) => k !== selecionado.id)) as Record<string, CandidatoMatchResponse[] | null>
      )
      await recarregarLancamentos()
    } catch (e: unknown) {
      console.error('Erro ao conciliar:', e instanceof Error ? e.message : e)
    }
  }

  if (!temAcesso) {
    return (
      <div className="flex-1 flex items-center justify-center text-ds-light">
        <div className="text-center">
          <XCircle size={40} className="mx-auto mb-2" />
          <p className="text-sm">Acesso não autorizado.</p>
        </div>
      </div>
    )
  }

  return (
    <>
      {modalAberto && selecionado && (
        <BuscaModal
          producoes={producoes}
          loading={loadingProducoes}
          onSelecionar={handleSelecionarProducao}
          onFechar={() => setModalAberto(false)}
        />
      )}

      <div className="flex-1 overflow-hidden flex flex-col">

        {/* Header da página */}
        <div className="bg-white border-b border-ds-border px-6 py-4 shrink-0">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-ds-text">Conciliação Assistida</h1>
              <p className="text-xs text-ds-light mt-0.5">
                Vincule lançamentos bancários às produções correspondentes
              </p>
            </div>
            <button
              onClick={carregarExtratos}
              disabled={loadingExtratos}
              className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-ds-border text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors text-sm"
            >
              <RefreshCw size={14} className={loadingExtratos ? 'animate-spin' : ''} />
              Atualizar
            </button>
          </div>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-hidden flex p-5 gap-5">

          {/* Card principal */}
          <div className="flex-1 bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden flex flex-col min-w-0">

            {/* Seletor de extrato */}
            <div className="px-5 py-3.5 border-b border-ds-border shrink-0 flex items-center gap-4 flex-wrap">
              <div className="flex items-center gap-2">
                <label className="text-xs font-semibold text-ds-mid whitespace-nowrap">Extrato</label>
                {loadingExtratos ? (
                  <Loader2 size={15} className="animate-spin text-ds-light" />
                ) : (
                  <select
                    value={extratoId}
                    onChange={(e) => setExtratoId(e.target.value)}
                    className="text-sm border border-ds-border rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-text min-w-[280px]"
                  >
                    <option value="">— Selecione um extrato —</option>
                    {extratos.map((e) => (
                      <option key={e.id} value={e.id}>
                        {BANCO_LABEL[e.banco] ?? e.banco} · {formatDate(e.periodoInicio)} → {formatDate(e.periodoFim)} · {e.nomeArquivo}
                      </option>
                    ))}
                  </select>
                )}
              </div>

              {extratoSel && (
                <div className="flex items-center gap-4 ml-auto">
                  {[
                    { label: 'Total',       value: counts.TODOS,      cls: 'bg-ds-surface text-ds-mid'       },
                    { label: 'Conciliados', value: counts.CONCILIADO, cls: 'bg-green-50 text-green-700'      },
                    { label: 'Pendentes',   value: counts.PENDENTE,   cls: 'bg-yellow-50 text-yellow-700'    },
                    { label: 'Ignorados',   value: counts.IGNORADO,   cls: 'bg-ds-surface text-ds-light'     },
                  ].map((s) => (
                    <div key={s.label} className="text-center">
                      <span className={`text-xs font-black px-2 py-0.5 rounded-md ${s.cls}`}>
                        {s.value}
                      </span>
                      <p className="text-[10px] text-ds-light mt-0.5">{s.label}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {!extratoId ? (
              <div className="flex-1 flex items-center justify-center text-ds-light">
                <div className="text-center">
                  <ArrowLeftRight size={40} className="mx-auto mb-3 opacity-20" />
                  <p className="text-sm font-medium text-ds-mid">Selecione um extrato para iniciar</p>
                  <p className="text-xs text-ds-light mt-1">Os lançamentos bancários aparecerão aqui</p>
                </div>
              </div>
            ) : (
              <>
                {/* Abas */}
                <div className="flex items-center px-5 border-b border-ds-border shrink-0 overflow-x-auto">
                  {TABS.map((t) => (
                    <button
                      key={t.key}
                      onClick={() => { setTab(t.key); setSelecionado(null) }}
                      className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors -mb-px whitespace-nowrap ${
                        tab === t.key
                          ? 'border-primary text-primary'
                          : 'border-transparent text-ds-light hover:text-ds-mid'
                      }`}
                    >
                      {t.label}
                      <span
                        className={`text-xs px-1.5 py-0.5 rounded-full font-semibold ${
                          tab === t.key ? 'bg-primary-100 text-primary-700' : 'bg-ds-surface text-ds-light'
                        }`}
                      >
                        {counts[t.key]}
                      </span>
                    </button>
                  ))}
                </div>

                {/* Lista */}
                <div className="flex-1 overflow-auto">
                  {loadingLanc ? (
                    <div className="flex items-center gap-2 p-6 text-ds-mid text-sm">
                      <Loader2 size={17} className="animate-spin" /> Carregando lançamentos…
                    </div>
                  ) : filtrados.length === 0 ? (
                    <div className="flex items-center justify-center h-40 text-ds-light text-sm">
                      Nenhum lançamento {tab !== 'TODOS' ? 'nesta categoria' : ''}.
                    </div>
                  ) : (
                    <div className="divide-y divide-ds-border">
                      {filtrados.map((l) => {
                        const cfg = STATUS_CFG[l.statusConciliacao]
                        const isSel = selecionado?.id === l.id
                        return (
                          <button
                            key={l.id}
                            onClick={() => handleSelecionarLanc(l)}
                            className={`w-full text-left px-5 py-3.5 flex items-center gap-3 transition-colors border-l-2 ${
                              isSel && l.statusConciliacao === 'CONCILIADO'
                                ? 'bg-green-100 border-green-500'
                                : isSel
                                ? 'bg-primary-50 border-primary'
                                : l.statusConciliacao === 'CONCILIADO'
                                ? 'bg-green-50 border-green-300 hover:bg-green-100'
                                : 'hover:bg-ds-surface/50 border-transparent'
                            }`}
                          >
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 mb-1">
                                {cfg.icon}
                                <span className="text-xs text-ds-light">{formatDate(l.dataLancamento)}</span>
                                <span
                                  className={`text-[11px] font-semibold px-1.5 py-0.5 rounded ${
                                    l.tipo === 'CREDITO'
                                      ? 'bg-blue-50 text-blue-600'
                                      : 'bg-orange-50 text-orange-600'
                                  }`}
                                >
                                  {l.tipo === 'CREDITO' ? 'CR' : 'DB'}
                                </span>
                                {l.statusConciliacao === 'PENDENTE' && l.scoreMatch > 0 && (
                                  <span className="text-[11px] text-primary font-semibold">✨ {l.scoreMatch}</span>
                                )}
                                {l.statusConciliacao === 'CONCILIADO' && l.conciliacao?.tipoMatch === 'AUTOMATICO' && (
                                  <span className="flex items-center gap-0.5 text-[11px] text-green-600 font-semibold">
                                    <Bot size={11} /> Auto
                                  </span>
                                )}
                              </div>
                              <p className="text-sm text-ds-text truncate">{l.descricao}</p>
                              {l.statusConciliacao === 'CONCILIADO' && l.conciliacao?.tomadorNome && (
                                <p className="text-xs text-green-600 truncate mt-0.5 font-medium">
                                  → {l.conciliacao.tomadorNome}
                                </p>
                              )}
                            </div>
                            <div className="text-right shrink-0">
                              <p className={`text-sm font-black ${l.statusConciliacao === 'CONCILIADO' ? 'text-green-700' : 'text-ds-text'}`}>
                                {formatBRL(l.valorCentavos)}
                              </p>
                            </div>
                            <ChevronRight size={14} className="text-ds-light shrink-0" />
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>
              </>
            )}
          </div>

          {/* Painel lateral de detalhe */}
          {selecionado && (
            <div className="w-80 xl:w-96 bg-white rounded-xl border border-ds-border shadow-sm flex flex-col shrink-0 overflow-hidden">
              <DetalhePanel
                lancamento={selecionado}
                candidates={candidatesMap[selecionado.id] ?? null}
                loadingCandidates={loadingCandidates && candidatesMap[selecionado.id] === undefined}
                onConciliar={handleConciliar}
                onIgnorar={handleIgnorar}
                onDesfazer={handleDesfazer}
                onBuscarManual={abrirModal}
                onFechar={() => setSelecionado(null)}
              />
            </div>
          )}
        </div>
      </div>
    </>
  )
}
