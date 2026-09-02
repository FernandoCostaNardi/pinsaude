import { useCallback, useEffect, useState } from 'react'
import {
  Plus, Pencil, Trash2, Layers, ChevronDown, ChevronRight,
  Moon, Sun, Loader2, FolderOpen, Tag, Clock,
} from 'lucide-react'
import { Modal, Button, Input, Alert, Spinner } from '@pinsaude/ui'
import {
  Tomador,
  TomadorGrupoFaturamento,
  TomadorGrupoFaturamentoRequest,
  TomadorHorarioPadrao,
  TomadorHorarioPadraoRequest,
  TomadorModalidade,
  TomadorModalidadeRequest,
  TomadorOcorrencia,
  TomadorOcorrenciaRequest,
  TomadorServicoOperacional,
  TomadorServicoOperacionalRequest,
  tomadoresApi,
} from '../api/tomadoresApi'
import { TIPO_ESCALA_LABEL, isTipoModalidadeFixa, type TipoEscala } from '../utils/tipoEscala'

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

// PINSAUDE-13.24: Tipo de Escala colapsado de 3 tipos (PLANTAO/MENSAL/META) para 2
// (PLANTONISTA/DIARISTA), depois estendido pra 4 (EVOLUCIONISTA/EVOLUCIONISTA_FDS) — mesmo
// vocabulário já usado no campo "Tipo de Escala" da Frequência.
const TIPO_BADGE_CLS: Record<TipoEscala, string> = {
  PLANTONISTA: 'bg-blue-50 text-blue-700',
  DIARISTA: 'bg-purple-50 text-purple-700',
  EVOLUCIONISTA: 'bg-green-50 text-green-700',
  EVOLUCIONISTA_FDS: 'bg-orange-50 text-orange-700',
}
function tipoBadgeInfo(m: TomadorModalidade): { label: string; cls: string } {
  return { label: m.tipo, cls: TIPO_BADGE_CLS[m.tipo] ?? 'bg-gray-50 text-gray-700' }
}

// Badge do tipo de valor na tabela de ocorrências.
function ocorrenciaTipoBadge(o: TomadorOcorrencia): { label: string; cls: string } {
  if (o.tipoValor === 'PERCENTUAL') return { label: 'PERCENTUAL', cls: 'bg-green-50 text-green-700' }
  if (o.tipoValor === 'FIXO') return { label: 'FIXO', cls: 'bg-orange-50 text-orange-700' }
  return { label: 'SEM VALOR', cls: 'bg-gray-100 text-gray-500' }
}

// PINSAUDE-13.20: os presets de "preencher rápido" (turno × horas × horário) deixaram de ser um
// array fixo global — agora vêm da API, configuráveis por tomador (aba "Preenchimento Rápido").
function formatHorarioPadraoLabel(h: TomadorHorarioPadrao): string {
  const icone = h.turno === 'DIURNO' ? '☀️' : '🌙'
  const turnoLabel = h.turno === 'DIURNO' ? 'Diurno' : 'Noturno'
  return `${icone} ${turnoLabel} ${h.horas}h — ${h.horario}`
}

// ─── Form state types ─────────────────────────────────────────────────────────

interface GrupoForm {
  nome: string
  descricaoNota: string
  ativo: boolean
}

// PINSAUDE-13.24: modo do formulário (UI) mapeia 1:1 no tipo do backend — sem mais a heurística
// com perda que existia entre os 4 modos antigos (Por Plantão/Valor Fixo Mensal/Por Horas/Por Mês)
// e os 3 tipos do backend (PLANTAO/MENSAL/META). Estendido pra 4 tipos: EVOLUCIONISTA reaproveita
// as mesmas regras de campos/valor do DIARISTA (tipo "fixo"); EVOLUCIONISTA_FDS reaproveita as
// mesmas regras do PLANTONISTA (tipo "por lançamento") — apesar do nome parecido com
// EVOLUCIONISTA, o comportamento é o oposto (correção pós-implantação, ver TipoEscala).
interface ModalidadeForm {
  nome: string
  tipo: TipoEscala
  turno: 'DIURNO' | 'NOTURNO' | ''
  horario: string
  horasStr: string
  valorStr: string
  deslocamentoStr: string
  ativo: boolean
  // Campo dos tipos "fixos" (Diarista/Evolucionista) — carga horária semanal obrigatória
  horasSemanaisStr: string
}

function emptyGrupoForm(): GrupoForm {
  return { nome: '', descricaoNota: '', ativo: true }
}

