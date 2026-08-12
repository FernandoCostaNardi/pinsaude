import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  AlertCircle, ArrowLeft, CalendarDays, CheckCircle2, ChevronDown, ChevronRight,
  Download, FileText, Loader2, Plus, Printer, Trash2, Upload, X,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Spinner, Alert } from '@pinsaude/ui'
import { portalApi, PerfilMedico } from '../api/portalApi'
import { tomadoresApi, Tomador, TomadorGrupoFaturamento, TomadorModalidade, TomadorOcorrencia } from '../api/tomadoresApi'
import {
  frequenciasApi,
  FrequenciaMedicaResp,
  FrequenciaMedicaRequest,
  FrequenciaItemRequest,
  FrequenciaSemanaProgresso,
} from '../api/frequenciasApi'
import { useAuth } from '../auth/AuthContext'
import { abrirPdfFrequencia } from '../utils/frequenciaPdf'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
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
  const meses = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro']
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

// Espelha FrequenciaService.calcularValorOcorrencia (backend) só para preview — o % sempre
// incide sobre o valor CADASTRADO da modalidade, nunca sobre o valor proporcional do item (META).
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
    <div className="px-4 py-3 border-b border-ds-border bg-purple-50/50">
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

const STATUS_LABEL: Record<string, string> = {
  RASCUNHO:              'Rascunho',
  PDF_GERADO:            'PDF Gerado',
  AGUARDANDO_ASSINATURA: 'Ag. Assinatura',
  ASSINADA_RECEBIDA:     'Assinada',
  ENVIADA_TOMADOR:       'Enviada',
  FATURADA:              'Faturada',
}

