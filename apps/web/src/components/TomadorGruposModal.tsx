import { useCallback, useEffect, useState } from 'react'
import {
  Plus, Pencil, Trash2, Layers, ChevronDown, ChevronRight,
  Moon, Sun, Loader2, FolderOpen, Tag,
} from 'lucide-react'
import { Modal, Button, Input, Alert, Spinner } from '@pinsaude/ui'
import {
  Tomador,
  TomadorGrupoFaturamento,
  TomadorGrupoFaturamentoRequest,
  TomadorModalidade,
  TomadorModalidadeRequest,
  TomadorOcorrencia,
  TomadorOcorrenciaRequest,
  TomadorServicoOperacionalRequest,
  tomadoresApi,
} from '../api/tomadoresApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function centavosParaBrl(centavos: number): string {
  if (!centavos) return ''
  return (centavos / 100).toLocaleString('pt-BR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function parseCentavos(str: string): number {
  return parseInt(str.replace(/\D/g, '') || '0', 10)
}

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function maskValor(e: React.ChangeEvent<HTMLInputElement>): string {
  const raw = e.target.value.replace(/\D/g, '')
  return centavosParaBrl(parseInt(raw || '0', 10))
}

// Badge do tipo na tabela de modalidades. META é exibida pela unidade de cálculo (hora/dia).
function tipoBadgeInfo(m: TomadorModalidade): { label: string; cls: string } {
  if (m.tipo === 'MENSAL') return { label: 'MENSAL', cls: 'bg-purple-50 text-purple-700' }
  if (m.tipo === 'META') {
    return m.unidadeCalculo === 'DIA'
      ? { label: 'POR DIA', cls: 'bg-teal-50 text-teal-700' }
      : { label: 'POR HORA', cls: 'bg-teal-50 text-teal-700' }
  }
  return { label: 'PLANTÃO', cls: 'bg-blue-50 text-blue-700' }
}

// Resumo da meta (coluna "Horas/Meta"): horas e/ou dias configurados na modalidade META.
function metaResumo(m: TomadorModalidade): string {
  const parts: string[] = []
  if (m.metaHoras != null) parts.push(`${m.metaHoras}h`)
  if (m.metaDias != null) parts.push(`${m.metaDias}d`)
  return parts.join(' + ')
}

// Badge do tipo de valor na tabela de ocorrências.
function ocorrenciaTipoBadge(o: TomadorOcorrencia): { label: string; cls: string } {
  if (o.tipoValor === 'PERCENTUAL') return { label: 'PERCENTUAL', cls: 'bg-green-50 text-green-700' }
  if (o.tipoValor === 'FIXO') return { label: 'FIXO', cls: 'bg-orange-50 text-orange-700' }
  return { label: 'SEM VALOR', cls: 'bg-gray-100 text-gray-500' }
}

// ─── Mapeamento fixo turno × horas × horário ──────────────────────────────────

const HORARIOS_FIXOS = [
  { turno: 'DIURNO'  as const, horas: 6,  horario: '07:00 as 13:00', label: '☀️ Diurno 6h — 07:00 as 13:00' },
  { turno: 'DIURNO'  as const, horas: 12, horario: '07:00 as 19:00', label: '☀️ Diurno 12h — 07:00 as 19:00' },
  { turno: 'NOTURNO' as const, horas: 6,  horario: '19:00 as 00:00', label: '🌙 Noturno 6h — 19:00 as 00:00' },
  { turno: 'NOTURNO' as const, horas: 12, horario: '19:00 as 07:00', label: '🌙 Noturno 12h — 19:00 as 07:00' },
]

// ─── Form state types ─────────────────────────────────────────────────────────

interface GrupoForm {
  nome: string
  descricaoNota: string
  ativo: boolean
}

// Modo do formulário (UI). PLANTAO/MENSAL mapeiam 1:1 no backend; HORAS e MES são apresentações
// do tipo META do backend (HORAS = unidade HORA; MES = unidade escolhida + metas horas/dias).
type ModalidadeModo = 'PLANTAO' | 'MENSAL' | 'HORAS' | 'MES'

interface ModalidadeForm {
  nome: string
  tipo: ModalidadeModo
  turno: 'DIURNO' | 'NOTURNO' | ''
  horario: string
  horasStr: string
  valorStr: string
  deslocamentoStr: string
  ativo: boolean
  // Campos do tipo META (modos HORAS / MES)
  unidadeCalculo: 'HORA' | 'DIA'
  metaHorasStr: string
  metaDiasStr: string
}

function emptyGrupoForm(): GrupoForm {
  return { nome: '', descricaoNota: '', ativo: true }
}

function emptyModalidadeForm(): ModalidadeForm {
  return {
    nome: '', tipo: 'PLANTAO', turno: '', horario: '',
    horasStr: '', valorStr: '', deslocamentoStr: '', ativo: true,
    unidadeCalculo: 'HORA', metaHorasStr: '', metaDiasStr: '',
  }
}

// Ocorrência PERCENTUAL e FIXO podem coexistir ("10% + R$ 50,00") — tipoValor só decide qual
// dos dois campos é obrigatório, o outro fica sempre disponível como valor extra opcional.
interface OcorrenciaForm {
  nome: string
  tipoValor: 'PERCENTUAL' | 'FIXO' | 'SEM_VALOR'
  valorPercentualStr: string
  valorStr: string
  ativo: boolean
}

function emptyOcorrenciaForm(): OcorrenciaForm {
  return { nome: '', tipoValor: 'SEM_VALOR', valorPercentualStr: '', valorStr: '', ativo: true }
}

const MODALIDADE_TIPOS: { modo: ModalidadeModo; titulo: string; sub: string }[] = [
  { modo: 'PLANTAO', titulo: 'Por Plantão',        sub: 'valor por dia; horas (turno/horário opcionais)' },
  { modo: 'HORAS',   titulo: 'Por Horas',          sub: 'proporcional; meta de horas (ex: Diária 40h)' },
  { modo: 'MES',     titulo: 'Por Mês',            sub: 'proporcional; meta de horas e/ou dias' },
  { modo: 'MENSAL',  titulo: 'Valor Fixo Mensal',  sub: 'valor fixo, ex: Coordenação de UTI' },
]

// ─── Sub-components ───────────────────────────────────────────────────────────

