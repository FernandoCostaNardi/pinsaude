import { useCallback, useEffect, useRef, useState } from 'react'
import {
  CalendarDays, ChevronDown, ClipboardList, FileText,
  Loader2, Plus, Printer, Search, Trash2, X,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import { tomadoresApi, Tomador, TomadorGrupoFaturamento, TomadorModalidade } from '../api/tomadoresApi'
import { medicosApi, Medico, MedicoPage } from '../api/medicosApi'
import {
  frequenciasApi,
  FrequenciaMedicaResp,
  FrequenciaMedicaRequest,
  FrequenciaItemRequest,
} from '../api/frequenciasApi'
import { useAuth } from '../auth/AuthContext'
import { abrirPdfFrequencia } from '../utils/frequenciaPdf'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function formatDate(iso: string): string {
  if (iso.includes('T')) return new Date(iso).toLocaleDateString('pt-BR')
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
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

const COMPETENCIAS = generateCompetencias()

const STATUS_LABEL: Record<string, string> = {
  RASCUNHO:              'Rascunho',
  PDF_GERADO:            'PDF Gerado',
  AGUARDANDO_ASSINATURA: 'Aguard. Assinatura',
  ASSINADA_RECEBIDA:     'Assinada',
  ENVIADA_TOMADOR:       'Enviada ao Tomador',
  FATURADA:              'Faturada',
}

const STATUS_CLS: Record<string, string> = {
  RASCUNHO:              'bg-gray-100 text-gray-600',
  PDF_GERADO:            'bg-blue-50 text-blue-700',
  AGUARDANDO_ASSINATURA: 'bg-yellow-50 text-yellow-700',
  ASSINADA_RECEBIDA:     'bg-purple-50 text-purple-700',
  ENVIADA_TOMADOR:       'bg-indigo-50 text-indigo-700',
  FATURADA:              'bg-green-50 text-green-700',
}

// ─── Dropdown genérico ────────────────────────────────────────────────────────

function Dropdown<T extends { id: string }>({
  label, placeholder, items, value, onChange, getLabel, disabled,
}: {
  label?: string
  placeholder: string
  items: T[]
  value: T | null
  onChange: (v: T) => void
  getLabel: (v: T) => string
  disabled?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [q, setQ]       = useState('')
  const ref             = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const filtered = items.filter(i => getLabel(i).toLowerCase().includes(q.toLowerCase()))

  return (
    <div ref={ref} className="relative">
      {label && <label className="block text-xs font-bold text-ds-mid mb-1">{label}</label>}
      <button type="button" disabled={disabled}
        onClick={() => { setOpen(!open); setQ('') }}
        className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border text-sm transition-colors disabled:opacity-50 ${
          value ? 'border-primary/40 bg-primary-50 text-ds-text font-medium' : 'border-ds-border bg-white text-ds-light'
        }`}>
        <span className="truncate">{value ? getLabel(value) : placeholder}</span>
        <ChevronDown size={14} className={`shrink-0 ml-2 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute z-30 top-full left-0 right-0 mt-1 bg-white border border-ds-border rounded-xl shadow-lg overflow-hidden">
          <div className="p-2 border-b border-ds-border">
            <input autoFocus value={q} onChange={e => setQ(e.target.value)} placeholder="Buscar..."
              className="w-full text-xs px-2 py-1.5 border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div className="max-h-52 overflow-y-auto divide-y divide-ds-border">
            {filtered.length === 0
              ? <p className="px-3 py-3 text-xs text-ds-light text-center">Sem resultados</p>
              : filtered.map(item => (
                <button key={item.id} type="button"
                  onClick={() => { onChange(item); setOpen(false); setQ('') }}
                  className="w-full text-left px-3 py-2.5 text-sm hover:bg-ds-surface transition-colors">
                  {getLabel(item)}
                </button>
              ))
            }
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Modal nova frequência ────────────────────────────────────────────────────

function NovaFrequenciaModal({
  tomadores, medicos, onClose, onCriada,
}: {
  tomadores: Tomador[]
  medicos: Medico[]
  onClose: () => void
  onCriada: (f: FrequenciaMedicaResp) => void
}) {
  const [tomador,     setTomador]     = useState<Tomador | null>(null)
  const [medico,      setMedico]      = useState<Medico | null>(null)
  const [setor,       setSetor]       = useState<{ id: string; nome: string } | null>(null)
  const [competencia, setCompetencia] = useState(COMPETENCIAS[0])
  const [especialidade, setEspecialidade] = useState('')
  const [grupos,      setGrupos]      = useState<TomadorGrupoFaturamento[]>([])
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

  const setores = grupos.flatMap(g => g.servicosOperacionais.filter(s => s.ativo))

  useEffect(() => {
    if (!tomador) { setGrupos([]); return }
    tomadoresApi.listarGrupos(tomador.id).then(setGrupos).catch(() => setGrupos([]))
  }, [tomador?.id])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!tomador || !medico || !setor || !especialidade.trim()) return
    setSaving(true); setErr(null)
    try {
      const req: FrequenciaMedicaRequest = {
        tomadorId: tomador.id,
        medicoId: medico.id,
        servicoOperacionalId: setor.id,
        competencia,
        especialidade: especialidade.trim().toUpperCase(),
      }
      const criada = await frequenciasApi.criar(req)
      onCriada(criada)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao criar')
    } finally { setSaving(false) }
  }

  const canSave = !!tomador && !!medico && !!setor && !!especialidade.trim()
  const medicosFiltrados = medicos.filter(m => m.status === 'ATIVO')

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-5 py-4 border-b border-ds-border">
          <p className="text-sm font-bold text-ds-text flex items-center gap-2">
            <CalendarDays size={16} className="text-primary" />
            Nova Frequência Médica
          </p>
          <button onClick={onClose} className="p-1 rounded-lg text-ds-light hover:bg-ds-input transition-colors">
            <X size={18} />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          {err && <Alert variant="error" onClose={() => setErr(null)}>{err}</Alert>}

          <Dropdown
            label="Médico *"
            placeholder="Selecione o médico..."
            items={medicosFiltrados}
            value={medico}
            onChange={m => { setMedico(m); setEspecialidade(m.especialidade ?? '') }}
            getLabel={m => `${m.nome} — CRM ${m.crm}/${m.crmUf}`}
          />

          <Dropdown
            label="Tomador *"
            placeholder="Selecione o tomador..."
            items={tomadores}
            value={tomador}
            onChange={t => { setTomador(t); setSetor(null) }}
            getLabel={t => t.razaoSocialNome + (t.municipio ? ` — ${t.municipio}` : '')}
          />

          <Dropdown
            label="Setor Operacional *"
            placeholder={tomador ? (setores.length === 0 ? 'Nenhum setor' : 'Selecione...') : 'Selecione o tomador...'}
            items={setores}
            value={setor}
            onChange={setSetor}
            getLabel={s => s.nome}
            disabled={!tomador || setores.length === 0}
          />

          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Competência *</label>
            <select value={competencia} onChange={e => setCompetencia(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30">
              {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Especialidade *</label>
            <input type="text" value={especialidade} onChange={e => setEspecialidade(e.target.value)}
              placeholder="Ex.: MÉDICO PLANTONISTA"
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>

          <div className="flex gap-3 pt-1">
            <Button type="button" variant="secondary" onClick={onClose} className="flex-1">Cancelar</Button>
            <Button type="submit" disabled={!canSave || saving} className="flex-1">
              {saving ? <><Loader2 size={14} className="animate-spin mr-1.5" />Criando...</> : 'Criar Frequência'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Row de adicionar item ─────────────────────────────────────────────────────

function AdicionarItemRow({
  tomadorId, onAdd, onCancel,
}: {
  tomadorId: string
  onAdd: (req: FrequenciaItemRequest) => Promise<void>
  onCancel: () => void
}) {
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [modalidade,  setModalidade]  = useState<TomadorModalidade | null>(null)
  const [data,        setData]        = useState(new Date().toISOString().slice(0, 10))
  const [ocorrencia,  setOcorrencia]  = useState('')
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

  useEffect(() => {
    tomadoresApi.listarModalidades(tomadorId)
      .then(ms => setModalidades(ms.filter(m => m.ativo)))
      .catch(() => {})
  }, [tomadorId])

  async function handleAdd() {
    if (!modalidade) return
    setSaving(true); setErr(null)
    try {
      await onAdd({ modalidadeId: modalidade.id, dataExecucao: data, ocorrencia: ocorrencia || undefined })
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao adicionar')
    } finally { setSaving(false) }
  }

  const total = modalidade ? modalidade.valorCentavos + modalidade.deslocamentoCentavos : 0

  return (
    <tr className="bg-primary-50/50 border-b border-ds-border">
      <td className="px-3 py-2">
        <input type="date" value={data} onChange={e => setData(e.target.value)}
          className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-xs text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30" />
      </td>
      <td className="px-3 py-2">
        <Dropdown
          placeholder={modalidades.length === 0 ? 'Sem modalidades' : 'Selecione...'}
          items={modalidades} value={modalidade} onChange={setModalidade}
          getLabel={m => `${m.nome} (${m.turno})`}
          disabled={modalidades.length === 0}
        />
        {err && <p className="text-[10px] text-red-600 mt-1">{err}</p>}
      </td>
      <td className="px-3 py-2">
        <input type="text" value={ocorrencia} onChange={e => setOcorrencia(e.target.value)}
          placeholder="(opcional)"
          className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-xs text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30" />
      </td>
      <td className="px-3 py-2 text-xs tabular-nums text-right text-ds-mid">
        {modalidade ? formatBRL(modalidade.valorCentavos) : '—'}
      </td>
      <td className="px-3 py-2 text-xs tabular-nums text-right text-ds-mid">
        {modalidade && modalidade.deslocamentoCentavos > 0 ? formatBRL(modalidade.deslocamentoCentavos) : '—'}
      </td>
      <td className="px-3 py-2 text-xs tabular-nums font-bold text-right text-ds-text">
        {total > 0 ? formatBRL(total) : '—'}
      </td>
      <td className="px-3 py-2">
        <div className="flex items-center gap-1 justify-end">
          <button type="button" onClick={handleAdd} disabled={!modalidade || saving}
            className="px-2 py-1 rounded-lg bg-primary text-white text-xs font-bold disabled:opacity-50 hover:bg-primary-700 transition-colors">
            {saving ? <Loader2 size={12} className="animate-spin" /> : 'OK'}
          </button>
          <button type="button" onClick={onCancel}
            className="px-2 py-1 rounded-lg border border-ds-border text-xs text-ds-mid hover:bg-ds-input transition-colors">
            ✕
          </button>
        </div>
      </td>
    </tr>
  )
}

// ─── Painel lateral ───────────────────────────────────────────────────────────

function PainelFrequencia({
  freq, tomadores, medicos, onClose, onAtualizar,
}: {
  freq: FrequenciaMedicaResp
  tomadores: Tomador[]
  medicos: Medico[]
  onClose: () => void
  onAtualizar: (f: FrequenciaMedicaResp) => void
}) {
  const { user } = useAuth()
  const [adicionando, setAdicionando] = useState(false)
  const [removendo,   setRemovendo]   = useState<string | null>(null)
  const [gerandoPdf,  setGerandoPdf]  = useState(false)
  const isFaturada = freq.status === 'FATURADA'

  const tomador = tomadores.find(t => t.id === freq.tomadorId)
  const medico  = medicos.find(m => m.id === freq.medicoId)

  async function handleAdd(req: FrequenciaItemRequest) {
    await frequenciasApi.adicionarItem(freq.id, req)
    const atualizada = await frequenciasApi.buscarPorId(freq.id)
    onAtualizar(atualizada)
    setAdicionando(false)
  }

  async function handleRemove(itemId: string) {
    setRemovendo(itemId)
    try {
      await frequenciasApi.removerItem(freq.id, itemId)
      const atualizada = await frequenciasApi.buscarPorId(freq.id)
      onAtualizar(atualizada)
    } catch { /* ignore */ }
    finally { setRemovendo(null) }
  }

  async function handleGerarPdf() {
    if (gerandoPdf) return
    setGerandoPdf(true)
    try {
      let freqAtual = freq
      // Transição de status apenas se ainda não enviada/assinada
      const podeTransicionar = ['RASCUNHO', 'PDF_GERADO'].includes(freq.status)
      if (podeTransicionar) {
        freqAtual = await frequenciasApi.gerarPdf(freq.id)
        onAtualizar(freqAtual)
      }
      abrirPdfFrequencia({
        freq: freqAtual,
        medicoNome:   medico?.nome  ?? '—',
        medicoCrm:    medico?.crm   ?? '—',
        medicoCrmUf:  medico?.crmUf ?? '—',
        tomadorNome:  tomador?.razaoSocialNome ?? '—',
        empresaNome:  'Pin Saúde',
        empresaCnpj:  user?.cnpj_id ?? '',
      })
    } catch { /* ignore — alert já vem do browser se popup bloqueado */ }
    finally { setGerandoPdf(false) }
  }

  return (
    <div className="w-[480px] xl:w-[560px] shrink-0 bg-white border-l border-ds-border flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-ds-border bg-ds-surface shrink-0">
        <div>
          <p className="text-sm font-bold text-ds-text">{formatCompetencia(freq.competencia)}</p>
          <p className="text-xs text-ds-light mt-0.5">{freq.servicoOperacionalNome ?? '—'}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold ${STATUS_CLS[freq.status] ?? 'bg-gray-100 text-gray-500'}`}>
            {STATUS_LABEL[freq.status] ?? freq.status}
          </span>
          <button
            onClick={handleGerarPdf}
            disabled={gerandoPdf}
            title="Gerar PDF do Relatório de Frequência"
            className="p-1.5 rounded-lg text-ds-light hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-50">
            {gerandoPdf ? <Loader2 size={15} className="animate-spin" /> : <Printer size={15} />}
          </button>
          <button onClick={onClose} className="p-1 rounded-lg text-ds-light hover:bg-ds-input transition-colors">
            <X size={16} />
          </button>
        </div>
      </div>

      {/* Info */}
      <div className="px-4 py-3 border-b border-ds-border bg-ds-surface/50 shrink-0 space-y-1">
        <div className="flex gap-2 flex-wrap text-xs text-ds-mid">
          <span className="font-semibold">Médico:</span>
          <span>{medico?.nome ?? freq.medicoId}</span>
          {medico && <span className="text-ds-light">CRM {medico.crm}/{medico.crmUf}</span>}
        </div>
        <div className="flex gap-2 flex-wrap text-xs text-ds-mid">
          <span className="font-semibold">Tomador:</span>
          <span className="truncate">{tomador?.razaoSocialNome ?? freq.tomadorId}</span>
        </div>
        <div className="flex gap-2 text-xs text-ds-mid">
          <span className="font-semibold">Especialidade:</span>
          <span>{freq.especialidade}</span>
        </div>
      </div>

      {/* Totalizador */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-ds-border shrink-0">
        <p className="text-xs text-ds-mid">
          <span className="font-bold text-ds-text">{freq.itens.length}</span> plantão{freq.itens.length !== 1 ? 'ões' : ''} ·
          Total: <span className="font-black text-ds-text tabular-nums ml-1">{formatBRL(freq.totalValorCentavos)}</span>
        </p>
        {!isFaturada && (
          <button onClick={() => setAdicionando(true)} disabled={adicionando}
            className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-primary text-white text-xs font-bold hover:bg-primary-700 transition-colors disabled:opacity-50">
            <Plus size={12} /> Adicionar
          </button>
        )}
      </div>

      {/* Itens */}
      <div className="flex-1 overflow-y-auto">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[400px]">
            <thead className="sticky top-0 bg-white">
              <tr className="border-b border-ds-border">
                {['Data', 'Modalidade', 'Ocorrência', 'Val.Unit.', 'Desl.', 'Total', ''].map(h => (
                  <th key={h} className="px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-ds-border">
              {freq.itens.map(item => (
                <tr key={item.id} className="hover:bg-ds-surface/50 transition-colors">
                  <td className="px-3 py-2 text-xs font-medium text-ds-text whitespace-nowrap">{formatDate(item.dataExecucao)}</td>
                  <td className="px-3 py-2">
                    <p className="text-xs font-semibold text-ds-text">{item.modalidadeNome ?? '—'}</p>
                    {item.modalidadeTurno && (
                      <p className="text-[10px] text-ds-light">{item.modalidadeTurno} · {item.modalidadeHorario}</p>
                    )}
                  </td>
                  <td className="px-3 py-2 text-xs text-ds-mid max-w-[80px] truncate">{item.ocorrencia ?? '—'}</td>
                  <td className="px-3 py-2 text-xs tabular-nums text-right text-ds-mid whitespace-nowrap">{formatBRL(item.valorUnitarioCentavos)}</td>
                  <td className="px-3 py-2 text-xs tabular-nums text-right text-ds-mid whitespace-nowrap">
                    {item.deslocamentoCentavos > 0 ? formatBRL(item.deslocamentoCentavos) : '—'}
                  </td>
                  <td className="px-3 py-2 text-xs tabular-nums font-bold text-right text-ds-text whitespace-nowrap">{formatBRL(item.totalItemCentavos)}</td>
                  <td className="px-3 py-2">
                    {!isFaturada && (
                      <button onClick={() => handleRemove(item.id)} disabled={removendo === item.id}
                        className="p-1 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors">
                        {removendo === item.id ? <Loader2 size={12} className="animate-spin" /> : <Trash2 size={12} />}
                      </button>
                    )}
                  </td>
                </tr>
              ))}

              {adicionando && (
                <AdicionarItemRow
                  tomadorId={freq.tomadorId}
                  onAdd={handleAdd}
                  onCancel={() => setAdicionando(false)}
                />
              )}

              {freq.itens.length === 0 && !adicionando && (
                <tr>
                  <td colSpan={7} className="px-4 py-10 text-center text-xs text-ds-light">
                    <FileText size={22} className="mx-auto mb-2 opacity-20" />
                    Nenhum plantão lançado.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

export function FrequenciasPage() {
  const [tomadores,   setTomadores]   = useState<Tomador[]>([])
  const [medicos,     setMedicos]     = useState<Medico[]>([])
  const [frequencias, setFrequencias] = useState<FrequenciaMedicaResp[]>([])
  const [loading,     setLoading]     = useState(true)
  const [initErr,     setInitErr]     = useState<string | null>(null)
  const [selecionada, setSelecionada] = useState<FrequenciaMedicaResp | null>(null)
  const [showNova,    setShowNova]    = useState(false)

  // Filtros
  const [filtroTomador,   setFiltroTomador]   = useState('')
  const [filtroMedico,    setFiltroMedico]    = useState('')
  const [filtroComp,      setFiltroComp]      = useState('')
  const [filtroStatus,    setFiltroStatus]    = useState('')
  const [q,               setQ]              = useState('')

  const carregar = useCallback(async () => {
    const data = await frequenciasApi.listar({
      tomadorId: filtroTomador || undefined,
      medicoId:  filtroMedico  || undefined,
      competencia: filtroComp  || undefined,
      status:    filtroStatus  || undefined,
    })
    setFrequencias(data)
  }, [filtroTomador, filtroMedico, filtroComp, filtroStatus])

  useEffect(() => {
    async function init() {
      try {
        const [t, mp] = await Promise.all([tomadoresApi.listar(), medicosApi.listar(0, 1000)])
        setTomadores(t)
        setMedicos((mp as MedicoPage).content)
        const data = await frequenciasApi.listar()
        setFrequencias(data)
      } catch (e) {
        setInitErr(e instanceof Error ? e.message : 'Erro ao carregar')
      } finally { setLoading(false) }
    }
    init()
  }, [])

  useEffect(() => {
    if (!loading) {
      carregar().catch(() => {})
    }
  }, [filtroTomador, filtroMedico, filtroComp, filtroStatus])

  function handleCriada(f: FrequenciaMedicaResp) {
    setFrequencias(prev => [f, ...prev])
    setShowNova(false)
    setSelecionada(f)
  }

  function handleAtualizar(f: FrequenciaMedicaResp) {
    setFrequencias(prev => prev.map(x => x.id === f.id ? f : x))
    setSelecionada(f)
  }

  const tomadoresMap = Object.fromEntries(tomadores.map(t => [t.id, t]))
  const medicosMap   = Object.fromEntries(medicos.map(m => [m.id, m]))

  const filtradas = frequencias.filter(f => {
    if (!q) return true
    const qL = q.toLowerCase()
    const tomNome = tomadoresMap[f.tomadorId]?.razaoSocialNome?.toLowerCase() ?? ''
    const medNome = medicosMap[f.medicoId]?.nome?.toLowerCase() ?? ''
    return tomNome.includes(qL) || medNome.includes(qL) || f.especialidade.toLowerCase().includes(qL)
      || (f.servicoOperacionalNome ?? '').toLowerCase().includes(qL)
  })

  if (loading) return <div className="flex justify-center py-16"><Spinner size="lg" /></div>
  if (initErr)  return <div className="p-6"><Alert variant="error">{initErr}</Alert></div>

  return (
    <div className="flex-1 overflow-hidden flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-ds-border shrink-0">
        <div>
          <h1 className="text-lg font-black text-ds-text flex items-center gap-2">
            <CalendarDays size={20} className="text-primary" />
            Frequências Médicas
          </h1>
          <p className="text-xs text-ds-light mt-0.5">Plantões lançados por médico, setor e competência</p>
        </div>
        <Button onClick={() => setShowNova(true)}>
          <Plus size={15} className="mr-2" /> Nova Frequência
        </Button>
      </div>

      {/* Filtros */}
      <div className="flex items-center gap-3 px-6 py-3 border-b border-ds-border bg-ds-surface shrink-0 flex-wrap">
        <div className="relative">
          <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-ds-light" />
          <input value={q} onChange={e => setQ(e.target.value)}
            placeholder="Buscar médico, tomador..."
            className="pl-8 pr-3 py-2 border border-ds-border rounded-lg text-sm bg-white w-52 focus:outline-none focus:ring-2 focus:ring-primary/30" />
        </div>
        <select value={filtroTomador} onChange={e => { setFiltroTomador(e.target.value); setSelecionada(null) }}
          className="border border-ds-border rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
          <option value="">Todos os tomadores</option>
          {tomadores.map(t => <option key={t.id} value={t.id}>{t.razaoSocialNome}</option>)}
        </select>
        <select value={filtroMedico} onChange={e => { setFiltroMedico(e.target.value); setSelecionada(null) }}
          className="border border-ds-border rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
          <option value="">Todos os médicos</option>
          {medicos.filter(m => m.status === 'ATIVO').map(m => <option key={m.id} value={m.id}>{m.nome}</option>)}
        </select>
        <select value={filtroComp} onChange={e => { setFiltroComp(e.target.value); setSelecionada(null) }}
          className="border border-ds-border rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
          <option value="">Todas as competências</option>
          {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
        </select>
        <select value={filtroStatus} onChange={e => { setFiltroStatus(e.target.value); setSelecionada(null) }}
          className="border border-ds-border rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
          <option value="">Todos os status</option>
          {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <span className="ml-auto text-xs text-ds-light">{filtradas.length} registro{filtradas.length !== 1 ? 's' : ''}</span>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-hidden flex">
        {/* Lista */}
        <div className="flex-1 overflow-auto">
          {filtradas.length === 0 ? (
            <div className="flex flex-col items-center py-20 text-ds-light">
              <ClipboardList size={40} className="mb-3 opacity-20" />
              <p className="text-sm font-medium">Nenhuma frequência encontrada</p>
            </div>
          ) : (
            <table className="w-full">
              <thead className="sticky top-0 bg-white z-10 border-b border-ds-border">
                <tr>
                  {['Competência', 'Médico', 'Tomador', 'Setor', 'Plantões', 'Total', 'Status'].map(h => (
                    <th key={h} className="px-4 py-3 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-ds-border">
                {filtradas.map(f => {
                  const isSelected = selecionada?.id === f.id
                  return (
                    <tr key={f.id}
                      onClick={() => setSelecionada(isSelected ? null : f)}
                      className={`cursor-pointer transition-colors ${isSelected ? 'bg-primary-50 border-l-2 border-l-primary' : 'hover:bg-ds-surface/50'}`}>
                      <td className="px-4 py-3">
                        <span className="text-xs font-bold text-ds-text">{formatCompetencia(f.competencia)}</span>
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-xs font-semibold text-ds-text">{medicosMap[f.medicoId]?.nome ?? '—'}</p>
                        <p className="text-[10px] text-ds-light">{f.especialidade}</p>
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-xs text-ds-mid max-w-[160px] truncate">{tomadoresMap[f.tomadorId]?.razaoSocialNome ?? '—'}</p>
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-xs text-ds-light max-w-[120px] truncate">{f.servicoOperacionalNome ?? '—'}</p>
                      </td>
                      <td className="px-4 py-3 text-xs tabular-nums text-ds-mid text-center">
                        {f.itens.length}
                      </td>
                      <td className="px-4 py-3 text-xs tabular-nums font-bold text-ds-text whitespace-nowrap">
                        {formatBRL(f.totalValorCentavos)}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex px-2 py-0.5 rounded-md text-[10px] font-bold whitespace-nowrap ${STATUS_CLS[f.status] ?? 'bg-gray-100 text-gray-500'}`}>
                          {STATUS_LABEL[f.status] ?? f.status}
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* Painel lateral */}
        {selecionada && (
          <PainelFrequencia
            freq={selecionada}
            tomadores={tomadores}
            medicos={medicos}
            onClose={() => setSelecionada(null)}
            onAtualizar={handleAtualizar}
          />
        )}
      </div>

      {/* Modal nova frequência */}
      {showNova && (
        <NovaFrequenciaModal
          tomadores={tomadores}
          medicos={medicos}
          onClose={() => setShowNova(false)}
          onCriada={handleCriada}
        />
      )}
    </div>
  )
}
