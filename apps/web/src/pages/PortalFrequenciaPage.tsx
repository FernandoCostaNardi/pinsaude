import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ArrowLeft, CalendarDays, CheckCircle2, ChevronDown, ChevronRight,
  Download, FileText, Loader2, Plus, Printer, Trash2, Upload, X,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import { portalApi, PerfilMedico } from '../api/portalApi'
import { tomadoresApi, Tomador, TomadorGrupoFaturamento, TomadorModalidade } from '../api/tomadoresApi'
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

const STATUS_LABEL: Record<string, string> = {
  RASCUNHO:            'Rascunho',
  PDF_GERADO:          'PDF Gerado',
  AGUARDANDO_ASSINATURA: 'Aguardando Assinatura',
  ASSINADA_RECEBIDA:   'Assinada Recebida',
  ENVIADA_TOMADOR:     'Enviada ao Tomador',
  FATURADA:            'Faturada',
}

const STATUS_CLS: Record<string, string> = {
  RASCUNHO:            'bg-gray-100 text-gray-600',
  PDF_GERADO:          'bg-blue-50 text-blue-700',
  AGUARDANDO_ASSINATURA: 'bg-yellow-50 text-yellow-700',
  ASSINADA_RECEBIDA:   'bg-purple-50 text-purple-700',
  ENVIADA_TOMADOR:     'bg-indigo-50 text-indigo-700',
  FATURADA:            'bg-green-50 text-green-700',
}

const COMPETENCIAS = generateCompetencias()