const STATUS_LABEL_FULL: Record<string, string> = {
  RASCUNHO:              'Rascunho',
  PDF_GERADO:            'PDF Gerado',
  AGUARDANDO_ASSINATURA: 'Aguardando Assinatura',
  ASSINADA_RECEBIDA:     'Assinada Recebida',
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

const COMPETENCIAS = generateCompetencias()

// ─── Dropdown (com portal para evitar clipping por overflow) ─────────────────

function Dropdown<T extends { id: string }>({
  label, placeholder, items, value, onChange, getLabel, disabled,
}: {
  label: string
  placeholder: string
  items: T[]
  value: T | null
  onChange: (v: T) => void
  getLabel: (v: T) => string
  disabled?: boolean
}) {
  const [open, setOpen]   = useState(false)
  const [q, setQ]         = useState('')
  const [pos, setPos]     = useState({ top: 0, left: 0, width: 0 })
  const btnRef            = useRef<HTMLButtonElement>(null)
  const containerRef      = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const h = (e: MouseEvent) => {
      const portal = document.getElementById('dropdown-portal-active')
      if (portal && portal.contains(e.target as Node)) return
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false)
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
        className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border text-sm transition-colors disabled:opacity-50 min-h-[44px] ${
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
              className="w-full text-xs px-2 py-2 border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div className="max-h-56 overflow-y-auto divide-y divide-ds-border">
            {filtered.length === 0
              ? <p className="px-3 py-3 text-xs text-ds-light text-center">Sem resultados</p>
              : filtered.map(item => (
                <button key={item.id} type="button"
                  onClick={() => { onChange(item); setOpen(false); setQ('') }}
                  className="w-full text-left px-3 py-3 text-sm hover:bg-ds-surface transition-colors">
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

// ─── Formulário nova frequência — bottom-sheet no mobile ──────────────────────

interface NovaFreqForm {
  tomador: Tomador | null
  setor: { id: string; nome: string } | null
  competencia: string
  tipoMedico: 'PLANTONISTA' | 'DIARISTA'
}

function NovaFrequenciaModal({
  tomadores, perfil, onClose, onCriada,
}: {
  tomadores: Tomador[]
  perfil: PerfilMedico
  onClose: () => void
  onCriada: (f: FrequenciaMedicaResp) => void
}) {
  const [form, setForm]     = useState<NovaFreqForm>({ tomador: null, setor: null, competencia: COMPETENCIAS[0], tipoMedico: 'PLANTONISTA' })
  const [grupos, setGrupos] = useState<TomadorGrupoFaturamento[]>([])
  const [saving, setSaving] = useState(false)
  const [err, setErr]       = useState<string | null>(null)

  const setores = grupos.flatMap(g => g.servicosOperacionais.filter(s => s.ativo))

  useEffect(() => {
    if (!form.tomador) { setGrupos([]); return }
    tomadoresApi.listarGrupos(form.tomador.id).then(setGrupos).catch(() => setGrupos([]))
  }, [form.tomador?.id])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.tomador || !form.setor) return
    setSaving(true); setErr(null)
    try {
      const req: FrequenciaMedicaRequest = {
        tomadorId: form.tomador.id,
        medicoId: perfil.id,
        servicoOperacionalId: form.setor.id,
        competencia: form.competencia,
        tipoMedico: form.tipoMedico,
      }
      const criada = await frequenciasApi.criar(req)
      onCriada(criada)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao criar')
    } finally { setSaving(false) }
  }

  const canSave = !!form.tomador && !!form.setor

  return (
    // Mobile: bottom-sheet que sobe. Desktop: modal centralizado.
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40 sm:p-4">
      <div className="bg-white rounded-t-2xl sm:rounded-2xl shadow-2xl w-full sm:max-w-2xl max-h-[92vh] flex flex-col">

        {/* Cabeçalho fixo */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-ds-border shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-primary-50 flex items-center justify-center shrink-0">
              <CalendarDays size={18} className="text-primary" />
            </div>
            <div>
              <p className="text-sm font-bold text-ds-text">Nova Frequência Médica</p>
              <p className="text-[11px] text-ds-light mt-0.5 hidden sm:block">Preencha os dados para registrar sua frequência</p>
            </div>
          </div>
          <button onClick={onClose}
            className="p-2 rounded-lg text-ds-light hover:bg-ds-input transition-colors min-h-[44px] min-w-[44px] flex items-center justify-center">
            <X size={18} />
          </button>
        </div>

        {/* Corpo scrollável */}
        <form onSubmit={handleSubmit} className="flex flex-col flex-1 min-h-0">
          <div className="flex-1 overflow-y-auto px-5 py-5 space-y-4">
            {err && <Alert variant="error" onClose={() => setErr(null)}>{err}</Alert>}

            {tomadores.length === 0 ? (
              <div>
                <label className="block text-xs font-bold text-ds-mid mb-1">Tomador (Hospital / Clínica) *</label>
                <div className="rounded-lg bg-orange-50 border border-orange-200 px-3 py-2 flex items-start gap-1.5">
                  <AlertCircle size={13} className="text-orange-500 shrink-0 mt-0.5" />
                  <p className="text-xs text-orange-700">
                    Você ainda não está alocado a nenhum tomador. Entre em contato com o time
                    operacional para liberar o lançamento de frequência.
                  </p>
                </div>
              </div>
            ) : (
              <Dropdown
                label="Tomador (Hospital / Clínica) *"
                placeholder="Selecione o tomador..."
                items={tomadores}
                value={form.tomador}
                onChange={t => setForm(f => ({ ...f, tomador: t, setor: null }))}
                getLabel={t => t.razaoSocialNome + (t.municipio ? ` — ${t.municipio}` : '')}
              />
            )}

            <Dropdown
              label="Setor Operacional *"
              placeholder={!form.tomador ? 'Selecione o tomador primeiro...' : setores.length === 0 ? 'Nenhum setor cadastrado' : 'Selecione o setor...'}
              items={setores}
              value={form.setor}
              onChange={s => setForm(f => ({ ...f, setor: s }))}
              getLabel={s => s.nome}
              disabled={!form.tomador || setores.length === 0}
            />

            {/* Competência + Tipo — 1 coluna no mobile, 2 colunas no desktop */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-ds-mid mb-1">Competência *</label>
                <select value={form.competencia} onChange={e => setForm(f => ({ ...f, competencia: e.target.value }))}
                  className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]">
                  {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-ds-mid mb-1">Tipo de Escala *</label>
                <select value={form.tipoMedico}
                  onChange={e => setForm(f => ({ ...f, tipoMedico: e.target.value as 'PLANTONISTA' | 'DIARISTA' }))}
                  className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]">
                  <option value="PLANTONISTA">Plantonista</option>
                  <option value="DIARISTA">Diarista</option>
                </select>
              </div>
            </div>
          </div>

          {/* Rodapé fixo */}
          <div className="flex gap-3 px-5 py-4 border-t border-ds-border shrink-0">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-3 rounded-xl border border-ds-border text-sm font-semibold text-ds-mid hover:bg-ds-surface transition-colors min-h-[48px]">
              Cancelar
            </button>
            <button type="submit" disabled={!canSave || saving}
              className="flex-1 px-4 py-3 rounded-xl bg-primary text-white text-sm font-bold disabled:opacity-50 hover:bg-primary-700 transition-colors flex items-center justify-center gap-2 min-h-[48px]">
              {saving ? <><Loader2 size={14} className="animate-spin" />Criando...</> : 'Criar Frequência'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Formulário de plantão — empilhado no mobile ──────────────────────────────

function PlantaoFormPanel({
  tomadorId, tipoMedico, onSave, onCancel,
}: {
  tomadorId: string
  tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null   // filtra a lista de modalidades (PINSAUDE-13.25)
  onSave: (req: FrequenciaItemRequest) => Promise<void>
  onCancel: () => void
}) {
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [modalidade,  setModalidade]  = useState<TomadorModalidade | null>(null)
  const [data,        setData]        = useState(new Date().toISOString().slice(0, 10))
  const [ocorrencia,  setOcorrencia]  = useState('')
  const [ocorrencias, setOcorrencias] = useState<TomadorOcorrencia[]>([])
  const [ocorrenciaId, setOcorrenciaId] = useState('')
  const [horaInicio,  setHoraInicio]  = useState('')
  const [horaFim,     setHoraFim]     = useState('')
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

  useEffect(() => {
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
  }, [tomadorId, tipoMedico])

  const precisaHoras = precisaHorasTrabalhadas(modalidade)
  const ocorrenciaSelecionada = ocorrencias.find(o => o.id === ocorrenciaId) ?? null

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
        modalidadeId: modalidade.id,
        dataExecucao: data,
        ocorrencia: ocorrencia || undefined,
        ocorrenciaId: ocorrenciaId || undefined,
        horaInicio: precisaHoras ? horaInicio : undefined,
        horaFim: precisaHoras ? horaFim : undefined,
      })
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao adicionar')
    } finally { setSaving(false) }
  }

  const horasPreview = precisaHoras ? calcularHorasEntrePeriodo(horaInicio, horaFim) : null
  const totalModalidade = modalidade ? calcularValorPreview(modalidade) : null
  const ocorrenciaValor = modalidade ? calcularValorOcorrenciaPreview(ocorrenciaSelecionada, modalidade.valorCentavos) : 0
  const total = totalModalidade != null ? totalModalidade + ocorrenciaValor : null

  return (
    <div className="mx-3 sm:mx-4 mb-3 rounded-xl border border-primary/20 bg-primary-50/40 p-4">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-bold text-primary flex items-center gap-1.5">
          <Plus size={12} /> Novo Plantão
        </p>
        <button type="button" onClick={onCancel}
          className="p-2 rounded-lg text-ds-light hover:bg-white/70 transition-colors min-h-[44px] min-w-[44px] flex items-center justify-center">
          <X size={14} />
        </button>
      </div>

      {/* Data e modalidade — empilhados no mobile, lado a lado no desktop */}
      <div className="grid grid-cols-1 sm:grid-cols-[160px_1fr] gap-3 mb-3">
        <div>
          <label className="block text-xs font-bold text-ds-mid mb-1">Data *</label>
          <input type="date" value={data} onChange={e => setData(e.target.value)}
            className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]" />
        </div>
        <Dropdown
          label="Modalidade *"
          placeholder={modalidades.length === 0 ? 'Sem modalidades cadastradas' : 'Selecione a modalidade...'}
          items={modalidades}
          value={modalidade}
          onChange={setModalidade}
          getLabel={m => `${m.nome} — ${detalheModalidade(m)}`}
          disabled={modalidades.length === 0}
        />
      </div>

      {/* Horário trabalhado — só para modalidade Diarista. O médico digita entrada/saída, não a
          quantidade de horas — o backend deriva a duração (também impressa no PDF, ver frequenciaPdf.ts) */}
      {precisaHoras && (
        <div className="grid grid-cols-2 gap-3 mb-3">
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Entrada *</label>
            <input type="time" value={horaInicio} onChange={e => setHoraInicio(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]" />
          </div>
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">
              Saída * <span className="font-normal text-ds-light">(meta: {modalidade?.horasSemanais}h/sem)</span>
            </label>
            <input type="time" value={horaFim} onChange={e => setHoraFim(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]" />
          </div>
          {horasPreview != null && (
            <p className="col-span-2 text-[11px] text-ds-light">{fmtQtd(horasPreview)}h trabalhadas neste dia</p>
          )}
        </div>
      )}

      {/* Preview de valores — quebra linha no mobile */}
      {modalidade && (
        <div className="bg-white rounded-lg px-3 py-2.5 mb-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs border border-ds-border/60">
          <span className="text-ds-light">{detalheModalidade(modalidade)}</span>
          {modalidade.tipo !== 'DIARISTA' && (
            <span className="text-ds-mid">Valor: <span className="font-bold text-ds-text">{formatBRL(modalidade.valorCentavos)}</span></span>
          )}
          {modalidade.deslocamentoCentavos > 0 && (
            <span className="text-ds-mid">Desl.: <span className="font-bold text-ds-text">{formatBRL(modalidade.deslocamentoCentavos)}</span></span>
          )}
          {ocorrenciaSelecionada && (
            <span className="text-ds-mid">Ocorrência: <span className="font-bold text-ds-text">{formatBRL(ocorrenciaValor)}</span></span>
          )}
          <span className="text-sm font-black text-primary sm:ml-auto">
            {modalidade.tipo === 'DIARISTA'
              ? 'Contabilizado no valor mensal'
              : total != null ? `Total: ${formatBRL(total)}` : 'Informe as horas para calcular'}
          </span>
        </div>
      )}

      <div className="mb-3">
        <label className="block text-xs font-bold text-ds-mid mb-1">
          Ocorrência do catálogo <span className="font-normal text-ds-light">(opcional)</span>
        </label>
        <select value={ocorrenciaId} onChange={e => setOcorrenciaId(e.target.value)}
          className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]">
          <option value="">Nenhuma</option>
          {ocorrencias.map(o => (
            <option key={o.id} value={o.id}>{o.nome}</option>
          ))}
        </select>
      </div>

      <div className="mb-3">
        <label className="block text-xs font-bold text-ds-mid mb-1">
          Observação <span className="font-normal text-ds-light">(opcional, texto livre sem valor)</span>
        </label>
        <input type="text" value={ocorrencia} onChange={e => setOcorrencia(e.target.value)}
          placeholder="Descreva alguma ocorrência especial..."
          className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white" />
      </div>

      {err && <p className="text-xs text-red-600 mb-2">{err}</p>}

      <div className="flex gap-2">
        <button type="button" onClick={onCancel}
          className="px-4 py-2.5 rounded-lg border border-ds-border text-xs font-semibold text-ds-mid hover:bg-white transition-colors min-h-[44px]">
          Cancelar
        </button>
        <button type="button" onClick={handleSave} disabled={!modalidade || saving}
          className="flex-1 px-4 py-2.5 rounded-lg bg-primary text-white text-xs font-bold disabled:opacity-50 hover:bg-primary-700 transition-colors flex items-center justify-center gap-1.5 min-h-[44px]">
          {saving ? <><Loader2 size={12} className="animate-spin" />Adicionando...</> : <><Plus size={12} />Adicionar Plantão</>}
        </button>
      </div>
    </div>
  )
}

// ─── Painel de itens — mobile-first ──────────────────────────────────────────

function FrequenciaItensPanel({
  freq, tomadorNome, perfil, onAtualizar,
}: {
  freq: FrequenciaMedicaResp
  tomadorNome: string
  perfil: PerfilMedico
  onAtualizar: (f: FrequenciaMedicaResp) => void
}) {
  const { user } = useAuth()
  const [adicionando,  setAdicionando]  = useState(false)
  const [removendo,    setRemovendo]    = useState<string | null>(null)
  const [gerandoPdf,   setGerandoPdf]   = useState(false)
  const [uploadingDoc, setUploadingDoc] = useState(false)
  const [uploadErr,    setUploadErr]    = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const isFaturada = freq.status === 'FATURADA'

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
      if (['RASCUNHO', 'PDF_GERADO'].includes(freq.status)) {
        freqAtual = await frequenciasApi.gerarPdf(freq.id)
        onAtualizar(freqAtual)
      }
      abrirPdfFrequencia({
        freq:          freqAtual,
        medicoNome:    perfil.nome,
        medicoCrm:     perfil.crm,
        medicoCrmUf:   perfil.crmUf,
        tomadorNome,
        empresaNome:   'Pin Saúde',
        empresaCnpj:   user?.cnpj_id ?? '',
      })
    } catch { /* popup bloqueado já exibe alerta */ }
    finally { setGerandoPdf(false) }
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

  return (
    <div className="bg-ds-surface rounded-xl border border-ds-border overflow-hidden">

      {/* Toolbar — flex-wrap para caber no mobile */}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3 bg-white border-b border-ds-border">
        <p className="text-xs font-bold text-ds-mid">
          {freq.itens.length} plantão{freq.itens.length !== 1 ? 'ões' : ''} lançado{freq.itens.length !== 1 ? 's' : ''}
        </p>
        <p className="text-xs text-ds-mid">
          Total: <span className="font-black text-ds-text tabular-nums">{formatBRL(freq.totalValorCentavos)}</span>
        </p>
        <div className="flex items-center gap-2 ml-auto">
          <button onClick={handleGerarPdf} disabled={gerandoPdf}
            title="Gerar PDF do Relatório de Frequência"
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs font-semibold hover:border-primary hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-50 min-h-[40px]">
            {gerandoPdf ? <Loader2 size={12} className="animate-spin" /> : <Printer size={12} />}
            <span>PDF</span>
          </button>
          {!isFaturada && !adicionando && (
            <button onClick={() => setAdicionando(true)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-primary text-white text-xs font-bold hover:bg-primary-700 transition-colors min-h-[40px]">
              <Plus size={12} />
              <span className="sm:hidden">Adicionar</span>
              <span className="hidden sm:inline">Adicionar Plantão</span>
            </button>
          )}
        </div>
      </div>

      {/* Progresso semanal (modalidade Diarista) */}
      <ProgressoSemanal semanas={freq.progressoSemanal} />

      {/* Seção de documento assinado — responsiva */}
      {(freq.status === 'AGUARDANDO_ASSINATURA' || freq.documentoAssinado) && (
        <div className="px-4 py-3 border-b border-ds-border bg-yellow-50/60 space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold text-yellow-800 uppercase tracking-wider">
              Documento Assinado
            </span>
            {freq.documentoAssinado && (
              <span className="flex items-center gap-1 text-xs text-green-700 font-semibold">
                <CheckCircle2 size={12} /> Recebido
              </span>
            )}
          </div>
          {uploadErr && <p className="text-xs text-red-600">{uploadErr}</p>}
          <div className="flex flex-col sm:flex-row gap-2">
            {(freq.status === 'AGUARDANDO_ASSINATURA' || (freq.documentoAssinado && !isFaturada)) && (
              <>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="application/pdf,image/*"
                  className="hidden"
                  onChange={handleUploadDocumento}
                />
                <button
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploadingDoc}
                  className="flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-lg bg-purple-600 text-white text-xs font-bold hover:bg-purple-700 transition-colors disabled:opacity-50 min-h-[44px]">
                  {uploadingDoc ? <Loader2 size={12} className="animate-spin" /> : <Upload size={12} />}
                  {freq.documentoAssinado ? 'Trocar Documento' : 'Enviar Assinado'}
                </button>
              </>
            )}
            {freq.documentoAssinado && (
              <button
                onClick={handleVerDocumento}
                className="flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-lg border border-purple-300 text-purple-700 bg-white text-xs font-semibold hover:bg-purple-50 transition-colors min-h-[44px]">
                <Download size={12} /> Ver Documento
              </button>
            )}
          </div>
        </div>
      )}

      {/* Formulário de novo plantão */}
      {adicionando && (
        <PlantaoFormPanel
          tomadorId={freq.tomadorId}
          tipoMedico={freq.tipoMedico}
          onSave={handleAdd}
          onCancel={() => setAdicionando(false)}
        />
      )}

      {/* Estado vazio */}
      {freq.itens.length === 0 && !adicionando && (
        <div className="px-4 py-10 text-center text-xs text-ds-light">
          <FileText size={28} className="mx-auto mb-2 opacity-20" />
          <p>Nenhum plantão lançado ainda.</p>
          {!isFaturada && <p className="mt-1">Toque em "Adicionar" para começar.</p>}
        </div>
      )}

      {freq.itens.length > 0 && (
        <>
          {/* ── Mobile: cards de plantão (telas < sm) ── */}
          <div className="sm:hidden divide-y divide-ds-border">
            {freq.itens.map(item => (
              <div key={item.id} className="p-4">
                <div className="flex items-start gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <span className="text-sm font-bold text-ds-text whitespace-nowrap">
                        {formatDate(item.dataExecucao)}
                      </span>
                      {item.modalidadeNome && (
                        <span className="text-xs font-semibold text-ds-mid">{item.modalidadeNome}</span>
                      )}
                    </div>
                    {item.modalidadeTurno && (
                      <p className="text-[11px] text-ds-light mt-0.5">
                        {item.modalidadeTurno} · {item.modalidadeHorario}
                      </p>
                    )}
                    {item.horasTrabalhadas != null && (
                      <p className="text-[11px] text-teal-600 font-medium mt-0.5">
                        {fmtQtd(item.horasTrabalhadas)}h lançadas
                        {item.horaInicio && item.horaFim && ` (${item.horaInicio.slice(0, 5)} às ${item.horaFim.slice(0, 5)})`}
                      </p>
                    )}
                    {item.ocorrenciaNome && (
                      <p className="text-[11px] text-teal-600 font-medium mt-0.5">
                        {item.ocorrenciaNome}
                        {!!item.ocorrenciaValorCentavos && ` +${formatBRL(item.ocorrenciaValorCentavos)}`}
                      </p>
                    )}
                    {item.ocorrencia && (
                      <p className="text-[11px] text-ds-mid italic mt-1">"{item.ocorrencia}"</p>
                    )}
                    <div className="flex flex-wrap gap-x-4 gap-y-0.5 mt-2 text-[11px] text-ds-light">
                      <span>Valor: {formatBRL(item.valorUnitarioCentavos)}</span>
                      {item.deslocamentoCentavos > 0 && (
                        <span>Desl.: {formatBRL(item.deslocamentoCentavos)}</span>
                      )}
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-1.5 shrink-0">
                    <p className="text-sm font-black text-ds-text tabular-nums">
                      {formatBRL(item.totalItemCentavos)}
                    </p>
                    {!isFaturada && (
                      <button onClick={() => handleRemove(item.id)} disabled={removendo === item.id}
                        className="p-2 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors -mr-1">
                        {removendo === item.id
                          ? <Loader2 size={14} className="animate-spin" />
                          : <Trash2 size={14} />
                        }
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* ── Desktop: tabela (sm+) ── */}
          <div className="hidden sm:block overflow-x-auto">
            <table className="w-full min-w-[640px]">
              <thead>
                <tr className="border-b border-ds-border">
                  {['Data', 'Modalidade', 'Ocorrência', 'Valor Unit.', 'Deslocamento', 'Total', ''].map(h => (
                    <th key={h} className="px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-ds-border">
                {freq.itens.map(item => (
                  <tr key={item.id} className="hover:bg-white/60 transition-colors">
                    <td className="px-3 py-2.5 text-xs font-medium text-ds-text whitespace-nowrap">
                      {formatDate(item.dataExecucao)}
                    </td>
                    <td className="px-3 py-2.5">
                      <p className="text-xs font-semibold text-ds-text">{item.modalidadeNome ?? '—'}</p>
                      {item.modalidadeTurno && (
                        <p className="text-[10px] text-ds-light">{item.modalidadeTurno} · {item.modalidadeHorario}</p>
                      )}
                      {item.horasTrabalhadas != null && (
                        <p className="text-[10px] text-teal-600 font-medium">
                          {fmtQtd(item.horasTrabalhadas)}h lançadas
                          {item.horaInicio && item.horaFim && ` (${item.horaInicio.slice(0, 5)} às ${item.horaFim.slice(0, 5)})`}
                        </p>
                      )}
                    </td>
                    <td className="px-3 py-2.5 text-xs text-ds-mid">
                      {item.ocorrenciaNome && (
                        <p className="text-ds-text font-medium">
                          {item.ocorrenciaNome}
                          {!!item.ocorrenciaValorCentavos && (
                            <span className="text-green-600 font-bold"> +{formatBRL(item.ocorrenciaValorCentavos)}</span>
                          )}
                        </p>
                      )}
                      {item.ocorrencia && <p className={item.ocorrenciaNome ? 'text-[10px] italic' : ''}>{item.ocorrencia}</p>}
                      {!item.ocorrenciaNome && !item.ocorrencia && '—'}
                    </td>
                    <td className="px-3 py-2.5 text-xs tabular-nums text-right text-ds-mid">
                      {formatBRL(item.valorUnitarioCentavos)}
                    </td>
                    <td className="px-3 py-2.5 text-xs tabular-nums text-right text-ds-mid">
                      {item.deslocamentoCentavos > 0 ? formatBRL(item.deslocamentoCentavos) : '—'}
                    </td>
                    <td className="px-3 py-2.5 text-xs tabular-nums font-bold text-right text-ds-text">
                      {formatBRL(item.totalItemCentavos)}
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      {!isFaturada && (
                        <button onClick={() => handleRemove(item.id)} disabled={removendo === item.id}
                          className="p-1 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors">
                          {removendo === item.id
                            ? <Loader2 size={13} className="animate-spin" />
                            : <Trash2 size={13} />
                          }
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}

// ─── Card de frequência ───────────────────────────────────────────────────────

function FrequenciaCard({
  freq, tomadores, perfil, onAtualizar,
}: {
  freq: FrequenciaMedicaResp
  tomadores: Tomador[]
  perfil: PerfilMedico
  onAtualizar: (f: FrequenciaMedicaResp) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const tomador = tomadores.find(t => t.id === freq.tomadorId)
  const tomadorNome = tomador?.razaoSocialNome ?? '—'

  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 px-4 sm:px-5 py-4 hover:bg-ds-surface/50 transition-colors text-left min-h-[72px]"
      >
        <div className={`w-2.5 h-2.5 rounded-full shrink-0 ${
          freq.status === 'FATURADA'              ? 'bg-green-500'
          : freq.status === 'RASCUNHO'            ? 'bg-gray-300'
          : freq.status === 'AGUARDANDO_ASSINATURA' ? 'bg-yellow-400'
          : 'bg-primary'
        }`} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <p className="text-sm font-bold text-ds-text">{formatCompetencia(freq.competencia)}</p>
            <span className={`inline-flex px-2 py-0.5 rounded-md text-[10px] font-bold whitespace-nowrap ${STATUS_CLS[freq.status] ?? 'bg-gray-100 text-gray-500'}`}>
              {STATUS_LABEL[freq.status] ?? freq.status}
            </span>
          </div>
          <div className="flex items-center gap-2 mt-0.5 flex-wrap">
            <p className="text-xs text-ds-mid truncate max-w-[160px] sm:max-w-xs">{tomadorNome}</p>
            {freq.servicoOperacionalNome && (
              <p className="text-xs text-ds-light truncate">· {freq.servicoOperacionalNome}</p>
            )}
          </div>
        </div>
        <div className="text-right shrink-0 ml-1">
          <p className="text-sm font-black text-ds-text tabular-nums">{formatBRL(freq.totalValorCentavos)}</p>
          <p className="text-[10px] text-ds-light">{freq.itens.length} plantão{freq.itens.length !== 1 ? 'ões' : ''}</p>
        </div>
        <ChevronRight size={16} className={`shrink-0 text-ds-light transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </button>

      {expanded && (
        // Padding menor no mobile para aproveitar mais a tela
        <div className="border-t border-ds-border p-2 sm:p-4">
          <FrequenciaItensPanel freq={freq} tomadorNome={tomadorNome} perfil={perfil} onAtualizar={onAtualizar} />
        </div>
      )}
    </div>
  )
}

// ─── Página ───────────────────────────────────────────────────────────────────

export function PortalFrequenciaPage() {
  const navigate       = useNavigate()
  const [perfil,       setPerfil]       = useState<PerfilMedico | null>(null)
  const [tomadores,    setTomadores]    = useState<Tomador[]>([])
  const [tomadoresAlocados, setTomadoresAlocados] = useState<Tomador[]>([])
  const [frequencias,  setFrequencias]  = useState<FrequenciaMedicaResp[]>([])
  const [loading,      setLoading]      = useState(true)
  const [initErr,      setInitErr]      = useState<string | null>(null)
  const [showNova,     setShowNova]     = useState(false)
  const [filtroComp,   setFiltroComp]   = useState('')
  const [filtroStatus, setFiltroStatus] = useState('')
  const [sucesso,      setSucesso]      = useState(false)

  const carregar = useCallback(async (medicoId: string) => {
    const data = await frequenciasApi.listar({ medicoId })
    setFrequencias(data)
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
        // tomadores (completo) alimenta o lookup de nome no histórico — inclui tomadores dos
        // quais o médico já foi desalocado, mas que ainda aparecem em frequências antigas.
        // tomadoresAlocados (EPIC-15.16) alimenta só o formulário de Nova Frequência, restrito
        // aos tomadores onde o médico atua atualmente.
        setTomadores(t)
        const idsAlocados = new Set(alocados.map(a => a.id))
        setTomadoresAlocados(t.filter(tom => idsAlocados.has(tom.id)))
        await carregar(p.id)
      } catch (e) {
        setInitErr(e instanceof Error ? e.message : 'Erro ao carregar')
      } finally { setLoading(false) }
    }
    init()
  }, [carregar])

  function handleCriada(f: FrequenciaMedicaResp) {
    setFrequencias(prev => [f, ...prev])
    setShowNova(false)
    setSucesso(true)
    setTimeout(() => setSucesso(false), 4000)
  }

  function handleAtualizar(f: FrequenciaMedicaResp) {
    setFrequencias(prev => prev.map(x => x.id === f.id ? f : x))
  }

  const filtradas = frequencias.filter(f => {
    const compOk   = !filtroComp   || f.competencia === filtroComp
    const statusOk = !filtroStatus || f.status === filtroStatus
    return compOk && statusOk
  })

  const tomadoresSorted = [...tomadores].sort((a, b) => a.razaoSocialNome.localeCompare(b.razaoSocialNome))
  const tomadoresAlocadosSorted = [...tomadoresAlocados].sort((a, b) => a.razaoSocialNome.localeCompare(b.razaoSocialNome))
  const temFiltro = filtroComp || filtroStatus

  if (loading) return <div className="flex justify-center py-16"><Spinner size="lg" /></div>
  if (initErr)  return <div className="p-4"><Alert variant="error">{initErr}</Alert></div>

  return (
    <div className="max-w-4xl mx-auto space-y-5 pb-8">

      {/* Header — botão vira só ícone no mobile */}
      <div className="flex items-center gap-3">
        <button onClick={() => navigate('/portal/dashboard')}
          className="p-2 rounded-lg hover:bg-ds-input text-ds-light hover:text-ds-mid transition-colors min-h-[44px] min-w-[44px] flex items-center justify-center">
          <ArrowLeft size={18} />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-black text-ds-text">Minhas Frequências</h1>
          <p className="text-sm text-ds-light mt-0.5 hidden sm:block">Plantões lançados por competência e setor</p>
        </div>
        <button
          onClick={() => setShowNova(true)}
          className="flex items-center justify-center gap-2 px-3 sm:px-4 py-2.5 rounded-xl bg-primary text-white text-sm font-bold hover:bg-primary-700 transition-colors shrink-0 min-h-[44px]">
          <Plus size={16} />
          <span className="hidden sm:inline">Nova Frequência</span>
        </button>
      </div>

      {/* Identidade do médico */}
      {perfil && (
        <div className="flex items-center gap-3 bg-primary-50 border border-primary/20 rounded-xl px-4 py-3">
          <div className="w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center text-primary font-black text-sm shrink-0">
            {perfil.nome.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0">
            <p className="text-sm font-bold text-primary-700 truncate">{perfil.nome}</p>
            <p className="text-xs text-primary-600">CRM {perfil.crm}/{perfil.crmUf}{perfil.especialidade ? ` · ${perfil.especialidade}` : ''}</p>
          </div>
        </div>
      )}

      {sucesso && (
        <div className="flex items-start gap-3 bg-green-50 border border-green-200 rounded-xl px-4 py-3">
          <CheckCircle2 size={18} className="text-green-600 shrink-0 mt-0.5" />
          <p className="text-sm font-semibold text-green-800">Frequência criada! Toque nela abaixo para adicionar os plantões.</p>
        </div>
      )}

      {/* Filtros — empilhados no mobile, inline no desktop */}
      <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center gap-2 sm:gap-3">
        <select value={filtroComp} onChange={e => setFiltroComp(e.target.value)}
          className="w-full sm:w-auto border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text bg-white focus:outline-none focus:ring-2 focus:ring-primary/30 min-h-[44px]">
          <option value="">Todas as competências</option>
          {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
        </select>
        <select value={filtroStatus} onChange={e => setFiltroStatus(e.target.value)}
          className="w-full sm:w-auto border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text bg-white focus:outline-none focus:ring-2 focus:ring-primary/30 min-h-[44px]">
          <option value="">Todos os status</option>
          {Object.entries(STATUS_LABEL_FULL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <div className="flex items-center justify-between">
          {temFiltro && (
            <button onClick={() => { setFiltroComp(''); setFiltroStatus('') }}
              className="text-sm text-primary hover:text-primary-700 font-semibold transition-colors">
              Limpar filtros
            </button>
          )}
          <span className={`text-xs text-ds-light ${temFiltro ? 'ml-auto sm:ml-0 sm:ml-auto' : 'sm:ml-auto'}`}>
            {filtradas.length} frequência{filtradas.length !== 1 ? 's' : ''}
          </span>
        </div>
      </div>

      {/* Lista */}
      {filtradas.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-ds-light">
          <CalendarDays size={40} className="mb-3 opacity-20" />
          <p className="text-sm font-medium">Nenhuma frequência encontrada</p>
          {frequencias.length === 0 && (
            <p className="text-xs mt-1 text-center px-8">Toque em "+" para registrar sua primeira frequência</p>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {filtradas.map(f => (
            <FrequenciaCard key={f.id} freq={f} tomadores={tomadoresSorted} perfil={perfil!} onAtualizar={handleAtualizar} />
          ))}
        </div>
      )}

      {/* Modal nova frequência */}
      {showNova && perfil && (
        <NovaFrequenciaModal
          tomadores={tomadoresAlocadosSorted}
          perfil={perfil}
          onClose={() => setShowNova(false)}
          onCriada={handleCriada}
        />
      )}
    </div>
  )
}
