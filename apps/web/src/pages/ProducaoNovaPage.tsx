import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowLeft, Calculator, CheckCircle2, ChevronDown,
  AlertCircle, Loader2, Plus, Trash2, Users,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import { Servico, servicosApi } from '../api/servicosApi'
import { Tomador, tomadoresApi } from '../api/tomadoresApi'
import { Medico, medicosApi } from '../api/medicosApi'
import { ProducaoRequest, producoesApi } from '../api/producoesApi'
import { calcularFiscal } from '../api/fiscalApi'
import { Empresa, empresasApi } from '../api/empresasApi'
import { useAuth } from '../auth/useAuth'
import { formatCnae } from '../components/CnaeSelect'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function parseBRL(str: string): number {
  const digits = str.replace(/\D/g, '')
  return digits ? parseInt(digits, 10) : 0
}

function maskBRL(centavos: number): string {
  if (!centavos) return ''
  return (centavos / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function currentCompetencia(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function competenciaLabel(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho',
                 'Julho','Agosto','Setembro','Outubro','Novembro','Dezembro']
  return `${meses[parseInt(mes, 10) - 1]} / ${ano}`
}

function generateCompetencias(): string[] {
  const result: string[] = []
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    result.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return result
}

// ─── Autocomplete genérico ────────────────────────────────────────────────────

interface AutocompleteItem { id: string; label: string; highlight?: string; sublabel?: string }

function Autocomplete({
  items, value, onChange, onClear, placeholder, disabled, loading: ext,
}: {
  items: AutocompleteItem[]
  value: AutocompleteItem | null
  onChange: (item: AutocompleteItem) => void
  onClear: () => void
  placeholder: string
  disabled?: boolean
  loading?: boolean
}) {
  const [query, setQuery]     = useState('')
  const [open, setOpen]       = useState(false)
  const containerRef          = useRef<HTMLDivElement>(null)

  const filtered = items.filter(i =>
    i.label.toLowerCase().includes(query.toLowerCase()) ||
    (i.highlight ?? '').toLowerCase().includes(query.toLowerCase()) ||
    (i.sublabel ?? '').toLowerCase().includes(query.toLowerCase())
  ).slice(0, 20)

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
        if (!value) setQuery('')
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [value])

  function handleSelect(item: AutocompleteItem) {
    onChange(item)
    setQuery('')
    setOpen(false)
  }

  function handleClear() {
    onClear()
    setQuery('')
    setOpen(false)
  }

  return (
    <div ref={containerRef} className="relative">
      {value ? (
        <div className="flex items-center justify-between border border-ds-border rounded-lg px-3 py-2 bg-white">
          <div>
            <div className="text-sm font-medium text-ds-mid">{value.label}</div>
            {value.highlight && <div className="text-xs font-medium text-red-600">{value.highlight}</div>}
            {value.sublabel && <div className="text-xs text-ds-light">{value.sublabel}</div>}
          </div>
          <button onClick={handleClear} className="text-ds-light hover:text-ds-mid ml-2 text-xs underline shrink-0">
            Alterar
          </button>
        </div>
      ) : (
        <div className="relative">
          <input
            value={query}
            onChange={e => { setQuery(e.target.value); setOpen(true) }}
            onFocus={() => setOpen(true)}
            placeholder={placeholder}
            disabled={disabled}
            className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:bg-ds-surface disabled:text-ds-light"
          />
          {ext && (
            <Loader2 size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-ds-light animate-spin" />
          )}
          {!ext && <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-ds-light pointer-events-none" />}

          {open && filtered.length > 0 && (
            <div className="absolute z-20 w-full mt-1 bg-white border border-ds-border rounded-lg shadow-lg max-h-56 overflow-y-auto">
              {filtered.map(item => (
                <button
                  key={item.id}
                  onMouseDown={e => { e.preventDefault(); handleSelect(item) }}
                  className="w-full text-left px-3 py-2.5 hover:bg-primary-50 transition-colors border-b border-ds-border last:border-0"
                >
                  <div className="text-sm font-medium text-ds-mid">{item.label}</div>
                  {item.highlight && <div className="text-xs font-medium text-red-600">{item.highlight}</div>}
                  {item.sublabel && <div className="text-xs text-ds-light">{item.sublabel}</div>}
                </button>
              ))}
            </div>
          )}
          {open && !ext && filtered.length === 0 && query.length >= 2 && (
            <div className="absolute z-20 w-full mt-1 bg-white border border-ds-border rounded-lg shadow-lg px-3 py-3 text-sm text-ds-light">
              Nenhum resultado encontrado
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Tipo local para o preview fiscal ─────────────────────────────────────────

interface TaxaItem { valor: number; retido: boolean }
interface FiscalPreview {
  valorBruto: number
  iss: TaxaItem
  ir: TaxaItem
  csll: TaxaItem
  pis: TaxaItem
  cofins: TaxaItem
  totalRetencoes: number
  valorLiquidoNota: number
}

// ─── Preview card ─────────────────────────────────────────────────────────────

function PreviewCard({ preview, loading }: {
  preview: FiscalPreview | null
  loading: boolean
}) {
  const impostos: { label: string; item: TaxaItem }[] = preview ? [
    { label: 'ISS',    item: preview.iss    },
    { label: 'IR',     item: preview.ir     },
    { label: 'CSLL',   item: preview.csll   },
    { label: 'PIS',    item: preview.pis    },
    { label: 'COFINS', item: preview.cofins },
  ] : []

  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm p-5 sticky top-6">
      <div className="flex items-center gap-2 mb-4">
        <Calculator size={16} className="text-primary" />
        <h3 className="font-semibold text-ds-mid text-sm">Cálculo Fiscal da Nota</h3>
      </div>

      {loading && (
        <div className="flex items-center justify-center py-8">
          <Spinner size="md" />
        </div>
      )}

      {!loading && !preview && (
        <div className="text-center text-ds-light py-8 text-sm">
          <Calculator size={32} className="mx-auto mb-2 opacity-30" />
          Preencha tomador, serviço e ao menos um participante para ver o cálculo
        </div>
      )}

      {!loading && preview && (
        <div className="space-y-4">
          {/* Valor bruto */}
          <div className="flex items-center justify-between py-2 border-b border-ds-border">
            <span className="text-sm font-semibold text-ds-mid">Valor Bruto da Nota</span>
            <span className="text-lg font-black text-ds-mid">{formatBRL(preview.valorBruto)}</span>
          </div>

          {/* Impostos individuais */}
          <div className="space-y-1">
            <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-2">Impostos</p>
            {impostos.map(({ label, item }) => (
              item.valor > 0 && (
                <div key={label} className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className={`text-sm font-medium ${item.retido ? 'text-ds-mid' : 'text-ds-light'}`}>
                      {label}
                    </span>
                    {item.retido ? (
                      <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-orange-100 text-orange-700">
                        retido
                      </span>
                    ) : (
                      <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-ds-surface text-ds-light">
                        Pin paga
                      </span>
                    )}
                  </div>
                  <span className={`text-sm font-semibold ${item.retido ? 'text-ds-mid' : 'text-ds-light'}`}>
                    {item.retido ? '−' : ''}{formatBRL(item.valor)}
                  </span>
                </div>
              )
            ))}
          </div>

          {/* Total retido */}
          <div className="flex items-center justify-between py-2 border-t border-ds-border">
            <span className="text-sm font-semibold text-ds-mid">Total retido na nota</span>
            <span className="text-sm font-bold text-orange-700">
              − {formatBRL(preview.totalRetencoes)}
            </span>
          </div>

          {/* Valor líquido */}
          <div className="flex items-center justify-between py-3 px-4 bg-primary-50 rounded-lg border border-primary-100">
            <span className="font-bold text-ds-mid text-sm">Valor Líquido da Nota</span>
            <span className="font-black text-primary text-xl">{formatBRL(preview.valorLiquidoNota)}</span>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Form field wrapper ────────────────────────────────────────────────────────

function Field({ label, required, children, error }: {
  label: string; required?: boolean; children: React.ReactNode; error?: string
}) {
  return (
    <div className="space-y-1.5">
      <label className="block text-sm font-medium text-ds-mid">
        {label}{required && <span className="text-red-500 ml-0.5">*</span>}
      </label>
      {children}
      {error && <p className="text-xs text-red-500 flex items-center gap-1"><AlertCircle size={11} />{error}</p>}
    </div>
  )
}

// ─── Participante row ─────────────────────────────────────────────────────────

interface PartItem {
  key: number
  medico: AutocompleteItem | null
  valorStr: string
}

function ParticipanteRow({
  part, index, medicoItems, usedMedicoIds, onChangeMedico, onChangeValor, onRemove, canRemove,
}: {
  part: PartItem
  index: number
  medicoItems: AutocompleteItem[]
  usedMedicoIds: Set<string>
  onChangeMedico: (item: AutocompleteItem | null) => void
  onChangeValor: (val: string) => void
  onRemove: () => void
  canRemove: boolean
}) {
  const available = medicoItems.filter(m => !usedMedicoIds.has(m.id) || m.id === part.medico?.id)
  const centavos = parseBRL(part.valorStr)

  return (
    <div className="flex gap-3 items-start p-3 bg-ds-surface rounded-lg border border-ds-border">
      <div className="flex-shrink-0 w-6 h-6 rounded-full bg-primary text-white text-xs font-bold flex items-center justify-center mt-2">
        {index + 1}
      </div>
      <div className="flex-1 grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-ds-mid mb-1">Médico</label>
          <Autocomplete
            items={available}
            value={part.medico}
            onChange={onChangeMedico}
            onClear={() => onChangeMedico(null)}
            placeholder="Buscar médico..."
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-ds-mid mb-1">Valor Bruto</label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-xs font-medium text-ds-mid">R$</span>
            <input
              value={part.valorStr}
              onChange={e => {
                const raw = e.target.value.replace(/\D/g, '')
                onChangeValor(maskBRL(parseInt(raw || '0', 10)))
              }}
              placeholder="0,00"
              className="w-full pl-8 pr-3 py-2 border border-ds-border rounded-lg text-sm text-right focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          {centavos > 0 && (
            <p className="text-xs text-ds-light mt-0.5 text-right">{formatBRL(centavos)}</p>
          )}
        </div>
      </div>
      {canRemove && (
        <button
          onClick={onRemove}
          className="mt-2 p-1.5 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
        >
          <Trash2 size={14} />
        </button>
      )}
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

let nextKey = 1

export function ProducaoNovaPage() {
  const navigate = useNavigate()
  const { user } = useAuth()

  const [medicos, setMedicos]     = useState<Medico[]>([])
  const [tomadores, setTomadores] = useState<Tomador[]>([])
  const [servicos, setServicos]   = useState<Servico[]>([])
  const [empresas, setEmpresas]   = useState<Empresa[]>([])
  const [loadingData, setLoadingData] = useState(true)

  const [tomador,    setTomador]    = useState<AutocompleteItem | null>(null)
  const [cnaeCodigo, setCnaeCodigo] = useState<string>('')
  const [servico,    setServico]    = useState<AutocompleteItem | null>(null)
  const [empresaId,  setEmpresaId]  = useState<string>('')
  const [competencia, setCompetencia] = useState(currentCompetencia())
  const [descricao, setDescricao] = useState('')

  const [participantes, setParticipantes] = useState<PartItem[]>([
    { key: nextKey++, medico: null, valorStr: '' },
  ])

  const [preview, setPreview]           = useState<FiscalPreview | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [errors, setErrors]             = useState<Record<string, string>>({})
  const [submitLoading, setSubmitLoading] = useState(false)
  const [globalError, setGlobalError]   = useState<string | null>(null)

  const previewTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const competencias    = generateCompetencias()

  // Filtro de tomadores por médico(s) participante(s) (EPIC-15.13): sem médico selecionado,
  // mostra todos os tomadores do tenant; com 1+ médicos, mostra a interseção dos tomadores
  // alocados a cada um deles (nunca um tomador onde só parte dos médicos está autorizada).
  const [tomadoresFiltrados,        setTomadoresFiltrados]        = useState<Tomador[] | null>(null)
  const [tomadoresFiltroLoading,    setTomadoresFiltroLoading]    = useState(false)
  const medicoIdsSelecionados = Array.from(
    new Set(participantes.map(p => p.medico?.id).filter(Boolean) as string[])
  )
  const medicoIdsKey = medicoIdsSelecionados.slice().sort().join(',')

  useEffect(() => {
    if (medicoIdsSelecionados.length === 0) {
      setTomadoresFiltrados(null)
      return
    }
    let cancelled = false
    setTomadoresFiltroLoading(true)
    Promise.all(
      medicoIdsSelecionados.map(id => tomadoresApi.listar(undefined, id).catch(() => [] as Tomador[]))
    ).then(listas => {
      if (cancelled) return
      const [primeira, ...resto] = listas
      const intersecao = primeira.filter(t => resto.every(lista => lista.some(x => x.id === t.id)))
      setTomadoresFiltrados(intersecao)
    }).finally(() => { if (!cancelled) setTomadoresFiltroLoading(false) })
    return () => { cancelled = true }
  }, [medicoIdsKey])

  // Tomadores com faturamento por grupo configurado não geram produção manual — a produção deles
  // é gerada pelo Fechamento por Grupo (Frequência Médica → Fechamento, EPIC-13.8/PINSAUDE-13.11).
  const tomadoresDisponiveis = (tomadoresFiltrados ?? tomadores).filter(t => !t.temGrupoFaturamento)
  const existeTomadorComGrupo = tomadores.some(t => t.temGrupoFaturamento)

  useEffect(() => {
    Promise.all([
      medicosApi.listar(0, 1000, 'ATIVO').catch(() => ({ content: [] as Medico[] })),
      tomadoresApi.listar().catch(() => [] as Tomador[]),
      servicosApi.listar().catch(() => [] as Servico[]),
      empresasApi.listar(0, 1000).catch(() => ({ content: [] as Empresa[], page: 0, size: 1000, totalElements: 0, totalPages: 0 })),
    ]).then(([mp, ts, ss, ep]) => {
      setMedicos(mp.content)
      setTomadores(ts)
      setServicos(ss)
      setEmpresas(ep.content)
      // Auto-select: única empresa → seleciona direto; múltiplas → tenta pelo cnpj_id do JWT
      if (ep.content.length === 1) {
        setEmpresaId(ep.content[0].id)
      } else if (user?.cnpj_id) {
        const digits = user.cnpj_id.replace(/\D/g, '')
        const match = ep.content.find(e => e.cnpj.replace(/\D/g, '') === digits)
        if (match) setEmpresaId(match.id)
      }
    }).finally(() => setLoadingData(false))
  }, [user])

  const totalCentavos = participantes.reduce((s, p) => s + parseBRL(p.valorStr), 0)

  useEffect(() => {
    if (previewTimerRef.current) clearTimeout(previewTimerRef.current)
    const tomObj = tomador ? tomadores.find(t => t.id === tomador.id) : null
    const servObj = servico ? servicos.find(s => s.id === servico.id) : null
    if (!tomObj || !servObj || totalCentavos <= 0) {
      setPreview(null)
      return
    }
    previewTimerRef.current = setTimeout(() => {
      const tomadorPj = tomObj.tipo !== 'PACIENTE_PF'
      const retFed    = tomadorPj && tomObj.indicadorRetencaoFederal
      const retIss    = tomadorPj && tomObj.indicadorRetencaoIss
      // Cenário D: PF sem equiparação — apenas IR retido pelo tomador
      const cenarioD  = !tomadorPj && !servObj.indicadorEquiparacao

      // Alíquotas do tomador sobrescrevem as da empresa: converter de percentual (5.0) para fração (0.05)
      const aliquotasOverride: Record<string, number> = {}
      if (tomObj.aliquotas?.length) {
        for (const a of tomObj.aliquotas) {
          aliquotasOverride[a.tipoTributo] = Number(a.valorAliquota) / 100
        }
      }

      setPreviewLoading(true)
      calcularFiscal({
        competencia,
        valorBruto: totalCentavos,
        tomadorPj,
        indicadorRetencaoFederal: tomObj.indicadorRetencaoFederal,
        indicadorRetencaoIss: tomObj.indicadorRetencaoIss,
        equiparacaoHospitalar: servObj.indicadorEquiparacao,
        aliquotasOverride: Object.keys(aliquotasOverride).length > 0 ? aliquotasOverride : undefined,
      })
        .then(r => setPreview({
          valorBruto:       r.valorBruto,
          iss:    { valor: r.valorIss,    retido: retIss },
          ir:     { valor: r.valorIr,     retido: retFed || cenarioD },
          csll:   { valor: r.valorCsll,   retido: retFed },
          pis:    { valor: r.valorPis,    retido: retFed },
          cofins: { valor: r.valorCofins, retido: retFed },
          totalRetencoes:   r.totalRetencoes,
          valorLiquidoNota: r.valorBruto - r.totalRetencoes,
        }))
        .catch(() => setPreview(null))
        .finally(() => setPreviewLoading(false))
    }, 400)
    return () => { if (previewTimerRef.current) clearTimeout(previewTimerRef.current) }
  }, [servico?.id, tomador?.id, totalCentavos, competencia, tomadores, servicos])

  // Serviços disponíveis para o tomador selecionado: se o tomador tem serviços cadastrados,
  // só eles aparecem; caso contrário, cai no catálogo completo (fallback para tomadores sem vínculo).
  const tomadorObj = tomador ? tomadores.find(t => t.id === tomador.id) ?? null : null
  const servicosDisponiveis: Servico[] = tomadorObj && tomadorObj.servicos && tomadorObj.servicos.length > 0
    ? servicos.filter(s => tomadorObj.servicos.some(v => v.servicoId === s.id))
    : servicos

  // Empresas emissoras disponíveis para o tomador selecionado (PINSAUDE-13.12): se o tomador
  // tem empresa(s) Pin vinculada(s), só elas aparecem; caso contrário, cai no catálogo completo
  // (fallback para tomadores sem vínculo — maioria dos casos hoje).
  const empresasDisponiveis: Empresa[] = tomadorObj && tomadorObj.empresas && tomadorObj.empresas.length > 0
    ? empresas.filter(e => tomadorObj.empresas.some(v => v.empresaId === e.id))
    : empresas

  // Auto-seleciona quando há exatamente 1 opção disponível (serviço, CNAE ou empresa do
  // tomador); limpa a seleção que deixou de ser válida para o tomador atual.
  useEffect(() => {
    // Serviço
    if (servicosDisponiveis.length === 1) {
      const only = servicosDisponiveis[0]
      setServico(prev => prev?.id === only.id ? prev : { id: only.id, label: `${only.codigoLc116} — ${only.descricaoPadrao}` })
    } else if (servico && !servicosDisponiveis.some(s => s.id === servico.id)) {
      setServico(null)
    }

    // CNAE do tomador
    const cnaesTomador = tomadorObj?.cnaes ?? []
    if (cnaesTomador.length === 1) {
      setCnaeCodigo(prev => prev === cnaesTomador[0].codigoCnae ? prev : cnaesTomador[0].codigoCnae)
    } else if (cnaeCodigo && !cnaesTomador.some(c => c.codigoCnae === cnaeCodigo)) {
      setCnaeCodigo('')
    }

    // Empresa emissora vinculada ao tomador
    const empresasTomador = tomadorObj?.empresas ?? []
    if (empresasTomador.length === 1) {
      setEmpresaId(prev => prev === empresasTomador[0].empresaId ? prev : empresasTomador[0].empresaId)
    } else if (empresaId && empresasTomador.length > 0 && !empresasTomador.some(v => v.empresaId === empresaId)) {
      setEmpresaId('')
    }
  }, [tomador?.id, servicos, tomadores, empresas])

  const usedMedicoIds = new Set(participantes.map(p => p.medico?.id).filter(Boolean) as string[])

  function addParticipante() {
    setParticipantes(prev => [...prev, { key: nextKey++, medico: null, valorStr: '' }])
  }

  function removeParticipante(key: number) {
    setParticipantes(prev => prev.filter(p => p.key !== key))
  }

  function updateParticipante(key: number, patch: Partial<PartItem>) {
    setParticipantes(prev => prev.map(p => p.key === key ? { ...p, ...patch } : p))
  }

  function validate(): boolean {
    const e: Record<string, string> = {}
    if (!tomador)    e.tomador   = 'Selecione um tomador'
    if (!servico)    e.servico   = 'Selecione um serviço'
    if (!competencia) e.competencia = 'Selecione a competência'
    if (!empresaId)  e.empresa   = 'Selecione a empresa emissora'

    const semMedico = participantes.some(p => !p.medico)
    const semValor  = participantes.some(p => parseBRL(p.valorStr) <= 0)
    if (semMedico) e.participantes = 'Todos os participantes precisam de um médico selecionado'
    if (semValor)  e.participantes = (e.participantes ? e.participantes + ' ' : '') + 'e valor maior que zero'

    // duplicate médicos
    const ids = participantes.map(p => p.medico?.id).filter(Boolean)
    if (new Set(ids).size !== ids.length) e.participantes = 'O mesmo médico não pode aparecer duas vezes'

    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function handleSubmit() {
    if (!validate()) return

    const medicoMap = new Map(medicos.map(m => [m.id, m]))
    const req: ProducaoRequest = {
      tomadorId:             tomador!.id,
      servicoId:             servico!.id,
      competencia,
      descricaoComplementar: descricao || undefined,
      cnaeCodigo:            cnaeCodigo || undefined,
      empresaId:             empresaId || null,
      participantes: participantes.map(p => ({
        medicoId:   p.medico!.id,
        valorBruto: parseBRL(p.valorStr),
        taxaPinPct: medicoMap.get(p.medico!.id)?.taxaPinPct ?? 0.15,
      })),
    }

    setSubmitLoading(true)
    setGlobalError(null)
    try {
      await producoesApi.criar(req)
      navigate('/producao')
    } catch (e: unknown) {
      setGlobalError(e instanceof Error ? e.message : 'Erro ao registrar produção')
    } finally {
      setSubmitLoading(false)
    }
  }

  const medicoItems: AutocompleteItem[] = medicos.map(m => ({
    id: m.id,
    label: m.nome,
    sublabel: `CRM ${m.crm}-${m.crmUf}${m.especialidade ? ` · ${m.especialidade}` : ''}`,
  }))

  const tomadorItems: AutocompleteItem[] = tomadoresDisponiveis.map(t => ({
    id: t.id,
    label: t.razaoSocialNome,
    highlight: t.nomeFantasia ?? undefined,
    sublabel: t.municipio ?? undefined,
  }))

  const canConfirm = !!tomador && !!servico && totalCentavos > 0 && !!competencia && !!empresaId
    && participantes.every(p => p.medico && parseBRL(p.valorStr) > 0)

  if (loadingData) return (
    <div className="flex items-center justify-center h-64">
      <Spinner size="lg" />
    </div>
  )

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate('/producao')}
          className="p-2 rounded-lg hover:bg-ds-input text-ds-light hover:text-ds-mid transition-colors">
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-ds-mid">Nova Produção Médica</h1>
          <p className="text-sm text-ds-light mt-0.5">Registre a produção de um ou mais médicos e veja o breakdown fiscal</p>
        </div>
      </div>

      {globalError && <Alert variant="error" onClose={() => setGlobalError(null)}>{globalError}</Alert>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">

        {/* Formulário (3/5) */}
        <div className="lg:col-span-3 space-y-5">
          <div className="bg-white rounded-xl border border-ds-border shadow-sm p-6 space-y-5">
            <h2 className="font-semibold text-ds-mid text-sm uppercase tracking-wide border-b border-ds-border pb-3">
              Dados da Nota / Tomador
            </h2>

            <Field label="Tomador (Hospital / Clínica / Operadora)" required error={errors.tomador}>
              <Autocomplete
                items={tomadorItems}
                value={tomador}
                onChange={item => { setTomador(item); setCnaeCodigo(''); setServico(null); setErrors(e => ({ ...e, tomador: '' })) }}
                onClear={() => { setTomador(null); setCnaeCodigo(''); setServico(null); setPreview(null) }}
                placeholder="Buscar por nome ou CNPJ..."
                loading={tomadoresFiltroLoading}
              />
              {tomadoresFiltrados && (
                <p className="mt-1 text-[11px] text-ds-light">
                  {medicoIdsSelecionados.length === 1
                    ? 'Exibindo apenas tomadores alocados ao médico selecionado.'
                    : 'Exibindo apenas tomadores alocados a todos os médicos participantes selecionados.'}
                </p>
              )}
              {existeTomadorComGrupo && (
                <p className="mt-1 text-[11px] text-ds-light">
                  Tomadores com faturamento por grupo configurado não aparecem aqui — a produção deles é gerada pelo Fechamento por Grupo.
                </p>
              )}
              {tomador && tomadoresFiltrados && !tomadoresFiltrados.some(t => t.id === tomador.id) && (
                <p className="mt-1 text-xs text-amber-600 flex items-center gap-1">
                  <AlertCircle size={11} />
                  Atenção: o tomador selecionado não está alocado a todos os médicos participantes.
                </p>
              )}
            </Field>

            {/* CNAE do tomador — aparece quando o tomador tem CNAEs cadastrados */}
            {(() => {
              const tomadorObj = tomadores.find(t => t.id === tomador?.id)
              if (!tomadorObj || !tomadorObj.cnaes || tomadorObj.cnaes.length === 0) return null
              const cnaeUnico = tomadorObj.cnaes.length === 1
              return (
                <Field label="CNAE (atividade econômica da nota)">
                  <select
                    value={cnaeCodigo}
                    onChange={e => setCnaeCodigo(e.target.value)}
                    disabled={cnaeUnico}
                    className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid disabled:bg-ds-surface disabled:text-ds-mid"
                  >
                    <option value="">Selecione o CNAE...</option>
                    {tomadorObj.cnaes.map(c => (
                      <option key={c.id} value={c.codigoCnae}>
                        {formatCnae(c.codigoCnae)}{c.descricao ? ` — ${c.descricao.charAt(0) + c.descricao.slice(1).toLowerCase()}` : ''}
                      </option>
                    ))}
                  </select>
                  {cnaeUnico && (
                    <p className="mt-1 text-[11px] text-ds-light">
                      CNAE único do tomador — selecionado automaticamente.
                    </p>
                  )}
                </Field>
              )
            })()}

            <Field label="Serviço (LC 116/2003)" required error={errors.servico}>
              <select
                value={servico?.id ?? ''}
                onChange={e => {
                  const s = servicosDisponiveis.find(s => s.id === e.target.value)
                  setServico(s ? { id: s.id, label: `${s.codigoLc116} — ${s.descricaoPadrao}` } : null)
                  setErrors(ex => ({ ...ex, servico: '' }))
                }}
                disabled={servicosDisponiveis.length === 1}
                className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid disabled:bg-ds-surface disabled:text-ds-mid"
              >
                <option value="">Selecione o serviço...</option>
                {servicosDisponiveis.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.codigoLc116} — {s.descricaoPadrao}
                  </option>
                ))}
              </select>
              {tomadorObj && tomadorObj.servicos && tomadorObj.servicos.length > 0 && (
                <p className="mt-1 text-[11px] text-ds-light">
                  {servicosDisponiveis.length === 1
                    ? 'Serviço único do tomador — selecionado automaticamente.'
                    : 'Exibindo apenas os serviços cadastrados para este tomador.'}
                </p>
              )}
            </Field>

            <Field label="Competência" required error={errors.competencia}>
              <select
                value={competencia}
                onChange={e => setCompetencia(e.target.value)}
                className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid"
              >
                {competencias.map(c => (
                  <option key={c} value={c}>{competenciaLabel(c)}</option>
                ))}
              </select>
            </Field>

            <Field label="Empresa Emissora (Pin Saúde)" required error={errors.empresa}>
              <select
                value={empresaId}
                onChange={e => { setEmpresaId(e.target.value); setErrors(ex => ({ ...ex, empresa: '' })) }}
                className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid"
              >
                <option value="">Selecione a empresa...</option>
                {empresasDisponiveis.map(e => (
                  <option key={e.id} value={e.id}>
                    {e.razaoSocial} — {e.cnpj}
                  </option>
                ))}
              </select>
              {tomadorObj && tomadorObj.empresas.length > 0 && (
                <p className="mt-1 text-[11px] text-ds-light">
                  {empresasDisponiveis.length === 1
                    ? 'Empresa única vinculada ao tomador — selecionada automaticamente.'
                    : 'Exibindo apenas as empresas Pin vinculadas a este tomador.'}
                </p>
              )}
            </Field>

            <Field label="Descrição Complementar">
              <textarea
                value={descricao}
                onChange={e => setDescricao(e.target.value)}
                placeholder="Informações adicionais sobre a produção (opcional)"
                rows={2}
                className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
              />
            </Field>
          </div>

          {/* Participantes */}
          <div className="bg-white rounded-xl border border-ds-border shadow-sm p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-ds-border pb-3">
              <div className="flex items-center gap-2">
                <Users size={16} className="text-primary" />
                <h2 className="font-semibold text-ds-mid text-sm uppercase tracking-wide">
                  Participantes
                </h2>
                <span className="text-xs bg-primary-50 text-primary font-semibold px-2 py-0.5 rounded-full">
                  {participantes.length}
                </span>
              </div>
              <button
                onClick={addParticipante}
                disabled={participantes.length >= 20}
                className="flex items-center gap-1 text-xs text-primary font-semibold hover:text-primary-700 disabled:opacity-40 transition-colors"
              >
                <Plus size={13} />
                Adicionar médico
              </button>
            </div>

            {errors.participantes && (
              <p className="text-xs text-red-500 flex items-center gap-1">
                <AlertCircle size={11} />{errors.participantes}
              </p>
            )}

            <div className="space-y-3">
              {participantes.map((part, idx) => (
                <ParticipanteRow
                  key={part.key}
                  part={part}
                  index={idx}
                  medicoItems={medicoItems}
                  usedMedicoIds={usedMedicoIds}
                  onChangeMedico={item => updateParticipante(part.key, { medico: item })}
                  onChangeValor={val => updateParticipante(part.key, { valorStr: val })}
                  onRemove={() => removeParticipante(part.key)}
                  canRemove={participantes.length > 1}
                />
              ))}
            </div>

            {/* Total */}
            {totalCentavos > 0 && (
              <div className="mt-3 px-4 py-3 bg-primary-50 rounded-lg flex items-center justify-between">
                <span className="text-sm font-semibold text-primary">Valor Bruto Total</span>
                <span className="text-lg font-black text-primary">{formatBRL(totalCentavos)}</span>
              </div>
            )}

            {medicos.length === 0 && (
              <p className="text-xs text-amber-600 flex items-center gap-1">
                <AlertCircle size={11} />
                Nenhum médico ativo encontrado. Ative um médico primeiro.
              </p>
            )}
          </div>

          <div className="flex items-center justify-between">
            <Button variant="ghost" onClick={() => navigate('/producao')} disabled={submitLoading}>
              Cancelar
            </Button>
            <div className="flex flex-col items-end gap-1">
              {!canConfirm && !submitLoading && (
                <p className="text-xs text-ds-light text-right">
                  Faltam: {[
                    !tomador && 'tomador',
                    !servico && 'serviço',
                    !empresaId && 'empresa emissora',
                    totalCentavos <= 0 && 'valor',
                    participantes.some(p => !p.medico) && 'médico',
                  ].filter(Boolean).join(', ')}
                </p>
              )}
              <Button
                onClick={handleSubmit}
                disabled={!canConfirm || submitLoading}
                className="min-w-40"
              >
                {submitLoading ? (
                  <><Loader2 size={15} className="mr-2 animate-spin" />Confirmando...</>
                ) : (
                  <><CheckCircle2 size={15} className="mr-2" />Confirmar Produção</>
                )}
              </Button>
            </div>
          </div>
        </div>

        {/* Preview (2/5) */}
        <div className="lg:col-span-2">
          <PreviewCard preview={preview} loading={previewLoading} />
        </div>
      </div>
    </div>
  )
}