// ─── Dropdown ─────────────────────────────────────────────────────────────────

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
      <button
        type="button"
        disabled={disabled}
        onClick={() => { setOpen(!open); setQ('') }}
        className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border text-sm transition-colors disabled:opacity-50 ${
          value ? 'border-primary/40 bg-primary-50 text-ds-text font-medium' : 'border-ds-border bg-white text-ds-light'
        }`}
      >
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

// ─── Formulário nova frequência ────────────────────────────────────────────────

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
  const [form, setForm]   = useState<NovaFreqForm>({ tomador: null, setor: null, competencia: COMPETENCIAS[0], tipoMedico: 'PLANTONISTA' })
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">
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
            label="Tomador (Hospital / Clínica) *"
            placeholder="Selecione o tomador..."
            items={tomadores}
            value={form.tomador}
            onChange={t => setForm(f => ({ ...f, tomador: t, setor: null }))}
            getLabel={t => t.razaoSocialNome + (t.municipio ? ` — ${t.municipio}` : '')}
          />

          <Dropdown
            label="Setor Operacional *"
            placeholder={form.tomador ? (setores.length === 0 ? 'Nenhum setor cadastrado' : 'Selecione o setor...') : 'Selecione o tomador primeiro...'}
            items={setores}
            value={form.setor}
            onChange={s => setForm(f => ({ ...f, setor: s }))}
            getLabel={s => s.nome}
            disabled={!form.tomador || setores.length === 0}
          />

          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Competência *</label>
            <select value={form.competencia} onChange={e => setForm(f => ({ ...f, competencia: e.target.value }))}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30">
              {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-ds-mid mb-1">Tipo de Escala *</label>
            <select value={form.tipoMedico}
              onChange={e => setForm(f => ({ ...f, tipoMedico: e.target.value as 'PLANTONISTA' | 'DIARISTA' }))}
              className="w-full border border-ds-border rounded-lg px-3 py-2.5 text-sm text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white">
              <option value="PLANTONISTA">Plantonista</option>
              <option value="DIARISTA">Diarista</option>
            </select>
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

// ─── Adicionar item ────────────────────────────────────────────────────────────

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
          label="" placeholder={modalidades.length === 0 ? 'Sem modalidades' : 'Selecione...'}
          items={modalidades} value={modalidade} onChange={setModalidade}
          getLabel={m => `${m.nome} (${m.turno} · ${m.horario})`}
          disabled={modalidades.length === 0}
        />
        {err && <p className="text-[10px] text-red-600 mt-1">{err}</p>}
      </td>
      <td className="px-3 py-2">
        <input type="text" value={ocorrencia} onChange={e => setOcorrencia(e.target.value)}
          placeholder="(opcional)"
          className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-xs text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30" />
      </td>
      <td className="px-3 py-2 text-xs tabular-nums text-ds-mid text-right">
        {modalidade ? formatBRL(modalidade.valorCentavos) : '—'}
      </td>
      <td className="px-3 py-2 text-xs tabular-nums text-ds-mid text-right">
        {modalidade && modalidade.deslocamentoCentavos > 0 ? formatBRL(modalidade.deslocamentoCentavos) : '—'}
      </td>
      <td className="px-3 py-2 text-xs tabular-nums font-bold text-ds-text text-right">
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

// ─── Painel de itens ──────────────────────────────────────────────────────────

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
      {/* Totalizador */}
      <div className="flex items-center justify-between px-4 py-3 bg-white border-b border-ds-border">
        <p className="text-xs font-bold text-ds-mid">
          {freq.itens.length} plantão{freq.itens.length !== 1 ? 'ões' : ''} lançado{freq.itens.length !== 1 ? 's' : ''}
        </p>
        <div className="flex items-center gap-3">
          <p className="text-xs text-ds-mid">
            Total: <span className="font-black text-ds-text tabular-nums">{formatBRL(freq.totalValorCentavos)}</span>
          </p>
          <button onClick={handleGerarPdf} disabled={gerandoPdf}
            title="Gerar PDF do Relatório de Frequência"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-ds-mid text-xs font-semibold hover:border-primary hover:text-primary hover:bg-primary-50 transition-colors disabled:opacity-50">
            {gerandoPdf ? <Loader2 size={12} className="animate-spin" /> : <Printer size={12} />}
            Gerar PDF
          </button>
          {!isFaturada && (
            <button onClick={() => setAdicionando(true)} disabled={adicionando}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-primary text-white text-xs font-bold hover:bg-primary-700 transition-colors disabled:opacity-50">
              <Plus size={12} /> Adicionar Plantão
            </button>
          )}
        </div>
      </div>

      {/* Documento assinado */}
      {(freq.status === 'AGUARDANDO_ASSINATURA' || freq.documentoAssinado) && (
        <div className="flex items-center gap-3 flex-wrap px-4 py-2.5 border-b border-ds-border bg-yellow-50/60">
          <span className="text-[10px] font-bold text-yellow-800 uppercase tracking-wider">
            Documento Assinado
          </span>
          {uploadErr && <span className="text-xs text-red-600">{uploadErr}</span>}
          {freq.status === 'AGUARDANDO_ASSINATURA' && (
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
                className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-purple-600 text-white text-xs font-bold hover:bg-purple-700 transition-colors disabled:opacity-50">
                {uploadingDoc ? <Loader2 size={11} className="animate-spin" /> : <Upload size={11} />}
                {freq.documentoAssinado ? 'Substituir' : 'Enviar Assinado'}
              </button>
            </>
          )}
          {freq.documentoAssinado && (
            <>
              <button
                onClick={handleVerDocumento}
                className="flex items-center gap-1.5 px-3 py-1 rounded-lg border border-purple-300 text-purple-700 bg-white text-xs font-semibold hover:bg-purple-50 transition-colors">
                <Download size={11} /> Ver Documento
              </button>
              <span className="flex items-center gap-1 text-xs text-green-700 font-semibold">
                <CheckCircle2 size={12} /> Recebido
              </span>
            </>
          )}
        </div>
      )}

      {/* Tabela */}
      <div className="overflow-x-auto">
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
                </td>
                <td className="px-3 py-2.5 text-xs text-ds-mid">{item.ocorrencia ?? '—'}</td>
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

            {adicionando && (
              <AdicionarItemRow
                tomadorId={freq.tomadorId}
                onAdd={handleAdd}
                onCancel={() => setAdicionando(false)}
              />
            )}

            {freq.itens.length === 0 && !adicionando && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-xs text-ds-light">
                  <FileText size={24} className="mx-auto mb-2 opacity-20" />
                  Nenhum plantão lançado ainda.{!isFaturada && ' Clique em "Adicionar Plantão" para começar.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
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
        className="w-full flex items-center gap-4 px-5 py-4 hover:bg-ds-surface/50 transition-colors text-left"
      >
        <div className={`w-2 h-2 rounded-full shrink-0 ${freq.status === 'FATURADA' ? 'bg-green-500' : freq.status === 'RASCUNHO' ? 'bg-gray-300' : 'bg-yellow-400'}`} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <p className="text-sm font-bold text-ds-text">{formatCompetencia(freq.competencia)}</p>
            <span className={`inline-flex px-2 py-0.5 rounded-md text-[10px] font-bold ${STATUS_CLS[freq.status] ?? 'bg-gray-100 text-gray-500'}`}>
              {STATUS_LABEL[freq.status] ?? freq.status}
            </span>
          </div>
          <div className="flex items-center gap-3 mt-0.5 flex-wrap">
            <p className="text-xs text-ds-mid truncate">{tomadorNome}</p>
            {freq.servicoOperacionalNome && (
              <p className="text-xs text-ds-light">· {freq.servicoOperacionalNome}</p>
            )}
            <p className="text-xs text-ds-light">· {freq.tipoMedico ?? '—'}</p>
          </div>
        </div>
        <div className="text-right shrink-0">
          <p className="text-sm font-black text-ds-text tabular-nums">{formatBRL(freq.totalValorCentavos)}</p>
          <p className="text-[10px] text-ds-light">{freq.itens.length} plantão{freq.itens.length !== 1 ? 'ões' : ''}</p>
        </div>
        <ChevronRight size={16} className={`shrink-0 text-ds-light transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </button>

      {expanded && (
        <div className="border-t border-ds-border p-4">
          <FrequenciaItensPanel freq={freq} tomadorNome={tomadorNome} perfil={perfil} onAtualizar={onAtualizar} />
        </div>
      )}
    </div>
  )
}

