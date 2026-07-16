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
    cls: 'bg-yellow-100 text-yellow-800',
    icon: <span className="w-2 h-2 rounded-full bg-yellow-500 inline-block" />,
  },
  CONCILIADO: {
    label: 'Conciliado',
    cls: 'bg-green-100 text-green-800',
    icon: <CheckCircle2 size={14} className="text-green-600" />,
  },
  IGNORADO: {
    label: 'Ignorado',
    cls: 'bg-gray-100 text-gray-500',
    icon: <EyeOff size={14} className="text-gray-400" />,
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

// ─── Painel de detalhes/ações ───────────────────────────────────────────────

interface DetalhePainelProps {
  lancamento: LancamentoExtratoResponse
  onConciliar: (lancId: string, prodId: string) => Promise<void>
  onIgnorar: (lancId: string) => Promise<void>
  onDesfazer: (lancId: string) => Promise<void>
  onBuscarManual: () => void
  candidates: CandidatoMatchResponse[] | null
  loadingCandidates: boolean
}

function DetalhePanel({
  lancamento,
  onConciliar,
  onIgnorar,
  onDesfazer,
  onBuscarManual,
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
      {/* header */}
      <div className="p-4 border-b border-gray-100">
        <p className="text-xs text-gray-400 uppercase tracking-wide mb-1">Lançamento</p>
        <p className="font-semibold text-gray-800 text-sm leading-snug line-clamp-2">
          {lancamento.descricao}
        </p>
        <div className="flex items-center gap-2 mt-2 flex-wrap">
          <span className="text-xs text-gray-500">{formatDate(lancamento.dataLancamento)}</span>
          <span
            className={`text-xs px-1.5 py-0.5 rounded font-medium ${
              lancamento.tipo === 'CREDITO'
                ? 'bg-blue-50 text-blue-700'
                : 'bg-orange-50 text-orange-700'
            }`}
          >
            {lancamento.tipo === 'CREDITO' ? 'Crédito' : 'Débito'}
          </span>
          <span
            className={`text-xs flex items-center gap-1 px-1.5 py-0.5 rounded font-medium ${cfg.cls}`}
          >
            {cfg.icon} {cfg.label}
          </span>
        </div>
        <p className="text-xl font-bold text-gray-900 mt-2">{formatBRL(lancamento.valorCentavos)}</p>
        {lancamento.scoreMatch > 0 && lancamento.statusConciliacao === 'PENDENTE' && (
          <p className="text-xs text-primary mt-1">✨ Score de match: {lancamento.scoreMatch}</p>
        )}
      </div>

      {/* conteúdo */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* CONCILIADO */}
        {lancamento.statusConciliacao === 'CONCILIADO' && lancamento.conciliacao && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-3 space-y-1">
            <div className="flex items-center gap-1.5 text-green-700 text-xs font-semibold uppercase">
              <CheckCircle2 size={14} />
              Conciliado
              {lancamento.conciliacao.tipoMatch === 'AUTOMATICO' && (
                <span className="ml-auto flex items-center gap-1 text-xs font-normal">
                  <Bot size={12} /> Automático
                </span>
              )}
            </div>
            {lancamento.conciliacao.tomadorNome && (
              <p className="text-sm font-medium text-gray-800">{lancamento.conciliacao.tomadorNome}</p>
            )}
            <div className="flex gap-3 text-xs text-gray-600">
              {lancamento.conciliacao.competencia && (
                <span>{formatCompetencia(lancamento.conciliacao.competencia)}</span>
              )}
              {lancamento.conciliacao.valorBruto > 0 && (
                <span>{formatBRL(lancamento.conciliacao.valorBruto)}</span>
              )}
              {lancamento.conciliacao.scoreConfianca > 0 && (
                <span>Score: {lancamento.conciliacao.scoreConfianca}</span>
              )}
            </div>
          </div>
        )}

        {/* PENDENTE — sugestões */}
        {lancamento.statusConciliacao === 'PENDENTE' && (
          <>
            {loadingCandidates ? (
              <div className="flex items-center gap-2 text-gray-500 text-sm py-2">
                <Loader2 size={16} className="animate-spin" /> Buscando sugestões…
              </div>
            ) : candidates && candidates.length > 0 ? (
              <div className="space-y-2">
                <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                  Sugestões ({candidates.length})
                </p>
                {candidates.map((c) => (
                  <div
                    key={c.producaoId}
                    className="border border-gray-200 rounded-lg p-3 bg-white hover:border-primary transition-colors"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-800 truncate">
                          {c.tomadorNome}
                        </p>
                        <div className="flex gap-2 text-xs text-gray-500 mt-0.5">
                          <span>{formatCompetencia(c.competencia)}</span>
                          <span>{formatBRL(c.valorBruto)}</span>
                          <span className="text-primary font-medium">Score {c.score}</span>
                        </div>
                      </div>
                      <button
                        disabled={busy}
                        onClick={() => handleAceitar(c.producaoId)}
                        className="shrink-0 text-xs px-3 py-1.5 bg-primary text-white rounded hover:bg-primary-700 disabled:opacity-50 transition-colors"
                      >
                        Aceitar
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : candidates !== null ? (
              <p className="text-sm text-gray-400 py-2">Nenhuma sugestão automática encontrada.</p>
            ) : null}
          </>
        )}

        {/* IGNORADO */}
        {lancamento.statusConciliacao === 'IGNORADO' && (
          <div className="bg-gray-50 border border-gray-200 rounded-lg p-3 text-sm text-gray-500">
            Este lançamento está marcado como ignorado.
          </div>
        )}

        {erro && (
          <div className="bg-red-50 text-red-700 text-sm rounded p-2">{erro}</div>
        )}
      </div>

      {/* ações */}
      <div className="p-4 border-t border-gray-100 space-y-2">
        {lancamento.statusConciliacao === 'PENDENTE' && (
          <>
            <button
              disabled={busy}
              onClick={onBuscarManual}
              className="w-full flex items-center justify-center gap-2 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors"
            >
              <Search size={15} /> Buscar produção manualmente
            </button>
            <button
              disabled={busy}
              onClick={handleIgnorar}
              className="w-full flex items-center justify-center gap-2 py-2 border border-red-200 rounded-lg text-sm text-red-600 hover:bg-red-50 disabled:opacity-50 transition-colors"
            >
              <EyeOff size={15} /> Ignorar lançamento
            </button>
          </>
        )}
        {lancamento.statusConciliacao === 'CONCILIADO' && (
          <>
            <button
              disabled={busy}
              onClick={onBuscarManual}
              className="w-full flex items-center justify-center gap-2 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors"
            >
              <Search size={15} /> Trocar produção
            </button>
            <button
              disabled={busy}
              onClick={handleDesfazer}
              className="w-full flex items-center justify-center gap-2 py-2 border border-orange-200 rounded-lg text-sm text-orange-600 hover:bg-orange-50 disabled:opacity-50 transition-colors"
            >
              <RefreshCw size={15} /> Desfazer conciliação
            </button>
          </>
        )}
        {lancamento.statusConciliacao === 'IGNORADO' && (
          <button
            disabled={busy}
            onClick={handleDesfazer}
            className="w-full flex items-center justify-center gap-2 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors"
          >
            <RefreshCw size={15} /> Reativar para pendente
          </button>
        )}
        {busy && (
          <div className="flex justify-center">
            <Loader2 size={16} className="animate-spin text-primary" />
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
      p.competencia.includes(ql) ||
      formatBRL(p.valorBruto).includes(ql)
    )
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 flex flex-col max-h-[80vh]">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="font-semibold text-gray-800">Selecionar produção</h2>
          <button onClick={onFechar} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <div className="p-3 border-b">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              autoFocus
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Buscar por tomador, competência ou valor…"
              className="w-full pl-9 pr-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex items-center gap-2 p-4 text-gray-500 text-sm">
              <Loader2 size={16} className="animate-spin" /> Carregando…
            </div>
          ) : filtradas.length === 0 ? (
            <p className="p-4 text-sm text-gray-400">Nenhuma produção encontrada.</p>
          ) : (
            filtradas.map((p) => (
              <button
                key={p.id}
                onClick={() => onSelecionar(p.id)}
                className="w-full text-left px-4 py-3 hover:bg-primary-50 border-b border-gray-50 transition-colors"
              >
                <p className="text-sm font-medium text-gray-800">{p.tomadorNome}</p>
                <div className="flex gap-3 text-xs text-gray-500 mt-0.5">
                  <span>{formatCompetencia(p.competencia)}</span>
                  <span>{formatBRL(p.valorBruto)}</span>
                  <span className="text-gray-400">{p.status}</span>
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

  // candidatos por lançamento (lazy load)
  const [candidatesMap, setCandidatesMap] = useState<Record<string, CandidatoMatchResponse[] | null>>({})
  const [loadingCandidates, setLoadingCandidates] = useState(false)

  // modal de busca
  const [modalAberto, setModalAberto] = useState(false)
  const [producoes, setProducoes] = useState<ProducaoResumo[]>([])
  const [loadingProducoes, setLoadingProducoes] = useState(false)

  const extratoSel = extratos.find((e) => e.id === extratoId)

  // carrega extratos
  useEffect(() => {
    if (!temAcesso) return
    listarExtratos()
      .then((data) => {
        setExtratos(data.filter((e) => e.statusImportacao === 'OK'))
        setLoadingExtratos(false)
      })
      .catch(() => setLoadingExtratos(false))
  }, [temAcesso])

  // carrega lançamentos ao trocar extrato
  useEffect(() => {
    if (!extratoId) { setLancamentos([]); return }
    setLoadingLanc(true)
    setSelecionado(null)
    setCandidatesMap({})
    listarLancamentos(extratoId)
      .then((data) => { setLancamentos(data); setLoadingLanc(false) })
      .catch(() => setLoadingLanc(false))
  }, [extratoId])

  // filtragem por aba
  const filtrados = lancamentos.filter((l) => {
    if (tab === 'TODOS') return true
    if (tab === 'SUGESTAO') return l.statusConciliacao === 'PENDENTE' && l.scoreMatch > 0
    return l.statusConciliacao === (tab as StatusConciliacao)
  })

  const counts = {
    TODOS: lancamentos.length,
    PENDENTE: lancamentos.filter((l) => l.statusConciliacao === 'PENDENTE').length,
    SUGESTAO: lancamentos.filter((l) => l.statusConciliacao === 'PENDENTE' && l.scoreMatch > 0).length,
    CONCILIADO: lancamentos.filter((l) => l.statusConciliacao === 'CONCILIADO').length,
    IGNORADO: lancamentos.filter((l) => l.statusConciliacao === 'IGNORADO').length,
  }

  // seleciona lançamento e carrega candidatos se pendente
  const handleSelecionarLanc = useCallback(async (l: LancamentoExtratoResponse) => {
    setSelecionado(l)
    if (l.statusConciliacao !== 'PENDENTE') return
    if (candidatesMap[l.id] !== undefined) return // já carregado
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

  // atualiza item na lista sem recarregar tudo
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

  // modal de busca manual
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

  const tabs: { key: TabKey; label: string }[] = [
    { key: 'TODOS',     label: 'Todos'         },
    { key: 'PENDENTE',  label: 'Pendentes'     },
    { key: 'SUGESTAO',  label: 'Com sugestão'  },
    { key: 'CONCILIADO',label: 'Conciliados'   },
    { key: 'IGNORADO',  label: 'Ignorados'     },
  ]

  if (!temAcesso) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400">
        <div className="text-center">
          <XCircle size={40} className="mx-auto mb-2" />
          <p>Acesso não autorizado.</p>
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
        {/* Cabeçalho */}
        <div className="px-6 py-4 border-b border-gray-100 bg-white flex items-center gap-3">
          <ArrowLeftRight size={22} className="text-primary" />
          <h1 className="text-xl font-semibold text-gray-800">Conciliação Assistida</h1>
        </div>

        {/* Seletor de extrato */}
        <div className="px-6 py-3 bg-white border-b border-gray-100 flex items-center gap-4 flex-wrap">
          <div className="flex items-center gap-2">
            <label className="text-sm font-medium text-gray-600 whitespace-nowrap">Extrato:</label>
            {loadingExtratos ? (
              <Loader2 size={16} className="animate-spin text-gray-400" />
            ) : (
              <select
                value={extratoId}
                onChange={(e) => setExtratoId(e.target.value)}
                className="text-sm border border-gray-300 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary min-w-[260px]"
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
            <div className="flex items-center gap-3 text-sm text-gray-500">
              <span>{extratoSel.totalLancamentos} lançamentos</span>
              <span>·</span>
              <span>
                {counts.CONCILIADO} conciliados · {counts.PENDENTE} pendentes · {counts.IGNORADO} ignorados
              </span>
            </div>
          )}
        </div>

        {!extratoId ? (
          <div className="flex-1 flex items-center justify-center text-gray-400">
            <div className="text-center">
              <ArrowLeftRight size={40} className="mx-auto mb-3 opacity-30" />
              <p className="text-sm">Selecione um extrato para iniciar a conciliação.</p>
            </div>
          </div>
        ) : (
          <>
            {/* Abas */}
            <div className="bg-white border-b border-gray-100 px-6 flex gap-1 overflow-x-auto">
              {tabs.map((t) => (
                <button
                  key={t.key}
                  onClick={() => { setTab(t.key); setSelecionado(null) }}
                  className={`px-3 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 transition-colors ${
                    tab === t.key
                      ? 'border-primary text-primary'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}
                >
                  {t.label}
                  <span
                    className={`ml-1.5 text-xs px-1.5 py-0.5 rounded-full ${
                      tab === t.key ? 'bg-primary-100 text-primary-700' : 'bg-gray-100 text-gray-500'
                    }`}
                  >
                    {counts[t.key]}
                  </span>
                </button>
              ))}
            </div>

            {/* Corpo — lista + painel */}
            <div className="flex-1 overflow-hidden flex">
              {/* Lista de lançamentos */}
              <div className="flex-1 overflow-auto">
                {loadingLanc ? (
                  <div className="flex items-center gap-2 p-6 text-gray-500">
                    <Loader2 size={18} className="animate-spin" /> Carregando lançamentos…
                  </div>
                ) : filtrados.length === 0 ? (
                  <div className="flex items-center justify-center h-40 text-gray-400 text-sm">
                    Nenhum lançamento {tab !== 'TODOS' ? 'nesta categoria' : ''}.
                  </div>
                ) : (
                  <div className="divide-y divide-gray-50">
                    {filtrados.map((l) => {
                      const cfg = STATUS_CFG[l.statusConciliacao]
                      const isSel = selecionado?.id === l.id
                      return (
                        <button
                          key={l.id}
                          onClick={() => handleSelecionarLanc(l)}
                          className={`w-full text-left px-5 py-3 flex items-center gap-3 transition-colors ${
                            isSel
                              ? 'bg-primary-50 border-l-2 border-primary'
                              : 'hover:bg-gray-50 border-l-2 border-transparent'
                          }`}
                        >
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 mb-0.5">
                              {cfg.icon}
                              <span className="text-xs text-gray-400">{formatDate(l.dataLancamento)}</span>
                              <span
                                className={`text-xs font-medium px-1.5 py-0.5 rounded ${
                                  l.tipo === 'CREDITO'
                                    ? 'bg-blue-50 text-blue-600'
                                    : 'bg-orange-50 text-orange-600'
                                }`}
                              >
                                {l.tipo === 'CREDITO' ? 'CR' : 'DB'}
                              </span>
                              {l.statusConciliacao === 'PENDENTE' && l.scoreMatch > 0 && (
                                <span className="text-xs text-primary">✨ {l.scoreMatch}</span>
                              )}
                              {l.statusConciliacao === 'CONCILIADO' && l.conciliacao?.tipoMatch === 'AUTOMATICO' && (
                                <span className="flex items-center gap-0.5 text-xs text-green-600">
                                  <Bot size={10} /> Auto
                                </span>
                              )}
                            </div>
                            <p className="text-sm text-gray-700 truncate">{l.descricao}</p>
                            {l.statusConciliacao === 'CONCILIADO' && l.conciliacao?.tomadorNome && (
                              <p className="text-xs text-green-600 truncate mt-0.5">
                                → {l.conciliacao.tomadorNome}
                              </p>
                            )}
                          </div>
                          <div className="text-right shrink-0">
                            <p className="text-sm font-semibold text-gray-800">{formatBRL(l.valorCentavos)}</p>
                          </div>
                          <ChevronRight size={14} className="text-gray-300 shrink-0" />
                        </button>
                      )
                    })}
                  </div>
                )}
              </div>

              {/* Painel lateral de detalhe */}
              {selecionado && (
                <div className="w-80 xl:w-96 bg-white border-l border-gray-100 flex flex-col h-full shrink-0">
                  <DetalhePanel
                    lancamento={selecionado}
                    candidates={candidatesMap[selecionado.id] ?? null}
                    loadingCandidates={loadingCandidates && candidatesMap[selecionado.id] === undefined}
                    onConciliar={handleConciliar}
                    onIgnorar={handleIgnorar}
                    onDesfazer={handleDesfazer}
                    onBuscarManual={abrirModal}
                  />
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </>
  )
}