function GrupoFormInline({
  form, onChange, onSave, onCancel, saving, isNew,
}: {
  form: GrupoForm
  onChange: (patch: Partial<GrupoForm>) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
  isNew: boolean
}) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 gap-3">
        <div>
          <Input
            label="Nome do grupo *"
            value={form.nome}
            onChange={e => onChange({ nome: e.target.value })}
            placeholder="ex: Plantões, Diárias e Exames"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Descrição da nota *
            <span className="ml-1 text-xs font-normal text-ds-light">(use {'{competencia}'} para substituição automática)</span>
          </label>
          <textarea
            value={form.descricaoNota}
            onChange={e => onChange({ descricaoNota: e.target.value })}
            rows={2}
            placeholder="ex: Prestação de serviços médicos na qualidade de PLANTONISTA, referente à competência de {competencia}."
            className="w-full rounded-lg border border-gray-300 text-sm text-gray-900 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary resize-none"
          />
          {form.descricaoNota.includes('{competencia}') && (
            <p className="text-[11px] text-green-600 mt-1">
              ✓ {'{competencia}'} será substituído (ex: JUNHO de 2026)
            </p>
          )}
        </div>
        <div>
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={form.ativo}
              onChange={e => onChange({ ativo: e.target.checked })}
              className="rounded border-gray-300 text-primary focus:ring-primary"
            />
            <span className="text-sm font-medium text-gray-700">Grupo ativo</span>
          </label>
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-2 border-t border-ds-border">
        <Button variant="ghost" size="sm" onClick={onCancel} disabled={saving}>
          Cancelar
        </Button>
        <Button size="sm" onClick={onSave} loading={saving}>
          {isNew ? 'Criar Grupo' : 'Salvar Alterações'}
        </Button>
      </div>
    </div>
  )
}