// ─── Página ───────────────────────────────────────────────────────────────────

export function PortalFrequenciaPage() {
  const navigate     = useNavigate()
  const [perfil,     setPerfil]     = useState<PerfilMedico | null>(null)
  const [tomadores,  setTomadores]  = useState<Tomador[]>([])
  const [frequencias, setFrequencias] = useState<FrequenciaMedicaResp[]>([])
  const [loading,    setLoading]    = useState(true)
  const [initErr,    setInitErr]    = useState<string | null>(null)
  const [showNova,   setShowNova]   = useState(false)
  const [filtroComp, setFiltroComp] = useState('')
  const [filtroStatus, setFiltroStatus] = useState('')
  const [sucesso,    setSucesso]    = useState(false)

  const carregar = useCallback(async (medicoId: string) => {
    const data = await frequenciasApi.listar({ medicoId })
    setFrequencias(data)
  }, [])

  useEffect(() => {
    async function init() {
      try {
        const [p, t] = await Promise.all([portalApi.getPerfil(), tomadoresApi.listar()])
        setPerfil(p)
        setTomadores(t)
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
    const compOk = !filtroComp   || f.competencia === filtroComp
    const statusOk = !filtroStatus || f.status === filtroStatus
    return compOk && statusOk
  })

  const tomadoresSorted = [...tomadores].sort((a, b) => a.razaoSocialNome.localeCompare(b.razaoSocialNome))

  if (loading) return <div className="flex justify-center py-16"><Spinner size="lg" /></div>
  if (initErr)  return <div className="p-6"><Alert variant="error">{initErr}</Alert></div>

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <button onClick={() => navigate('/portal/dashboard')}
          className="p-2 rounded-lg hover:bg-ds-input text-ds-light hover:text-ds-mid transition-colors">
          <ArrowLeft size={18} />
        </button>
        <div className="flex-1">
          <h1 className="text-xl font-black text-ds-text">Minhas Frequências</h1>
          <p className="text-sm text-ds-light mt-0.5">Plantões lançados por competência e setor</p>
        </div>
        <Button onClick={() => setShowNova(true)}>
          <Plus size={15} className="mr-2" /> Nova Frequência
        </Button>
      </div>

      {/* Identidade */}
      {perfil && (
        <div className="flex items-center gap-3 bg-primary-50 border border-primary/20 rounded-xl px-4 py-3">
          <div className="w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center text-primary font-black text-sm">
            {perfil.nome.charAt(0).toUpperCase()}
          </div>
          <div>
            <p className="text-sm font-bold text-primary-700">{perfil.nome}</p>
            <p className="text-xs text-primary-600">CRM {perfil.crm}/{perfil.crmUf}{perfil.especialidade ? ` · ${perfil.especialidade}` : ''}</p>
          </div>
        </div>
      )}

      {sucesso && (
        <div className="flex items-center gap-3 bg-green-50 border border-green-200 rounded-xl px-4 py-3">
          <CheckCircle2 size={18} className="text-green-600 shrink-0" />
          <p className="text-sm font-semibold text-green-800">Frequência criada! Agora adicione os plantões clicando nela abaixo.</p>
        </div>
      )}

      {/* Filtros */}
      <div className="flex items-center gap-3 flex-wrap">
        <select value={filtroComp} onChange={e => setFiltroComp(e.target.value)}
          className="border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
          <option value="">Todas as competências</option>
          {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
        </select>
        <select value={filtroStatus} onChange={e => setFiltroStatus(e.target.value)}
          className="border border-ds-border rounded-lg px-3 py-2 text-sm text-ds-text bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
          <option value="">Todos os status</option>
          {Object.entries(STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        {(filtroComp || filtroStatus) && (
          <button onClick={() => { setFiltroComp(''); setFiltroStatus('') }}
            className="text-xs text-primary hover:text-primary-700 font-semibold transition-colors">
            Limpar filtros
          </button>
        )}
        <span className="ml-auto text-xs text-ds-light">{filtradas.length} frequência{filtradas.length !== 1 ? 's' : ''}</span>
      </div>

      {/* Lista */}
      {filtradas.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-ds-light">
          <CalendarDays size={40} className="mb-3 opacity-20" />
          <p className="text-sm font-medium">Nenhuma frequência encontrada</p>
          {frequencias.length === 0 && (
            <p className="text-xs mt-1">Clique em "Nova Frequência" para começar a lançar plantões</p>
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
          tomadores={tomadoresSorted}
          perfil={perfil}
          onClose={() => setShowNova(false)}
          onCriada={handleCriada}
        />
      )}
    </div>
  )
}
