import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  AlertCircle, ArrowLeft, CalendarDays, CheckCircle2, ChevronDown, ChevronRight,
  Download, FileText, Loader2, Pencil, Plus, Printer, Trash2, Upload, X,
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

// Diarista também exige horas trabalhadas por lançamento (usadas no acompanhamento semanal,
// PINSAUDE-13.23) — mesma exigência que Plantonista nunca teve.
function precisaHorasTrabalhadas(m: TomadorModalidade | null): boolean {
  return m?.tipo === 'DIARISTA'
}

function fmtQtd(n: number): string {
  return n % 1 === 0 ? String(n) : n.toFixed(1).replace('.', ',')
}

// Pedido do cliente: o lançamento individual dentro de "Minhas Frequências" é chamado de
// "plantão" para Tipo de Escala Plantonista, mas de "frequência" para Diarista — vocabulário
// mais próximo do dia a dia de cada tipo de médico. `tipoMedico === null` (frequência legada,
// sem Tipo de Escala definido) mantém o rótulo antigo ("plantão").
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
  grupo: TomadorGrupoFaturamento | null
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
  const [form, setForm]     = useState<NovaFreqForm>({ tomador: null, grupo: null, setor: null, competencia: COMPETENCIAS[0], tipoMedico: 'PLANTONISTA' })
  const [grupos, setGrupos] = useState<TomadorGrupoFaturamento[]>([])
  const [saving, setSaving] = useState(false)
  const [err, setErr]       = useState<string | null>(null)

  // PINSAUDE-13.26: modalidade (obrigatória) e ocorrência (opcional) escolhidas uma única vez
  // aqui — o formulário de lançamento de plantão não pergunta mais nenhuma das duas.
  const [modalidades,  setModalidades]  = useState<TomadorModalidade[]>([])
  const [modalidade,   setModalidade]   = useState<TomadorModalidade | null>(null)
  const [ocorrencias,  setOcorrencias]  = useState<TomadorOcorrencia[]>([])
  const [ocorrenciaId, setOcorrenciaId] = useState('')

  // Setores atribuídos ao médico logado neste tomador — só populado quando o tomador exige
  // controle de frequência (Tomador.exigeFrequencia). `null` = sem restrição (tomador não usa
  // essa granularidade, ou ainda carregando) → mostra o catálogo completo do grupo, como sempre.
  const [setoresPermitidosIds, setSetoresPermitidosIds] = useState<Set<string> | null>(null)

  // PINSAUDE: Setor Operacional virou catálogo reutilizável entre grupos — o combo de Setor é
  // sempre escopado ao Grupo escolhido (o mesmo setor pode estar em mais de um grupo), e — quando
  // o tomador exige frequência — também restrito aos setores atribuídos ao médico logado.
  const setores = form.grupo
    ? form.grupo.servicosOperacionais.filter(s =>
        s.ativo && (setoresPermitidosIds === null || setoresPermitidosIds.has(s.id)))
    : []

  useEffect(() => {
    setForm(f => ({ ...f, grupo: null, setor: null }))
    if (!form.tomador) { setGrupos([]); return }
    tomadoresApi.listarGrupos(form.tomador.id).then(gs => {
      setGrupos(gs)
      const ativos = gs.filter(g => g.ativo)
      if (ativos.length === 1) setForm(f => ({ ...f, grupo: ativos[0] }))
    }).catch(() => setGrupos([]))
  }, [form.tomador?.id])

  useEffect(() => {
    if (!form.tomador?.exigeFrequencia) { setSetoresPermitidosIds(null); return }
    portalApi.getSetoresDoMedicoNoTomador(form.tomador.id)
      .then(lista => setSetoresPermitidosIds(new Set(lista.map(s => s.id))))
      .catch(() => setSetoresPermitidosIds(new Set()))
  }, [form.tomador?.id, form.tomador?.exigeFrequencia])

  useEffect(() => {
    setModalidade(null)
    setOcorrenciaId('')
    if (!form.tomador) { setModalidades([]); setOcorrencias([]); return }
    tomadoresApi.listarModalidades(form.tomador.id)
      .then(ms => setModalidades(ms.filter(m => m.tipo === form.tipoMedico && m.ativo)))
      .catch(() => setModalidades([]))
    tomadoresApi.listarOcorrencias(form.tomador.id)
      .then(os => setOcorrencias(os.filter(o => o.ativo)))
      .catch(() => setOcorrencias([]))
  }, [form.tomador?.id, form.tipoMedico])

  // Ajuste pós-implantação: modalidade (e ocorrência) só são fixadas na frequência para
  // Diarista — Plantonista volta a escolher isso a cada plantão lançado, podendo ter
  // turnos/modalidades diferentes dentro da mesma frequência (ver CLAUDE.md).
  const isDiarista = form.tipoMedico === 'DIARISTA'

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.tomador || !form.grupo || !form.setor || (isDiarista && !modalidade)) return
    setSaving(true); setErr(null)
    try {
      const req: FrequenciaMedicaRequest = {
        tomadorId: form.tomador.id,
        medicoId: perfil.id,
        grupoId: form.grupo.id,
        servicoOperacionalId: form.setor.id,
        competencia: form.competencia,
        tipoMedico: form.tipoMedico,
        modalidadeId: isDiarista ? modalidade?.id : undefined,
        ocorrenciaId: isDiarista ? (ocorrenciaId || undefined) : undefined,
      }
      const criada = await frequenciasApi.criar(req)
      onCriada(criada)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erro ao criar')
    } finally { setSaving(false) }
  }

  const canSave = !!form.tomador && !!form.grupo && !!form.setor && (!isDiarista || !!modalidade)

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
                onChange={t => setForm(f => ({ ...f, tomador: t, grupo: null, setor: null }))}
                getLabel={t => t.razaoSocialNome + (t.municipio ? ` — ${t.municipio}` : '')}
              />
            )}

            <Dropdown
              label="Grupo de Faturamento *"
              placeholder={!form.tomador ? 'Selecione o tomador primeiro...' : grupos.length === 0 ? 'Nenhum grupo cadastrado' : 'Selecione o grupo...'}
              items={grupos.filter(g => g.ativo)}
              value={form.grupo}
              onChange={g => setForm(f => ({ ...f, grupo: g, setor: null }))}
              getLabel={g => g.nome}
              disabled={!form.tomador || grupos.length === 0}
            />

            <Dropdown
              label="Setor Operacional *"
              placeholder={
                !form.grupo ? 'Selecione o grupo primeiro...'
                : setores.length > 0 ? 'Selecione o setor...'
                : setoresPermitidosIds !== null ? 'Você não está alocado a nenhum setor deste grupo'
                : 'Nenhum setor cadastrado neste grupo'
              }
              items={setores}
              value={form.setor}
              onChange={s => setForm(f => ({ ...f, setor: s }))}
              getLabel={s => s.nome}
              disabled={!form.grupo || setores.length === 0}
            />
            {form.grupo && setores.length === 0 && setoresPermitidosIds !== null && (
              <p className="text-xs text-ds-light -mt-2">
                Este tomador exige controle de frequência — fale com a operação para atribuir os setores em que você atua aqui.
              </p>
            )}

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

            {/* Modalidade — só para Diarista (PINSAUDE-13.26). Escolhida uma única vez aqui;
                todo lançamento desta frequência sempre usará esta modalidade. Plantonista não
                escolhe modalidade aqui — cada plantão lançado escolhe a sua própria (turnos
                diferentes dentro da mesma frequência são permitidos). */}
            {isDiarista ? (
              <div>
                <Dropdown
                  label="Modalidade *"
                  placeholder={
                    !form.tomador ? 'Selecione o tomador primeiro...'
                    : modalidades.length === 0 ? 'Nenhuma modalidade Diarista cadastrada'
                    : 'Selecione a modalidade...'
                  }
                  items={modalidades}
                  value={modalidade}
                  onChange={setModalidade}
                  getLabel={m => `${m.nome} — ${detalheModalidade(m)}`}
                  disabled={!form.tomador || modalidades.length === 0}
                />
                <p className="mt-1 text-[11px] text-ds-light">
                  Toda frequência lançada aqui usará esta modalidade.
                </p>
              </div>
            ) : (
              <p className="text-[11px] text-ds-light bg-ds-input/40 rounded-lg px-3 py-2">
                A modalidade de cada plantão é escolhida no momento do lançamento — turnos/modalidades diferentes podem ser lançados dentro desta mesma frequência.
              </p>
            )}

            {/* Ocorrência (opcional, PINSAUDE-13.26) — só para Diarista, mesmo motivo acima.
                Com uma única ocorrência cadastrada para o tomador, um checkbox simples substitui
                o select (menos fricção que abrir um dropdown pra escolher a única opção
                disponível); com 2+ opções, mantém o select de sempre. */}
            {isDiarista && (ocorrencias.length === 1 ? (
              <label className="flex items-center gap-2.5 cursor-pointer group">
                <input
                  type="checkbox"
                  checked={ocorrenciaId === ocorrencias[0].id}
                  onChange={e => setOcorrenciaId(e.target.checked ? ocorrencias[0].id : '')}
                  className="w-4 h-4 rounded border-ds-border text-primary focus:ring-primary/30 cursor-pointer"
                />
                <span className="text-sm text-ds-text group-hover:text-primary transition-colors">
                  Ocorrência: <span className="font-semibold">{ocorrencias[0].nome}</span>
                </span>
              </label>
            ) : (
              <div>
                <label className="block text-xs font-bold text-ds-mid mb-1">
                  Ocorrência do catálogo <span className="font-normal text-ds-light">(opcional)</span>
                </label>
                <select value={ocorrenciaId} onChange={e => setOcorrenciaId(e.target.value)}
                  disabled={!form.tomador || ocorrencias.length === 0}
                  className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px] disabled:opacity-50">
                  <option value="">Nenhuma</option>
                  {ocorrencias.map(o => (
                    <option key={o.id} value={o.id}>{o.nome}</option>
                  ))}
                </select>
              </div>
            ))}
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
  tomadorId, tipoMedico, modalidadeFixa, ocorrenciaFixaNome, onSave, onCancel,
}: {
  tomadorId: string
  tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null   // filtra a lista de modalidades (PINSAUDE-13.25)
  // PINSAUDE-13.26: quando a frequência já tem modalidade/ocorrência fixa (escolhida na
  // criação), o formulário não pergunta mais nenhuma das duas. null = frequência legada.
  modalidadeFixa: TomadorModalidade | null
  ocorrenciaFixaNome: string | null
  onSave: (req: FrequenciaItemRequest) => Promise<void>
  onCancel: () => void
}) {
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [modalidade,  setModalidade]  = useState<TomadorModalidade | null>(modalidadeFixa)
  const [data,        setData]        = useState(new Date().toISOString().slice(0, 10))
  const [ocorrencia,  setOcorrencia]  = useState('')
  const [ocorrencias, setOcorrencias] = useState<TomadorOcorrencia[]>([])
  const [ocorrenciaId, setOcorrenciaId] = useState('')
  const [horaInicio,  setHoraInicio]  = useState('')
  const [horaFim,     setHoraFim]     = useState('')
  const [saving,      setSaving]      = useState(false)
  const [err,         setErr]         = useState<string | null>(null)

  useEffect(() => {
    if (modalidadeFixa) { setModalidade(modalidadeFixa); return } // PINSAUDE-13.26: nada pra buscar
    // PINSAUDE-13.25: só oferece modalidades do mesmo Tipo de Escala da frequência aberta.
    tomadoresApi.listarModalidades(tomadorId)
      .then(ms => {
        const doTipo = tipoMedico ? ms.filter(m => m.tipo === tipoMedico) : ms
        setModalidades(doTipo.filter(m => m.ativo))
      })
      .catch(() => {})
  }, [tomadorId, tipoMedico, modalidadeFixa])

  useEffect(() => {
    if (modalidadeFixa) return // PINSAUDE-13.26: ocorrência também fixa — nada pra buscar
    tomadoresApi.listarOcorrencias(tomadorId)
      .then(os => setOcorrencias(os.filter(o => o.ativo)))
      .catch(() => {})
  }, [tomadorId, modalidadeFixa])

  const precisaHoras = precisaHorasTrabalhadas(modalidade)

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
      setErr(e instanceof Error ? e.message : 'Erro ao adicionar')
    } finally { setSaving(false) }
  }

  const horasPreview = precisaHoras ? calcularHorasEntrePeriodo(horaInicio, horaFim) : null

  return (
    <div className="mx-3 sm:mx-4 mb-3 rounded-xl border border-primary/20 bg-primary-50/40 p-4">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-bold text-primary flex items-center gap-1.5">
          <Plus size={12} /> {tipoMedico === 'DIARISTA' ? 'Nova Frequência' : 'Novo Plantão'}
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
        {modalidadeFixa ? (
          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Modalidade</label>
            <div className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text bg-ds-input/40 min-h-[44px] flex items-center truncate">
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

      {/* Detalhe da modalidade — sem valores financeiros na visão do médico (Portal) */}
      {modalidade && (
        <div className="bg-white rounded-lg px-3 py-2.5 mb-3 text-xs border border-ds-border/60">
          <span className="text-ds-light">{detalheModalidade(modalidade)}</span>
        </div>
      )}

      {/* Ocorrência do catálogo — PINSAUDE-13.26: fixa na frequência (escolhida na criação),
          nunca mais perguntada por lançamento. Sem seletor aqui; só um aviso informativo (sem
          valor — visão do médico não exibe valores financeiros). */}
      {modalidadeFixa ? (
        ocorrenciaFixaNome && (
          <p className="mb-3 text-xs text-ds-mid">
            Ocorrência aplicada nesta frequência: <span className="font-semibold text-ds-text">{ocorrenciaFixaNome}</span>
          </p>
        )
      ) : (
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
      )}

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
          {saving
            ? <><Loader2 size={12} className="animate-spin" />Adicionando...</>
            : <><Plus size={12} />{tipoMedico === 'DIARISTA' ? 'Adicionar Frequência' : 'Adicionar Plantão'}</>}
        </button>
      </div>
    </div>
  )
}

// ─── Painel de itens — mobile-first ──────────────────────────────────────────

function FrequenciaItensPanel({
  freq, tomadorNome, perfil, onAtualizar, onExcluida,
}: {
  freq: FrequenciaMedicaResp
  tomadorNome: string
  perfil: PerfilMedico
  onAtualizar: (f: FrequenciaMedicaResp) => void
  onExcluida: (id: string) => void
}) {
  const { user } = useAuth()
  const [adicionando,  setAdicionando]  = useState(false)
  const [removendo,    setRemovendo]    = useState<string | null>(null)
  const [gerandoPdf,   setGerandoPdf]   = useState(false)
  const [uploadingDoc, setUploadingDoc] = useState(false)
  const [uploadErr,    setUploadErr]    = useState<string | null>(null)
  const [confirmExcluir, setConfirmExcluir] = useState(false)
  const [excluindo,    setExcluindo]    = useState(false)
  const [excluirErr,   setExcluirErr]   = useState<string | null>(null)
  const [itemPage,     setItemPage]     = useState(0)
  const [editandoFrequencia, setEditandoFrequencia] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const isFaturada = freq.status === 'FATURADA'

  // Plantões lançados — paginados em 5, do mais atual (data) para o mais antigo. O backend
  // retorna em ordem crescente de data (ver FrequenciaService.toResponse), então a ordenação
  // "mais recente primeiro" é feita aqui, só para exibição no Portal.
  const itensOrdenados = [...freq.itens].sort((a, b) =>
    b.dataExecucao.localeCompare(a.dataExecucao) || b.createdAt.localeCompare(a.createdAt))
  const ITENS_POR_PAGINA = 5
  const totalItemPages = Math.max(1, Math.ceil(itensOrdenados.length / ITENS_POR_PAGINA))
  const itemPageAtual  = Math.min(itemPage, totalItemPages - 1)
  const itensPaginados = itensOrdenados.slice(itemPageAtual * ITENS_POR_PAGINA, (itemPageAtual + 1) * ITENS_POR_PAGINA)

  useEffect(() => { setItemPage(0) }, [freq.id])

  // PINSAUDE-13.26: modalidade/ocorrência fixas na criação da frequência — quando presentes, o
  // formulário de lançamento de plantão não pergunta mais nenhuma das duas.
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

  async function handleAdd(req: FrequenciaItemRequest) {
    await frequenciasApi.adicionarItem(freq.id, req)
    const atualizada = await frequenciasApi.buscarPorId(freq.id)
    onAtualizar(atualizada)
    setAdicionando(false)
    setItemPage(0) // volta pra primeira página pra garantir que o plantão recém-lançado fique visível
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
          {freq.itens.length} {itemLabel(freq.tipoMedico, freq.itens.length)} {itemAgree(freq.tipoMedico, freq.itens.length, 'lançad')}
        </p>
        <div className="flex items-center gap-2 ml-auto">
          {/* Competência e Setor Operacional são editáveis a qualquer momento antes de
              faturada. Tomador, Tipo de Escala, Modalidade e Ocorrência permanecem fixos —
              se algum deles estiver errado, o jeito continua sendo excluir e criar de novo. */}
          {!isFaturada && (
            <button onClick={() => setEditandoFrequencia(true)}
              title="Editar competência e setor"
              className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs font-semibold hover:bg-ds-input transition-colors min-h-[40px]">
              <Pencil size={12} />
              <span className="hidden sm:inline">Editar</span>
            </button>
          )}
          {!isFaturada && (
            <button onClick={() => setConfirmExcluir(true)} disabled={excluindo}
              title="Excluir frequência"
              className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-red-200 text-red-600 text-xs font-bold hover:bg-red-50 transition-colors disabled:opacity-50 min-h-[40px]">
              <Trash2 size={12} />
              <span className="hidden sm:inline">Excluir</span>
            </button>
          )}
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
              <span className="hidden sm:inline">
                {freq.tipoMedico === 'DIARISTA' ? 'Adicionar Frequência' : 'Adicionar Plantão'}
              </span>
            </button>
          )}
        </div>
      </div>

      {/* ── Confirmação de exclusão (PINSAUDE-13.26) ─────────────────────── */}
      {confirmExcluir && (
        <div className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-black/50 p-4">
          <div className="bg-white rounded-t-2xl sm:rounded-2xl shadow-2xl w-full sm:max-w-sm p-6">
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
            {excluirErr && <p className="mb-4 text-xs text-red-600">{excluirErr}</p>}
            <div className="flex gap-3">
              <button type="button" onClick={() => setConfirmExcluir(false)} disabled={excluindo}
                className="flex-1 px-4 py-2.5 rounded-xl border border-ds-border text-sm font-semibold text-ds-mid hover:bg-ds-surface transition-colors disabled:opacity-50">
                Cancelar
              </button>
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
          modalidadeFixa={modalidadeFixa}
          ocorrenciaFixaNome={ocorrenciaFixaNome}
          onSave={handleAdd}
          onCancel={() => setAdicionando(false)}
        />
      )}

      {/* Estado vazio */}
      {freq.itens.length === 0 && !adicionando && (
        <div className="px-4 py-10 text-center text-xs text-ds-light">
          <FileText size={28} className="mx-auto mb-2 opacity-20" />
          <p>Nenhum{freq.tipoMedico === 'DIARISTA' ? 'a' : ''} {itemLabel(freq.tipoMedico, 1)} {itemAgree(freq.tipoMedico, 1, 'lançad')} ainda.</p>
          {!isFaturada && <p className="mt-1">Toque em "Adicionar" para começar.</p>}
        </div>
      )}

      {freq.itens.length > 0 && (
        <>
          {/* ── Mobile: cards de plantão (telas < sm) — paginados, mais atual primeiro ── */}
          <div className="sm:hidden divide-y divide-ds-border">
            {itensPaginados.map(item => (
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
                      </p>
                    )}
                    {item.ocorrencia && (
                      <p className="text-[11px] text-ds-mid italic mt-1">"{item.ocorrencia}"</p>
                    )}
                  </div>
                  {!isFaturada && (
                    <button onClick={() => handleRemove(item.id)} disabled={removendo === item.id}
                      className="p-2 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors -mr-1 shrink-0">
                      {removendo === item.id
                        ? <Loader2 size={14} className="animate-spin" />
                        : <Trash2 size={14} />
                      }
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* ── Desktop: tabela (sm+) — sem colunas de valor (visão do médico não exibe
              valores financeiros, ver CLAUDE.md) ── */}
          <div className="hidden sm:block overflow-x-auto">
            <table className="w-full min-w-[480px]">
              <thead>
                <tr className="border-b border-ds-border">
                  {['Data', 'Modalidade', 'Ocorrência', ''].map(h => (
                    <th key={h} className="px-3 py-2 text-[10px] font-bold text-ds-light uppercase tracking-wider text-left">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-ds-border">
                {itensPaginados.map(item => (
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
                        <p className="text-ds-text font-medium">{item.ocorrenciaNome}</p>
                      )}
                      {item.ocorrencia && <p className={item.ocorrenciaNome ? 'text-[10px] italic' : ''}>{item.ocorrencia}</p>}
                      {!item.ocorrenciaNome && !item.ocorrencia && '—'}
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

          {/* Paginação dos plantões lançados — 5 por página, mais atual primeiro */}
          {totalItemPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3 text-xs text-ds-light border-t border-ds-border">
              <span>
                Página <strong className="text-ds-mid">{itemPageAtual + 1}</strong> de{' '}
                <strong className="text-ds-mid">{totalItemPages}</strong>
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setItemPage(p => p - 1)}
                  disabled={itemPageAtual === 0}
                  className="px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs font-semibold hover:bg-ds-input transition-colors disabled:opacity-40 disabled:cursor-not-allowed min-h-[40px]"
                >
                  Anterior
                </button>
                <button
                  onClick={() => setItemPage(p => p + 1)}
                  disabled={itemPageAtual >= totalItemPages - 1}
                  className="px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs font-semibold hover:bg-ds-input transition-colors disabled:opacity-40 disabled:cursor-not-allowed min-h-[40px]"
                >
                  Próximo
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

// ─── Modal de edição de Competência + Setor — bottom-sheet no mobile ──────────

// Só Competência e Setor Operacional são editáveis pós-criação (Tomador, Tipo de Escala,
// Modalidade e Ocorrência permanecem fixos). Bloqueado só quando FATURADA (garantido pelo
// caller, que só renderiza este modal nesse caso).
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

  // A competência atual pode estar fora da janela de 12 meses de COMPETENCIAS (frequência
  // antiga) — injeta na lista pra não sumir do <select> quando o modal abre.
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
    <div className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-t-2xl sm:rounded-2xl shadow-2xl w-full sm:max-w-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <p className="text-sm font-bold text-ds-text">Editar Frequência</p>
          <button type="button" onClick={onClose}
            className="p-2 rounded-lg text-ds-light hover:bg-ds-input transition-colors min-h-[44px] min-w-[44px] flex items-center justify-center">
            <X size={16} />
          </button>
        </div>
        <form onSubmit={handleSubmit}>
          {err && <div className="mb-3"><Alert variant="error" onClose={() => setErr(null)}>{err}</Alert></div>}
          <div className="mb-3">
            <label className="block text-xs font-bold text-ds-mid mb-1">Competência *</label>
            <select value={competencia} onChange={e => setCompetencia(e.target.value)}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]">
              {competenciaOptions.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
            </select>
          </div>
          <div className="mb-3">
            <label className="block text-xs font-bold text-ds-mid mb-1">Grupo de Faturamento *</label>
            <select value={grupoId} onChange={e => { setGrupoId(e.target.value); setSetorId('') }}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px]">
              <option value="">Selecione o grupo...</option>
              {gruposOptions.map(g => <option key={g.id} value={g.id}>{g.nome}{!g.ativo ? ' (inativo)' : ''}</option>)}
            </select>
          </div>
          <div className="mb-4">
            <label className="block text-xs font-bold text-ds-mid mb-1">Setor Operacional *</label>
            <select value={setorId} onChange={e => setSetorId(e.target.value)} disabled={!grupoId}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white min-h-[44px] disabled:opacity-50">
              <option value="">Selecione o setor...</option>
              {setores.map(s => <option key={s.id} value={s.id}>{s.nome}{!s.ativo ? ' (inativo)' : ''}</option>)}
            </select>
          </div>
          <p className="text-[11px] text-ds-light mb-4">
            Tomador, Tipo de Escala, Modalidade e Ocorrência não podem ser alterados aqui — se
            algum deles estiver errado, exclua esta frequência e crie uma nova.
          </p>
          <div className="flex gap-3">
            <button type="button" onClick={onClose} disabled={saving}
              className="flex-1 px-4 py-2.5 rounded-xl border border-ds-border text-sm font-semibold text-ds-mid hover:bg-ds-surface transition-colors disabled:opacity-50">
              Cancelar
            </button>
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

// ─── Card de frequência ───────────────────────────────────────────────────────

function FrequenciaCard({
  freq, tomadores, perfil, onAtualizar, onExcluida,
}: {
  freq: FrequenciaMedicaResp
  tomadores: Tomador[]
  perfil: PerfilMedico
  onAtualizar: (f: FrequenciaMedicaResp) => void
  onExcluida: (id: string) => void
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
          <p className="text-sm font-bold text-ds-text tabular-nums">{freq.itens.length} {itemLabel(freq.tipoMedico, freq.itens.length)}</p>
        </div>
        <ChevronRight size={16} className={`shrink-0 text-ds-light transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </button>

      {expanded && (
        // Padding menor no mobile para aproveitar mais a tela
        <div className="border-t border-ds-border p-2 sm:p-4">
          <FrequenciaItensPanel freq={freq} tomadorNome={tomadorNome} perfil={perfil} onAtualizar={onAtualizar} onExcluida={onExcluida} />
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
  const [page,         setPage]         = useState(0)
  const PAGE_SIZE = 5

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
    setPage(0) // volta pra primeira página pra garantir que a nova frequência fique visível
    setTimeout(() => setSucesso(false), 4000)
  }

  function handleAtualizar(f: FrequenciaMedicaResp) {
    setFrequencias(prev => prev.map(x => x.id === f.id ? f : x))
  }

  // PINSAUDE-13.26: excluir frequência (só disponível em Rascunho) — permite corrigir uma
  // escolha errada de modalidade/ocorrência (não editável depois de criada) apagando e criando de novo.
  function handleExcluida(id: string) {
    setFrequencias(prev => prev.filter(f => f.id !== id))
  }

  // PINSAUDE-13.26 (refinamento): ordenado da competência mais atual para a mais antiga
  // (competencia no formato YYYY-MM já é comparável lexicograficamente) — createdAt desc como
  // desempate para frequências da mesma competência.
  const filtradas = useMemo(() => {
    return frequencias
      .filter(f => {
        const compOk   = !filtroComp   || f.competencia === filtroComp
        const statusOk = !filtroStatus || f.status === filtroStatus
        return compOk && statusOk
      })
      .sort((a, b) => b.competencia.localeCompare(a.competencia) || b.createdAt.localeCompare(a.createdAt))
  }, [frequencias, filtroComp, filtroStatus])

  // Paginação — 5 frequências por página
  useEffect(() => { setPage(0) }, [filtroComp, filtroStatus])
  const totalPages = Math.max(1, Math.ceil(filtradas.length / PAGE_SIZE))
  const pageAtual  = Math.min(page, totalPages - 1)
  const paginadas  = filtradas.slice(pageAtual * PAGE_SIZE, (pageAtual + 1) * PAGE_SIZE)

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

      {/* Lista — paginada em 5, mais atual para mais antiga */}
      {filtradas.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-ds-light">
          <CalendarDays size={40} className="mb-3 opacity-20" />
          <p className="text-sm font-medium">Nenhuma frequência encontrada</p>
          {frequencias.length === 0 && (
            <p className="text-xs mt-1 text-center px-8">Toque em "+" para registrar sua primeira frequência</p>
          )}
        </div>
      ) : (
        <>
          <div className="space-y-3">
            {paginadas.map(f => (
              <FrequenciaCard key={f.id} freq={f} tomadores={tomadoresSorted} perfil={perfil!} onAtualizar={handleAtualizar} onExcluida={handleExcluida} />
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between px-1 pt-1 text-xs text-ds-light">
              <span>
                Página <strong className="text-ds-mid">{pageAtual + 1}</strong> de{' '}
                <strong className="text-ds-mid">{totalPages}</strong>
              </span>
              <div className="flex items-center gap-2">
                <button onClick={() => setPage(p => p - 1)} disabled={pageAtual === 0}
                  className="px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs font-semibold hover:bg-ds-input transition-colors disabled:opacity-40 disabled:cursor-not-allowed min-h-[40px]">
                  Anterior
                </button>
                <button onClick={() => setPage(p => p + 1)} disabled={pageAtual >= totalPages - 1}
                  className="px-3 py-2 rounded-lg border border-ds-border text-ds-mid text-xs font-semibold hover:bg-ds-input transition-colors disabled:opacity-40 disabled:cursor-not-allowed min-h-[40px]">
                  Próximo
                </button>
              </div>
            </div>
          )}
        </>
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