function ModalidadeFormInline({
  form, onChange, onSave, onCancel, saving, isNew,
}: {
  form: ModalidadeForm
  onChange: (patch: Partial<ModalidadeForm>) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
  isNew: boolean
}) {
  const SELECT_CLS = 'w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary bg-white'
  const isPlantao = form.tipo === 'PLANTAO'
  const isMensal  = form.tipo === 'MENSAL'
  const isHoras   = form.tipo === 'HORAS'
  const isMes     = form.tipo === 'MES'
  const valorLabel = isMensal ? 'Valor Mensal *'
    : isHoras ? 'Valor do bloco (meta completa) *'
    : isMes ? 'Valor do pacote (meta completa) *'
    : 'Valor *'

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <Input
            label="Nome da modalidade *"
            value={form.nome}
            onChange={e => onChange({ nome: e.target.value })}
            placeholder="ex: Plantão 12h Noturno, Diária 40h, Evolucionista, Coordenação de UTI"
          />
        </div>

        {/* Tipo de modalidade — decide quais campos aparecem abaixo */}
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">Tipo de modalidade *</label>
          <div className="grid grid-cols-2 gap-2">
            {MODALIDADE_TIPOS.map(t => (
              <button
                key={t.modo}
                type="button"
                onClick={() => onChange(t.modo === 'HORAS' ? { tipo: 'HORAS', unidadeCalculo: 'HORA' } : { tipo: t.modo })}
                className={[
                  'px-3 py-2 rounded-lg border text-xs font-semibold text-left transition-colors',
                  form.tipo === t.modo ? 'border-primary bg-primary-50 text-primary' : 'border-gray-300 text-gray-600 hover:border-primary/40',
                ].join(' ')}
              >
                {t.titulo}
                <span className="block font-normal text-[10px] text-ds-light mt-0.5">{t.sub}</span>
              </button>
            ))}
          </div>
        </div>

        {/* ── PLANTÃO: turno/horas/horário ── */}
        {isPlantao && (
          <>
            {/* Presets rápidos — preenchem turno + horas + horário de uma vez, mas os 3 campos abaixo continuam livres para edição */}
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Preencher rápido (opcional)</label>
              <div className="flex flex-wrap gap-1.5">
                {HORARIOS_FIXOS.map(c => (
                  <button
                    key={c.horario}
                    type="button"
                    onClick={() => onChange({ turno: c.turno, horasStr: String(c.horas), horario: c.horario })}
                    className="px-2.5 py-1 rounded-lg border border-gray-300 text-[11px] text-gray-700 hover:border-primary hover:text-primary transition-colors"
                  >
                    {c.label}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Turno</label>
              <select
                value={form.turno}
                onChange={e => onChange({ turno: e.target.value as 'DIURNO' | 'NOTURNO' | '' })}
                className={SELECT_CLS}
              >
                <option value="">Não especificar</option>
                <option value="DIURNO">☀️ DIURNO</option>
                <option value="NOTURNO">🌙 NOTURNO</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Horas *</label>
              <input
                type="number"
                step="0.5"
                min="0.5"
                value={form.horasStr}
                onChange={e => onChange({ horasStr: e.target.value })}
                placeholder="ex: 10"
                className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              />
            </div>

            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Horário</label>
              <input
                value={form.horario}
                onChange={e => onChange({ horario: e.target.value })}
                placeholder="ex: 07:00 as 17:00 (opcional)"
                className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              />
            </div>
          </>
        )}

        {/* ── POR HORAS: meta de horas ── */}
        {isHoras && (
          <div className="col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">Meta de horas *</label>
            <input
              type="number"
              step="0.5"
              min="0.5"
              value={form.metaHorasStr}
              onChange={e => onChange({ metaHorasStr: e.target.value })}
              placeholder="ex: 40"
              className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
            />
            <p className="text-[11px] text-ds-light mt-1">
              O pagamento é proporcional às horas lançadas: valor do bloco ÷ meta de horas. Ao passar da meta, inicia um novo bloco.
            </p>
          </div>
        )}

        {/* ── POR MÊS: unidade + meta de horas e/ou dias ── */}
        {isMes && (
          <>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Unidade de cálculo *</label>
              <div className="grid grid-cols-2 gap-2">
                {(['HORA', 'DIA'] as const).map(u => (
                  <button
                    key={u}
                    type="button"
                    onClick={() => onChange({ unidadeCalculo: u })}
                    className={[
                      'px-3 py-1.5 rounded-lg border text-xs font-semibold transition-colors',
                      form.unidadeCalculo === u ? 'border-primary bg-primary-50 text-primary' : 'border-gray-300 text-gray-600 hover:border-primary/40',
                    ].join(' ')}
                  >
                    {u === 'HORA' ? 'Por hora' : 'Por dia'}
                  </button>
                ))}
              </div>
              <p className="text-[11px] text-ds-light mt-1">
                Base do rateio do valor. A outra meta abaixo é opcional (validação secundária).
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Meta de horas {form.unidadeCalculo === 'HORA' ? '*' : '(opcional)'}
              </label>
              <input
                type="number"
                step="0.5"
                min="0.5"
                value={form.metaHorasStr}
                onChange={e => onChange({ metaHorasStr: e.target.value })}
                placeholder="ex: 160"
                className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Meta de dias {form.unidadeCalculo === 'DIA' ? '*' : '(opcional)'}
              </label>
              <input
                type="number"
                step="1"
                min="1"
                value={form.metaDiasStr}
                onChange={e => onChange({ metaDiasStr: e.target.value })}
                placeholder="ex: 20"
                className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              />
            </div>
          </>
        )}

        <div className={isPlantao ? '' : 'col-span-2'}>
          <label className="block text-sm font-medium text-gray-700 mb-1">{valorLabel}</label>
          <div className="relative">
            <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-ds-mid pointer-events-none">R$</span>
            <input
              value={form.valorStr}
              onChange={e => onChange({ valorStr: maskValor(e) })}
              placeholder="0,00"
              className="block w-full pl-7 pr-3 py-2 rounded-lg border border-gray-300 text-sm text-right text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
            />
          </div>
        </div>
        {isPlantao && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Deslocamento</label>
            <div className="relative">
              <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-ds-mid pointer-events-none">R$</span>
              <input
                value={form.deslocamentoStr}
                onChange={e => onChange({ deslocamentoStr: maskValor(e) })}
                placeholder="0,00"
                className="block w-full pl-7 pr-3 py-2 rounded-lg border border-gray-300 text-sm text-right text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              />
            </div>
          </div>
        )}
        <div className="col-span-2">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={form.ativo}
              onChange={e => onChange({ ativo: e.target.checked })}
              className="rounded border-gray-300 text-primary focus:ring-primary"
            />
            <span className="text-sm font-medium text-gray-700">Modalidade ativa</span>
          </label>
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-2 border-t border-ds-border">
        <Button variant="ghost" size="sm" onClick={onCancel} disabled={saving}>
          Cancelar
        </Button>
        <Button size="sm" onClick={onSave} loading={saving}>
          {isNew ? 'Adicionar Modalidade' : 'Salvar Alterações'}
        </Button>
      </div>
    </div>
  )
}

function OcorrenciaFormInline({
  form, onChange, onSave, onCancel, saving, isNew,
}: {
  form: OcorrenciaForm
  onChange: (patch: Partial<OcorrenciaForm>) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
  isNew: boolean
}) {
  const isPercentual = form.tipoValor === 'PERCENTUAL'
  const isFixo = form.tipoValor === 'FIXO'
  const isSemValor = form.tipoValor === 'SEM_VALOR'

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <Input
            label="Nome da ocorrência *"
            value={form.nome}
            onChange={e => onChange({ nome: e.target.value })}
            placeholder="ex: Feriado, Sobreaviso, Observação"
          />
        </div>

        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">Tipo de valor *</label>
          <div className="grid grid-cols-3 gap-2">
            {([
              { tipo: 'PERCENTUAL' as const, titulo: 'Percentual', sub: '% sobre a modalidade' },
              { tipo: 'FIXO' as const,       titulo: 'Valor Fixo', sub: 'soma em R$' },
              { tipo: 'SEM_VALOR' as const,  titulo: 'Sem Valor',  sub: 'só observação' },
            ]).map(t => (
              <button
                key={t.tipo}
                type="button"
                onClick={() => onChange({ tipoValor: t.tipo })}
                className={[
                  'px-3 py-2 rounded-lg border text-xs font-semibold text-left transition-colors',
                  form.tipoValor === t.tipo ? 'border-primary bg-primary-50 text-primary' : 'border-gray-300 text-gray-600 hover:border-primary/40',
                ].join(' ')}
              >
                {t.titulo}
                <span className="block font-normal text-[10px] text-ds-light mt-0.5">{t.sub}</span>
              </button>
            ))}
          </div>
        </div>

        {!isSemValor && (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Percentual (%) {isPercentual ? '*' : <span className="font-normal text-ds-light">(opcional, soma ao valor fixo)</span>}
              </label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={form.valorPercentualStr}
                onChange={e => onChange({ valorPercentualStr: e.target.value })}
                placeholder="ex: 50"
                className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Valor Fixo {isFixo ? '*' : <span className="font-normal text-ds-light">(opcional, soma ao percentual)</span>}
              </label>
              <div className="relative">
                <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-ds-mid pointer-events-none">R$</span>
                <input
                  value={form.valorStr}
                  onChange={e => onChange({ valorStr: maskValor(e) })}
                  placeholder="0,00"
                  className="block w-full pl-7 pr-3 py-2 rounded-lg border border-gray-300 text-sm text-right text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
                />
              </div>
            </div>
            <p className="col-span-2 text-[11px] text-ds-light -mt-1">
              O valor soma ao lançamento: (percentual × valor cadastrado da modalidade) + valor fixo.
            </p>
          </>
        )}
        {isSemValor && (
          <p className="col-span-2 text-[11px] text-ds-light">
            Ocorrência apenas informativa — não altera o valor pago ao médico.
          </p>
        )}

        <div className="col-span-2">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={form.ativo}
              onChange={e => onChange({ ativo: e.target.checked })}
              className="rounded border-gray-300 text-primary focus:ring-primary"
            />
            <span className="text-sm font-medium text-gray-700">Ocorrência ativa</span>
          </label>
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-2 border-t border-ds-border">
        <Button variant="ghost" size="sm" onClick={onCancel} disabled={saving}>
          Cancelar
        </Button>
        <Button size="sm" onClick={onSave} loading={saving}>
          {isNew ? 'Adicionar Ocorrência' : 'Salvar Alterações'}
        </Button>
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

interface Props {
  tomador: Tomador
  canWrite: boolean
  onClose: () => void
}

export function TomadorGruposModal({ tomador, canWrite, onClose }: Props) {
  const [aba, setAba] = useState<'grupos' | 'modalidades' | 'ocorrencias'>('grupos')

  // ── Grupos ────────────────────────────────────────────────────────────────
  const [grupos, setGrupos] = useState<TomadorGrupoFaturamento[]>([])
  const [gruposLoading, setGruposLoading] = useState(false)
  const [grupoErr, setGrupoErr] = useState<string | null>(null)
  const [expandedGrupos, setExpandedGrupos] = useState<Set<string>>(new Set())

  const [grupoForm, setGrupoForm] = useState<GrupoForm | null>(null)
  const [editingGrupoId, setEditingGrupoId] = useState<string | null>(null)
  const [grupoSaving, setGrupoSaving] = useState(false)

  const [setorForms, setSetorForms] = useState<Record<string, string>>({})
  const [setorSaving, setSetorSaving] = useState<string | null>(null)

  // ── Modalidades ───────────────────────────────────────────────────────────
  const [modalidades, setModalidades] = useState<TomadorModalidade[]>([])
  const [modLoading, setModLoading] = useState(false)
  const [modErr, setModErr] = useState<string | null>(null)

  const [modForm, setModForm] = useState<ModalidadeForm | null>(null)
  const [editingModId, setEditingModId] = useState<string | null>(null)
  const [modSaving, setModSaving] = useState(false)

  // ── Ocorrências ───────────────────────────────────────────────────────────
  const [ocorrencias, setOcorrencias] = useState<TomadorOcorrencia[]>([])
  const [ocLoading, setOcLoading] = useState(false)
  const [ocErr, setOcErr] = useState<string | null>(null)

  const [ocForm, setOcForm] = useState<OcorrenciaForm | null>(null)
  const [editingOcId, setEditingOcId] = useState<string | null>(null)
  const [ocSaving, setOcSaving] = useState(false)

  // ── Load ──────────────────────────────────────────────────────────────────

  const carregarGrupos = useCallback(async () => {
    setGruposLoading(true)
    try {
      const data = await tomadoresApi.listarGrupos(tomador.id)
      setGrupos(data)
      // Expandir automaticamente se houver só 1 grupo
      if (data.length === 1) setExpandedGrupos(new Set([data[0].id]))
    } catch (e) {
      setGrupoErr(e instanceof Error ? e.message : 'Erro ao carregar grupos')
    } finally {
      setGruposLoading(false)
    }
  }, [tomador.id])

  const carregarModalidades = useCallback(async () => {
    setModLoading(true)
    try {
      setModalidades(await tomadoresApi.listarModalidades(tomador.id))
    } catch (e) {
      setModErr(e instanceof Error ? e.message : 'Erro ao carregar modalidades')
    } finally {
      setModLoading(false)
    }
  }, [tomador.id])

  const carregarOcorrencias = useCallback(async () => {
    setOcLoading(true)
    try {
      setOcorrencias(await tomadoresApi.listarOcorrencias(tomador.id))
    } catch (e) {
      setOcErr(e instanceof Error ? e.message : 'Erro ao carregar ocorrências')
    } finally {
      setOcLoading(false)
    }
  }, [tomador.id])

  useEffect(() => {
    carregarGrupos()
    carregarModalidades()
    carregarOcorrencias()
  }, [carregarGrupos, carregarModalidades, carregarOcorrencias])

  // ── Grupos CRUD ───────────────────────────────────────────────────────────

  function abrirNovoGrupo() {
    setGrupoForm(emptyGrupoForm())
    setEditingGrupoId(null)
    setGrupoErr(null)
  }

  function abrirEditarGrupo(g: TomadorGrupoFaturamento) {
    setGrupoForm({ nome: g.nome, descricaoNota: g.descricaoNota, ativo: g.ativo })
    setEditingGrupoId(g.id)
    setGrupoErr(null)
  }

  function cancelarGrupo() {
    setGrupoForm(null)
    setEditingGrupoId(null)
    setGrupoErr(null)
  }

  async function salvarGrupo() {
    if (!grupoForm) return
    if (!grupoForm.nome.trim() || !grupoForm.descricaoNota.trim()) {
      setGrupoErr('Preencha nome e descrição da nota')
      return
    }
    const servicoLc116Id = tomador.servicos[0]?.servicoId ?? ''
    if (!servicoLc116Id) {
      setGrupoErr('Configure ao menos um serviço LC116 no tomador antes de criar grupos')
      return
    }
    const ordem = editingGrupoId
      ? (grupos.find(g => g.id === editingGrupoId)?.ordem ?? 1)
      : (grupos.length > 0 ? Math.max(...grupos.map(g => g.ordem)) + 1 : 1)
    setGrupoSaving(true)
    setGrupoErr(null)
    try {
      const req: TomadorGrupoFaturamentoRequest = {
        servicoLc116Id,
        nome: grupoForm.nome.trim(),
        descricaoNota: grupoForm.descricaoNota.trim(),
        ordem,
        ativo: grupoForm.ativo,
      }
      if (editingGrupoId) {
        await tomadoresApi.atualizarGrupo(tomador.id, editingGrupoId, req)
      } else {
        await tomadoresApi.criarGrupo(tomador.id, req)
      }
      cancelarGrupo()
      await carregarGrupos()
    } catch (e) {
      setGrupoErr(e instanceof Error ? e.message : 'Erro ao salvar grupo')
    } finally {
      setGrupoSaving(false)
    }
  }

  async function removerGrupo(grupoId: string, nome: string) {
    if (!window.confirm(`Remover o grupo "${nome}"? Os setores vinculados também serão removidos.`)) return
    setGrupoErr(null)
    try {
      await tomadoresApi.removerGrupo(tomador.id, grupoId)
      await carregarGrupos()
    } catch (e) {
      setGrupoErr(e instanceof Error ? e.message : 'Erro ao remover grupo')
    }
  }

  function toggleGrupo(id: string) {
    setExpandedGrupos(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  // ── Setores CRUD ──────────────────────────────────────────────────────────

  async function adicionarSetor(grupoId: string) {
    const nome = (setorForms[grupoId] ?? '').trim()
    if (!nome) return
    setSetorSaving(grupoId)
    try {
      const req: TomadorServicoOperacionalRequest = { grupoId, nome, ativo: true }
      await tomadoresApi.criarServicoOperacional(tomador.id, req)
      setSetorForms(f => ({ ...f, [grupoId]: '' }))
      await carregarGrupos()
    } catch (e) {
      setGrupoErr(e instanceof Error ? e.message : 'Erro ao adicionar setor')
    } finally {
      setSetorSaving(null)
    }
  }

  async function removerSetor(setorId: string) {
    try {
      await tomadoresApi.removerServicoOperacional(tomador.id, setorId)
      await carregarGrupos()
    } catch (e) {
      setGrupoErr(e instanceof Error ? e.message : 'Erro ao remover setor')
    }
  }

  // ── Modalidades CRUD ──────────────────────────────────────────────────────

  function abrirNovaModalidade() {
    setModForm(emptyModalidadeForm())
    setEditingModId(null)
    setModErr(null)
  }

  function abrirEditarModalidade(m: TomadorModalidade) {
    // Backend tem 3 tipos (PLANTAO/MENSAL/META); o form tem 4 modos (META vira HORAS ou MES).
    // META unidade HORA sem meta de dias = "Por Horas"; o resto de META = "Por Mês".
    const modo: ModalidadeModo = m.tipo === 'META'
      ? (m.unidadeCalculo === 'HORA' && m.metaDias == null ? 'HORAS' : 'MES')
      : m.tipo
    setModForm({
      nome: m.nome,
      tipo: modo,
      turno: m.turno ?? '',
      horario: m.horario ?? '',
      horasStr: m.horas != null ? String(m.horas) : '',
      valorStr: centavosParaBrl(m.valorCentavos),
      deslocamentoStr: m.deslocamentoCentavos > 0 ? centavosParaBrl(m.deslocamentoCentavos) : '',
      ativo: m.ativo,
      unidadeCalculo: m.unidadeCalculo ?? 'HORA',
      metaHorasStr: m.metaHoras != null ? String(m.metaHoras) : '',
      metaDiasStr: m.metaDias != null ? String(m.metaDias) : '',
    })
    setEditingModId(m.id)
    setModErr(null)
  }

  function cancelarModalidade() {
    setModForm(null)
    setEditingModId(null)
    setModErr(null)
  }

  async function salvarModalidade() {
    if (!modForm) return
    const modo = modForm.tipo
    const isPlantao = modo === 'PLANTAO'
    const valorCentavos = parseCentavos(modForm.valorStr)

    if (!modForm.nome.trim() || valorCentavos <= 0) {
      setModErr('Preencha nome e valor corretamente')
      return
    }

    // Monta o request no contrato do backend (tipo PLANTAO/MENSAL/META).
    let req: TomadorModalidadeRequest
    const base = {
      nome: modForm.nome.trim(),
      valorCentavos,
      deslocamentoCentavos: 0,
      ativo: modForm.ativo,
      turno: null as 'DIURNO' | 'NOTURNO' | null,
      horario: null as string | null,
      horas: null as number | null,
      unidadeCalculo: null as 'HORA' | 'DIA' | null,
      metaHoras: null as number | null,
      metaDias: null as number | null,
    }

    if (isPlantao) {
      const horas = parseFloat(modForm.horasStr.replace(',', '.'))
      if (isNaN(horas) || horas <= 0) {
        setModErr('Preencha a quantidade de horas corretamente')
        return
      }
      req = {
        ...base, tipo: 'PLANTAO', horas,
        turno: modForm.turno || null,
        horario: modForm.horario.trim() || null,
        deslocamentoCentavos: parseCentavos(modForm.deslocamentoStr),
      }
    } else if (modo === 'MENSAL') {
      req = { ...base, tipo: 'MENSAL' }
    } else {
      // HORAS ou MES → tipo META
      const unidade = modo === 'HORAS' ? 'HORA' : modForm.unidadeCalculo
      const metaHoras = modForm.metaHorasStr.trim() ? parseFloat(modForm.metaHorasStr.replace(',', '.')) : null
      const metaDias  = modForm.metaDiasStr.trim() ? parseInt(modForm.metaDiasStr, 10) : null
      if (unidade === 'HORA' && (metaHoras == null || isNaN(metaHoras) || metaHoras <= 0)) {
        setModErr('Preencha a meta de horas corretamente')
        return
      }
      if (unidade === 'DIA' && (metaDias == null || isNaN(metaDias) || metaDias <= 0)) {
        setModErr('Preencha a meta de dias corretamente')
        return
      }
      req = { ...base, tipo: 'META', unidadeCalculo: unidade, metaHoras, metaDias }
    }

    setModSaving(true)
    setModErr(null)
    try {
      if (editingModId) {
        await tomadoresApi.atualizarModalidade(tomador.id, editingModId, req)
      } else {
        await tomadoresApi.criarModalidade(tomador.id, req)
      }
      cancelarModalidade()
      await carregarModalidades()
    } catch (e) {
      setModErr(e instanceof Error ? e.message : 'Erro ao salvar modalidade')
    } finally {
      setModSaving(false)
    }
  }

  async function removerModalidade(id: string, nome: string) {
    if (!window.confirm(`Remover a modalidade "${nome}"?`)) return
    setModErr(null)
    try {
      await tomadoresApi.removerModalidade(tomador.id, id)
      await carregarModalidades()
    } catch (e) {
      setModErr(e instanceof Error ? e.message : 'Erro ao remover modalidade')
    }
  }

  // ── Ocorrências CRUD ──────────────────────────────────────────────────────

  function abrirNovaOcorrencia() {
    setOcForm(emptyOcorrenciaForm())
    setEditingOcId(null)
    setOcErr(null)
  }

  function abrirEditarOcorrencia(o: TomadorOcorrencia) {
    setOcForm({
      nome: o.nome,
      tipoValor: o.tipoValor,
      valorPercentualStr: o.valorPercentual != null ? String(o.valorPercentual) : '',
      valorStr: o.valorCentavos != null && o.valorCentavos > 0 ? centavosParaBrl(o.valorCentavos) : '',
      ativo: o.ativo,
    })
    setEditingOcId(o.id)
    setOcErr(null)
  }

  function cancelarOcorrencia() {
    setOcForm(null)
    setEditingOcId(null)
    setOcErr(null)
  }

  async function salvarOcorrencia() {
    if (!ocForm) return
    if (!ocForm.nome.trim()) {
      setOcErr('Preencha o nome da ocorrência')
      return
    }

    const percentualNum = ocForm.valorPercentualStr.trim()
      ? parseFloat(ocForm.valorPercentualStr.replace(',', '.'))
      : null
    const centavosNum = ocForm.tipoValor !== 'SEM_VALOR' ? parseCentavos(ocForm.valorStr) : 0
    const percentualValido = percentualNum != null && !isNaN(percentualNum) && percentualNum > 0
    const centavosValido = centavosNum > 0

    if (ocForm.tipoValor === 'PERCENTUAL' && !percentualValido) {
      setOcErr('Preencha o percentual corretamente')
      return
    }
    if (ocForm.tipoValor === 'FIXO' && !centavosValido) {
      setOcErr('Preencha o valor fixo corretamente')
      return
    }

    const req: TomadorOcorrenciaRequest = {
      nome: ocForm.nome.trim(),
      tipoValor: ocForm.tipoValor,
      valorPercentual: ocForm.tipoValor !== 'SEM_VALOR' && percentualValido ? percentualNum : null,
      valorCentavos: ocForm.tipoValor !== 'SEM_VALOR' && centavosValido ? centavosNum : null,
      ativo: ocForm.ativo,
    }

    setOcSaving(true)
    setOcErr(null)
    try {
      if (editingOcId) {
        await tomadoresApi.atualizarOcorrencia(tomador.id, editingOcId, req)
      } else {
        await tomadoresApi.criarOcorrencia(tomador.id, req)
      }
      cancelarOcorrencia()
      await carregarOcorrencias()
    } catch (e) {
      setOcErr(e instanceof Error ? e.message : 'Erro ao salvar ocorrência')
    } finally {
      setOcSaving(false)
    }
  }

  async function removerOcorrencia(id: string, nome: string) {
    if (!window.confirm(`Remover a ocorrência "${nome}"?`)) return
    setOcErr(null)
    try {
      await tomadoresApi.removerOcorrencia(tomador.id, id)
      await carregarOcorrencias()
    } catch (e) {
      setOcErr(e instanceof Error ? e.message : 'Erro ao remover ocorrência')
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <Modal
      open
      title={
        <div>
          <div className="flex items-center gap-2">
            <Layers size={16} className="text-primary" />
            <span className="text-base font-bold">Faturamento por Grupo</span>
          </div>
          <p className="text-xs text-ds-light mt-0.5 truncate">{tomador.razaoSocialNome}</p>
        </div>
      }
      onClose={onClose}
      size="2xl"
    >
      {/* Tab bar */}
      <div className="flex gap-1 mb-4 p-1 bg-ds-input rounded-xl border border-ds-border">
        {([
          ['grupos', 'Grupos & Setores'],
          ['modalidades', 'Modalidades (Tabela de Preços)'],
          ['ocorrencias', 'Ocorrências'],
        ] as const).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setAba(key)}
            className={[
              'flex-1 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all',
              aba === key
                ? 'bg-white text-primary shadow-sm border border-ds-border'
                : 'text-ds-mid hover:text-ds-text',
            ].join(' ')}
          >
            {label}
          </button>
        ))}
      </div>

      {/* ── Aba: Grupos & Setores ── */}
      {aba === 'grupos' && (
        <div className="flex flex-col gap-3">
          {grupoErr && (
            <Alert variant="error" onClose={() => setGrupoErr(null)}>{grupoErr}</Alert>
          )}

          {gruposLoading ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : grupos.length === 0 && !grupoForm ? (
            <div className="flex flex-col items-center py-10 gap-2 text-ds-light">
              <FolderOpen size={36} className="opacity-25" />
              <p className="text-sm font-semibold">Nenhum grupo de faturamento</p>
              <p className="text-xs">Cada grupo gera uma NFS-e com descrição própria</p>
              {canWrite && (
                <Button size="sm" className="mt-2" onClick={abrirNovoGrupo}>
                  <Plus size={14} /> Criar primeiro grupo
                </Button>
              )}
            </div>
          ) : (
            <div className="space-y-2">
              {grupos.map(g => {
                const expanded = expandedGrupos.has(g.id)
                const isEditing = editingGrupoId === g.id
                return (
                  <div key={g.id} className="rounded-xl border border-ds-border bg-white overflow-hidden">
                    {/* Grupo header */}
                    <div className="flex items-center gap-2 px-4 py-3">
                      <button
                        type="button"
                        onClick={() => toggleGrupo(g.id)}
                        className="text-ds-light hover:text-primary transition-colors shrink-0"
                        title={expanded ? 'Recolher' : 'Expandir'}
                      >
                        {expanded
                          ? <ChevronDown size={16} />
                          : <ChevronRight size={16} />}
                      </button>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-bold text-ds-text">
                            {g.ordem}. {g.nome}
                          </span>
                          <span className={[
                            'px-1.5 py-0.5 rounded text-[10px] font-bold',
                            g.ativo ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500',
                          ].join(' ')}>
                            {g.ativo ? 'ATIVO' : 'INATIVO'}
                          </span>
                        </div>
                        <p className="text-[11px] text-ds-light mt-0.5">
                          {g.codigoLc116 && `LC116: ${g.codigoLc116}${g.descricaoServico ? ` · ${g.descricaoServico}` : ''} · `}
                          {g.servicosOperacionais.length} setor(es)
                        </p>
                      </div>
                      {canWrite && (
                        <div className="flex items-center gap-1 shrink-0">
                          <button
                            type="button"
                            onClick={() => { abrirEditarGrupo(g); toggleGrupo(g.id) }}
                            className="p-1.5 rounded-lg text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                            title="Editar grupo"
                          >
                            <Pencil size={14} />
                          </button>
                          <button
                            type="button"
                            onClick={() => removerGrupo(g.id, g.nome)}
                            className="p-1.5 rounded-lg text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors"
                            title="Remover grupo"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      )}
                    </div>

                    {/* Corpo expandido — conteúdo ou form de edição */}
                    {expanded && (
                      <div className="border-t border-ds-border bg-ds-surface/30 px-4 py-3">
                        {isEditing && grupoForm ? (
                          <div>
                            <p className="text-xs font-bold text-ds-mid uppercase mb-3">Editar grupo</p>
                            <GrupoFormInline
                              form={grupoForm}
                              onChange={patch => setGrupoForm(f => f ? { ...f, ...patch } : f)}
                              onSave={salvarGrupo}
                              onCancel={cancelarGrupo}
                              saving={grupoSaving}
                              isNew={false}
                            />
                          </div>
                        ) : (
                          <div className="space-y-3">
                            {/* Descrição da nota */}
                            <div>
                              <p className="text-[10px] text-ds-light font-semibold uppercase mb-1">
                                Descrição da nota
                              </p>
                              <p className="text-xs text-ds-mid italic">"{g.descricaoNota}"</p>
                            </div>

                            {/* Setores */}
                            <div>
                              <p className="text-[10px] text-ds-light font-semibold uppercase mb-1.5">
                                Setores operacionais ({g.servicosOperacionais.length})
                              </p>
                              {g.servicosOperacionais.length === 0 ? (
                                <p className="text-xs text-ds-light mb-2">Nenhum setor ainda</p>
                              ) : (
                                <div className="space-y-1 mb-2">
                                  {g.servicosOperacionais.map(s => (
                                    <div
                                      key={s.id}
                                      className="flex items-center justify-between bg-white rounded-lg px-3 py-1.5 border border-ds-border"
                                    >
                                      <div className="flex items-center gap-2">
                                        <span className="text-xs text-ds-text">{s.nome}</span>
                                        {!s.ativo && (
                                          <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500">
                                            INATIVO
                                          </span>
                                        )}
                                      </div>
                                      {canWrite && (
                                        <button
                                          type="button"
                                          onClick={() => removerSetor(s.id)}
                                          className="text-ds-light hover:text-red-500 transition-colors p-0.5"
                                          title="Remover setor"
                                        >
                                          <Trash2 size={12} />
                                        </button>
                                      )}
                                    </div>
                                  ))}
                                </div>
                              )}

                              {/* Adicionar setor */}
                              {canWrite && (
                                <div className="flex gap-2">
                                  <input
                                    value={setorForms[g.id] ?? ''}
                                    onChange={e => setSetorForms(f => ({ ...f, [g.id]: e.target.value }))}
                                    onKeyDown={e => {
                                      if (e.key === 'Enter') {
                                        e.preventDefault()
                                        adicionarSetor(g.id)
                                      }
                                    }}
                                    placeholder="Nome do setor (ex: Emergência Cardiológica)"
                                    className="flex-1 h-8 text-xs rounded-lg border border-ds-border px-2.5 focus:outline-none focus:ring-1 focus:ring-primary/30 focus:border-primary"
                                  />
                                  <button
                                    type="button"
                                    onClick={() => adicionarSetor(g.id)}
                                    disabled={setorSaving === g.id || !(setorForms[g.id] ?? '').trim()}
                                    className="h-8 px-2.5 rounded-lg bg-primary text-white text-xs font-semibold flex items-center gap-1 hover:bg-primary-600 disabled:opacity-50 transition-colors shrink-0"
                                  >
                                    {setorSaving === g.id
                                      ? <Loader2 size={12} className="animate-spin" />
                                      : <Plus size={12} />}
                                    Adicionar
                                  </button>
                                </div>
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}

          {/* Form de novo grupo */}
          {canWrite && grupoForm && !editingGrupoId && (
            <div className="rounded-xl border border-primary/30 bg-primary-50/30 p-4">
              <p className="text-xs font-bold text-ds-mid uppercase mb-3">Novo grupo</p>
              <GrupoFormInline
                form={grupoForm}
                onChange={patch => setGrupoForm(f => f ? { ...f, ...patch } : f)}
                onSave={salvarGrupo}
                onCancel={cancelarGrupo}
                saving={grupoSaving}
                isNew
              />
            </div>
          )}

          {/* Botão adicionar novo grupo */}
          {canWrite && !grupoForm && !editingGrupoId && grupos.length > 0 && (
            <button
              type="button"
              onClick={abrirNovoGrupo}
              className="flex items-center gap-2 justify-center w-full py-2.5 rounded-xl border-2 border-dashed border-ds-border text-ds-light hover:border-primary hover:text-primary text-xs font-semibold transition-all"
            >
              <Plus size={14} /> Novo Grupo de Faturamento
            </button>
          )}
        </div>
      )}

      {/* ── Aba: Modalidades ── */}
      {aba === 'modalidades' && (
        <div className="flex flex-col gap-3">
          {modErr && (
            <Alert variant="error" onClose={() => setModErr(null)}>{modErr}</Alert>
          )}

          {modLoading ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : modalidades.length === 0 && !modForm ? (
            <div className="flex flex-col items-center py-10 gap-2 text-ds-light">
              <Sun size={36} className="opacity-25" />
              <p className="text-sm font-semibold">Tabela de preços vazia</p>
              <p className="text-xs">Cadastre as modalidades (plantões, diaristas, sobreaviso…)</p>
              {canWrite && (
                <Button size="sm" className="mt-2" onClick={abrirNovaModalidade}>
                  <Plus size={14} /> Adicionar primeira modalidade
                </Button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-ds-border">
              <table className="w-full text-xs min-w-[600px]">
                <thead>
                  <tr className="bg-ds-surface border-b border-ds-border">
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Nome</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Tipo</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Turno</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Horário</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-bold uppercase tracking-wider text-ds-light">Horas/Meta</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-bold uppercase tracking-wider text-ds-light">Valor</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-bold uppercase tracking-wider text-ds-light">Deslocamento</th>
                    <th className="px-3 py-2.5 text-center text-[10px] font-bold uppercase tracking-wider text-ds-light">Status</th>
                    {canWrite && <th className="px-3 py-2.5 w-16" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-ds-border">
                  {modalidades.map(m => (
                    <tr key={m.id} className="hover:bg-ds-surface/50">
                      <td className="px-3 py-2 font-medium text-ds-text">{m.nome}</td>
                      <td className="px-3 py-2">
                        {(() => {
                          const b = tipoBadgeInfo(m)
                          return (
                            <span className={['px-1.5 py-0.5 rounded text-[10px] font-bold', b.cls].join(' ')}>
                              {b.label}
                            </span>
                          )
                        })()}
                      </td>
                      <td className="px-3 py-2">
                        {m.turno ? (
                          <span className={[
                            'inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold',
                            m.turno === 'DIURNO'
                              ? 'bg-yellow-50 text-yellow-700'
                              : 'bg-indigo-50 text-indigo-700',
                          ].join(' ')}>
                            {m.turno === 'DIURNO' ? <Sun size={10} /> : <Moon size={10} />}
                            {m.turno}
                          </span>
                        ) : <span className="text-ds-light">—</span>}
                      </td>
                      <td className="px-3 py-2 text-ds-mid">{m.horario ?? '—'}</td>
                      <td className="px-3 py-2 text-right tabular-nums">
                        {m.tipo === 'META' ? (metaResumo(m) || '—') : (m.horas != null ? `${m.horas}h` : '—')}
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums font-semibold text-ds-text">
                        {formatBRL(m.valorCentavos)}
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums text-ds-mid">
                        {m.deslocamentoCentavos > 0 ? formatBRL(m.deslocamentoCentavos) : '—'}
                      </td>
                      <td className="px-3 py-2 text-center">
                        <span className={[
                          'px-1.5 py-0.5 rounded text-[10px] font-bold',
                          m.ativo ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500',
                        ].join(' ')}>
                          {m.ativo ? 'ATIVO' : 'INATIVO'}
                        </span>
                      </td>
                      {canWrite && (
                        <td className="px-3 py-2">
                          <div className="flex items-center gap-1 justify-end">
                            <button
                              type="button"
                              onClick={() => abrirEditarModalidade(m)}
                              className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                              title="Editar"
                            >
                              <Pencil size={12} />
                            </button>
                            <button
                              type="button"
                              onClick={() => removerModalidade(m.id, m.nome)}
                              className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors"
                              title="Remover"
                            >
                              <Trash2 size={12} />
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Form de nova / editar modalidade */}
          {canWrite && modForm && (
            <div className="rounded-xl border border-primary/30 bg-primary-50/30 p-4">
              <p className="text-xs font-bold text-ds-mid uppercase mb-3">
                {editingModId ? 'Editar modalidade' : 'Nova modalidade'}
              </p>
              <ModalidadeFormInline
                form={modForm}
                onChange={patch => setModForm(f => f ? { ...f, ...patch } : f)}
                onSave={salvarModalidade}
                onCancel={cancelarModalidade}
                saving={modSaving}
                isNew={!editingModId}
              />
            </div>
          )}

          {/* Botão adicionar modalidade */}
          {canWrite && !modForm && modalidades.length > 0 && (
            <button
              type="button"
              onClick={abrirNovaModalidade}
              className="flex items-center gap-2 justify-center w-full py-2.5 rounded-xl border-2 border-dashed border-ds-border text-ds-light hover:border-primary hover:text-primary text-xs font-semibold transition-all"
            >
              <Plus size={14} /> Nova Modalidade
            </button>
          )}
        </div>
      )}

      {/* ── Aba: Ocorrências ── */}
      {aba === 'ocorrencias' && (
        <div className="flex flex-col gap-3">
          {ocErr && (
            <Alert variant="error" onClose={() => setOcErr(null)}>{ocErr}</Alert>
          )}

          {ocLoading ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : ocorrencias.length === 0 && !ocForm ? (
            <div className="flex flex-col items-center py-10 gap-2 text-ds-light">
              <Tag size={36} className="opacity-25" />
              <p className="text-sm font-semibold">Nenhuma ocorrência cadastrada</p>
              <p className="text-xs">Ex: Feriado (+50%), Sobreaviso (+R$ 150,00), Observação (sem valor)</p>
              {canWrite && (
                <Button size="sm" className="mt-2" onClick={abrirNovaOcorrencia}>
                  <Plus size={14} /> Adicionar primeira ocorrência
                </Button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-ds-border">
              <table className="w-full text-xs min-w-[560px]">
                <thead>
                  <tr className="bg-ds-surface border-b border-ds-border">
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Nome</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Tipo</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-bold uppercase tracking-wider text-ds-light">Percentual</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-bold uppercase tracking-wider text-ds-light">Valor Fixo</th>
                    <th className="px-3 py-2.5 text-center text-[10px] font-bold uppercase tracking-wider text-ds-light">Status</th>
                    {canWrite && <th className="px-3 py-2.5 w-16" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-ds-border">
                  {ocorrencias.map(o => (
                    <tr key={o.id} className="hover:bg-ds-surface/50">
                      <td className="px-3 py-2 font-medium text-ds-text">{o.nome}</td>
                      <td className="px-3 py-2">
                        {(() => {
                          const b = ocorrenciaTipoBadge(o)
                          return (
                            <span className={['px-1.5 py-0.5 rounded text-[10px] font-bold', b.cls].join(' ')}>
                              {b.label}
                            </span>
                          )
                        })()}
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums">
                        {o.valorPercentual != null ? `${o.valorPercentual}%` : <span className="text-ds-light">—</span>}
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums">
                        {o.valorCentavos != null ? formatBRL(o.valorCentavos) : <span className="text-ds-light">—</span>}
                      </td>
                      <td className="px-3 py-2 text-center">
                        <span className={[
                          'px-1.5 py-0.5 rounded text-[10px] font-bold',
                          o.ativo ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500',
                        ].join(' ')}>
                          {o.ativo ? 'ATIVO' : 'INATIVO'}
                        </span>
                      </td>
                      {canWrite && (
                        <td className="px-3 py-2">
                          <div className="flex items-center gap-1 justify-end">
                            <button
                              type="button"
                              onClick={() => abrirEditarOcorrencia(o)}
                              className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                              title="Editar"
                            >
                              <Pencil size={12} />
                            </button>
                            <button
                              type="button"
                              onClick={() => removerOcorrencia(o.id, o.nome)}
                              className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors"
                              title="Remover"
                            >
                              <Trash2 size={12} />
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Form de nova / editar ocorrência */}
          {canWrite && ocForm && (
            <div className="rounded-xl border border-primary/30 bg-primary-50/30 p-4">
              <p className="text-xs font-bold text-ds-mid uppercase mb-3">
                {editingOcId ? 'Editar ocorrência' : 'Nova ocorrência'}
              </p>
              <OcorrenciaFormInline
                form={ocForm}
                onChange={patch => setOcForm(f => f ? { ...f, ...patch } : f)}
                onSave={salvarOcorrencia}
                onCancel={cancelarOcorrencia}
                saving={ocSaving}
                isNew={!editingOcId}
              />
            </div>
          )}

          {/* Botão adicionar ocorrência */}
          {canWrite && !ocForm && ocorrencias.length > 0 && (
            <button
              type="button"
              onClick={abrirNovaOcorrencia}
              className="flex items-center gap-2 justify-center w-full py-2.5 rounded-xl border-2 border-dashed border-ds-border text-ds-light hover:border-primary hover:text-primary text-xs font-semibold transition-all"
            >
              <Plus size={14} /> Nova Ocorrência
            </button>
          )}
        </div>
      )}
    </Modal>
  )
}
