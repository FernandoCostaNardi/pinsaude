import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowLeft, Calculator, CheckCircle2, ChevronDown,
  Plus, Loader2, ClipboardList, AlertCircle,
  TrendingDown, DollarSign,
} from 'lucide-react'
import { Button, Spinner, Alert, Modal } from '@pinsaude/ui'
import { Tomador, tomadoresApi } from '../api/tomadoresApi'
import { producoesApi } from '../api/producoesApi'
import { portalApi, PerfilMedico, ProducaoPortal } from '../api/portalApi'
import { formatCnae } from '../components/CnaeSelect'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function maskBRL(centavos: number): string {
  if (centavos === 0) return ''
  return (centavos / 100).toLocaleString('pt-BR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  })
}

function parseBRL(str: string): number {
  return parseInt(str.replace(/\D/g, '') || '0', 10)
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: '2-digit', year: '2-digit',
  })
}

function generateCompetencias(): string[] {
  const comps: string[] = []
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    comps.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return comps
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

const STATUS_LABELS: Record<string, string> = {
  RASCUNHO:   'Rascunho',
  CONFIRMADA: 'Aguardando Emissão',
  EMITIDA:    'Emitida',
  CANCELADA:  'Cancelada',
}

const STATUS_CLS: Record<string, string> = {
  RASCUNHO:   'bg-gray-100 text-gray-500',
  CONFIRMADA: 'bg-yellow-50 text-yellow-700',
  EMITIDA:    'bg-green-50 text-green-700',
  CANCELADA:  'bg-red-50 text-red-500',
}

const COMPETENCIAS = generateCompetencias()

// ─── Dropdown busca ──────────────────────────────────────────────────────────

function SearchDropdown<T extends { id: string }>({
  label, placeholder, items, selected, onSelect, getLabel, disabled,
}: {
  label: string
  placeholder: string
  items: T[]
  selected: T | null
  onSelect: (item: T) => void
  getLabel: (item: T) => string
  disabled?: boolean
}) {
  const [open, setOpen]   = useState(false)
  const [q, setQ]         = useState('')
  const ref               = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handle(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handle)
    return () => document.removeEventListener('mousedown', handle)
  }, [])

  const filtered = items.filter(i =>
    getLabel(i).toLowerCase().includes(q.toLowerCase())
  )

  return (
    <div ref={ref} className="relative">
      <label className="block text-xs font-bold text-ds-mid mb-1">{label}</label>
      <button
        type="button"
        disabled={disabled}
        onClick={() => { setOpen(!open); setQ('') }}
        className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border text-sm transition-colors ${
          selected
            ? 'border-primary/40 bg-primary-50 text-ds-text font-medium'
            : 'border-ds-border bg-white text-ds-light'
        } disabled:opacity-50`}
      >
        <span className="truncate">{selected ? getLabel(selected) : placeholder}</span>
        <ChevronDown size={14} className={`shrink-0 ml-2 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute z-30 top-full left-0 right-0 mt-1 bg-white border border-ds-border rounded-xl shadow-lg overflow-hidden">
          <div className="p-2 border-b border-ds-border">
            <input
              autoFocus
              value={q}
              onChange={e => setQ(e.target.value)}
              placeholder="Buscar..."
              className="w-full text-xs px-2 py-1.5 border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          <div className="max-h-52 overflow-y-auto divide-y divide-ds-border">
            {filtered.length === 0 ? (
              <p className="px-3 py-3 text-xs text-ds-light text-center">Nenhum resultado</p>
            ) : filtered.map(item => (
              <button
                key={item.id}
                type="button"
                onClick={() => { onSelect(item); setOpen(false); setQ('') }}
                className="w-full text-left px-3 py-2.5 text-sm hover:bg-ds-surface transition-colors"
              >
                {getLabel(item)}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Preview card ─────────────────────────────────────────────────────────────

function PreviewCard({ valorBruto }: { valorBruto: number }) {
  const taxaPin     = Math.round(valorBruto * 0.15)
  const valorLiquid = valorBruto - taxaPin

  if (valorBruto <= 0) {
    return (
      <div className="bg-ds-surface rounded-xl border border-ds-border p-5 flex flex-col items-center justify-center min-h-40 text-ds-light text-center">
        <Calculator size={28} className="opacity-25 mb-2" />
        <p className="text-xs font-medium">Informe o valor dos honorários<br />para ver o preview</p>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
      <div className="bg-ds-surface px-4 py-3 border-b border-ds-border">
        <p className="text-xs font-bold text-ds-mid uppercase tracking-wide flex items-center gap-1.5">
          <Calculator size={12} />
          Preview do Valor
        </p>
      </div>
      <div className="p-4 space-y-0">
        {/* Valor bruto */}
        <div className="flex justify-between py-2 border-b border-ds-border/50">
          <span className="text-xs text-ds-mid">Valor Bruto da Nota</span>
          <span className="text-xs font-semibold tabular-nums text-ds-text">{formatBRL(valorBruto)}</span>
        </div>

        {/* Taxa Pin */}
        <div className="flex justify-between py-2 border-b border-ds-border/50">
          <div className="flex items-center gap-1">
            <TrendingDown size={11} className="text-purple-500" />
            <span className="text-xs text-ds-mid">Taxa Pin Saúde (15%)</span>
          </div>
          <span className="text-xs tabular-nums text-purple-700">({formatBRL(taxaPin)})</span>
        </div>

        {/* Tributos */}
        <div className="py-2 border-b border-ds-border/50">
          <div className="flex items-start gap-1.5">
            <AlertCircle size={11} className="text-orange-400 mt-0.5 shrink-0" />
            <p className="text-[11px] text-ds-light leading-relaxed">
              ISS, IR, CSLL, PIS e COFINS calculados na emissão
              conforme regime do tomador. <strong>Esses tributos são custo
              fiscal da Pin Saúde</strong>, não afetam seu valor líquido.
            </p>
          </div>
        </div>

        {/* Valor líquido — destaque */}
        <div className="pt-3 pb-1">
          <div className="bg-green-50 rounded-xl p-4 flex items-center justify-between border border-green-200">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-green-100 flex items-center justify-center">
                <DollarSign size={16} className="text-green-700" />
              </div>
              <div>
                <p className="text-[10px] font-bold text-green-700 uppercase tracking-wide">
                  Você Recebe (85%)
                </p>
                <p className="text-[11px] text-green-600">Garantido independente de tributos</p>
              </div>
            </div>
            <p className="text-xl font-black text-green-700 tabular-nums">{formatBRL(valorLiquid)}</p>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Histórico ─────────────────────────────────────────────────────────────────

function HistoricoProducoes({ producoes, loading }: { producoes: ProducaoPortal[]; loading: boolean }) {
  if (loading) return (
    <div className="flex justify-center py-8"><Spinner /></div>
  )
  if (producoes.length === 0) return (
    <div className="flex flex-col items-center py-10 text-ds-light">
      <ClipboardList size={28} className="mb-2 opacity-25" />
      <p className="text-sm font-medium">Nenhuma solicitação anterior</p>
    </div>
  )

  return (
    <>
      {/* Desktop: tabela — nunca aparece no mobile, então nunca precisa de scroll horizontal */}
      <div className="hidden sm:block overflow-x-auto">
        <table className="w-full text-sm min-w-[500px]">
          <thead>
            <tr className="bg-ds-surface border-b border-ds-border">
              {['Competência','Tomador','Serviço','Valor Bruto','Estimativa Líquida','Status','Data'].map(h => (
                <th key={h} className="px-4 py-3 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-ds-border">
            {producoes.map(p => (
              <tr key={p.id} className="hover:bg-ds-surface/50 transition-colors">
                <td className="px-4 py-3">
                  <span className="text-xs font-semibold text-ds-text">{formatCompetencia(p.competencia)}</span>
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs text-ds-mid block max-w-[160px] truncate">{p.tomadorNome}</span>
                </td>
                <td className="px-4 py-3">
                  {p.servicoDescricao ? (
                    <span className="text-xs text-ds-light block max-w-[140px] truncate">{p.servicoDescricao}</span>
                  ) : (
                    <span className="text-xs text-orange-600">Aguardando definição</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs tabular-nums font-semibold text-ds-text">
                    {formatBRL(p.valorBrutoCentavos)}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs tabular-nums font-bold text-green-700">
                    {formatBRL(p.valorLiquidoEstimadoCentavos)}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-md text-[10px] font-bold ${STATUS_CLS[p.status] ?? 'bg-gray-100 text-gray-500'}`}>
                    {STATUS_LABELS[p.status] ?? p.status}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs text-ds-light">{formatDate(p.createdAt)}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile: cards empilhados — mesmo conteúdo da tabela, sem nenhuma coluna cortada/escondida */}
      <div className="sm:hidden divide-y divide-ds-border">
        {producoes.map(p => (
          <div key={p.id} className="px-4 py-3 space-y-1.5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-bold text-ds-text">{formatCompetencia(p.competencia)}</span>
              <span className={`shrink-0 inline-flex px-2 py-0.5 rounded-md text-[10px] font-bold ${STATUS_CLS[p.status] ?? 'bg-gray-100 text-gray-500'}`}>
                {STATUS_LABELS[p.status] ?? p.status}
              </span>
            </div>
            <p className="text-xs text-ds-mid truncate">{p.tomadorNome}</p>
            {p.servicoDescricao ? (
              <p className="text-xs text-ds-light truncate">{p.servicoDescricao}</p>
            ) : (
              <p className="text-xs text-orange-600">Aguardando definição de serviço</p>
            )}
            <div className="flex items-center justify-between pt-0.5">
              <div>
                <span className="text-[10px] text-ds-light block">Valor Bruto</span>
                <span className="text-xs tabular-nums font-semibold text-ds-text">{formatBRL(p.valorBrutoCentavos)}</span>
              </div>
              <div className="text-right">
                <span className="text-[10px] text-ds-light block">Estimativa Líquida</span>
                <span className="text-xs tabular-nums font-bold text-green-700">{formatBRL(p.valorLiquidoEstimadoCentavos)}</span>
              </div>
            </div>
            <p className="text-[11px] text-ds-light text-right">{formatDate(p.createdAt)}</p>
          </div>
        ))}
      </div>
    </>
  )
}

// ─── Modal: Nova Produção ───────────────────────────────────────────────────────
// Facelift: o card "Dados da Produção" virou modal, aberto pelo botão "Nova Produção". Serviço
// (LC 116/2003) e Empresa Emissora deixaram de ser escolhidos pelo médico: são resolvidos em
// silêncio a partir do cadastro do tomador (tomador.servicos / tomador.empresas, EPIC-13.1/13.12)
// quando há exatamente 1 configurado — sem UI nenhuma para isso. Quando o tomador não tem
// exatamente 1 de cada, a produção é enviada assim mesmo (servicoId/empresaId nulos); a operação
// completa o que faltar depois, antes de emitir a NFS-e (ver NfseEmissaoPage.tsx).

function NovaProducaoModal({
  perfil, tomadores, open, onClose, onCriada,
}: {
  perfil: PerfilMedico
  tomadores: Tomador[]
  open: boolean
  onClose: () => void
  onCriada: () => void
}) {
  const [tomador,     setTomador]     = useState<Tomador | null>(null)
  const [cnaeCodigo,  setCnaeCodigo]  = useState('')
  const [competencia, setCompetencia] = useState(COMPETENCIAS[0])
  const [valorStr,    setValorStr]    = useState('')
  const [descricao,   setDescricao]   = useState('')
  const [submitting,  setSubmitting]  = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const totalCentavos = parseBRL(valorStr)
  const canConfirm = !!tomador && totalCentavos > 0

  const servicosDoTomador = tomador?.servicos ?? []
  const servicoIdResolvido = servicosDoTomador.length === 1 ? servicosDoTomador[0].servicoId : null
  const empresasDoTomador = tomador?.empresas ?? []
  const empresaIdResolvida = empresasDoTomador.length === 1 ? empresasDoTomador[0].empresaId : null

  function fecharEResetar() {
    setTomador(null)
    setCnaeCodigo('')
    setCompetencia(COMPETENCIAS[0])
    setValorStr('')
    setDescricao('')
    setSubmitError(null)
    onClose()
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!canConfirm) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      await producoesApi.criar({
        tomadorId: tomador!.id,
        servicoId: servicoIdResolvido,
        competencia,
        descricaoComplementar: descricao || undefined,
        cnaeCodigo: cnaeCodigo || undefined,
        empresaId: empresaIdResolvida,
        participantes: [{ medicoId: perfil.id, valorBruto: totalCentavos }],
      })
      fecharEResetar()
      onCriada()
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Erro ao registrar produção')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={fecharEResetar} title="Nova Produção" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {submitError && (
          <Alert variant="error" onClose={() => setSubmitError(null)}>{submitError}</Alert>
        )}

        {/* Tomador */}
        {tomadores.length === 0 ? (
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">
              Tomador (Hospital / Clínica / Operadora) *
            </label>
            <div className="rounded-lg bg-orange-50 border border-orange-200 px-3 py-2">
              <p className="text-xs text-orange-700">
                Você ainda não está alocado a nenhum tomador. Entre em contato com o time
                operacional para liberar o lançamento de produção.
              </p>
            </div>
          </div>
        ) : (
          <SearchDropdown
            label="Tomador (Hospital / Clínica / Operadora) *"
            placeholder="Selecione o tomador..."
            items={tomadores}
            selected={tomador}
            onSelect={t => { setTomador(t); setCnaeCodigo('') }}
            getLabel={t => t.razaoSocialNome + (t.municipio ? ` — ${t.municipio}` : '')}
          />
        )}

        {/* CNAE do tomador — aparece quando o tomador tem CNAEs cadastrados */}
        {tomador && tomador.cnaes && tomador.cnaes.length > 0 && (
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">CNAE (atividade econômica da nota)</label>
            <select
              value={cnaeCodigo}
              onChange={e => setCnaeCodigo(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="">Selecione o CNAE...</option>
              {tomador.cnaes.map(c => (
                <option key={c.id} value={c.codigoCnae}>
                  {formatCnae(c.codigoCnae)}{c.descricao ? ` — ${c.descricao.charAt(0) + c.descricao.slice(1).toLowerCase()}` : ''}
                </option>
              ))}
            </select>
          </div>
        )}

        {/* Competência */}
        <div>
          <label className="block text-xs font-bold text-ds-mid mb-1">Competência *</label>
          <select
            value={competencia}
            onChange={e => setCompetencia(e.target.value)}
            className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30"
          >
            {COMPETENCIAS.map(c => (
              <option key={c} value={c}>{formatCompetencia(c)}</option>
            ))}
          </select>
        </div>

        {/* Valor */}
        <div>
          <label className="block text-xs font-bold text-ds-mid mb-1">Valor dos Honorários Brutos *</label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-ds-light">R$</span>
            <input
              type="text"
              inputMode="numeric"
              value={valorStr}
              onChange={e => {
                const raw = e.target.value.replace(/\D/g, '')
                const cents = parseInt(raw || '0', 10)
                setValorStr(cents === 0 ? '' : maskBRL(cents))
              }}
              placeholder="0,00"
              className="w-full pl-9 pr-3 py-2.5 border border-ds-border rounded-lg text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
        </div>

        <PreviewCard valorBruto={totalCentavos} />

        {/* Descrição */}
        <div>
          <label className="block text-xs font-bold text-ds-mid mb-1">
            Descrição Complementar (opcional)
          </label>
          <textarea
            value={descricao}
            onChange={e => setDescricao(e.target.value)}
            rows={2}
            maxLength={500}
            placeholder="Detalhes adicionais sobre o serviço prestado..."
            className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
          />
        </div>

        {/* Botão */}
        <div className="pt-1">
          <Button
            type="submit"
            disabled={!canConfirm || submitting}
            className="w-full"
          >
            {submitting
              ? <><Loader2 size={15} className="animate-spin mr-2" /> Registrando...</>
              : <><Plus size={15} className="mr-2" /> Solicitar Emissão</>}
          </Button>
          {!canConfirm && !submitting && (
            <p className="text-xs text-ds-light text-center mt-2">
              Faltam:{' '}
              {[
                !tomador && 'tomador',
                totalCentavos <= 0 && 'valor',
              ].filter(Boolean).join(', ')}
            </p>
          )}
        </div>
      </form>
    </Modal>
  )
}

// ─── Page ──────────────────────────────────────────────────────────────────────

export function PortalProducaoNovaPage() {
  const navigate = useNavigate()

  // dados carregados
  const [perfil,    setPerfil]    = useState<PerfilMedico | null>(null)
  const [tomadores, setTomadores] = useState<Tomador[]>([])
  const [historico, setHistorico] = useState<ProducaoPortal[]>([])
  const [initLoading, setInitLoading] = useState(true)
  const [histLoading, setHistLoading] = useState(true)
  const [initError,   setInitError]   = useState<string | null>(null)

  const [modalAberto, setModalAberto] = useState(false)
  const [sucesso, setSucesso] = useState(false)

  // ─── Carregamento inicial ──────────────────────────────────────────────────

  const carregarHistorico = useCallback(async () => {
    setHistLoading(true)
    try {
      const data = await portalApi.getProducao()
      setHistorico(data)
    } catch { /* silencioso */ }
    finally { setHistLoading(false) }
  }, [])

  useEffect(() => {
    async function init() {
      try {
        const [p, t, alocados] = await Promise.all([
          portalApi.getPerfil(),
          tomadoresApi.listar(),
          portalApi.getTomadoresAlocados(),
        ])
        setPerfil(p)
        // Restringe aos tomadores alocados ao médico (EPIC-15.15), mantendo o shape completo
        // de Tomador (cnaes/servicos/empresas) já usado pelo formulário — getTomadoresAlocados()
        // só retorna { id, razaoSocial, municipio }, insuficiente para essa lógica.
        const idsAlocados = new Set(alocados.map(a => a.id))
        // Tomadores com faturamento por grupo configurado não geram produção manual — a produção
        // deles é gerada pelo Fechamento por Grupo (PINSAUDE-13.11).
        setTomadores(t.filter(tom => idsAlocados.has(tom.id) && !tom.temGrupoFaturamento))
      } catch (err) {
        setInitError(err instanceof Error ? err.message : 'Erro ao carregar dados')
      } finally {
        setInitLoading(false)
      }
    }
    init()
    carregarHistorico()
  }, [carregarHistorico])

  function handleCriada() {
    setSucesso(true)
    carregarHistorico()
  }

  // ─── Render ────────────────────────────────────────────────────────────────

  if (initLoading) return (
    <div className="flex items-center justify-center h-64"><Spinner size="lg" /></div>
  )

  if (initError) return (
    <div className="p-6">
      <Alert variant="error">{initError}</Alert>
    </div>
  )

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header — empilha no mobile (título em cima, botão full-width embaixo) */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <button
            onClick={() => navigate('/portal/dashboard')}
            className="shrink-0 p-2 rounded-lg hover:bg-ds-input text-ds-light hover:text-ds-mid transition-colors"
          >
            <ArrowLeft size={18} />
          </button>
          <div className="min-w-0">
            <h1 className="text-xl font-black text-ds-text truncate">Informar Produção</h1>
            <p className="text-sm text-ds-light mt-0.5 truncate">
              Solicite a emissão de uma nova nota fiscal em seu nome
            </p>
          </div>
        </div>
        <Button onClick={() => { setSucesso(false); setModalAberto(true) }} className="w-full sm:w-auto shrink-0">
          <Plus size={15} className="mr-1.5" />
          Nova Produção
        </Button>
      </div>

      {/* Identidade do médico — flex-wrap pra o selo cair numa 2ª linha no mobile em vez de
          espremer o nome/CRM ou vazar da tela */}
      {perfil && (
        <div className="flex flex-wrap items-center gap-3 bg-primary-50 border border-primary/20 rounded-xl px-4 py-3">
          <div className="shrink-0 w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center text-primary font-black text-sm">
            {perfil.nome.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0">
            <p className="text-sm font-bold text-primary-700 truncate">{perfil.nome}</p>
            <p className="text-xs text-primary-600 truncate">CRM {perfil.crm}/{perfil.crmUf}
              {perfil.especialidade ? ` · ${perfil.especialidade}` : ''}
            </p>
          </div>
          <span className="sm:ml-auto shrink-0 text-[10px] font-bold text-primary-600 bg-primary/10 px-2 py-1 rounded-md">
            Produção registrada para você
          </span>
        </div>
      )}

      {/* Alerta de sucesso */}
      {sucesso && (
        <div className="flex items-start gap-3 bg-green-50 border border-green-200 rounded-xl px-4 py-4">
          <CheckCircle2 size={20} className="text-green-600 shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-bold text-green-800">Solicitação recebida com sucesso!</p>
            <p className="text-xs text-green-700 mt-0.5">
              Sua produção foi registrada e está sendo processada pelo time da Pin Saúde.
              A nota fiscal será emitida em breve e você pode acompanhar o status abaixo.
            </p>
          </div>
        </div>
      )}

      {/* Minhas Solicitações */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b border-ds-border">
          <p className="text-sm font-bold text-ds-text">Minhas Solicitações</p>
          <p className="text-xs text-ds-light mt-0.5">Histórico de produções registradas</p>
        </div>
        <HistoricoProducoes producoes={historico} loading={histLoading} />
      </div>

      {perfil && (
        <NovaProducaoModal
          perfil={perfil}
          tomadores={tomadores}
          open={modalAberto}
          onClose={() => setModalAberto(false)}
          onCriada={handleCriada}
        />
      )}
    </div>
  )
}