function emptyModalidadeForm(): ModalidadeForm {
  return {
    nome: '', tipo: 'PLANTONISTA', turno: '', horario: '',
    horasStr: '', valorStr: '', deslocamentoStr: '', ativo: true,
    horasSemanaisStr: '',
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

interface HorarioPadraoForm {
  turno: 'DIURNO' | 'NOTURNO'
  horasStr: string
  horario: string
  ativo: boolean
}

function emptyHorarioPadraoForm(): HorarioPadraoForm {
  return { turno: 'DIURNO', horasStr: '', horario: '', ativo: true }
}

// Cadastro dedicado de Setores Operacionais (catálogo por tomador, com categoria própria) —
// separado do fluxo de Grupos, que passa a só selecionar entre os setores já cadastrados aqui.
//
// modalidadeIds: pedido do cliente — o setor define explicitamente qual(is) Modalidade(s)
// daquele setor (pode ter mais de uma — usadas pra derivar o Tipo de Escala da Frequência
// automaticamente quando só há 1, ou oferecer a escolha quando há mais de 1, sem precisar
// recadastrar nada). O campo "Tipo de Escala" do PDF é 100% calculado a partir do Tipo de Escala
// da frequência + nome do setor — sem texto customizável (campo "Texto no PDF" removido).
interface SetorForm {
  nome: string
  categoria: string
  ativo: boolean
  modalidadeIds: string[]
}

function emptySetorForm(): SetorForm {
  return { nome: '', categoria: '', ativo: true, modalidadeIds: [] }
}

// Categoria "Sem categoria" agrupa setores sem esse campo preenchido — usado tanto no cadastro
// (aba Setores Operacionais) quanto na seleção por grupo (aba Grupos).
const SEM_CATEGORIA = 'Sem categoria'

function agruparPorCategoria(setores: TomadorServicoOperacional[]): [string, TomadorServicoOperacional[]][] {
  const porCategoria = new Map<string, TomadorServicoOperacional[]>()
  for (const s of setores) {
    const chave = s.categoria?.trim() || SEM_CATEGORIA
    if (!porCategoria.has(chave)) porCategoria.set(chave, [])
    porCategoria.get(chave)!.push(s)
  }
  for (const lista of porCategoria.values()) lista.sort((a, b) => a.nome.localeCompare(b.nome))
  return Array.from(porCategoria.entries()).sort(([a], [b]) => {
    if (a === SEM_CATEGORIA) return 1
    if (b === SEM_CATEGORIA) return -1
    return a.localeCompare(b)
  })
}

// EVOLUCIONISTA se comporta como DIARISTA por trás dos panos (modalidade fixa, horas semanais);
// EVOLUCIONISTA_FDS se comporta como PLANTONISTA (modalidade por lançamento, turno/horas) —
// apesar do nome parecido com EVOLUCIONISTA, o comportamento é o oposto. Ver isTipoModalidadeFixa.
const MODALIDADE_TIPOS: { modo: TipoEscala; titulo: string; sub: string }[] = [
  { modo: 'PLANTONISTA', titulo: TIPO_ESCALA_LABEL.PLANTONISTA, sub: 'valor por plantão; turno, horário e horas obrigatórios' },
  { modo: 'DIARISTA', titulo: TIPO_ESCALA_LABEL.DIARISTA, sub: 'valor mensal fixo; carga horária semanal obrigatória' },
  { modo: 'EVOLUCIONISTA', titulo: TIPO_ESCALA_LABEL.EVOLUCIONISTA, sub: 'valor mensal fixo; carga horária semanal obrigatória' },
  { modo: 'EVOLUCIONISTA_FDS', titulo: TIPO_ESCALA_LABEL.EVOLUCIONISTA_FDS, sub: 'valor por plantão; turno, horário e horas obrigatórios' },
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
  form, onChange, onSave, onCancel, saving, isNew, horariosPadrao,
}: {
  form: ModalidadeForm
  onChange: (patch: Partial<ModalidadeForm>) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
  isNew: boolean
  horariosPadrao: TomadorHorarioPadrao[]
}) {
  const SELECT_CLS = 'w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary bg-white'
  const isTipoFixo = isTipoModalidadeFixa(form.tipo)
  // "por lançamento" (turno/horário/horas) = PLANTONISTA e EVOLUCIONISTA_FDS
  const isPorLancamento = !isTipoFixo
  const valorLabel = isTipoFixo ? 'Valor Mensal *' : 'Valor *'

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <Input
            label="Nome da modalidade *"
            value={form.nome}
            onChange={e => onChange({ nome: e.target.value })}
            placeholder="ex: Plantão 12h Noturno, Diarista 40h Semanais"
          />
        </div>

        {/* Tipo de Escala — decide quais campos aparecem abaixo (mesmo vocabulário da Frequência) */}
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">Tipo de Escala *</label>
          <div className="grid grid-cols-2 gap-2">
            {MODALIDADE_TIPOS.map(t => (
              <button
                key={t.modo}
                type="button"
                onClick={() => onChange({ tipo: t.modo })}
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

        {/* ── Tipos "por lançamento" (Plantonista/Evolucionista FDS): turno/horas/horário, todos obrigatórios ── */}
        {isPorLancamento && (
          <>
            {/* Presets rápidos — preenchem turno + horas + horário de uma vez, mas os 3 campos abaixo continuam livres para edição.
                Configuráveis por tomador na aba "Preenchimento Rápido" (PINSAUDE-13.20). */}
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Preencher rápido (opcional)</label>
              {horariosPadrao.length === 0 ? (
                <p className="text-[11px] text-ds-light">
                  Nenhum preenchimento rápido cadastrado para este tomador — configure na aba "Preenchimento Rápido".
                </p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {horariosPadrao.map(c => (
                    <button
                      key={c.id}
                      type="button"
                      onClick={() => onChange({ turno: c.turno, horasStr: String(c.horas), horario: c.horario })}
                      className="px-2.5 py-1 rounded-lg border border-gray-300 text-[11px] text-gray-700 hover:border-primary hover:text-primary transition-colors"
                    >
                      {formatHorarioPadraoLabel(c)}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Turno *</label>
              <select
                value={form.turno}
                onChange={e => onChange({ turno: e.target.value as 'DIURNO' | 'NOTURNO' | '' })}
                className={SELECT_CLS}
              >
                <option value="">Selecione...</option>
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
              <label className="block text-sm font-medium text-gray-700 mb-1">Horário *</label>
              <input
                value={form.horario}
                onChange={e => onChange({ horario: e.target.value })}
                placeholder="ex: 07:00 as 17:00"
                className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
              />
            </div>
          </>
        )}

        {/* ── Tipos "fixos" (Diarista/Evolucionista): carga horária semanal obrigatória, pagam
             valor mensal fixo ── */}
        {isTipoFixo && (
          <div className="col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">Horas semanais obrigatórias *</label>
            <input
              type="number"
              step="0.5"
              min="0.5"
              value={form.horasSemanaisStr}
              onChange={e => onChange({ horasSemanaisStr: e.target.value })}
              placeholder="ex: 40"
              className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
            />
            <p className="text-[11px] text-ds-light mt-1">
              O valor mensal é pago uma única vez por frequência, independente de quantos dias forem lançados. O acompanhamento
              semanal de horas trabalhadas aparece no lançamento da frequência.
            </p>
          </div>
        )}

        <div>
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
        {/* Deslocamento é reembolso por lançamento — ortogonal ao valor do plantão/mês, existe nos 2 tipos */}
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

function HorarioPadraoFormInline({
  form, onChange, onSave, onCancel, saving, isNew,
}: {
  form: HorarioPadraoForm
  onChange: (patch: Partial<HorarioPadraoForm>) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
  isNew: boolean
}) {
  const SELECT_CLS = 'w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary bg-white'

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Turno *</label>
          <select
            value={form.turno}
            onChange={e => onChange({ turno: e.target.value as 'DIURNO' | 'NOTURNO' })}
            className={SELECT_CLS}
          >
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
            placeholder="ex: 6"
            className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
          />
        </div>
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">Horário *</label>
          <input
            value={form.horario}
            onChange={e => onChange({ horario: e.target.value })}
            placeholder="ex: 07:00 as 13:00"
            className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 px-2.5 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
          />
        </div>
        <div className="col-span-2">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={form.ativo}
              onChange={e => onChange({ ativo: e.target.checked })}
              className="rounded border-gray-300 text-primary focus:ring-primary"
            />
            <span className="text-sm font-medium text-gray-700">Preset ativo</span>
          </label>
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-2 border-t border-ds-border">
        <Button variant="ghost" size="sm" onClick={onCancel} disabled={saving}>
          Cancelar
        </Button>
        <Button size="sm" onClick={onSave} loading={saving}>
          {isNew ? 'Adicionar Preset' : 'Salvar Alterações'}
        </Button>
      </div>
    </div>
  )
}

// Switch estilo pílula (padrão do resto do app, ao invés de checkbox nativo). Sem `label`,
// renderiza só o botão (pra compor livremente em listas que já têm seu próprio texto ao lado —
// evita aninhar <label> dentro de <label>, que é inválido).
function Switch({
  checked, onChange, label, disabled,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  label?: string
  disabled?: boolean
}) {
  const button = (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={[
        'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors',
        'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:ring-offset-1',
        disabled ? 'opacity-50 cursor-not-allowed' : '',
        checked ? 'bg-primary' : 'bg-gray-300',
      ].join(' ')}
    >
      <span
        className={[
          'inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform',
          checked ? 'translate-x-[18px]' : 'translate-x-0.5',
        ].join(' ')}
      />
    </button>
  )
  if (!label) return button
  return (
    <label className="flex items-center gap-2.5 cursor-pointer select-none">
      {button}
      <span className="text-sm font-medium text-gray-700">{label}</span>
    </label>
  )
}

// Combobox de texto livre com sugestões — clicar/focar mostra todas as categorias já
// cadastradas (filtradas conforme o usuário digita); escolher uma preenche o campo; digitar
// algo que não existe ainda funciona normalmente (cria uma categoria nova ao salvar).
function CategoriaCombobox({
  value, onChange, categoriasExistentes,
}: {
  value: string
  onChange: (v: string) => void
  categoriasExistentes: string[]
}) {
  const [open, setOpen] = useState(false)
  const filtradas = categoriasExistentes.filter(c =>
    !value.trim() || c.toLowerCase().includes(value.trim().toLowerCase())
  )
  return (
    <div className="relative">
      <input
        value={value}
        onChange={e => onChange(e.target.value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        placeholder="ex: Emergência, UTI, Ambulatório"
        className="w-full h-9 rounded-lg border border-gray-300 text-sm text-gray-900 pl-2.5 pr-8 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary bg-white"
      />
      <ChevronDown size={14} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-ds-light pointer-events-none" />
      {open && categoriasExistentes.length > 0 && (
        <div className="absolute z-10 mt-1 w-full max-h-48 overflow-y-auto rounded-lg border border-ds-border bg-white shadow-lg py-1">
          {filtradas.length === 0 ? (
            <p className="px-3 py-2 text-xs text-ds-light">Nenhuma categoria encontrada — "{value}" será criada</p>
          ) : (
            filtradas.map(c => (
              <button
                key={c}
                type="button"
                onMouseDown={e => e.preventDefault()}
                onClick={() => { onChange(c); setOpen(false) }}
                className="w-full text-left px-3 py-1.5 text-xs text-ds-text hover:bg-primary-50 hover:text-primary transition-colors"
              >
                {c}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}

function SetorFormInline({
  form, onChange, onSave, onCancel, saving, isNew, categoriasExistentes, modalidades,
}: {
  form: SetorForm
  onChange: (patch: Partial<SetorForm>) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
  isNew: boolean
  categoriasExistentes: string[]
  modalidades: TomadorModalidade[]
}) {
  // Modalidade(s) já selecionadas mas hoje inativas (ex: setor cadastrado antes de uma delas ser
  // desativada) — injetadas no topo da lista pra não sumirem dos checkboxes (mesmo padrão já
  // usado em outros formulários deste componente para modalidade/ocorrência inativa).
  const modalidadesSelecionadasInativas = modalidades.filter(m => form.modalidadeIds.includes(m.id) && !m.ativo)
  const modalidadeOptions = [...modalidadesSelecionadasInativas, ...modalidades.filter(m => m.ativo)]

  function toggleModalidade(modalidadeId: string) {
    const novosIds = form.modalidadeIds.includes(modalidadeId)
      ? form.modalidadeIds.filter(id => id !== modalidadeId)
      : [...form.modalidadeIds, modalidadeId]
    onChange({ modalidadeIds: novosIds })
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <Input
            label="Nome do setor *"
            value={form.nome}
            onChange={e => onChange({ nome: e.target.value })}
            placeholder="ex: Emergência Cardiológica"
          />
        </div>
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Categoria
            <span className="ml-1 text-xs font-normal text-ds-light">(opcional — agrupa os setores na seleção por grupo)</span>
          </label>
          <CategoriaCombobox
            value={form.categoria}
            onChange={v => onChange({ categoria: v })}
            categoriasExistentes={categoriasExistentes}
          />
        </div>
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Modalidade(s) *
            <span className="ml-1 text-xs font-normal text-ds-light">
              (define o Tipo de Escala da Frequência automaticamente — marque mais de uma se o setor puder usar modalidades diferentes; a Nova Frequência pergunta qual usar quando houver mais de uma)
            </span>
          </label>
          {modalidades.length === 0 ? (
            <p className="text-[11px] text-ds-light">
              Nenhuma modalidade cadastrada — configure ao menos uma na aba "Modalidades" antes de cadastrar o setor.
            </p>
          ) : (
            <div className="space-y-2 max-h-40 overflow-y-auto border border-gray-300 rounded-lg p-2.5">
              {modalidadeOptions.map(m => (
                <Switch
                  key={m.id}
                  checked={form.modalidadeIds.includes(m.id)}
                  onChange={() => toggleModalidade(m.id)}
                  label={`${m.nome} (${TIPO_ESCALA_LABEL[m.tipo]}${!m.ativo ? ' — inativa' : ''})`}
                />
              ))}
            </div>
          )}
        </div>
        <div className="col-span-2">
          <Switch checked={form.ativo} onChange={v => onChange({ ativo: v })} label="Setor ativo" />
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-2 border-t border-ds-border">
        <Button variant="ghost" size="sm" onClick={onCancel} disabled={saving}>
          Cancelar
        </Button>
        <Button size="sm" onClick={onSave} loading={saving}>
          {isNew ? 'Adicionar Setor' : 'Salvar Alterações'}
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
  const [aba, setAba] = useState<'grupos' | 'setores' | 'modalidades' | 'ocorrencias' | 'horarios'>('grupos')

  // ── Grupos ────────────────────────────────────────────────────────────────
  const [grupos, setGrupos] = useState<TomadorGrupoFaturamento[]>([])
  const [gruposLoading, setGruposLoading] = useState(false)
  const [grupoErr, setGrupoErr] = useState<string | null>(null)
  const [expandedGrupos, setExpandedGrupos] = useState<Set<string>>(new Set())

  const [grupoForm, setGrupoForm] = useState<GrupoForm | null>(null)
  const [editingGrupoId, setEditingGrupoId] = useState<string | null>(null)
  const [grupoSaving, setGrupoSaving] = useState(false)

  // Chave "grupoId:setorId" do checkbox em vôo (evita clique duplo enquanto a chamada não volta).
  const [setorTogglingKey, setSetorTogglingKey] = useState<string | null>(null)

  // ── Setores Operacionais (catálogo por tomador, com categoria própria) ─────
  const [todosSetores, setTodosSetores] = useState<TomadorServicoOperacional[]>([])
  const [setoresLoading, setSetoresLoading] = useState(false)
  const [setorErr, setSetorErr] = useState<string | null>(null)

  const [setorForm, setSetorForm] = useState<SetorForm | null>(null)
  const [editingSetorId, setEditingSetorId] = useState<string | null>(null)
  const [setorSaving, setSetorSaving] = useState(false)

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

  // ── Preenchimento rápido de turno ─────────────────────────────────────────
  const [horariosPadrao, setHorariosPadrao] = useState<TomadorHorarioPadrao[]>([])
  const [hpLoading, setHpLoading] = useState(false)
  const [hpErr, setHpErr] = useState<string | null>(null)

  const [hpForm, setHpForm] = useState<HorarioPadraoForm | null>(null)
  const [editingHpId, setEditingHpId] = useState<string | null>(null)
  const [hpSaving, setHpSaving] = useState(false)

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

  const carregarTodosSetores = useCallback(async () => {
    setSetoresLoading(true)
    try {
      setTodosSetores(await tomadoresApi.listarServicosOperacionais(tomador.id))
    } catch (e) {
      setSetorErr(e instanceof Error ? e.message : 'Erro ao carregar setores operacionais')
    } finally {
      setSetoresLoading(false)
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

  const carregarHorariosPadrao = useCallback(async () => {
    setHpLoading(true)
    try {
      setHorariosPadrao(await tomadoresApi.listarHorariosPadrao(tomador.id))
    } catch (e) {
      setHpErr(e instanceof Error ? e.message : 'Erro ao carregar preenchimento rápido')
    } finally {
      setHpLoading(false)
    }
  }, [tomador.id])

  useEffect(() => {
    carregarGrupos()
    carregarTodosSetores()
    carregarModalidades()
    carregarOcorrencias()
    carregarHorariosPadrao()
  }, [carregarGrupos, carregarTodosSetores, carregarModalidades, carregarOcorrencias, carregarHorariosPadrao])

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
    if (!window.confirm(`Remover o grupo "${nome}"? O vínculo com os setores operacionais será removido (os setores continuam disponíveis para outros grupos).`)) return
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

  // ── Seleção de setores dentro de um Grupo (marca/desmarca do catálogo) ─────

  // O catálogo de setores é cadastrado à parte (aba "Setores Operacionais"); aqui só
  // marca/desmarca quais setores já cadastrados pertencem a este grupo — nunca cria/edita/apaga
  // o setor em si.
  async function toggleSetorNoGrupo(grupoId: string, setorId: string, marcar: boolean) {
    const key = `${grupoId}:${setorId}`
    setSetorTogglingKey(key)
    setGrupoErr(null)
    try {
      if (marcar) {
        await tomadoresApi.adicionarSetorAoGrupo(tomador.id, grupoId, setorId)
      } else {
        await tomadoresApi.removerSetorDoGrupo(tomador.id, grupoId, setorId)
      }
      await carregarGrupos()
    } catch (e) {
      setGrupoErr(e instanceof Error ? e.message : 'Erro ao atualizar setores deste grupo')
    } finally {
      setSetorTogglingKey(null)
    }
  }

  // ── Setores Operacionais CRUD (catálogo por tomador, com categoria própria) ─

  function abrirNovoSetor() {
    setSetorForm(emptySetorForm())
    setEditingSetorId(null)
    setSetorErr(null)
  }

  function abrirEditarSetor(s: TomadorServicoOperacional) {
    setSetorForm({
      nome: s.nome,
      categoria: s.categoria ?? '',
      ativo: s.ativo,
      modalidadeIds: s.modalidades.map(m => m.id),
    })
    setEditingSetorId(s.id)
    setSetorErr(null)
  }

  function cancelarSetor() {
    setSetorForm(null)
    setEditingSetorId(null)
    setSetorErr(null)
  }

  async function salvarSetor() {
    if (!setorForm) return
    if (!setorForm.nome.trim()) {
      setSetorErr('Preencha o nome do setor')
      return
    }
    if (setorForm.modalidadeIds.length === 0) {
      setSetorErr('Selecione ao menos uma modalidade do setor')
      return
    }
    const req: TomadorServicoOperacionalRequest = {
      nome: setorForm.nome.trim(),
      categoria: setorForm.categoria.trim() || null,
      ativo: setorForm.ativo,
      modalidadeIds: setorForm.modalidadeIds,
    }
    setSetorSaving(true)
    setSetorErr(null)
    try {
      if (editingSetorId) {
        await tomadoresApi.atualizarServicoOperacional(tomador.id, editingSetorId, req)
      } else {
        await tomadoresApi.criarServicoOperacional(tomador.id, req)
      }
      cancelarSetor()
      // Recarrega grupos também: nome/status do setor pode aparecer na lista de cada grupo.
      await Promise.all([carregarTodosSetores(), carregarGrupos()])
    } catch (e) {
      setSetorErr(e instanceof Error ? e.message : 'Erro ao salvar setor')
    } finally {
      setSetorSaving(false)
    }
  }

  // Remove o setor do catálogo inteiro (não só de um grupo) — bloqueado pelo backend (409) se
  // já houver frequência médica lançada com este setor.
  async function removerSetorCatalogo(id: string, nome: string) {
    if (!window.confirm(`Remover o setor "${nome}" do catálogo? Ele será desvinculado de todos os grupos.`)) return
    setSetorErr(null)
    try {
      await tomadoresApi.removerServicoOperacional(tomador.id, id)
      await Promise.all([carregarTodosSetores(), carregarGrupos()])
    } catch (e) {
      setSetorErr(e instanceof Error ? e.message : 'Erro ao remover setor')
    }
  }

  // ── Modalidades CRUD ──────────────────────────────────────────────────────

  function abrirNovaModalidade() {
    setModForm(emptyModalidadeForm())
    setEditingModId(null)
    setModErr(null)
  }

  function abrirEditarModalidade(m: TomadorModalidade) {
    // PINSAUDE-13.24: tipo do backend mapeia 1:1 no modo do form — sem mais heurística de conversão.
    setModForm({
      nome: m.nome,
      tipo: m.tipo,
      turno: m.turno ?? '',
      horario: m.horario ?? '',
      horasStr: m.horas != null ? String(m.horas) : '',
      valorStr: centavosParaBrl(m.valorCentavos),
      deslocamentoStr: m.deslocamentoCentavos > 0 ? centavosParaBrl(m.deslocamentoCentavos) : '',
      ativo: m.ativo,
      horasSemanaisStr: m.horasSemanais != null ? String(m.horasSemanais) : '',
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
    const isPorLancamento = !isTipoModalidadeFixa(modo)
    const valorCentavos = parseCentavos(modForm.valorStr)

    if (!modForm.nome.trim() || valorCentavos <= 0) {
      setModErr('Preencha nome e valor corretamente')
      return
    }

    // Monta o request no contrato do backend (tipo PLANTONISTA/DIARISTA/EVOLUCIONISTA/
    // EVOLUCIONISTA_FDS — mapeamento 1:1, sem heurística de conversão). Validação espelha
    // TomadorService.aplicarCamposPorTipo (backend) — EVOLUCIONISTA_FDS reaproveita as mesmas
    // regras de campo do Plantonista (não do Diarista/Evolucionista, apesar do nome parecido).
    let req: TomadorModalidadeRequest
    const base = {
      nome: modForm.nome.trim(),
      valorCentavos,
      deslocamentoCentavos: parseCentavos(modForm.deslocamentoStr),
      ativo: modForm.ativo,
      turno: null as 'DIURNO' | 'NOTURNO' | null,
      horario: null as string | null,
      horas: null as number | null,
      horasSemanais: null as number | null,
    }

    if (isPorLancamento) {
      const horas = parseFloat(modForm.horasStr.replace(',', '.'))
      if (!modForm.turno) {
        setModErr(`Turno é obrigatório para modalidade do tipo ${TIPO_ESCALA_LABEL[modo]}`)
        return
      }
      if (!modForm.horario.trim()) {
        setModErr(`Horário é obrigatório para modalidade do tipo ${TIPO_ESCALA_LABEL[modo]}`)
        return
      }
      if (isNaN(horas) || horas <= 0) {
        setModErr('Preencha a quantidade de horas corretamente')
        return
      }
      req = {
        ...base, tipo: modo, horas,
        turno: modForm.turno,
        horario: modForm.horario.trim(),
      }
    } else {
      const horasSemanais = modForm.horasSemanaisStr.trim() ? parseFloat(modForm.horasSemanaisStr.replace(',', '.')) : null
      if (horasSemanais == null || isNaN(horasSemanais) || horasSemanais <= 0) {
        setModErr('Preencha as horas semanais corretamente')
        return
      }
      req = { ...base, tipo: modo, horasSemanais }
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

  // ── Preenchimento rápido CRUD ────────────────────────────────────────────────

  function abrirNovoHorarioPadrao() {
    setHpForm(emptyHorarioPadraoForm())
    setEditingHpId(null)
    setHpErr(null)
  }

  function abrirEditarHorarioPadrao(h: TomadorHorarioPadrao) {
    setHpForm({ turno: h.turno, horasStr: String(h.horas), horario: h.horario, ativo: h.ativo })
    setEditingHpId(h.id)
    setHpErr(null)
  }

  function cancelarHorarioPadrao() {
    setHpForm(null)
    setEditingHpId(null)
    setHpErr(null)
  }

  async function salvarHorarioPadrao() {
    if (!hpForm) return
    const horas = parseFloat(hpForm.horasStr.replace(',', '.'))
    if (isNaN(horas) || horas <= 0) {
      setHpErr('Preencha as horas corretamente')
      return
    }
    if (!hpForm.horario.trim()) {
      setHpErr('Preencha o horário')
      return
    }

    const ordem = editingHpId
      ? (horariosPadrao.find(h => h.id === editingHpId)?.ordem ?? 1)
      : (horariosPadrao.length > 0 ? Math.max(...horariosPadrao.map(h => h.ordem)) + 1 : 1)

    const req: TomadorHorarioPadraoRequest = {
      turno: hpForm.turno,
      horas,
      horario: hpForm.horario.trim(),
      ordem,
      ativo: hpForm.ativo,
    }

    setHpSaving(true)
    setHpErr(null)
    try {
      if (editingHpId) {
        await tomadoresApi.atualizarHorarioPadrao(tomador.id, editingHpId, req)
      } else {
        await tomadoresApi.criarHorarioPadrao(tomador.id, req)
      }
      cancelarHorarioPadrao()
      await carregarHorariosPadrao()
    } catch (e) {
      setHpErr(e instanceof Error ? e.message : 'Erro ao salvar preenchimento rápido')
    } finally {
      setHpSaving(false)
    }
  }

  async function removerHorarioPadrao(id: string, h: TomadorHorarioPadrao) {
    if (!window.confirm(`Remover o preset "${formatHorarioPadraoLabel(h)}"?`)) return
    setHpErr(null)
    try {
      await tomadoresApi.removerHorarioPadrao(tomador.id, id)
      await carregarHorariosPadrao()
    } catch (e) {
      setHpErr(e instanceof Error ? e.message : 'Erro ao remover preenchimento rápido')
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
          ['grupos', 'Grupos'],
          ['setores', 'Setores Operacionais'],
          ['modalidades', 'Modalidades (Tabela de Preços)'],
          ['ocorrencias', 'Ocorrências'],
          ['horarios', 'Preenchimento Rápido'],
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

                            {/* Setores — seleção a partir do catálogo (aba "Setores Operacionais"),
                                agrupados por categoria. Nada aqui cria/edita/apaga o setor em si. */}
                            <div>
                              <p className="text-[10px] text-ds-light font-semibold uppercase mb-1.5">
                                Setores operacionais ({g.servicosOperacionais.length})
                              </p>
                              {!canWrite ? (
                                g.servicosOperacionais.length === 0 ? (
                                  <p className="text-xs text-ds-light">Nenhum setor vinculado</p>
                                ) : (
                                  <div className="space-y-1">
                                    {g.servicosOperacionais.map(s => (
                                      <div key={s.id} className="flex items-center gap-2 bg-white rounded-lg px-3 py-1.5 border border-ds-border">
                                        <span className="text-xs text-ds-text">{s.nome}</span>
                                        {s.categoria && (
                                          <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-primary-50 text-primary">
                                            {s.categoria}
                                          </span>
                                        )}
                                        {!s.ativo && (
                                          <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500">
                                            INATIVO
                                          </span>
                                        )}
                                      </div>
                                    ))}
                                  </div>
                                )
                              ) : todosSetores.length === 0 ? (
                                <div className="text-xs text-ds-light bg-white rounded-lg border border-dashed border-ds-border px-3 py-2.5">
                                  Nenhum setor cadastrado ainda.{' '}
                                  <button type="button" onClick={() => setAba('setores')} className="text-primary font-semibold hover:underline">
                                    Cadastre na aba "Setores Operacionais" →
                                  </button>
                                </div>
                              ) : (
                                <div className="max-h-56 overflow-y-auto rounded-lg border border-ds-border bg-white divide-y divide-ds-border">
                                  {agruparPorCategoria(todosSetores).map(([categoria, setoresCategoria]) => (
                                    <div key={categoria} className="px-3 py-2">
                                      <p className="text-[10px] font-bold uppercase tracking-wide text-ds-light mb-1">{categoria}</p>
                                      <div className="space-y-1">
                                        {setoresCategoria.map(s => {
                                          const vinculado = g.servicosOperacionais.some(x => x.id === s.id)
                                          const key = `${g.id}:${s.id}`
                                          return (
                                            <div key={s.id} className="flex items-center gap-2">
                                              <Switch
                                                checked={vinculado}
                                                disabled={setorTogglingKey === key}
                                                onChange={marcar => toggleSetorNoGrupo(g.id, s.id, marcar)}
                                              />
                                              <span className="text-xs text-ds-text">{s.nome}</span>
                                              {!s.ativo && (
                                                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500">
                                                  INATIVO
                                                </span>
                                              )}
                                              {setorTogglingKey === key && <Loader2 size={11} className="animate-spin text-ds-light" />}
                                            </div>
                                          )
                                        })}
                                      </div>
                                    </div>
                                  ))}
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

      {/* ── Aba: Setores Operacionais (catálogo por tomador, com categoria) ── */}
      {aba === 'setores' && (
        <div className="flex flex-col gap-3">
          {setorErr && (
            <Alert variant="error" onClose={() => setSetorErr(null)}>{setorErr}</Alert>
          )}

          {setoresLoading ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : todosSetores.length === 0 && !setorForm ? (
            <div className="flex flex-col items-center py-10 gap-2 text-ds-light">
              <FolderOpen size={36} className="opacity-25" />
              <p className="text-sm font-semibold">Nenhum setor operacional cadastrado</p>
              <p className="text-xs">Ex: Emergência Cardiológica, UTI Neonatal, Ambulatório Geral</p>
              {canWrite && (
                <Button size="sm" className="mt-2" onClick={abrirNovoSetor}>
                  <Plus size={14} /> Adicionar primeiro setor
                </Button>
              )}
            </div>
          ) : (
            <div className="space-y-3">
              {agruparPorCategoria(todosSetores).map(([categoria, setoresCategoria]) => (
                <div key={categoria} className="rounded-xl border border-ds-border overflow-hidden">
                  <p className="px-3 py-1.5 text-[10px] font-bold uppercase tracking-wide text-ds-light bg-ds-surface border-b border-ds-border">
                    {categoria}
                  </p>
                  <div className="divide-y divide-ds-border">
                    {setoresCategoria.map(s => (
                      <div key={s.id} className="flex items-center justify-between px-3 py-2 hover:bg-ds-surface/50">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-medium text-ds-text">{s.nome}</span>
                            {!s.ativo && (
                              <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500">
                                INATIVO
                              </span>
                            )}
                          </div>
                          {s.modalidades.length > 0 ? (
                            <p className="text-[10px] text-ds-light mt-0.5">
                              {s.modalidades.map(m => `${m.nome} (${TIPO_ESCALA_LABEL[m.tipo]})`).join(' · ')}
                            </p>
                          ) : (
                            <p className="text-[10px] text-amber-600 mt-0.5">Sem modalidade configurada</p>
                          )}
                        </div>
                        {canWrite && (
                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              onClick={() => abrirEditarSetor(s)}
                              className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                              title="Editar"
                            >
                              <Pencil size={12} />
                            </button>
                            <button
                              type="button"
                              onClick={() => removerSetorCatalogo(s.id, s.nome)}
                              className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors"
                              title="Remover do catálogo"
                            >
                              <Trash2 size={12} />
                            </button>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Form de novo / editar setor */}
          {canWrite && setorForm && (
            <div className="rounded-xl border border-primary/30 bg-primary-50/30 p-4">
              <p className="text-xs font-bold text-ds-mid uppercase mb-3">
                {editingSetorId ? 'Editar setor' : 'Novo setor'}
              </p>
              <SetorFormInline
                key={editingSetorId ?? 'novo-setor'}
                form={setorForm}
                onChange={patch => setSetorForm(f => f ? { ...f, ...patch } : f)}
                onSave={salvarSetor}
                onCancel={cancelarSetor}
                saving={setorSaving}
                isNew={!editingSetorId}
                categoriasExistentes={Array.from(new Set(
                  todosSetores.map(s => s.categoria?.trim()).filter((c): c is string => !!c)
                )).sort()}
                modalidades={modalidades}
              />
            </div>
          )}

          {/* Botão adicionar setor */}
          {canWrite && !setorForm && todosSetores.length > 0 && (
            <button
              type="button"
              onClick={abrirNovoSetor}
              className="flex items-center gap-2 justify-center w-full py-2.5 rounded-xl border-2 border-dashed border-ds-border text-ds-light hover:border-primary hover:text-primary text-xs font-semibold transition-all"
            >
              <Plus size={14} /> Novo Setor Operacional
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
                    <th className="px-3 py-2.5 text-right text-[10px] font-bold uppercase tracking-wider text-ds-light">Horas/Semana</th>
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
                        {isTipoModalidadeFixa(m.tipo) ? (m.horasSemanais != null ? `${m.horasSemanais}h/sem` : '—') : (m.horas != null ? `${m.horas}h` : '—')}
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
                horariosPadrao={horariosPadrao.filter(h => h.ativo)}
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

      {/* ── Aba: Preenchimento Rápido ── */}
      {aba === 'horarios' && (
        <div className="flex flex-col gap-3">
          <p className="text-xs text-ds-light -mt-1">
            Configura os botões de "Preencher rápido" exibidos ao cadastrar uma modalidade Por Plantão para este tomador.
          </p>

          {hpErr && (
            <Alert variant="error" onClose={() => setHpErr(null)}>{hpErr}</Alert>
          )}

          {hpLoading ? (
            <div className="flex justify-center py-10"><Spinner /></div>
          ) : horariosPadrao.length === 0 && !hpForm ? (
            <div className="flex flex-col items-center py-10 gap-2 text-ds-light">
              <Clock size={36} className="opacity-25" />
              <p className="text-sm font-semibold">Nenhum preenchimento rápido cadastrado</p>
              <p className="text-xs">Ex: ☀️ Diurno 6h — 07:00 as 13:00</p>
              {canWrite && (
                <Button size="sm" className="mt-2" onClick={abrirNovoHorarioPadrao}>
                  <Plus size={14} /> Adicionar primeiro preset
                </Button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-ds-border">
              <table className="w-full text-xs min-w-[480px]">
                <thead>
                  <tr className="bg-ds-surface border-b border-ds-border">
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Turno</th>
                    <th className="px-3 py-2.5 text-right text-[10px] font-bold uppercase tracking-wider text-ds-light">Horas</th>
                    <th className="px-3 py-2.5 text-left text-[10px] font-bold uppercase tracking-wider text-ds-light">Horário</th>
                    <th className="px-3 py-2.5 text-center text-[10px] font-bold uppercase tracking-wider text-ds-light">Status</th>
                    {canWrite && <th className="px-3 py-2.5 w-16" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-ds-border">
                  {horariosPadrao.map(h => (
                    <tr key={h.id} className="hover:bg-ds-surface/50">
                      <td className="px-3 py-2">
                        <span className={[
                          'inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold',
                          h.turno === 'DIURNO' ? 'bg-yellow-50 text-yellow-700' : 'bg-indigo-50 text-indigo-700',
                        ].join(' ')}>
                          {h.turno === 'DIURNO' ? <Sun size={10} /> : <Moon size={10} />}
                          {h.turno}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums font-medium text-ds-text">{h.horas}h</td>
                      <td className="px-3 py-2 text-ds-mid">{h.horario}</td>
                      <td className="px-3 py-2 text-center">
                        <span className={[
                          'px-1.5 py-0.5 rounded text-[10px] font-bold',
                          h.ativo ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500',
                        ].join(' ')}>
                          {h.ativo ? 'ATIVO' : 'INATIVO'}
                        </span>
                      </td>
                      {canWrite && (
                        <td className="px-3 py-2">
                          <div className="flex items-center gap-1 justify-end">
                            <button
                              type="button"
                              onClick={() => abrirEditarHorarioPadrao(h)}
                              className="p-1 rounded text-ds-light hover:text-primary hover:bg-primary-50 transition-colors"
                              title="Editar"
                            >
                              <Pencil size={12} />
                            </button>
                            <button
                              type="button"
                              onClick={() => removerHorarioPadrao(h.id, h)}
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

          {/* Form de novo / editar preset */}
          {canWrite && hpForm && (
            <div className="rounded-xl border border-primary/30 bg-primary-50/30 p-4">
              <p className="text-xs font-bold text-ds-mid uppercase mb-3">
                {editingHpId ? 'Editar preset' : 'Novo preset'}
              </p>
              <HorarioPadraoFormInline
                form={hpForm}
                onChange={patch => setHpForm(f => f ? { ...f, ...patch } : f)}
                onSave={salvarHorarioPadrao}
                onCancel={cancelarHorarioPadrao}
                saving={hpSaving}
                isNew={!editingHpId}
              />
            </div>
          )}

          {/* Botão adicionar preset */}
          {canWrite && !hpForm && horariosPadrao.length > 0 && (
            <button
              type="button"
              onClick={abrirNovoHorarioPadrao}
              className="flex items-center gap-2 justify-center w-full py-2.5 rounded-xl border-2 border-dashed border-ds-border text-ds-light hover:border-primary hover:text-primary text-xs font-semibold transition-all"
            >
              <Plus size={14} /> Novo Preset
            </button>
          )}
        </div>
      )}
    </Modal>
  )
}
