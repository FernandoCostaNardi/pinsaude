import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  AlertCircle, CalendarDays, CheckCircle2, ChevronDown, ClipboardList, Clock, Download,
  FileText, Loader2, Pencil, Plus, Printer, Search, Trash2, Upload, X,
} from 'lucide-react'
import { Button, Spinner, Alert, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { tomadoresApi, Tomador, TomadorGrupoFaturamento, TomadorModalidade, TomadorOcorrencia } from '../api/tomadoresApi'
import { medicosApi, Medico, MedicoPage } from '../api/medicosApi'
import {
  frequenciasApi,
  FrequenciaMedicaResp,
  FrequenciaMedicaRequest,
  FrequenciaItemRequest,
  FrequenciaItemResp,
  FrequenciaSemanaProgresso,
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

// PINSAUDE-13.22: turno/horário são ambos obrigatórios numa modalidade Plantonista — sempre
// mostra os dois. Diarista não usa turno/horário/horas, mostra a carga horária semanal cadastrada.
function detalheModalidade(m: TomadorModalidade): string {
  if (m.tipo === 'DIARISTA') {
    return m.horasSemanais != null ? `Diarista — ${m.horasSemanais}h/semana` : 'Diarista'
  }
  const partes = [m.turno, m.horario].filter(Boolean)
  return partes.length > 0 ? partes.join(' · ') : `${m.horas}h`
}

// Espelha FrequenciaService.calcularValorItem (backend) só para exibir um preview do valor
// antes de salvar — o valor real que fica gravado é sempre recalculado no servidor. Diarista
// paga um valor mensal fixo somado uma única vez pela frequência (não por lançamento) — cada
// item individual vale R$0, então não há um "valor deste lançamento" pra mostrar aqui.
function calcularValorPreview(m: TomadorModalidade): number | null {
  if (m.tipo === 'DIARISTA') return 0
  return m.valorCentavos + m.deslocamentoCentavos
}

// Diarista também exige horas trabalhadas por lançamento (usadas no acompanhamento semanal,
// PINSAUDE-13.23) — mesma exigência que Plantonista nunca teve.
function precisaHorasTrabalhadas(m: TomadorModalidade | null): boolean {
  return m?.tipo === 'DIARISTA'
}

// Pedido do cliente: o lançamento individual dentro de uma frequência é chamado de "plantão"
// para Tipo de Escala Plantonista, mas de "frequência" para Diarista — vocabulário mais próximo
// do dia a dia de cada tipo de médico. `tipoMedico === null` (frequência legada, sem Tipo de
// Escala definido) mantém o rótulo antigo ("plantão"). Réplica de PortalFrequenciaPage.tsx.
function itemLabel(tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null, count: number): string {
  const singular = tipoMedico === 'DIARISTA' ? 'frequência' : 'plantão'
  const plural = tipoMedico === 'DIARISTA' ? 'frequências' : 'plantões'
  return count === 1 ? singular : plural
}

// "plantão" é masculino, "frequência" é feminino — qualquer adjetivo/particípio que acompanhe
// o rótulo (lançado/apagado etc.) precisa concordar em gênero e número.
function itemAgree(tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null, count: number, stem: string): string {
  const fem = tipoMedico === 'DIARISTA'
  return stem + (fem ? (count === 1 ? 'a' : 'as') : (count === 1 ? 'o' : 'os'))
}

function itemArtigo(tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null, count: number): string {
  const fem = tipoMedico === 'DIARISTA'
  if (count === 1) return fem ? 'A' : 'O'
  return fem ? 'As' : 'Os'
}

// Espelha FrequenciaService.calcularValorOcorrencia (backend) só para preview — o % sempre
// incide sobre o valor CADASTRADO da modalidade (m.valorCentavos), nunca sobre o valor
// proporcional já calculado do item (META). SEM_VALOR ou nenhuma ocorrência selecionada = 0.
function calcularValorOcorrenciaPreview(o: TomadorOcorrencia | null, valorModalidadeCentavos: number): number {
  if (!o) return 0
  let total = 0
  if (o.valorPercentual != null) {
    total += Math.round((valorModalidadeCentavos * o.valorPercentual) / 100)
  }
  if (o.valorCentavos != null) {
    total += o.valorCentavos
  }
  return total
}

function fmtQtd(n: number): string {
  return n % 1 === 0 ? String(n) : n.toFixed(1).replace('.', ',')
}

// PINSAUDE-13.25: modalidade Diarista pede a hora de entrada e saída do dia (em vez da
// quantidade de horas direto) — este helper espelha FrequenciaService.calcularHorasTrabalhadas
// (backend) só para exibir um preview antes de salvar; o valor real é sempre recalculado no
// servidor. Turnos que atravessam a meia-noite (ex: 19:00 às 07:00) são detectados quando o
// horário de saída é menor ou igual ao de entrada, somando 24h à duração.
function calcularHorasEntrePeriodo(horaInicio: string, horaFim: string): number | null {
  if (!horaInicio || !horaFim || horaInicio === horaFim) return null
  const [hi, mi] = horaInicio.split(':').map(Number)
  const [hf, mf] = horaFim.split(':').map(Number)
  let minutos = (hf * 60 + mf) - (hi * 60 + mi)
  if (minutos <= 0) minutos += 24 * 60
  return Math.round((minutos / 60) * 100) / 100
}

function formatSemanaRange(inicio: string, fim: string): string {
  return `${formatDate(inicio)}–${formatDate(fim)}`
}

// PINSAUDE-13.25: substitui a antiga barrinha "progresso da meta" (EPIC-13.19.4, baseada no
// extinto tipo META) por um badge por semana ISO, consumindo o campo `progressoSemanal` do
// backend (PINSAUDE-13.23) — puramente informativo, nunca altera o valor pago (a frequência
// sempre paga o valor mensal fixo da modalidade Diarista, ver freq.totalValorCentavos). Sem
// nenhuma ação automática quando a meta não é cumprida — só o indicador visual (⚠️/✓).
function ProgressoSemanal({ semanas }: { semanas: FrequenciaSemanaProgresso[] }) {
  if (semanas.length === 0) return null
  return (
    <div className="px-6 py-3 border-b border-ds-border bg-purple-50/50 shrink-0">
      <p className="text-[10px] font-bold text-purple-700 uppercase tracking-wider mb-2">Progresso Semanal (Diarista)</p>
      <div className="flex flex-wrap gap-2">
        {semanas.map((s, i) => (
          <span key={`${s.semanaInicio}-${i}`}
            title={formatSemanaRange(s.semanaInicio, s.semanaFim)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border ${
              s.cumprida ? 'bg-green-50 text-green-700 border-green-200' : 'bg-yellow-50 text-yellow-700 border-yellow-200'
            }`}>
            Sem {i + 1}: {fmtQtd(s.horasLancadas)}h{s.metaHoras != null ? `/${fmtQtd(s.metaHoras)}h` : ''}
            {s.cumprida ? <CheckCircle2 size={12} /> : <AlertCircle size={12} />}
          </span>
        ))}
      </div>
    </div>
  )
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

const ITENS_POR_PAGINA = 5

// ─── StatCard ─────────────────────────────────────────────────────────────────

function StatCard({ icon: Icon, label, value, sub, iconBg, iconColor }: {
  icon: React.ElementType; label: string; value: number | string
  sub: string; iconBg: string; iconColor: string
}) {
  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm p-4 flex items-center gap-4">
      <div className={`w-11 h-11 rounded-xl ${iconBg} flex items-center justify-center shrink-0`}>
        <Icon size={20} className={iconColor} />
      </div>
      <div>
        <p className="text-2xl font-black text-ds-text leading-none">{value}</p>
        <p className="text-xs font-semibold text-ds-mid mt-0.5">{label}</p>
        <p className="text-[11px] text-ds-light">{sub}</p>
      </div>
    </div>
  )
}

// ─── Dropdown genérico (com portal para evitar clipping por overflow) ─────────

function Dropdown<T extends { id: string }>({
  label, placeholder, items, value, onChange, getLabel, getSubLabel, getMeta, disabled,
}: {
  label?: string
  placeholder: string
  items: T[]
  value: T | null
  onChange: (v: T) => void
  getLabel: (v: T) => string
  getSubLabel?: (v: T) => string | null | undefined
  getMeta?: (v: T) => string | null | undefined
  disabled?: boolean
}) {
  const [open, setOpen]       = useState(false)
  const [q, setQ]             = useState('')
  const [pos, setPos]         = useState({ top: 0, left: 0, width: 0 })
  const btnRef                = useRef<HTMLButtonElement>(null)
  const containerRef          = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        // verifica se o clique foi dentro da lista do portal
        const portal = document.getElementById('dropdown-portal-active')
        if (portal && portal.contains(e.target as Node)) return
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  function handleToggle() {
    if (!open && btnRef.current) {
      const r = btnRef.current.getBoundingClientRect()
      setPos({ top: r.bottom + 4, left: r.left, width: r.width })
    }
    if (open) setQ('')
    setOpen(o => !o)
  }

  const filtered = items.filter(i =>
    getLabel(i).toLowerCase().includes(q.toLowerCase()) ||
    (getSubLabel?.(i) ?? '').toLowerCase().includes(q.toLowerCase()) ||
    (getMeta?.(i) ?? '').toLowerCase().includes(q.toLowerCase())
  )

  return (
    <div ref={containerRef} className="relative">
      {label && <label className="block text-xs font-bold text-ds-mid mb-1">{label}</label>}
      <button ref={btnRef} type="button" disabled={disabled}
        onClick={handleToggle}
        className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border text-sm transition-colors disabled:opacity-50 ${
          value ? 'border-primary/40 bg-primary-50 text-ds-text font-medium' : 'border-ds-border bg-white text-ds-light'
        }`}>
        {value ? (
          <span className="min-w-0 flex-1 text-left">
            <span className="block truncate">{getLabel(value)}</span>
            {getSubLabel?.(value) && (
              <span className="block truncate text-xs font-medium text-red-600">{getSubLabel(value)}</span>
            )}
            {getMeta?.(value) && (
              <span className="block truncate text-xs text-ds-light">{getMeta(value)}</span>
            )}
          </span>
        ) : (
          <span className="truncate">{placeholder}</span>
        )}
        <ChevronDown size={14} className={`shrink-0 ml-2 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && createPortal(
        <div id="dropdown-portal-active"
          style={{ position: 'fixed', top: pos.top, left: pos.left, width: pos.width, zIndex: 9999 }}
          className="bg-white border border-ds-border rounded-xl shadow-2xl overflow-hidden">
          <div className="p-2 border-b border-ds-border">
            <input autoFocus value={q} onChange={e => setQ(e.target.value)} placeholder="Buscar..."
              className="w-full text-xs px-2 py-1.5 border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div className="max-h-56 overflow-y-auto divide-y divide-ds-border">
            {filtered.length === 0
              ? <p className="px-3 py-3 text-xs text-ds-light text-center">Sem resultados</p>
              : filtered.map(item => (
                <button key={item.id} type="button"
                  onClick={() => { onChange(item); setOpen(false); setQ('') }}
                  className="w-full text-left px-3 py-2.5 text-sm hover:bg-ds-surface transition-colors">
                  <span className="block truncate">{getLabel(item)}</span>
                  {getSubLabel?.(item) && (
                    <span className="block truncate text-xs font-medium text-red-600">{getSubLabel(item)}</span>
                  )}
                  {getMeta?.(item) && (
                    <span className="block truncate text-xs text-ds-light">{getMeta(item)}</span>
                  )}
                </button>
              ))
            }
          </div>
        </div>,
        document.body
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
  const [grupo,       setGrupo]       = useState<TomadorGrupoFaturamento | null>(null)
  const [setor,       setSetor]       = useState<{ id: string; nome: string } | null>(null)
  const [competencia, setCompetencia] = useState(COMPETENCIAS[0])
  const [tipoMedico, setTipoMedico] = useState<'PLANTONISTA' | 'DIARISTA'>('PLANTONISTA')
  const [grupos,      setGrupos]      = useState<TomadorGrupoFaturamento[]>([])
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

  // PINSAUDE-13.26: modalidade (obrigatória) e ocorrência (opcional) passam a ser escolhidas
  // aqui, uma única vez — o formulário de lançamento de plantão não pergunta mais nenhuma das duas.
  const [modalidades,  setModalidades]  = useState<TomadorModalidade[]>([])
  const [modalidade,   setModalidade]   = useState<TomadorModalidade | null>(null)
  const [ocorrencias,  setOcorrencias]  = useState<TomadorOcorrencia[]>([])
  const [ocorrenciaId, setOcorrenciaId] = useState('')

  // Filtro de tomadores pelo médico selecionado (EPIC-15.14): sem médico, mostra todos os
  // tomadores do tenant; com médico selecionado, mostra só os tomadores alocados a ele.
  const [tomadoresFiltrados, setTomadoresFiltrados] = useState<Tomador[] | null>(null)

  useEffect(() => {
    if (!medico) { setTomadoresFiltrados(null); return }
    let cancelled = false
    tomadoresApi.listar(undefined, medico.id)
      .then(ts => { if (!cancelled) setTomadoresFiltrados(ts) })
      .catch(() => { if (!cancelled) setTomadoresFiltrados([]) })
    return () => { cancelled = true }
  }, [medico?.id])

  const tomadoresDisponiveis = tomadoresFiltrados ?? tomadores

  // PINSAUDE: Setor Operacional virou catálogo reutilizável entre grupos — o mesmo setor pode
  // aparecer em mais de um Grupo de Faturamento, então a frequência precisa fixar explicitamente
  // a qual Grupo pertence (usado pelo Fechamento pra saber em qual NFS-e somar). O combo de Setor
  // é sempre escopado ao Grupo escolhido.
  const setores = grupo ? grupo.servicosOperacionais.filter(s => s.ativo) : []

  useEffect(() => {
    setGrupo(null)
    setSetor(null)
    if (!tomador) { setGrupos([]); return }
    tomadoresApi.listarGrupos(tomador.id).then(gs => {
      setGrupos(gs)
      // Auto-seleciona quando há exatamente 1 grupo ativo — mesmo padrão já usado em outros
      // combos de vínculo único do projeto (empresa emissora, etc.).
      const ativos = gs.filter(g => g.ativo)
      if (ativos.length === 1) setGrupo(ativos[0])
    }).catch(() => setGrupos([]))
  }, [tomador?.id])

  // PINSAUDE-13.26: modalidades filtradas pelo Tipo de Escala escolhido — troca de tomador ou
  // de tipo de escala reseta a modalidade/ocorrência já selecionadas (podem não ser mais válidas).
  useEffect(() => {
    setModalidade(null)
    setOcorrenciaId('')
    if (!tomador) { setModalidades([]); setOcorrencias([]); return }
    tomadoresApi.listarModalidades(tomador.id)
      .then(ms => setModalidades(ms.filter(m => m.tipo === tipoMedico && m.ativo)))
      .catch(() => setModalidades([]))
    tomadoresApi.listarOcorrencias(tomador.id)
      .then(os => setOcorrencias(os.filter(o => o.ativo)))
      .catch(() => setOcorrencias([]))
  }, [tomador?.id, tipoMedico])

  // Ajuste pós-implantação: modalidade (e ocorrência) só são fixadas na frequência para
  // Diarista — Plantonista volta a escolher isso a cada plantão lançado, podendo ter
  // turnos/modalidades diferentes dentro da mesma frequência (ver CLAUDE.md).
  const isDiarista = tipoMedico === 'DIARISTA'

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!tomador || !medico || !grupo || !setor || (isDiarista && !modalidade)) return
    setSaving(true); setErr(null)
    try {
      const req: FrequenciaMedicaRequest = {
        tomadorId: tomador.id,
        medicoId: medico.id,
        grupoId: grupo.id,
        servicoOperacionalId: setor.id,
        competencia,
        tipoMedico,
        modalidadeId: isDiarista ? modalidade?.id : undefined,
        ocorrenciaId: isDiarista ? (ocorrenciaId || undefined) : undefined,
      }
      const criada = await frequenciasApi.criar(req)
      onCriada(criada)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao criar')
    } finally { setSaving(false) }
  }

  const canSave = !!tomador && !!medico && !!grupo && !!setor && (!isDiarista || !!modalidade)
  const medicosFiltrados = medicos.filter(m => m.status === 'ATIVO')

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl">

        {/* Cabeçalho */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-ds-border">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-primary-50 flex items-center justify-center shrink-0">
              <CalendarDays size={18} className="text-primary" />
            </div>
            <div>
              <p className="text-sm font-bold text-ds-text">Nova Frequência Médica</p>
              <p className="text-[11px] text-ds-light mt-0.5">Preencha os dados para criar o registro de frequência</p>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg text-ds-light hover:bg-ds-input transition-colors">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6">
          {err && <div className="mb-5"><Alert variant="error" onClose={() => setErr(null)}>{err}</Alert></div>}

          {/* Linha 1: Médico (full width) */}
          <Dropdown
            label="Médico *"
            placeholder="Selecione o médico..."
            items={medicosFiltrados}
            value={medico}
            onChange={m => { setMedico(m) }}
            getLabel={m => `${m.nome} — CRM ${m.crm}/${m.crmUf}`}
          />

          {/* Linha 2: Tomador (full width) */}
          <div className="mt-4">
            <Dropdown
              label="Tomador *"
              placeholder="Selecione o tomador..."
              items={tomadoresDisponiveis}
              value={tomador}
              onChange={t => { setTomador(t); setSetor(null) }}
              getLabel={t => t.razaoSocialNome}
              getSubLabel={t => t.nomeFantasia}
              getMeta={t => t.municipio}
            />
            {tomadoresFiltrados && (
              <p className="mt-1 text-[11px] text-ds-light">
                Exibindo apenas tomadores alocados ao médico selecionado.
              </p>
            )}
            {tomador && tomadoresFiltrados && !tomadoresFiltrados.some(t => t.id === tomador.id) && (
              <p className="mt-1 text-xs text-amber-600 flex items-center gap-1">
                <AlertCircle size={11} />
                Atenção: o tomador selecionado não está alocado ao médico escolhido.
              </p>
            )}
          </div>

          {/* Linha 3: Grupo de Faturamento (full width) */}
          <div className="mt-4">
            <Dropdown
              label="Grupo de Faturamento *"
              placeholder={!tomador ? 'Selecione o tomador primeiro...' : grupos.length === 0 ? 'Nenhum grupo cadastrado' : 'Selecione o grupo...'}
              items={grupos.filter(g => g.ativo)}
              value={grupo}
              onChange={g => { setGrupo(g); setSetor(null) }}
              getLabel={g => g.nome}
              disabled={!tomador || grupos.length === 0}
            />
          </div>

          {/* Linha 4: Setor (full width) — escopado ao Grupo escolhido acima (o mesmo setor pode
              estar em vários grupos, então o combo nunca mostra o catálogo inteiro do tomador) */}
          <div className="mt-4">
            <Dropdown
              label="Setor Operacional *"
              placeholder={!grupo ? 'Selecione o grupo primeiro...' : setores.length === 0 ? 'Nenhum setor cadastrado neste grupo' : 'Selecione o setor...'}
              items={setores}
              value={setor}
              onChange={setSetor}
              getLabel={s => s.nome}
              disabled={!grupo || setores.length === 0}
            />
          </div>

          {/* Linha 4: Competência + Tipo de Escala (2 colunas) */}
          <div className="mt-4 grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-ds-mid mb-1">Competência *</label>
              <select value={competencia} onChange={e => setCompetencia(e.target.value)}
                className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white">
                {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold text-ds-mid mb-1">Tipo de Escala *</label>
              <select value={tipoMedico} onChange={e => setTipoMedico(e.target.value as 'PLANTONISTA' | 'DIARISTA')}
                className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white">
                <option value="PLANTONISTA">Plantonista</option>
                <option value="DIARISTA">Diarista</option>
              </select>
            </div>
          </div>

          {/* Linha 5: Modalidade — só para Diarista (PINSAUDE-13.26). Escolhida uma única vez
              aqui; todo lançamento desta frequência sempre usará esta modalidade. Plantonista
              não escolhe modalidade aqui — cada plantão lançado escolhe a sua própria (turnos
              diferentes dentro da mesma frequência são permitidos). */}
          {isDiarista ? (
            <div className="mt-4">
              <Dropdown
                label="Modalidade *"
                placeholder={
                  !tomador ? 'Selecione o tomador primeiro...'
                  : modalidades.length === 0 ? 'Nenhuma modalidade Diarista cadastrada'
                  : 'Selecione a modalidade...'
                }
                items={modalidades}
                value={modalidade}
                onChange={setModalidade}
                getLabel={m => `${m.nome} — ${detalheModalidade(m)}`}
                disabled={!tomador || modalidades.length === 0}
              />
              <p className="mt-1 text-[11px] text-ds-light">
                Toda frequência lançada aqui usará esta modalidade — não será mais necessário escolher a cada lançamento.
              </p>
            </div>
          ) : (
            <p className="mt-4 text-[11px] text-ds-light bg-ds-input/40 rounded-lg px-3 py-2">
              A modalidade de cada plantão é escolhida no momento do lançamento — turnos/modalidades diferentes podem ser lançados dentro desta mesma frequência.
            </p>
          )}

          {/* Linha 6: Ocorrência (opcional, PINSAUDE-13.26) — só para Diarista, mesmo motivo
              acima. Plantonista escolhe (ou não) uma ocorrência a cada lançamento. */}
          {isDiarista && (
            <div className="mt-4">
              <label className="block text-xs font-bold text-ds-mid mb-1">
                Ocorrência do catálogo <span className="font-normal text-ds-light">(opcional)</span>
              </label>
              <select value={ocorrenciaId} onChange={e => setOcorrenciaId(e.target.value)}
                disabled={!tomador || ocorrencias.length === 0}
                className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white disabled:opacity-50">
                <option value="">Nenhuma</option>
                {ocorrencias.map(o => (
                  <option key={o.id} value={o.id}>{o.nome}</option>
                ))}
              </select>
            </div>
          )}

          {/* Rodapé com ações */}
          <div className="flex gap-3 mt-6 pt-5 border-t border-ds-border">
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

// ─── Panel de adicionar / editar plantão ──────────────────────────────────────

function PlantaoFormPanel({
  tomadorId, tipoMedico, modalidadeFixa, ocorrenciaFixaNome, ocorrenciaFixaValorCentavos, item, onSave, onCancel,
}: {
  tomadorId: string
  tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null   // filtra a lista de modalidades (PINSAUDE-13.25)
  // PINSAUDE-13.26: quando a frequência já tem modalidade/ocorrência fixa (escolhida na
  // criação), o formulário não pergunta mais nenhuma das duas — usa sempre estes valores.
  // null = frequência legada sem modalidade fixa, mantém o seletor por lançamento de sempre.
  modalidadeFixa: TomadorModalidade | null
  ocorrenciaFixaNome: string | null
  // Ajuste pós-implantação: valor aplicado UMA ÚNICA VEZ pela frequência, não por lançamento —
  // só para exibição informativa aqui (o cálculo real acontece no backend, ver CLAUDE.md).
  ocorrenciaFixaValorCentavos: number | null
  item?: FrequenciaItemResp          // se presente, modo edição
  onSave: (req: FrequenciaItemRequest) => Promise<void>
  onCancel: () => void
}) {
  const isEdit = !!item
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [modalidade,  setModalidade]  = useState<TomadorModalidade | null>(modalidadeFixa)
  const [data,        setData]        = useState(item?.dataExecucao ?? new Date().toISOString().slice(0, 10))
  const [ocorrencia,  setOcorrencia]  = useState(item?.ocorrencia ?? '')
  const [ocorrenciasTodas, setOcorrenciasTodas] = useState<TomadorOcorrencia[]>([])
  const [ocorrenciaId, setOcorrenciaId] = useState(item?.ocorrenciaId ?? '')
  const [horaInicio,  setHoraInicio]  = useState(item?.horaInicio?.slice(0, 5) ?? '')
  const [horaFim,     setHoraFim]     = useState(item?.horaFim?.slice(0, 5) ?? '')
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

  useEffect(() => {
    if (modalidadeFixa) { setModalidade(modalidadeFixa); return } // PINSAUDE-13.26: nada pra buscar
    tomadoresApi.listarModalidades(tomadorId)
      .then(ms => {
        // PINSAUDE-13.25: só oferece modalidades do mesmo Tipo de Escala da frequência aberta —
        // uma frequência Plantonista nunca deve lançar uma modalidade Diarista e vice-versa.
        const doTipo = tipoMedico ? ms.filter(m => m.tipo === tipoMedico) : ms
        const ativas = doTipo.filter(m => m.ativo)
        setModalidades(ativas)
        if (item) {
          // pré-seleciona a modalidade atual (inclusive inativas)
          const atual = ms.find(m => m.id === item.modalidadeId) ?? null
          setModalidade(atual)
        }
      })
      .catch(() => {})
  }, [tomadorId, tipoMedico, item?.modalidadeId, modalidadeFixa])

  useEffect(() => {
    if (modalidadeFixa) return // PINSAUDE-13.26: ocorrência também fixa — nada pra buscar
    tomadoresApi.listarOcorrencias(tomadorId).then(setOcorrenciasTodas).catch(() => {})
  }, [tomadorId, modalidadeFixa])

  const precisaHoras = precisaHorasTrabalhadas(modalidade)

  // Se a ocorrência selecionada foi desativada depois do lançamento, ainda precisa aparecer
  // como opção (senão o <select> mostra em branco) — igual ao tratamento de modalidade inativa.
  const ocorrenciaSelecionada = ocorrenciasTodas.find(o => o.id === ocorrenciaId) ?? null
  const ocorrenciasAtivas = ocorrenciasTodas.filter(o => o.ativo)
  const ocorrenciaOptions = ocorrenciaSelecionada && !ocorrenciaSelecionada.ativo
    ? [ocorrenciaSelecionada, ...ocorrenciasAtivas]
    : ocorrenciasAtivas

  async function handleSave() {
    if (!modalidade) return
    if (precisaHoras) {
      if (!horaInicio || !horaFim) {
        setErr('Informe o horário de entrada e saída para esta modalidade')
        return
      }
      if (horaInicio === horaFim) {
        setErr('Horário de saída deve ser diferente do horário de entrada')
        return
      }
    }
    setSaving(true); setErr(null)
    try {
      await onSave({
        // PINSAUDE-13.26: com modalidade/ocorrência fixas na frequência, o backend ignora
        // qualquer valor enviado aqui e usa sempre o da frequência — omitido de propósito.
        modalidadeId: modalidadeFixa ? undefined : modalidade.id,
        dataExecucao: data,
        ocorrencia: ocorrencia || undefined,
        ocorrenciaId: modalidadeFixa ? undefined : (ocorrenciaId || undefined),
        horaInicio: precisaHoras ? horaInicio : undefined,
        horaFim: precisaHoras ? horaFim : undefined,
      })
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao salvar')
    } finally { setSaving(false) }
  }

  const horasPreview = precisaHoras ? calcularHorasEntrePeriodo(horaInicio, horaFim) : null
  const totalModalidade = modalidade ? calcularValorPreview(modalidade) : null
  const ocorrenciaValor = modalidade ? calcularValorOcorrenciaPreview(ocorrenciaSelecionada, modalidade.valorCentavos) : 0
  const total = totalModalidade != null ? totalModalidade + ocorrenciaValor : null

  return (
    <div className={`mx-5 mb-3 rounded-xl border p-4 ${isEdit ? 'bg-yellow-50/60 border-yellow-200' : 'bg-primary-50/40 border-primary/20'}`}>
      <div className="flex items-center justify-between mb-3">
        <p className={`text-xs font-bold flex items-center gap-1.5 ${isEdit ? 'text-yellow-700' : 'text-primary'}`}>
          {isEdit ? <Pencil size={12} /> : <Plus size={12} />}
          {isEdit
            ? (tipoMedico === 'DIARISTA' ? 'Editar Frequência' : 'Editar Plantão')
            : (tipoMedico === 'DIARISTA' ? 'Nova Frequência' : 'Novo Plantão')}
        </p>
        <button type="button" onClick={onCancel}
          className="p-1 rounded-lg text-ds-light hover:bg-white/70 transition-colors">
          <X size={14} />
        </button>
      </div>

      {/* Data + Modalidade */}
      <div className="grid grid-cols-[160px_1fr] gap-3 mb-3">
        <div>
          <label className="block text-xs font-bold text-ds-mid mb-1">Data *</label>
          <input type="date" value={data} onChange={e => setData(e.target.value)}
            className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white" />
        </div>
        {modalidadeFixa ? (
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Modalidade</label>
            <div className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text bg-ds-input/40 truncate">
              {modalidadeFixa.nome} — {detalheModalidade(modalidadeFixa)}
            </div>
          </div>
        ) : (
          <Dropdown
            label="Modalidade *"
            placeholder={modalidades.length === 0 ? 'Sem modalidades cadastradas' : 'Selecione a modalidade...'}
            items={modalidades}
            value={modalidade}
            onChange={setModalidade}
            getLabel={m => `${m.nome} — ${detalheModalidade(m)}`}
            disabled={modalidades.length === 0}
          />
        )}
      </div>

      {/* Horário trabalhado — só para modalidade Diarista. O médico digita entrada/saída, não a
          quantidade de horas — o backend deriva a duração (também impressa no PDF, ver frequenciaPdf.ts) */}
      {precisaHoras && (
        <div className="grid grid-cols-2 gap-3 mb-3">
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Entrada *</label>
            <input type="time" value={horaInicio} onChange={e => setHoraInicio(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white" />
          </div>
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">
              Saída * <span className="font-normal text-ds-light">(meta semanal: {modalidade?.horasSemanais}h)</span>
            </label>
            <input type="time" value={horaFim} onChange={e => setHoraFim(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white" />
          </div>
          {horasPreview != null && (
            <p className="col-span-2 text-[11px] text-ds-light">{fmtQtd(horasPreview)}h trabalhadas neste dia</p>
          )}
        </div>
      )}

      {/* Preview de valores */}
      {modalidade && (
        <div className="bg-white rounded-lg px-4 py-2.5 mb-3 flex items-center gap-5 text-xs border border-ds-border/60">
          <span className="text-ds-light">{detalheModalidade(modalidade)}</span>
          {modalidade.tipo !== 'DIARISTA' && (
            <span className="text-ds-mid">Valor: <span className="font-bold text-ds-text">{formatBRL(modalidade.valorCentavos)}</span></span>
          )}
          {modalidade.deslocamentoCentavos > 0 && (
            <span className="text-ds-mid">Deslocamento: <span className="font-bold text-ds-text">{formatBRL(modalidade.deslocamentoCentavos)}</span></span>
          )}
          {ocorrenciaSelecionada && (
            <span className="text-ds-mid">Ocorrência: <span className="font-bold text-ds-text">{formatBRL(ocorrenciaValor)}</span></span>
          )}
          <span className="ml-auto text-sm font-black text-primary">
            {modalidade.tipo === 'DIARISTA'
              ? 'Contabilizado no valor mensal'
              : total != null ? `Total: ${formatBRL(total)}` : 'Informe as horas para calcular'}
          </span>
        </div>
      )}

      {/* Ocorrência do catálogo — PINSAUDE-13.26: fixa na frequência (escolhida na criação),
          nunca mais perguntada por lançamento. Sem seletor aqui; só um aviso informativo. */}
      {modalidadeFixa ? (
        ocorrenciaFixaNome && (
          <p className="mb-3 text-xs text-ds-mid">
            Ocorrência aplicada uma única vez nesta frequência (não por lançamento): <span className="font-semibold text-ds-text">{ocorrenciaFixaNome}</span>
            {!!ocorrenciaFixaValorCentavos && <span className="text-green-600 font-bold"> +{formatBRL(ocorrenciaFixaValorCentavos)}</span>}
          </p>
        )
      ) : (
        <div className="mb-3">
          <label className="block text-xs font-bold text-ds-mid mb-1">
            Ocorrência do catálogo <span className="font-normal text-ds-light">(opcional)</span>
          </label>
          <select value={ocorrenciaId} onChange={e => setOcorrenciaId(e.target.value)}
            className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white">
            <option value="">Nenhuma</option>
            {ocorrenciaOptions.map(o => (
              <option key={o.id} value={o.id}>{o.nome}{!o.ativo ? ' (inativa)' : ''}</option>
            ))}
          </select>
        </div>
      )}

      {/* Observação livre */}
      <div className="mb-3">
        <label className="block text-xs font-bold text-ds-mid mb-1">
          Observação <span className="font-normal text-ds-light">(opcional, texto livre sem valor)</span>
        </label>
        <input type="text" value={ocorrencia} onChange={e => setOcorrencia(e.target.value)}
          placeholder={`Descreva alguma ocorrência especial ${tipoMedico === 'DIARISTA' ? 'nesta frequência' : 'neste plantão'}...`}
          className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white" />
      </div>

      {err && <p className="text-xs text-red-600 mb-2">{err}</p>}

      <div className="flex gap-2">
        <button type="button" onClick={onCancel}
          className="px-4 py-2 rounded-lg border border-ds-border text-xs font-semibold text-ds-mid hover:bg-white transition-colors">
          Cancelar
        </button>
        <button type="button" onClick={handleSave} disabled={!modalidade || saving}
          className={`flex-1 px-4 py-2 rounded-lg text-white text-xs font-bold disabled:opacity-50 transition-colors flex items-center justify-center gap-1.5 ${
            isEdit ? 'bg-yellow-500 hover:bg-yellow-600' : 'bg-primary hover:bg-primary-700'
          }`}>
          {saving
            ? <><Loader2 size={12} className="animate-spin" />{isEdit ? 'Salvando...' : 'Adicionando...'}</>
            : isEdit ? 'Salvar Alterações' : (tipoMedico === 'DIARISTA' ? 'Adicionar Frequência' : 'Adicionar Plantão')
          }
        </button>
      </div>
    </div>
  )
}

// ─── Grid estilo planilha para adicionar vários plantões de uma vez (desktop) ──

interface PlantaoRow {
  key: number
  dataExecucao: string
  modalidadeId: string
  ocorrencia: string
  ocorrenciaId: string
  horaInicio: string
  horaFim: string
}

let plantaoRowKey = 1

function criarLinhasVazias(qtd: number): PlantaoRow[] {
  return Array.from({ length: qtd }, () => ({
    key: plantaoRowKey++, dataExecucao: '', modalidadeId: '', ocorrencia: '', ocorrenciaId: '', horaInicio: '', horaFim: '',
  }))
}

function PlantaoGridPanel({
  freqId, tomadorId, tipoMedico, modalidadeFixa, ocorrenciaFixaNome, ocorrenciaFixaValorCentavos, onSaved, onCancel,
}: {
  freqId: string
  tomadorId: string
  tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null   // filtra a lista de modalidades (PINSAUDE-13.25)
  // PINSAUDE-13.26: com modalidade/ocorrência fixas na frequência, as colunas correspondentes
  // somem do grid inteiro — não faz mais sentido escolher por linha. null = frequência legada.
  modalidadeFixa: TomadorModalidade | null
  ocorrenciaFixaNome: string | null
  // Ajuste pós-implantação: valor aplicado UMA ÚNICA VEZ pela frequência (exibição informativa).
  ocorrenciaFixaValorCentavos: number | null
  onSaved: () => void | Promise<void>
  onCancel: () => void
}) {
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [ocorrencias, setOcorrencias] = useState<TomadorOcorrencia[]>([])
  const [rows,        setRows]        = useState<PlantaoRow[]>(() => criarLinhasVazias(6))
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)
  const [linhasSemModalidade, setLinhasSemModalidade] = useState<Set<number>>(new Set())
  const [linhasSemHoras, setLinhasSemHoras] = useState<Set<number>>(new Set())
  const gridRef        = useRef<HTMLDivElement>(null)
  const focarProximaLinha = useRef<number | null>(null)

  function precisaHorasRow(modalidadeId: string): boolean {
    if (modalidadeFixa) return precisaHorasTrabalhadas(modalidadeFixa)
    return precisaHorasTrabalhadas(modalidades.find(m => m.id === modalidadeId) ?? null)
  }

  useEffect(() => {
    if (modalidadeFixa) return // PINSAUDE-13.26: nada pra buscar — modalidade/ocorrência já fixas
    // PINSAUDE-13.25: só oferece modalidades do mesmo Tipo de Escala da frequência aberta.
    tomadoresApi.listarModalidades(tomadorId)
      .then(ms => {
        const doTipo = tipoMedico ? ms.filter(m => m.tipo === tipoMedico) : ms
        setModalidades(doTipo.filter(m => m.ativo))
      })
      .catch(() => {})
    tomadoresApi.listarOcorrencias(tomadorId)
      .then(os => setOcorrencias(os.filter(o => o.ativo)))
      .catch(() => {})
  }, [tomadorId, tipoMedico, modalidadeFixa])

  // Foca o campo "Dia" da linha recém-adicionada (via botão ou Tab na última linha)
  useEffect(() => {
    if (focarProximaLinha.current == null) return
    const el = gridRef.current?.querySelector<HTMLInputElement>(
      `input[data-row-key="${focarProximaLinha.current}"][data-field="dia"]`
    )
    el?.focus()
    focarProximaLinha.current = null
  }, [rows])

  function updateRow(key: number, patch: Partial<PlantaoRow>) {
    setRows(prev => prev.map(r => r.key === key ? { ...r, ...patch } : r))
    setErr(null)
    if (patch.modalidadeId) {
      setLinhasSemModalidade(prev => {
        if (!prev.has(key)) return prev
        const next = new Set(prev)
        next.delete(key)
        return next
      })
      // troca de modalidade pode tornar as horas desnecessárias — reavalia o destaque
      if (!precisaHorasRow(patch.modalidadeId)) {
        setLinhasSemHoras(prev => {
          if (!prev.has(key)) return prev
          const next = new Set(prev)
          next.delete(key)
          return next
        })
      }
    }
    if (patch.horaInicio || patch.horaFim) {
      setLinhasSemHoras(prev => {
        if (!prev.has(key)) return prev
        const next = new Set(prev)
        next.delete(key)
        return next
      })
    }
  }

  function addRow(foco = false) {
    const [nova] = criarLinhasVazias(1)
    if (foco) focarProximaLinha.current = nova.key
    setRows(prev => [...prev, nova])
  }

  function removeRow(key: number) {
    setRows(prev => prev.filter(r => r.key !== key))
    setLinhasSemModalidade(prev => {
      if (!prev.has(key)) return prev
      const next = new Set(prev)
      next.delete(key)
      return next
    })
    setLinhasSemHoras(prev => {
      if (!prev.has(key)) return prev
      const next = new Set(prev)
      next.delete(key)
      return next
    })
  }

  // Tab na Ocorrência da última linha adiciona e foca automaticamente a próxima —
  // continua o preenchimento tipo planilha sem precisar clicar em "Adicionar linha".
  function handleOcorrenciaKeyDown(e: React.KeyboardEvent<HTMLInputElement>, rowKey: number) {
    const isUltimaLinha = rows[rows.length - 1]?.key === rowKey
    if (e.key === 'Tab' && !e.shiftKey && isUltimaLinha) {
      e.preventDefault()
      addRow(true)
    }
  }

  async function handleSalvarTodos() {
    // PINSAUDE-13.26: com modalidade fixa não há coluna de modalidade por linha — pula direto
    // pra validação de dia preenchido.
    if (!modalidadeFixa) {
      // Linha "em uso" = tem dia ou ocorrência preenchidos — se estiver sem modalidade,
      // não pode ser silenciosamente ignorada (o usuário claramente começou a preencher essa linha).
      const emUsoSemModalidade = rows.filter(r => (r.dataExecucao || r.ocorrencia.trim()) && !r.modalidadeId)
      if (emUsoSemModalidade.length > 0) {
        setLinhasSemModalidade(new Set(emUsoSemModalidade.map(r => r.key)))
        setErr(`Selecione a modalidade em ${emUsoSemModalidade.length === 1 ? 'linha' : `${emUsoSemModalidade.length} linhas`} destacada${emUsoSemModalidade.length === 1 ? '' : 's'} antes de continuar`)
        return
      }
      setLinhasSemModalidade(new Set())
    }

    const validas = modalidadeFixa
      ? rows.filter(r => r.dataExecucao)
      : rows.filter(r => r.dataExecucao && r.modalidadeId)
    if (validas.length === 0) { setErr('Preencha ao menos uma linha com a data'); return }

    // Linhas de modalidade Diarista exigem entrada e saída preenchidas (o backend deriva as horas)
    const semHoras = validas.filter(r => precisaHorasRow(r.modalidadeId) && (!r.horaInicio || !r.horaFim || r.horaInicio === r.horaFim))
    if (semHoras.length > 0) {
      setLinhasSemHoras(new Set(semHoras.map(r => r.key)))
      setErr(`Informe o horário de entrada e saída em ${semHoras.length === 1 ? 'linha' : `${semHoras.length} linhas`} destacada${semHoras.length === 1 ? '' : 's'} antes de continuar`)
      return
    }
    setLinhasSemHoras(new Set())

    setSaving(true); setErr(null)
    const restantes = [...rows]
    try {
      for (const r of validas) {
        await frequenciasApi.adicionarItem(freqId, {
          modalidadeId: modalidadeFixa ? undefined : r.modalidadeId,
          dataExecucao: r.dataExecucao,
          ocorrencia: r.ocorrencia || undefined,
          ocorrenciaId: modalidadeFixa ? undefined : (r.ocorrenciaId || undefined),
          horaInicio: precisaHorasRow(r.modalidadeId) ? r.horaInicio : undefined,
          horaFim: precisaHorasRow(r.modalidadeId) ? r.horaFim : undefined,
        })
        const idx = restantes.findIndex(x => x.key === r.key)
        if (idx >= 0) restantes.splice(idx, 1)
      }
      await onSaved()
    } catch (e) {
      // mantém no grid só as linhas ainda não salvas (inclusive a que falhou), pra não duplicar no retry
      setRows(restantes)
      setErr(e instanceof Error ? e.message : `Erro ao salvar um${tipoMedico === 'DIARISTA' ? 'a das frequências' : ' dos plantões'}`)
    } finally {
      setSaving(false)
    }
  }

  const qtdPreenchidas = modalidadeFixa
    ? rows.filter(r => r.dataExecucao).length
    : rows.filter(r => r.dataExecucao && r.modalidadeId).length
  const linhasEmUso = modalidadeFixa
    ? rows.filter(r => r.dataExecucao || r.ocorrencia.trim()).length
    : rows.filter(r => r.dataExecucao || r.modalidadeId || r.ocorrencia.trim()).length

  return (
    <div className="mx-5 mb-3 rounded-xl border border-primary/20 bg-primary-50/40 p-4">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-bold text-primary flex items-center gap-1.5">
          <Plus size={12} /> {tipoMedico === 'DIARISTA' ? 'Nova(s) Frequência(s)' : 'Novo(s) Plantão(ões)'}
        </p>
        <button type="button" onClick={onCancel}
          className="p-1 rounded-lg text-ds-light hover:bg-white/70 transition-colors">
          <X size={14} />
        </button>
      </div>

      {/* PINSAUDE-13.26: modalidade/ocorrência fixas na frequência — sem coluna por linha,
          só um aviso informativo com o que será aplicado a todo plantão lançado abaixo. */}
      {modalidadeFixa && (
        <div className="mb-3 px-3 py-2 rounded-lg bg-white border border-ds-border/60 text-xs text-ds-mid">
          Modalidade: <span className="font-semibold text-ds-text">{modalidadeFixa.nome} — {detalheModalidade(modalidadeFixa)}</span>
          {ocorrenciaFixaNome && (
            <> · Ocorrência (aplicada uma única vez, não por lançamento): <span className="font-semibold text-ds-text">{ocorrenciaFixaNome}</span>
              {!!ocorrenciaFixaValorCentavos && <span className="text-green-600 font-bold"> +{formatBRL(ocorrenciaFixaValorCentavos)}</span>}
            </>
          )}
        </div>
      )}

      {/* max-h calibrado para mostrar exatamente 5 linhas (cabeçalho ~32px + 5 × linha ~50px); o resto rola.
          `sticky` vai em cada <th>, não no <thead> — com `border-collapse: collapse` (Tailwind preflight),
          um <thead> sticky não pinta fundo sólido sobre as linhas rolando por baixo (bug conhecido de tabelas
          HTML), fazendo o texto das linhas "vazar" por cima do cabeçalho. Sticky por célula não tem esse problema. */}
      <div ref={gridRef} className="bg-white rounded-lg border border-ds-border overflow-y-auto max-h-[282px] mb-3">
        <table className="w-full">
          <thead>
            <tr>
              <th className="sticky top-0 z-10 bg-white border-b border-ds-border px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left w-40">Dia</th>
              {!modalidadeFixa && (
                <th className="sticky top-0 z-10 bg-white border-b border-ds-border px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left">Modalidade</th>
              )}
              <th className="sticky top-0 z-10 bg-white border-b border-ds-border px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left w-40">Horário</th>
              {!modalidadeFixa && (
                <th className="sticky top-0 z-10 bg-white border-b border-ds-border px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left w-40">Ocorrência</th>
              )}
              <th className="sticky top-0 z-10 bg-white border-b border-ds-border px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left">Nota</th>
              <th className="sticky top-0 z-10 bg-white border-b border-ds-border w-9"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-ds-border">
            {rows.map(r => (
              <tr key={r.key}>
                <td className="px-2 py-1.5">
                  <input type="date" value={r.dataExecucao}
                    data-row-key={r.key} data-field="dia"
                    onChange={e => updateRow(r.key, { dataExecucao: e.target.value })}
                    className="w-full border border-transparent hover:border-ds-border focus:border-primary rounded-md px-2 py-1.5 text-sm text-ds-text focus:outline-none focus:ring-1 focus:ring-primary/30" />
                </td>
                {!modalidadeFixa && (
                  <td className="px-2 py-1.5">
                    <select value={r.modalidadeId}
                      onChange={e => updateRow(r.key, { modalidadeId: e.target.value })}
                      disabled={modalidades.length === 0}
                      className={`w-full border rounded-md px-2 py-1.5 text-sm text-ds-text focus:outline-none focus:ring-1 disabled:opacity-50 ${
                        linhasSemModalidade.has(r.key)
                          ? 'border-red-400 focus:border-red-500 focus:ring-red-300'
                          : 'border-transparent hover:border-ds-border focus:border-primary focus:ring-primary/30'
                      }`}>
                      <option value="">{modalidades.length === 0 ? 'Sem modalidades' : 'Selecione...'}</option>
                      {modalidades.map(m => (
                        <option key={m.id} value={m.id}>{m.nome} — {detalheModalidade(m)}</option>
                      ))}
                    </select>
                  </td>
                )}
                <td className="px-2 py-1.5">
                  <div className="flex items-center gap-1">
                    <input type="time" value={r.horaInicio}
                      disabled={!precisaHorasRow(r.modalidadeId)}
                      onChange={e => updateRow(r.key, { horaInicio: e.target.value })}
                      title="Entrada"
                      className={`w-full border rounded-md px-1.5 py-1.5 text-sm text-ds-text focus:outline-none focus:ring-1 disabled:opacity-40 disabled:cursor-not-allowed ${
                        linhasSemHoras.has(r.key)
                          ? 'border-red-400 focus:border-red-500 focus:ring-red-300'
                          : 'border-transparent hover:border-ds-border focus:border-primary focus:ring-primary/30'
                      }`} />
                    <span className="text-ds-light text-xs shrink-0">às</span>
                    <input type="time" value={r.horaFim}
                      disabled={!precisaHorasRow(r.modalidadeId)}
                      onChange={e => updateRow(r.key, { horaFim: e.target.value })}
                      title="Saída"
                      className={`w-full border rounded-md px-1.5 py-1.5 text-sm text-ds-text focus:outline-none focus:ring-1 disabled:opacity-40 disabled:cursor-not-allowed ${
                        linhasSemHoras.has(r.key)
                          ? 'border-red-400 focus:border-red-500 focus:ring-red-300'
                          : 'border-transparent hover:border-ds-border focus:border-primary focus:ring-primary/30'
                      }`} />
                  </div>
                </td>
                {!modalidadeFixa && (
                  <td className="px-2 py-1.5">
                    <select value={r.ocorrenciaId}
                      onChange={e => updateRow(r.key, { ocorrenciaId: e.target.value })}
                      disabled={ocorrencias.length === 0}
                      className="w-full border border-transparent hover:border-ds-border focus:border-primary rounded-md px-2 py-1.5 text-sm text-ds-text focus:outline-none focus:ring-1 focus:ring-primary/30 disabled:opacity-50">
                      <option value="">{ocorrencias.length === 0 ? 'Sem ocorrências' : 'Nenhuma'}</option>
                      {ocorrencias.map(o => (
                        <option key={o.id} value={o.id}>{o.nome}</option>
                      ))}
                    </select>
                  </td>
                )}
                <td className="px-2 py-1.5">
                  <input type="text" value={r.ocorrencia}
                    onChange={e => updateRow(r.key, { ocorrencia: e.target.value })}
                    onKeyDown={e => handleOcorrenciaKeyDown(e, r.key)}
                    placeholder="Opcional"
                    className="w-full border border-transparent hover:border-ds-border focus:border-primary rounded-md px-2 py-1.5 text-sm text-ds-text focus:outline-none focus:ring-1 focus:ring-primary/30" />
                </td>
                <td className="px-1">
                  <button type="button" onClick={() => removeRow(r.key)}
                    tabIndex={-1}
                    title="Remover linha"
                    className="p-1.5 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors">
                    <Trash2 size={13} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <button type="button" onClick={() => addRow(true)}
        className="mb-3 flex items-center gap-1.5 text-xs font-semibold text-primary hover:text-primary-700 transition-colors">
        <Plus size={13} /> Adicionar linha
      </button>

      {err && <p className="text-xs text-red-600 mb-2">{err}</p>}

      <div className="flex gap-2">
        <button type="button" onClick={onCancel}
          className="px-4 py-2 rounded-lg border border-ds-border text-xs font-semibold text-ds-mid hover:bg-white transition-colors">
          Cancelar
        </button>
        <button type="button" onClick={handleSalvarTodos} disabled={saving || linhasEmUso === 0}
          className="flex-1 px-4 py-2 rounded-lg text-white text-xs font-bold disabled:opacity-50 transition-colors flex items-center justify-center gap-1.5 bg-primary hover:bg-primary-700">
          {saving
            ? <><Loader2 size={12} className="animate-spin" />Adicionando...</>
            : qtdPreenchidas === 0
              ? (tipoMedico === 'DIARISTA' ? 'Adicionar Frequências' : 'Adicionar Plantões')
              : `Adicionar ${qtdPreenchidas} ${tipoMedico === 'DIARISTA'
                  ? (qtdPreenchidas === 1 ? 'Frequência' : 'Frequências')
                  : (qtdPreenchidas === 1 ? 'Plantão' : 'Plantões')}`
          }
        </button>
      </div>
    </div>
  )
}

// ─── Modal de detalhe/edição ───────────────────────────────────────────────────

function PainelFrequencia({
  freq, tomadores, medicos, onClose, onAtualizar, onExcluida,
}: {
  freq: FrequenciaMedicaResp
  tomadores: Tomador[]
  medicos: Medico[]
  onClose: () => void
  onAtualizar: (f: FrequenciaMedicaResp) => void
  onExcluida: (id: string) => void
}) {
  const { user } = useAuth()
  const [adicionando,   setAdicionando]   = useState(false)
  const [editandoId,    setEditandoId]    = useState<string | null>(null)
  const [removendo,     setRemovendo]     = useState<string | null>(null)
  const [gerandoPdf,    setGerandoPdf]    = useState(false)
  const [uploadingDoc,  setUploadingDoc]  = useState(false)
  const [uploadErr,     setUploadErr]     = useState<string | null>(null)
  const [itemPage,      setItemPage]      = useState(0)
  const [confirmExcluir, setConfirmExcluir] = useState(false)
  const [excluindo,     setExcluindo]     = useState(false)
  const [excluirErr,    setExcluirErr]    = useState<string | null>(null)
  const [editandoFrequencia, setEditandoFrequencia] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const isFaturada = freq.status === 'FATURADA'

  const tomador = tomadores.find(t => t.id === freq.tomadorId)
  const medico  = medicos.find(m => m.id === freq.medicoId)

  // PINSAUDE-13.26: modalidade/ocorrência fixas na criação da frequência — quando presentes, os
  // formulários de lançamento de plantão (grid e painel) não perguntam mais nenhuma das duas.
  const modalidadeFixa: TomadorModalidade | null = freq.modalidadeId ? {
    id: freq.modalidadeId,
    tomadorId: freq.tomadorId,
    nome: freq.modalidadeNome ?? '',
    tipo: freq.modalidadeTipo ?? 'PLANTONISTA',
    turno: (freq.modalidadeTurno as 'DIURNO' | 'NOTURNO' | null) ?? null,
    horario: freq.modalidadeHorario,
    horas: freq.modalidadeHoras,
    valorCentavos: freq.modalidadeValorCentavos,
    deslocamentoCentavos: freq.modalidadeDeslocamentoCentavos,
    ativo: true,
    horasSemanais: freq.modalidadeHorasSemanais,
  } : null
  const ocorrenciaFixaNome = freq.ocorrenciaId ? freq.ocorrenciaNome : null
  // Ajuste pós-implantação: valor aplicado uma única vez pela frequência (não por lançamento).
  const ocorrenciaFixaValorCentavos = freq.ocorrenciaId ? freq.ocorrenciaValorCentavos : null

  async function handleExcluir() {
    setExcluindo(true); setExcluirErr(null)
    try {
      await frequenciasApi.excluir(freq.id)
      onExcluida(freq.id)
    } catch (e) {
      setExcluirErr(e instanceof Error ? e.message : 'Erro ao excluir')
      setExcluindo(false)
    }
  }

  // Reseta a página ao abrir uma frequência diferente
  useEffect(() => { setItemPage(0) }, [freq.id])

  const totalItemPages  = Math.max(1, Math.ceil(freq.itens.length / ITENS_POR_PAGINA))
  const itemPageAtual   = Math.min(itemPage, totalItemPages - 1)
  const itensPaginados  = freq.itens.slice(itemPageAtual * ITENS_POR_PAGINA, (itemPageAtual + 1) * ITENS_POR_PAGINA)

  async function handleAdd(req: FrequenciaItemRequest) {
    await frequenciasApi.adicionarItem(freq.id, req)
    const atualizada = await frequenciasApi.buscarPorId(freq.id)
    onAtualizar(atualizada)
    setAdicionando(false)
  }

  // O grid (desktop) já persiste cada linha via frequenciasApi.adicionarItem internamente —
  // aqui só recarrega a frequência e fecha o painel.
  async function handleAddGrid() {
    const atualizada = await frequenciasApi.buscarPorId(freq.id)
    onAtualizar(atualizada)
    setAdicionando(false)
  }

  async function handleEdit(itemId: string, req: FrequenciaItemRequest) {
    await frequenciasApi.atualizarItem(freq.id, itemId, req)
    const atualizada = await frequenciasApi.buscarPorId(freq.id)
    onAtualizar(atualizada)
    setEditandoId(null)
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

  async function handleUploadDocumento(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploadErr(null)
    setUploadingDoc(true)
    try {
      const atualizada = await frequenciasApi.uploadDocumentoAssinado(freq.id, file)
      onAtualizar(atualizada)
    } catch (err: unknown) {
      setUploadErr(err instanceof Error ? err.message : 'Erro ao fazer upload')
    } finally {
      setUploadingDoc(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  async function handleVerDocumento() {
    try {
      const url = await frequenciasApi.getDocumentoUrl(freq.id)
      window.open(url, '_blank', 'noopener')
    } catch { /* ignore */ }
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl max-h-[92vh] flex flex-col">

        {/* ── Header ─────────────────────────────────────────────────────── */}
        <div className="flex items-start justify-between px-6 py-5 border-b border-ds-border shrink-0">
          <div className="min-w-0">
            <div className="flex items-center gap-3 mb-1">
              <ClipboardList size={20} className="text-primary shrink-0" />
              <h2 className="text-lg font-bold text-ds-text">Frequência Médica</h2>
              <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold ${STATUS_CLS[freq.status] ?? 'bg-gray-100 text-gray-500'}`}>
                {STATUS_LABEL[freq.status] ?? freq.status}
              </span>
            </div>
            <p className="text-sm text-ds-mid ml-8">
              <span className="font-semibold text-ds-text">{formatCompetencia(freq.competencia)}</span>
              {freq.servicoOperacionalNome && <span className="text-ds-light"> · {freq.servicoOperacionalNome}</span>}
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0 ml-6">
            {/* Competência e Setor Operacional são editáveis a qualquer momento antes de
                faturada. Tomador, Tipo de Escala, Modalidade e Ocorrência permanecem fixos —
                se algum deles estiver errado, o jeito continua sendo excluir e criar de novo. */}
            {!isFaturada && (
              <button
                onClick={() => setEditandoFrequencia(true)}
                title="Editar competência e setor"
                className="flex items-center gap-2 px-3 py-2 rounded-xl border border-ds-border text-ds-mid text-sm font-bold hover:bg-ds-input transition-colors">
                <Pencil size={15} />
                Editar
              </button>
            )}
            {!isFaturada && (
              <button
                onClick={() => setConfirmExcluir(true)}
                disabled={excluindo}
                title="Excluir frequência"
                className="flex items-center gap-2 px-3 py-2 rounded-xl border border-red-200 text-red-600 text-sm font-bold hover:bg-red-50 transition-colors disabled:opacity-50">
                <Trash2 size={15} />
                Excluir
              </button>
            )}
            <button
              onClick={handleGerarPdf}
              disabled={gerandoPdf}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary text-white text-sm font-bold hover:bg-primary-700 transition-colors disabled:opacity-50 shadow-sm">
              {gerandoPdf ? <Loader2 size={15} className="animate-spin" /> : <Printer size={15} />}
              Gerar PDF
            </button>
            <button onClick={onClose}
              className="p-2 rounded-xl text-ds-light hover:bg-ds-input hover:text-ds-text transition-colors ml-1">
              <X size={20} />
            </button>
          </div>
        </div>

        {/* ── Confirmação de exclusão (PINSAUDE-13.26) ─────────────────────── */}
        {confirmExcluir && (
          <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6">
              <div className="flex items-start gap-3 mb-4">
                <div className="w-10 h-10 rounded-full bg-red-100 flex items-center justify-center shrink-0">
                  <Trash2 size={18} className="text-red-600" />
                </div>
                <div>
                  <p className="text-sm font-bold text-ds-text">Excluir esta frequência?</p>
                  <p className="mt-1 text-xs text-ds-light">
                    {freq.itens.length > 0
                      ? `${itemArtigo(freq.tipoMedico, freq.itens.length)} ${freq.itens.length} ${itemLabel(freq.tipoMedico, freq.itens.length)} ${itemAgree(freq.tipoMedico, freq.itens.length, 'lançad')} ${freq.itens.length !== 1 ? 'serão' : 'será'} ${itemAgree(freq.tipoMedico, freq.itens.length, 'apagad')} junto. Esta ação não pode ser desfeita.`
                      : 'Esta ação não pode ser desfeita.'}
                  </p>
                </div>
              </div>
              {excluirErr && <div className="mb-4"><Alert variant="error" onClose={() => setExcluirErr(null)}>{excluirErr}</Alert></div>}
              <div className="flex gap-3">
                <Button type="button" variant="secondary" className="flex-1" disabled={excluindo}
                  onClick={() => setConfirmExcluir(false)}>
                  Cancelar
                </Button>
                <button type="button" onClick={handleExcluir} disabled={excluindo}
                  className="flex-1 px-4 py-2.5 rounded-xl bg-red-600 text-white text-sm font-bold hover:bg-red-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5">
                  {excluindo ? <><Loader2 size={14} className="animate-spin" />Excluindo...</> : 'Excluir'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── Edição de Competência + Setor Operacional ─────────────────────── */}
        {editandoFrequencia && (
          <EditarFrequenciaModal
            freq={freq}
            onClose={() => setEditandoFrequencia(false)}
            onSalvo={f => { onAtualizar(f); setEditandoFrequencia(false) }}
          />
        )}

        {/* Corpo scrollável — envolve dados, progresso, painel de adição/edição, tabela de
            itens e paginação num único container com scroll vertical, para telas menores
            conseguirem rolar até todos os itens (edição/exclusão inclusive). Só o header
            fica fixo fora daqui. */}
        <div className="flex-1 overflow-y-auto min-h-0">

        {/* ── Dados do profissional ───────────────────────────────────────── */}
        <div className="grid grid-cols-3 gap-0 border-b border-ds-border shrink-0 bg-ds-surface/40">
          <div className="px-6 py-3 border-r border-ds-border">
            <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider mb-0.5">Médico</p>
            <p className="text-sm font-semibold text-ds-text truncate">{medico?.nome ?? '—'}</p>
            {medico && <p className="text-xs text-ds-light">CRM {medico.crm}/{medico.crmUf} · {freq.tipoMedico ?? '—'}</p>}
          </div>
          <div className="px-6 py-3 border-r border-ds-border">
            <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider mb-0.5">Tomador</p>
            <p className="text-sm font-semibold text-ds-text truncate">{tomador?.razaoSocialNome ?? '—'}</p>
            {tomador?.nomeFantasia && (
              <p className="text-xs font-medium text-red-600 truncate">{tomador.nomeFantasia}</p>
            )}
            <p className="text-xs text-ds-light">{formatCompetencia(freq.competencia)}</p>
          </div>
          <div className="px-6 py-3">
            <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider mb-0.5">Total Apurado</p>
            <p className="text-xl font-black text-primary tabular-nums">{formatBRL(freq.totalValorCentavos)}</p>
            <p className="text-xs text-ds-light">{freq.itens.length} {itemLabel(freq.tipoMedico, freq.itens.length)}</p>
          </div>
        </div>

        {/* ── Progresso semanal (modalidade Diarista) ──────────────────────── */}
        <ProgressoSemanal semanas={freq.progressoSemanal} />

        {/* ── Documento assinado (condicional) ───────────────────────────── */}
        {(freq.status === 'AGUARDANDO_ASSINATURA' || freq.documentoAssinado) && (
          <div className="px-6 py-3 border-b border-ds-border bg-yellow-50/50 shrink-0 flex items-center gap-3 flex-wrap">
            <span className="text-[10px] font-bold text-yellow-800 uppercase tracking-wider">Documento Assinado</span>
            {uploadErr && <span className="text-xs text-red-600">{uploadErr}</span>}
            {/* Mostrar botão de upload no upload inicial E na substituição (qualquer status não-faturado) */}
            {(freq.status === 'AGUARDANDO_ASSINATURA' || (freq.documentoAssinado && !isFaturada)) && (
              <>
                <input ref={fileInputRef} type="file" accept="application/pdf,image/*" className="hidden" onChange={handleUploadDocumento} />
                <button
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploadingDoc}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-600 text-white text-xs font-bold hover:bg-purple-700 transition-colors disabled:opacity-50">
                  {uploadingDoc ? <Loader2 size={11} className="animate-spin" /> : <Upload size={11} />}
                  {freq.documentoAssinado ? 'Trocar Documento' : 'Upload Assinado'}
                </button>
              </>
            )}
            {freq.documentoAssinado && (
              <>
                <button onClick={handleVerDocumento}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-purple-300 text-purple-700 bg-white text-xs font-bold hover:bg-purple-50 transition-colors">
                  <Download size={11} /> Ver Documento
                </button>
                <span className="flex items-center gap-1 text-xs text-green-700 font-semibold">
                  <CheckCircle2 size={12} /> Recebido
                </span>
              </>
            )}
          </div>
        )}

        {/* ── Barra de ação ──────────────────────────────────────────────── */}
        <div className="flex items-center justify-between px-6 py-3 border-b border-ds-border shrink-0 bg-white">
          <p className="text-xs font-semibold text-ds-mid capitalize">
            {itemLabel(freq.tipoMedico, 2)} {itemAgree(freq.tipoMedico, 2, 'lançad')}
          </p>
          {!isFaturada && !adicionando && editandoId === null && (
            <button
              onClick={() => setAdicionando(true)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-primary text-white text-xs font-bold hover:bg-primary-700 transition-colors">
              <Plus size={13} /> {freq.tipoMedico === 'DIARISTA' ? 'Adicionar Frequência' : 'Adicionar Plantão'}
            </button>
          )}
        </div>

        {/* ── Panel de adição ────────────────────────────────────────────── */}
        {/* Desktop: grid estilo planilha (várias linhas de uma vez). Mobile: form de 1 plantão por vez (inalterado). */}
        {adicionando && (
          <div className="shrink-0 border-b border-ds-border bg-primary-50/20 pt-3">
            <div className="hidden sm:block">
              <PlantaoGridPanel
                freqId={freq.id}
                tomadorId={freq.tomadorId}
                tipoMedico={freq.tipoMedico}
                modalidadeFixa={modalidadeFixa}
                ocorrenciaFixaNome={ocorrenciaFixaNome}
                ocorrenciaFixaValorCentavos={ocorrenciaFixaValorCentavos}
                onSaved={handleAddGrid}
                onCancel={() => setAdicionando(false)}
              />
            </div>
            <div className="sm:hidden">
              <PlantaoFormPanel
                tomadorId={freq.tomadorId}
                tipoMedico={freq.tipoMedico}
                modalidadeFixa={modalidadeFixa}
                ocorrenciaFixaNome={ocorrenciaFixaNome}
                ocorrenciaFixaValorCentavos={ocorrenciaFixaValorCentavos}
                onSave={handleAdd}
                onCancel={() => setAdicionando(false)}
              />
            </div>
          </div>
        )}

        {/* ── Panel de edição ────────────────────────────────────────────── */}
        {editandoId && (
          <div className="shrink-0 border-b border-ds-border bg-yellow-50/20 pt-3">
            <PlantaoFormPanel
              tomadorId={freq.tomadorId}
              tipoMedico={freq.tipoMedico}
              modalidadeFixa={modalidadeFixa}
              ocorrenciaFixaNome={ocorrenciaFixaNome}
              ocorrenciaFixaValorCentavos={ocorrenciaFixaValorCentavos}
              item={freq.itens.find(i => i.id === editandoId)}
              onSave={req => handleEdit(editandoId, req)}
              onCancel={() => setEditandoId(null)}
            />
          </div>
        )}

        {/* ── Tabela de itens ────────────────────────────────────────────── */}
        <div className="overflow-x-auto">
            <table className="w-full min-w-[600px]">
              <thead className="sticky top-0 bg-white z-10 border-b border-ds-border">
                <tr>
                  {['Data', 'Modalidade', 'Ocorrência', 'Val. Unit.', 'Deslocamento', 'Total', ''].map(h => (
                    <th key={h} className="px-5 py-3 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-ds-border">
                {itensPaginados.map(item => (
                  <tr key={item.id}
                    className={`hover:bg-ds-surface/50 transition-colors group ${editandoId === item.id ? 'bg-yellow-50/40' : ''}`}>
                    <td className="px-5 py-3 text-sm font-medium text-ds-text whitespace-nowrap">{formatDate(item.dataExecucao)}</td>
                    <td className="px-5 py-3">
                      <p className="text-sm font-semibold text-ds-text">{item.modalidadeNome ?? '—'}</p>
                      {item.modalidadeTurno && (
                        <p className="text-xs text-ds-light">{item.modalidadeTurno} · {item.modalidadeHorario}</p>
                      )}
                      {item.horasTrabalhadas != null && (
                        <p className="text-xs text-teal-600 font-medium">
                          {fmtQtd(item.horasTrabalhadas)}h lançadas
                          {item.horaInicio && item.horaFim && ` (${item.horaInicio.slice(0, 5)} às ${item.horaFim.slice(0, 5)})`}
                        </p>
                      )}
                    </td>
                    <td className="px-5 py-3 text-sm text-ds-mid">
                      {item.ocorrenciaNome && (
                        <p className="text-ds-text font-medium">
                          {item.ocorrenciaNome}
                          {!!item.ocorrenciaValorCentavos && (
                            <span className="text-green-600 font-bold"> +{formatBRL(item.ocorrenciaValorCentavos)}</span>
                          )}
                        </p>
                      )}
                      {item.ocorrencia && <p className={item.ocorrenciaNome ? 'text-xs italic' : ''}>{item.ocorrencia}</p>}
                      {!item.ocorrenciaNome && !item.ocorrencia && '—'}
                    </td>
                    <td className="px-5 py-3 text-sm tabular-nums text-right text-ds-mid whitespace-nowrap">{formatBRL(item.valorUnitarioCentavos)}</td>
                    <td className="px-5 py-3 text-sm tabular-nums text-right text-ds-mid whitespace-nowrap">
                      {item.deslocamentoCentavos > 0 ? formatBRL(item.deslocamentoCentavos) : '—'}
                    </td>
                    <td className="px-5 py-3 text-sm tabular-nums font-bold text-right text-ds-text whitespace-nowrap">{formatBRL(item.totalItemCentavos)}</td>
                    <td className="px-5 py-3">
                      {!isFaturada && editandoId !== item.id && (
                        <div className="flex items-center gap-1 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={() => { setEditandoId(item.id); setAdicionando(false) }}
                            disabled={adicionando}
                            title={freq.tipoMedico === 'DIARISTA' ? 'Editar frequência' : 'Editar plantão'}
                            className="p-1.5 rounded-lg text-ds-light hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-30">
                            <Pencil size={14} />
                          </button>
                          <button
                            onClick={() => handleRemove(item.id)}
                            disabled={removendo === item.id}
                            title={freq.tipoMedico === 'DIARISTA' ? 'Remover frequência' : 'Remover plantão'}
                            className="p-1.5 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors">
                            {removendo === item.id ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}

                {freq.itens.length === 0 && !adicionando && (
                  <tr>
                    <td colSpan={7} className="px-5 py-16 text-center">
                      <FileText size={32} className="mx-auto mb-3 text-ds-light opacity-30" />
                      <p className="text-sm text-ds-light">
                        Nenhum{freq.tipoMedico === 'DIARISTA' ? 'a' : ''} {itemLabel(freq.tipoMedico, 1)} {itemAgree(freq.tipoMedico, 1, 'lançad')} ainda.
                      </p>
                      {!isFaturada && (
                        <p className="text-xs text-ds-light mt-1">
                          Clique em "{freq.tipoMedico === 'DIARISTA' ? 'Adicionar Frequência' : 'Adicionar Plantão'}" para começar.
                        </p>
                      )}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
        </div>

        {/* ── Paginação dos plantões ──────────────────────────────────────── */}
        {freq.itens.length > 0 && (
          <div className="flex items-center justify-between px-6 py-3 border-t border-ds-border shrink-0 bg-white text-xs text-ds-light">
            <span>
              Exibindo <strong className="text-ds-mid">{Math.min(itemPageAtual * ITENS_POR_PAGINA + 1, freq.itens.length)}
              –{Math.min((itemPageAtual + 1) * ITENS_POR_PAGINA, freq.itens.length)}</strong> de{' '}
              <strong className="text-ds-mid">{freq.itens.length}</strong> {itemLabel(freq.tipoMedico, freq.itens.length)}
            </span>
            {totalItemPages > 1 && (
              <div className="flex items-center gap-2">
                <Button variant="ghost" size="sm" disabled={itemPageAtual === 0} onClick={() => setItemPage(itemPageAtual - 1)}>
                  Anterior
                </Button>
                <span className="px-2 text-ds-mid font-medium">{itemPageAtual + 1} / {totalItemPages}</span>
                <Button variant="ghost" size="sm" disabled={itemPageAtual >= totalItemPages - 1} onClick={() => setItemPage(itemPageAtual + 1)}>
                  Próximo
                </Button>
              </div>
            )}
          </div>
        )}

        </div>
        {/* ── /Corpo scrollável ── */}

      </div>
    </div>
  )
}

// ─── Modal de edição de Competência + Setor ────────────────────────────────────

// Só Competência e Setor Operacional são editáveis pós-criação (Tomador, Tipo de Escala,
// Modalidade e Ocorrência permanecem fixos — pedido explícito do cliente). Bloqueado só quando
// FATURADA (garantido pelo caller, que só renderiza este modal nesse caso).
function EditarFrequenciaModal({
  freq, onClose, onSalvo,
}: {
  freq: FrequenciaMedicaResp
  onClose: () => void
  onSalvo: (f: FrequenciaMedicaResp) => void
}) {
  const [competencia, setCompetencia] = useState(freq.competencia)
  const [grupos, setGrupos] = useState<TomadorGrupoFaturamento[]>([])
  const [grupoId, setGrupoId] = useState(freq.grupoId ?? '')
  const [setorId, setSetorId] = useState(freq.servicoOperacionalId)
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => {
    tomadoresApi.listarGrupos(freq.tomadorId).then(setGrupos).catch(() => setGrupos([]))
  }, [freq.tomadorId])

  // Garante que o grupo atual da frequência apareça na lista mesmo se tiver sido desativado
  // depois (ou removido — freq.grupoId pode não bater com nenhum grupo carregado).
  const gruposOptions = !freq.grupoId || grupos.some(g => g.id === freq.grupoId)
    ? grupos
    : [{
        id: freq.grupoId, tomadorId: freq.tomadorId, servicoLc116Id: '', codigoLc116: null,
        descricaoServico: null, nome: '(grupo removido)', descricaoNota: '', ordem: 0,
        ativo: false, servicosOperacionais: [],
      }, ...grupos]

  const grupoAtual = gruposOptions.find(g => g.id === grupoId) ?? null
  const setoresAtivos = grupoAtual ? grupoAtual.servicosOperacionais.filter(s => s.ativo) : []
  // Garante que o setor atual da frequência apareça na lista mesmo se tiver sido desativado
  // depois — mesmo padrão já usado pra modalidade/ocorrência inativa em outros formulários.
  const setores = setoresAtivos.some(s => s.id === freq.servicoOperacionalId) || !freq.servicoOperacionalNome
    ? setoresAtivos
    : [{ id: freq.servicoOperacionalId, tomadorId: freq.tomadorId, nome: freq.servicoOperacionalNome, ativo: false }, ...setoresAtivos]

  // A competência atual pode estar fora da janela de 12 meses gerada por generateCompetencias()
  // (frequência antiga) — injeta na lista pra não sumir do <select> quando o modal abre.
  const competenciaOptions = COMPETENCIAS.includes(freq.competencia)
    ? COMPETENCIAS
    : [freq.competencia, ...COMPETENCIAS]

  const canSave = !!grupoId && !!setorId

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!canSave) return
    setSaving(true); setErr(null)
    try {
      const atualizada = await frequenciasApi.atualizar(freq.id, { competencia, grupoId, servicoOperacionalId: setorId })
      onSalvo(atualizada)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao salvar')
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <p className="text-sm font-bold text-ds-text">Editar Frequência</p>
          <button type="button" onClick={onClose} className="p-1 rounded-lg text-ds-light hover:bg-ds-input transition-colors">
            <X size={16} />
          </button>
        </div>
        <form onSubmit={handleSubmit}>
          {err && <div className="mb-3"><Alert variant="error" onClose={() => setErr(null)}>{err}</Alert></div>}
          <div className="mb-3">
            <label className="block text-xs font-bold text-ds-mid mb-1">Competência *</label>
            <select value={competencia} onChange={e => setCompetencia(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white">
              {competenciaOptions.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
            </select>
          </div>
          <div className="mb-3">
            <label className="block text-xs font-bold text-ds-mid mb-1">Grupo de Faturamento *</label>
            <select value={grupoId} onChange={e => { setGrupoId(e.target.value); setSetorId('') }}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white">
              <option value="">Selecione o grupo...</option>
              {gruposOptions.map(g => <option key={g.id} value={g.id}>{g.nome}{!g.ativo ? ' (inativo)' : ''}</option>)}
            </select>
          </div>
          <div className="mb-4">
            <label className="block text-xs font-bold text-ds-mid mb-1">Setor Operacional *</label>
            <select value={setorId} onChange={e => setSetorId(e.target.value)} disabled={!grupoId}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white disabled:opacity-50">
              <option value="">Selecione o setor...</option>
              {setores.map(s => <option key={s.id} value={s.id}>{s.nome}{!s.ativo ? ' (inativo)' : ''}</option>)}
            </select>
          </div>
          <p className="text-[11px] text-ds-light mb-4">
            Tomador, Tipo de Escala, Modalidade e Ocorrência não podem ser alterados aqui — se
            algum deles estiver errado, exclua esta frequência e crie uma nova.
          </p>
          <div className="flex gap-3">
            <Button type="button" variant="secondary" className="flex-1" disabled={saving} onClick={onClose}>
              Cancelar
            </Button>
            <button type="submit" disabled={saving || !canSave}
              className="flex-1 px-4 py-2.5 rounded-xl bg-primary text-white text-sm font-bold hover:bg-primary-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5">
              {saving ? <><Loader2 size={14} className="animate-spin" />Salvando...</> : 'Salvar'}
            </button>
          </div>
        </form>
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
  const [filtroTomador, setFiltroTomador] = useState('')
  const [filtroMedico,  setFiltroMedico]  = useState('')
  const [filtroComp,    setFiltroComp]    = useState('')
  const [filtroStatus,  setFiltroStatus]  = useState('')
  const [q,             setQ]             = useState('')

  // Paginação
  const [page, setPage] = useState(0)
  const pageSize = 10

  const carregar = useCallback(async () => {
    const data = await frequenciasApi.listar({
      tomadorId:   filtroTomador || undefined,
      medicoId:    filtroMedico  || undefined,
      competencia: filtroComp    || undefined,
      status:      filtroStatus  || undefined,
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
    if (!loading) { carregar().catch(() => {}); setPage(0) }
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

  // PINSAUDE-13.26: excluir frequência (só disponível em Rascunho) — permite corrigir uma
  // escolha errada de modalidade/ocorrência (não editável depois de criada) apagando e criando de novo.
  function handleExcluida(id: string) {
    setFrequencias(prev => prev.filter(f => f.id !== id))
    setSelecionada(null)
  }

  const tomadoresMap = useMemo(() => Object.fromEntries(tomadores.map(t => [t.id, t])), [tomadores])
  const medicosMap   = useMemo(() => Object.fromEntries(medicos.map(m => [m.id, m])),   [medicos])

  const filtradas = useMemo(() => {
    const qL = q.toLowerCase()
    return frequencias.filter(f => {
      const tomNome = tomadoresMap[f.tomadorId]?.razaoSocialNome?.toLowerCase() ?? ''
      const tomFantasia = tomadoresMap[f.tomadorId]?.nomeFantasia?.toLowerCase() ?? ''
      const medNome = medicosMap[f.medicoId]?.nome?.toLowerCase() ?? ''
      const matchQ = !q || tomNome.includes(qL) || tomFantasia.includes(qL) || medNome.includes(qL)
        || (f.tipoMedico ?? '').toLowerCase().includes(qL)
        || (f.servicoOperacionalNome ?? '').toLowerCase().includes(qL)
        || f.competencia.includes(q)
      return matchQ
    })
  }, [frequencias, q, tomadoresMap, medicosMap])

  const paginated  = filtradas.slice(page * pageSize, (page + 1) * pageSize)
  const totalPages = Math.ceil(filtradas.length / pageSize)

  // Stats globais
  const emElaboracao = frequencias.filter(f => ['RASCUNHO', 'PDF_GERADO'].includes(f.status)).length
  const pendentes    = frequencias.filter(f => f.status === 'AGUARDANDO_ASSINATURA').length
  const assinadas    = frequencias.filter(f => ['ASSINADA_RECEBIDA', 'ENVIADA_TOMADOR'].includes(f.status)).length
  const faturadas    = frequencias.filter(f => f.status === 'FATURADA').length

  // Totalizadores do filtro
  const totalFiltradoValor = filtradas.reduce((s, f) => s + f.totalValorCentavos, 0)
  const temFiltroAtivo     = q || filtroTomador || filtroMedico || filtroComp || filtroStatus

  const selectCls = "py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"

  function limparFiltros() {
    setQ(''); setFiltroTomador(''); setFiltroMedico('')
    setFiltroComp(''); setFiltroStatus(''); setPage(0)
  }

  if (loading) return <div className="flex items-center justify-center h-64"><Spinner size="lg" /></div>

  return (
    <div className="flex flex-col gap-5">

      {initErr && <Alert variant="error">{initErr}</Alert>}

      {/* ── Stats ────────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard icon={CalendarDays} label="Total de Frequências" value={frequencias.length}
          sub="todas as competências" iconBg="bg-primary-50" iconColor="text-primary" />
        <StatCard icon={Clock} label="Em Elaboração" value={emElaboracao}
          sub="rascunho ou aguardando PDF" iconBg="bg-gray-100" iconColor="text-gray-500" />
        <StatCard icon={CheckCircle2} label="Aguardando / Assinadas" value={pendentes + assinadas}
          sub="prontas para faturar" iconBg="bg-purple-50" iconColor="text-purple-600" />
        <StatCard icon={FileText} label="Faturadas" value={faturadas}
          sub="incluídas em produção" iconBg="bg-green-50" iconColor="text-green-600" />
      </div>

      {/* ── Totalizadores do filtro ──────────────────────────────────────── */}
      {temFiltroAtivo && (
        <div className="bg-primary-50 border border-primary/20 rounded-xl px-5 py-3 flex flex-wrap gap-6 items-center">
          <div>
            <p className="text-xs text-primary/70 font-semibold uppercase tracking-wide">Registros filtrados</p>
            <p className="text-xl font-black text-primary leading-none mt-0.5">{filtradas.length}</p>
          </div>
          <div className="w-px h-10 bg-primary/20" />
          <div>
            <p className="text-xs text-primary/70 font-semibold uppercase tracking-wide">Volume Total</p>
            <p className="text-xl font-black text-primary leading-none mt-0.5">{formatBRL(totalFiltradoValor)}</p>
          </div>
          <button onClick={limparFiltros}
            className="ml-auto text-xs text-primary/70 hover:text-primary underline underline-offset-2 transition-colors">
            Limpar filtros
          </button>
        </div>
      )}

      {/* ── Main card ────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">

        {/* Card header */}
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-ds-text">Frequências Médicas</p>
            <p className="text-xs text-ds-light mt-0.5">Plantões lançados por médico, setor e competência</p>
          </div>
          <Button size="sm" onClick={() => setShowNova(true)}>
            <Plus size={15} className="mr-1" /> Nova Frequência
          </Button>
        </div>

        {/* Filter bar */}
        <div className="flex flex-wrap gap-3 items-center px-5 py-3 border-b border-ds-border bg-ds-input">
          <div className="relative flex-1 min-w-[200px] max-w-sm">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light pointer-events-none" />
            <input
              value={q}
              onChange={e => { setQ(e.target.value); setPage(0) }}
              placeholder="Médico, tomador, setor ou competência..."
              className="block w-full pl-9 pr-3 py-1.5 text-sm border border-ds-border rounded-lg bg-white text-ds-text placeholder-ds-light focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            />
          </div>
          <select value={filtroTomador} onChange={e => { setFiltroTomador(e.target.value); setPage(0) }} className={`${selectCls} flex-1 min-w-[160px]`}>
            <option value="">Todos os tomadores</option>
            {tomadores.map(t => (
              <option key={t.id} value={t.id}>
                {t.razaoSocialNome}{t.nomeFantasia ? ` — ${t.nomeFantasia}` : ''}
              </option>
            ))}
          </select>
          <select value={filtroMedico} onChange={e => { setFiltroMedico(e.target.value); setPage(0) }} className={`${selectCls} flex-1 min-w-[160px]`}>
            <option value="">Todos os médicos</option>
            {medicos.filter(m => m.status === 'ATIVO').map(m => <option key={m.id} value={m.id}>{m.nome}</option>)}
          </select>
          <select value={filtroComp} onChange={e => { setFiltroComp(e.target.value); setPage(0) }} className={selectCls}>
            <option value="">Todas as competências</option>
            {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
          </select>
          <select value={filtroStatus} onChange={e => { setFiltroStatus(e.target.value); setPage(0) }} className={selectCls}>
            <option value="">Todos os status</option>
            {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          {temFiltroAtivo && (
            <button onClick={limparFiltros}
              className="px-3 py-1.5 text-xs font-medium text-ds-mid border border-ds-border rounded-lg bg-white hover:bg-ds-hover transition-colors whitespace-nowrap">
              Limpar filtros
            </button>
          )}
        </div>

        {/* Tabela */}
        <Table>
          <THead>
            <TRow>
              <TH>Competência</TH>
              <TH>Médico</TH>
              <TH>Tomador</TH>
              <TH>Setor</TH>
              <TH className="text-center">Plantões</TH>
              <TH className="text-right">Total</TH>
              <TH>Status</TH>
            </TRow>
          </THead>
          <TBody>
            {paginated.length === 0 ? (
              <TRow>
                <TD colSpan={7}>
                  <div className="flex flex-col items-center justify-center py-16 text-center">
                    <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
                      <CalendarDays size={28} className="text-primary-200" />
                    </div>
                    <p className="text-sm font-semibold text-ds-mid">
                      {frequencias.length === 0 ? 'Nenhuma frequência registrada' : 'Nenhum registro encontrado'}
                    </p>
                    <p className="text-xs text-ds-light mt-1">
                      {frequencias.length === 0
                        ? 'Clique em "Nova Frequência" para começar.'
                        : 'Tente ajustar os filtros.'}
                    </p>
                  </div>
                </TD>
              </TRow>
            ) : paginated.map(f => (
              <TRow
                key={f.id}
                className="cursor-pointer hover:bg-ds-input transition-colors"
                onClick={() => setSelecionada(f)}
              >
                <TD>
                  <span className="font-bold text-ds-text text-sm">{formatCompetencia(f.competencia)}</span>
                </TD>
                <TD>
                  <div className="font-semibold text-ds-text text-sm">{medicosMap[f.medicoId]?.nome ?? '—'}</div>
                  <div className="text-xs text-ds-light">{f.tipoMedico ?? '—'}</div>
                </TD>
                <TD>
                  <div className="text-sm text-ds-mid truncate max-w-[200px]">
                    {tomadoresMap[f.tomadorId]?.razaoSocialNome ?? '—'}
                  </div>
                  {tomadoresMap[f.tomadorId]?.nomeFantasia && (
                    <div className="text-xs font-medium text-red-600 truncate max-w-[200px]">
                      {tomadoresMap[f.tomadorId]?.nomeFantasia}
                    </div>
                  )}
                </TD>
                <TD>
                  <div className="text-sm text-ds-light truncate max-w-[160px]">
                    {f.servicoOperacionalNome ?? '—'}
                  </div>
                </TD>
                <TD className="text-center">
                  <span className="text-sm tabular-nums text-ds-mid font-medium">{f.itens.length}</span>
                </TD>
                <TD className="text-right">
                  <span className="text-sm tabular-nums font-bold text-ds-text whitespace-nowrap">
                    {formatBRL(f.totalValorCentavos)}
                  </span>
                </TD>
                <TD>
                  <span className={`inline-flex px-2.5 py-0.5 rounded-md text-xs font-semibold whitespace-nowrap ${STATUS_CLS[f.status] ?? 'bg-gray-100 text-gray-500'}`}>
                    {STATUS_LABEL[f.status] ?? f.status}
                  </span>
                </TD>
              </TRow>
            ))}
          </TBody>
        </Table>

        {/* Footer */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-ds-border bg-ds-input text-xs text-ds-light">
          <span>
            Exibindo <strong className="text-ds-mid">{paginated.length}</strong> de{' '}
            <strong className="text-ds-mid">{filtradas.length}</strong> registro{filtradas.length !== 1 ? 's' : ''}
            {temFiltroAtivo && filtradas.length !== frequencias.length && (
              <> (total: {frequencias.length})</>
            )}
          </span>
          {totalPages > 1 && (
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                Anterior
              </Button>
              <span className="px-2 text-ds-mid font-medium">{page + 1} / {totalPages}</span>
              <Button variant="ghost" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                Próximo
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Modal de detalhe */}
      {selecionada && (
        <PainelFrequencia
          freq={selecionada}
          tomadores={tomadores}
          medicos={medicos}
          onClose={() => setSelecionada(null)}
          onAtualizar={handleAtualizar}
          onExcluida={handleExcluida}
        />
      )}

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
