import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  AlertCircle, CalendarDays, CheckCircle2, ChevronDown, ClipboardList, Clock, Download,
  FileText, Loader2, Pencil, Plus, Printer, Search, Trash2, Upload, X,
} from 'lucide-react'
import { Button, Spinner, Alert, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { tomadoresApi, Tomador, TomadorGrupoFaturamento, TomadorModalidade } from '../api/tomadoresApi'
import { medicosApi, Medico, MedicoPage } from '../api/medicosApi'
import {
  frequenciasApi,
  FrequenciaMedicaResp,
  FrequenciaMedicaRequest,
  FrequenciaItemRequest,
  FrequenciaItemResp,
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

  const filtered = items.filter(i => getLabel(i).toLowerCase().includes(q.toLowerCase()))

  return (
    <div ref={containerRef} className="relative">
      {label && <label className="block text-xs font-bold text-ds-mid mb-1">{label}</label>}
      <button ref={btnRef} type="button" disabled={disabled}
        onClick={handleToggle}
        className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border text-sm transition-colors disabled:opacity-50 ${
          value ? 'border-primary/40 bg-primary-50 text-ds-text font-medium' : 'border-ds-border bg-white text-ds-light'
        }`}>
        <span className="truncate">{value ? getLabel(value) : placeholder}</span>
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
                  {getLabel(item)}
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
  const [setor,       setSetor]       = useState<{ id: string; nome: string } | null>(null)
  const [competencia, setCompetencia] = useState(COMPETENCIAS[0])
  const [tipoMedico, setTipoMedico] = useState<'PLANTONISTA' | 'DIARISTA'>('PLANTONISTA')
  const [grupos,      setGrupos]      = useState<TomadorGrupoFaturamento[]>([])
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

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

  const setores = grupos.flatMap(g => g.servicosOperacionais.filter(s => s.ativo))

  useEffect(() => {
    if (!tomador) { setGrupos([]); return }
    tomadoresApi.listarGrupos(tomador.id).then(setGrupos).catch(() => setGrupos([]))
  }, [tomador?.id])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!tomador || !medico || !setor) return
    setSaving(true); setErr(null)
    try {
      const req: FrequenciaMedicaRequest = {
        tomadorId: tomador.id,
        medicoId: medico.id,
        servicoOperacionalId: setor.id,
        competencia,
        tipoMedico,
      }
      const criada = await frequenciasApi.criar(req)
      onCriada(criada)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao criar')
    } finally { setSaving(false) }
  }

  const canSave = !!tomador && !!medico && !!setor
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
              getLabel={t => t.razaoSocialNome + (t.municipio ? ` — ${t.municipio}` : '')}
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

          {/* Linha 3: Setor (full width) */}
          <div className="mt-4">
            <Dropdown
              label="Setor Operacional *"
              placeholder={!tomador ? 'Selecione o tomador primeiro...' : setores.length === 0 ? 'Nenhum setor cadastrado' : 'Selecione o setor...'}
              items={setores}
              value={setor}
              onChange={setSetor}
              getLabel={s => s.nome}
              disabled={!tomador || setores.length === 0}
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
  tomadorId, item, onSave, onCancel,
}: {
  tomadorId: string
  item?: FrequenciaItemResp          // se presente, modo edição
  onSave: (req: FrequenciaItemRequest) => Promise<void>
  onCancel: () => void
}) {
  const isEdit = !!item
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [modalidade,  setModalidade]  = useState<TomadorModalidade | null>(null)
  const [data,        setData]        = useState(item?.dataExecucao ?? new Date().toISOString().slice(0, 10))
  const [ocorrencia,  setOcorrencia]  = useState(item?.ocorrencia ?? '')
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

  useEffect(() => {
    tomadoresApi.listarModalidades(tomadorId)
      .then(ms => {
        const ativas = ms.filter(m => m.ativo)
        setModalidades(ativas)
        if (item) {
          // pré-seleciona a modalidade atual (inclusive inativas)
          const atual = ms.find(m => m.id === item.modalidadeId) ?? null
          setModalidade(atual)
        }
      })
      .catch(() => {})
  }, [tomadorId, item?.modalidadeId])

  async function handleSave() {
    if (!modalidade) return
    setSaving(true); setErr(null)
    try {
      await onSave({ modalidadeId: modalidade.id, dataExecucao: data, ocorrencia: ocorrencia || undefined })
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao salvar')
    } finally { setSaving(false) }
  }

  const total = modalidade ? modalidade.valorCentavos + modalidade.deslocamentoCentavos : 0

  return (
    <div className={`mx-5 mb-3 rounded-xl border p-4 ${isEdit ? 'bg-yellow-50/60 border-yellow-200' : 'bg-primary-50/40 border-primary/20'}`}>
      <div className="flex items-center justify-between mb-3">
        <p className={`text-xs font-bold flex items-center gap-1.5 ${isEdit ? 'text-yellow-700' : 'text-primary'}`}>
          {isEdit ? <Pencil size={12} /> : <Plus size={12} />}
          {isEdit ? 'Editar Plantão' : 'Novo Plantão'}
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
        <Dropdown
          label="Modalidade *"
          placeholder={modalidades.length === 0 ? 'Sem modalidades cadastradas' : 'Selecione a modalidade...'}
          items={modalidades}
          value={modalidade}
          onChange={setModalidade}
          getLabel={m => `${m.nome} — ${m.turno} · ${m.horario}`}
          disabled={modalidades.length === 0}
        />
      </div>

      {/* Preview de valores */}
      {modalidade && (
        <div className="bg-white rounded-lg px-4 py-2.5 mb-3 flex items-center gap-5 text-xs border border-ds-border/60">
          <span className="text-ds-light">{modalidade.horario}</span>
          <span className="text-ds-mid">Valor: <span className="font-bold text-ds-text">{formatBRL(modalidade.valorCentavos)}</span></span>
          {modalidade.deslocamentoCentavos > 0 && (
            <span className="text-ds-mid">Deslocamento: <span className="font-bold text-ds-text">{formatBRL(modalidade.deslocamentoCentavos)}</span></span>
          )}
          <span className="ml-auto text-sm font-black text-primary">Total: {formatBRL(total)}</span>
        </div>
      )}

      {/* Ocorrência */}
      <div className="mb-3">
        <label className="block text-xs font-bold text-ds-mid mb-1">
          Ocorrência <span className="font-normal text-ds-light">(opcional)</span>
        </label>
        <input type="text" value={ocorrencia} onChange={e => setOcorrencia(e.target.value)}
          placeholder="Descreva alguma ocorrência especial neste plantão..."
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
            : isEdit ? 'Salvar Alterações' : 'Adicionar Plantão'
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
}

let plantaoRowKey = 1

function criarLinhasVazias(qtd: number): PlantaoRow[] {
  return Array.from({ length: qtd }, () => ({
    key: plantaoRowKey++, dataExecucao: '', modalidadeId: '', ocorrencia: '',
  }))
}

function PlantaoGridPanel({
  freqId, tomadorId, onSaved, onCancel,
}: {
  freqId: string
  tomadorId: string
  onSaved: () => void | Promise<void>
  onCancel: () => void
}) {
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [rows,        setRows]        = useState<PlantaoRow[]>(() => criarLinhasVazias(6))
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)
  const gridRef        = useRef<HTMLDivElement>(null)
  const focarProximaLinha = useRef<number | null>(null)

  useEffect(() => {
    tomadoresApi.listarModalidades(tomadorId)
      .then(ms => setModalidades(ms.filter(m => m.ativo)))
      .catch(() => {})
  }, [tomadorId])

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
  }

  function addRow(foco = false) {
    const [nova] = criarLinhasVazias(1)
    if (foco) focarProximaLinha.current = nova.key
    setRows(prev => [...prev, nova])
  }

  function removeRow(key: number) {
    setRows(prev => prev.filter(r => r.key !== key))
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
    const validas = rows.filter(r => r.dataExecucao && r.modalidadeId)
    if (validas.length === 0) { setErr('Preencha ao menos uma linha com dia e modalidade'); return }
    setSaving(true); setErr(null)
    const restantes = [...rows]
    try {
      for (const r of validas) {
        await frequenciasApi.adicionarItem(freqId, {
          modalidadeId: r.modalidadeId,
          dataExecucao: r.dataExecucao,
          ocorrencia: r.ocorrencia || undefined,
        })
        const idx = restantes.findIndex(x => x.key === r.key)
        if (idx >= 0) restantes.splice(idx, 1)
      }
      await onSaved()
    } catch (e) {
      // mantém no grid só as linhas ainda não salvas (inclusive a que falhou), pra não duplicar no retry
      setRows(restantes)
      setErr(e instanceof Error ? e.message : 'Erro ao salvar um dos plantões')
    } finally {
      setSaving(false)
    }
  }

  const qtdPreenchidas = rows.filter(r => r.dataExecucao && r.modalidadeId).length

  return (
    <div className="mx-5 mb-3 rounded-xl border border-primary/20 bg-primary-50/40 p-4">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-bold text-primary flex items-center gap-1.5">
          <Plus size={12} /> Novo(s) Plantão(ões)
        </p>
        <button type="button" onClick={onCancel}
          className="p-1 rounded-lg text-ds-light hover:bg-white/70 transition-colors">
          <X size={14} />
        </button>
      </div>

      <div ref={gridRef} className="bg-white rounded-lg border border-ds-border overflow-hidden mb-3">
        <table className="w-full">
          <thead className="bg-ds-surface/60 border-b border-ds-border">
            <tr>
              <th className="px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left w-40">Dia</th>
              <th className="px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left">Modalidade</th>
              <th className="px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left">Ocorrência</th>
              <th className="w-9"></th>
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
                <td className="px-2 py-1.5">
                  <select value={r.modalidadeId}
                    onChange={e => updateRow(r.key, { modalidadeId: e.target.value })}
                    disabled={modalidades.length === 0}
                    className="w-full border border-transparent hover:border-ds-border focus:border-primary rounded-md px-2 py-1.5 text-sm text-ds-text focus:outline-none focus:ring-1 focus:ring-primary/30 disabled:opacity-50">
                    <option value="">{modalidades.length === 0 ? 'Sem modalidades' : 'Selecione...'}</option>
                    {modalidades.map(m => (
                      <option key={m.id} value={m.id}>{m.nome} — {m.turno} · {m.horario}</option>
                    ))}
                  </select>
                </td>
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
        <button type="button" onClick={handleSalvarTodos} disabled={saving || qtdPreenchidas === 0}
          className="flex-1 px-4 py-2 rounded-lg text-white text-xs font-bold disabled:opacity-50 transition-colors flex items-center justify-center gap-1.5 bg-primary hover:bg-primary-700">
          {saving
            ? <><Loader2 size={12} className="animate-spin" />Adicionando...</>
            : qtdPreenchidas === 0
              ? 'Adicionar Plantões'
              : `Adicionar ${qtdPreenchidas} Plantão${qtdPreenchidas === 1 ? '' : 'ões'}`
          }
        </button>
      </div>
    </div>
  )
}

// ─── Modal de detalhe/edição ───────────────────────────────────────────────────

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
  const [adicionando,   setAdicionando]   = useState(false)
  const [editandoId,    setEditandoId]    = useState<string | null>(null)
  const [removendo,     setRemovendo]     = useState<string | null>(null)
  const [gerandoPdf,    setGerandoPdf]    = useState(false)
  const [uploadingDoc,  setUploadingDoc]  = useState(false)
  const [uploadErr,     setUploadErr]     = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const isFaturada = freq.status === 'FATURADA'

  const tomador = tomadores.find(t => t.id === freq.tomadorId)
  const medico  = medicos.find(m => m.id === freq.medicoId)

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
            <p className="text-xs text-ds-light">{formatCompetencia(freq.competencia)}</p>
          </div>
          <div className="px-6 py-3">
            <p className="text-[10px] font-bold text-ds-light uppercase tracking-wider mb-0.5">Total Apurado</p>
            <p className="text-xl font-black text-primary tabular-nums">{formatBRL(freq.totalValorCentavos)}</p>
            <p className="text-xs text-ds-light">{freq.itens.length} plantão{freq.itens.length !== 1 ? 'ões' : ''}</p>
          </div>
        </div>

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
          <p className="text-xs font-semibold text-ds-mid">Plantões lançados</p>
          {!isFaturada && !adicionando && editandoId === null && (
            <button
              onClick={() => setAdicionando(true)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-primary text-white text-xs font-bold hover:bg-primary-700 transition-colors">
              <Plus size={13} /> Adicionar Plantão
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
                onSaved={handleAddGrid}
                onCancel={() => setAdicionando(false)}
              />
            </div>
            <div className="sm:hidden">
              <PlantaoFormPanel
                tomadorId={freq.tomadorId}
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
              item={freq.itens.find(i => i.id === editandoId)}
              onSave={req => handleEdit(editandoId, req)}
              onCancel={() => setEditandoId(null)}
            />
          </div>
        )}

        {/* ── Tabela de itens ────────────────────────────────────────────── */}
        <div className="flex-1 overflow-y-auto">
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
                {freq.itens.map(item => (
                  <tr key={item.id}
                    className={`hover:bg-ds-surface/50 transition-colors group ${editandoId === item.id ? 'bg-yellow-50/40' : ''}`}>
                    <td className="px-5 py-3 text-sm font-medium text-ds-text whitespace-nowrap">{formatDate(item.dataExecucao)}</td>
                    <td className="px-5 py-3">
                      <p className="text-sm font-semibold text-ds-text">{item.modalidadeNome ?? '—'}</p>
                      {item.modalidadeTurno && (
                        <p className="text-xs text-ds-light">{item.modalidadeTurno} · {item.modalidadeHorario}</p>
                      )}
                    </td>
                    <td className="px-5 py-3 text-sm text-ds-mid">{item.ocorrencia ?? '—'}</td>
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
                            title="Editar plantão"
                            className="p-1.5 rounded-lg text-ds-light hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-30">
                            <Pencil size={14} />
                          </button>
                          <button
                            onClick={() => handleRemove(item.id)}
                            disabled={removendo === item.id}
                            title="Remover plantão"
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
                      <p className="text-sm text-ds-light">Nenhum plantão lançado ainda.</p>
                      {!isFaturada && <p className="text-xs text-ds-light mt-1">Clique em "Adicionar Plantão" para começar.</p>}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
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
  const [filtroTomador, setFiltroTomador] = useState('')
  const [filtroMedico,  setFiltroMedico]  = useState('')
  const [filtroComp,    setFiltroComp]    = useState('')
  const [filtroStatus,  setFiltroStatus]  = useState('')
  const [q,             setQ]             = useState('')

  // Paginação
  const [page, setPage] = useState(0)
  const pageSize = 15

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

  const tomadoresMap = useMemo(() => Object.fromEntries(tomadores.map(t => [t.id, t])), [tomadores])
  const medicosMap   = useMemo(() => Object.fromEntries(medicos.map(m => [m.id, m])),   [medicos])

  const filtradas = useMemo(() => {
    const qL = q.toLowerCase()
    return frequencias.filter(f => {
      const tomNome = tomadoresMap[f.tomadorId]?.razaoSocialNome?.toLowerCase() ?? ''
      const medNome = medicosMap[f.medicoId]?.nome?.toLowerCase() ?? ''
      const matchQ = !q || tomNome.includes(qL) || medNome.includes(qL)
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
            {tomadores.map(t => <option key={t.id} value={t.id}>{t.razaoSocialNome}</option>)}
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
