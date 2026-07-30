import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, Calculator, CheckCircle2, ChevronDown,
  Plus, Loader2, ClipboardList, AlertCircle, Building2,
  TrendingDown, DollarSign,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import { Servico, servicosApi } from '../api/servicosApi'
import { Tomador, tomadoresApi } from '../api/tomadoresApi'
import { producoesApi } from '../api/producoesApi'
import { portalApi, PerfilMedico, EmpresaPortal, ProducaoPortal } from '../api/portalApi'
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
      <div className="bg-ds-surface rounded-xl border border-ds-border p-5 flex flex-col items-center justify-center h-full min-h-40 text-ds-light text-center">
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
    <div className="overflow-x-auto">
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
                <span className="text-xs text-ds-light block max-w-[140px] truncate">{p.servicoDescricao}</span>
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
  )
}

// ─── Page ──────────────────────────────────────────────────────────────────────

export function PortalProducaoNovaPage() {
  const navigate = useNavigate()

  // dados carregados
  const [perfil,    setPerfil]    = useState<PerfilMedico | null>(null)
  const [tomadores, setTomadores] = useState<Tomador[]>([])
  const [servicos,  setServicos]  = useState<Servico[]>([])
  const [empresas,  setEmpresas]  = useState<EmpresaPortal[]>([])
  const [historico, setHistorico] = useState<ProducaoPortal[]>([])
  const [initLoading, setInitLoading] = useState(true)
  const [histLoading, setHistLoading] = useState(true)
  const [initError,   setInitError]   = useState<string | null>(null)

  // seleções do formulário
  const [tomador,      setTomador]      = useState<Tomador | null>(null)
  const [cnaeCodigo,   setCnaeCodigo]   = useState<string>('')
  const [servico,      setServico]      = useState<Servico | null>(null)
  const [empresa,      setEmpresa]      = useState<EmpresaPortal | null>(null)
  const [competencia,  setCompetencia]  = useState(COMPETENCIAS[0])
  const [valorStr,     setValorStr]     = useState('')
  const [descricao,    setDescricao]    = useState('')

  // submissão
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState(false)

  const totalCentavos = parseBRL(valorStr)
  const canConfirm = !!tomador && !!servico && !!empresa && totalCentavos > 0 && !!perfil

  // Serviços do tomador: se houver vínculos, só eles aparecem; senão, catálogo completo.
  const servicosDisponiveis: Servico[] = tomador && tomador.servicos && tomador.servicos.length > 0
    ? servicos.filter(s => tomador.servicos.some(v => v.servicoId === s.id))
    : servicos

  // Auto-seleciona quando há exatamente 1 serviço; limpa seleção que deixou de ser válida.
  useEffect(() => {
    if (servicosDisponiveis.length === 1) {
      const only = servicosDisponiveis[0]
      setServico(prev => prev?.id === only.id ? prev : only)
    } else if (servico && !servicosDisponiveis.some(s => s.id === servico.id)) {
      setServico(null)
    }
  }, [tomador?.id, servicos])

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
        const [p, t, s, e, alocados] = await Promise.all([
          portalApi.getPerfil(),
          tomadoresApi.listar(),
          servicosApi.listar(),
          portalApi.getVinculosEmpresa(),
          portalApi.getTomadoresAlocados(),
        ])
        setPerfil(p)
        // Restringe aos tomadores alocados ao médico (EPIC-15.15), mantendo o shape completo
        // de Tomador (cnaes/servicos) já usado pelo formulário — getTomadoresAlocados() só
        // retorna { id, razaoSocial, municipio }, insuficiente para essa lógica.
        const idsAlocados = new Set(alocados.map(a => a.id))
        // Tomadores com faturamento por grupo configurado não geram produção manual — a produção
        // deles é gerada pelo Fechamento por Grupo (PINSAUDE-13.11).
        setTomadores(t.filter(tom => idsAlocados.has(tom.id) && !tom.temGrupoFaturamento))
        setServicos(s)
        setEmpresas(e)
        // Auto-seleciona empresa se única
        if (e.length === 1) setEmpresa(e[0])
      } catch (err) {
        setInitError(err instanceof Error ? err.message : 'Erro ao carregar dados')
      } finally {
        setInitLoading(false)
      }
    }
    init()
    carregarHistorico()
  }, [carregarHistorico])

  // ─── Submit ────────────────────────────────────────────────────────────────

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!canConfirm || !perfil) return
    setSubmitting(true)
    setSubmitError(null)
    setSucesso(false)
    try {
      await producoesApi.criar({
        tomadorId: tomador!.id,
        servicoId: servico!.id,
        competencia,
        descricaoComplementar: descricao || undefined,
        cnaeCodigo: cnaeCodigo || undefined,
        empresaId: empresa!.id,
        participantes: [{ medicoId: perfil.id, valorBruto: totalCentavos }],
      })
      setSucesso(true)
      // Limpa formulário
      setTomador(null)
      setServico(null)
      setValorStr('')
      setDescricao('')
      setCompetencia(COMPETENCIAS[0])
      // Recarrega histórico
      await carregarHistorico()
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Erro ao registrar produção')
    } finally {
      setSubmitting(false)
    }
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
      {/* Header */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/portal/dashboard')}
          className="p-2 rounded-lg hover:bg-ds-input text-ds-light hover:text-ds-mid transition-colors"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-xl font-black text-ds-text">Informar Produção</h1>
          <p className="text-sm text-ds-light mt-0.5">
            Solicite a emissão de uma nova nota fiscal em seu nome
          </p>
        </div>
      </div>

      {/* Identidade do médico */}
      {perfil && (
        <div className="flex items-center gap-3 bg-primary-50 border border-primary/20 rounded-xl px-4 py-3">
          <div className="w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center text-primary font-black text-sm">
            {perfil.nome.charAt(0).toUpperCase()}
          </div>
          <div>
            <p className="text-sm font-bold text-primary-700">{perfil.nome}</p>
            <p className="text-xs text-primary-600">CRM {perfil.crm}/{perfil.crmUf}
              {perfil.especialidade ? ` · ${perfil.especialidade}` : ''}
            </p>
          </div>
          <span className="ml-auto text-[10px] font-bold text-primary-600 bg-primary/10 px-2 py-1 rounded-md">
            Produção registrada para você
          </span>
        </div>
      )}

      {/* Alertas */}
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
      {submitError && (
        <Alert variant="error" onClose={() => setSubmitError(null)}>{submitError}</Alert>
      )}

      {/* Formulário + Preview */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Formulário */}
        <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-ds-border shadow-sm p-5 space-y-4">
          <p className="text-sm font-bold text-ds-text border-b border-ds-border pb-3 -mx-5 px-5">
            Dados da Produção
          </p>

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
              onSelect={t => { setTomador(t); setCnaeCodigo(''); setServico(null) }}
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

          {/* Serviço */}
          <SearchDropdown
            label="Serviço (LC 116/2003) *"
            placeholder="Selecione o serviço..."
            items={servicosDisponiveis}
            selected={servico}
            onSelect={setServico}
            getLabel={s => `${s.codigoLc116} — ${s.descricaoPadrao}`}
            disabled={servicosDisponiveis.length === 1}
          />
          {tomador && tomador.servicos && tomador.servicos.length > 0 && (
            <p className="-mt-2 text-[11px] text-ds-light">
              {servicosDisponiveis.length === 1
                ? 'Serviço único do tomador — selecionado automaticamente.'
                : 'Exibindo apenas os serviços cadastrados para este tomador.'}
            </p>
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

          {/* Empresa emissora */}
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">
              <Building2 size={11} className="inline mr-1" />
              Empresa Emissora *
            </label>
            {empresas.length === 0 ? (
              <div className="rounded-lg bg-orange-50 border border-orange-200 px-3 py-2">
                <p className="text-xs text-orange-700">
                  Nenhuma empresa vinculada ao seu cadastro.
                  Entre em contato com o time operacional.
                </p>
              </div>
            ) : empresas.length === 1 ? (
              <div className="flex items-center gap-2 rounded-lg bg-ds-surface border border-ds-border px-3 py-2.5">
                <Building2 size={13} className="text-ds-light" />
                <div>
                  <p className="text-xs font-semibold text-ds-text">{empresa?.razaoSocial}</p>
                  <p className="text-[10px] text-ds-light">{empresa?.cnpj}</p>
                </div>
              </div>
            ) : (
              <SearchDropdown
                label=""
                placeholder="Selecione a empresa emissora..."
                items={empresas}
                selected={empresa}
                onSelect={setEmpresa}
                getLabel={e => `${e.razaoSocial} — ${e.cnpj}`}
              />
            )}
          </div>

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
                  !tomador  && 'tomador',
                  !servico  && 'serviço',
                  !empresa  && 'empresa',
                  totalCentavos <= 0 && 'valor',
                ].filter(Boolean).join(', ')}
              </p>
            )}
          </div>
        </form>

        {/* Preview */}
        <div className="space-y-4">
          <PreviewCard valorBruto={totalCentavos} />

          {/* Informações adicionais */}
          <div className="bg-ds-surface rounded-xl border border-ds-border p-4 space-y-2">
            <p className="text-xs font-bold text-ds-mid">O que acontece após a solicitação?</p>
            <ol className="space-y-1.5">
              {[
                'Sua solicitação é enviada ao time da Pin Saúde',
                'A operação valida os dados e emite a NFS-e',
                'Você recebe a nota em "Minhas Notas" quando emitida',
                'O repasse é realizado conforme calendário Pin',
              ].map((step, i) => (
                <li key={i} className="flex items-start gap-2 text-xs text-ds-mid">
                  <span className="shrink-0 w-4 h-4 rounded-full bg-primary/10 text-primary text-[10px] font-bold flex items-center justify-center mt-0.5">
                    {i + 1}
                  </span>
                  {step}
                </li>
              ))}
            </ol>
          </div>
        </div>
      </div>

      {/* Histórico */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-ds-border">
          <div>
            <p className="text-sm font-bold text-ds-text">Minhas Solicitações</p>
            <p className="text-xs text-ds-light mt-0.5">Histórico de produções registradas</p>
          </div>
          <Link
            to="/portal/notas"
            className="text-xs font-semibold text-primary hover:text-primary-700 transition-colors"
          >
            Ver notas fiscais →
          </Link>
        </div>
        <HistoricoProducoes producoes={historico} loading={histLoading} />
      </div>
    </div>
  )
}
