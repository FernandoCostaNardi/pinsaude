import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowLeft, Calculator, CheckCircle2, ChevronDown,
  AlertCircle, Loader2
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import { Servico, servicosApi } from '../api/servicosApi'
import { Tomador, tomadoresApi } from '../api/tomadoresApi'
import { Medico, medicosApi } from '../api/medicosApi'
import { PreviewCalculoResponse, ProducaoRequest, producoesApi } from '../api/producoesApi'

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

interface AutocompleteItem { id: string; label: string; sublabel?: string }

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

// ─── Preview card ─────────────────────────────────────────────────────────────

function PreviewCard({ preview, loading }: { preview: PreviewCalculoResponse | null; loading: boolean }) {
  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm p-5 sticky top-6">
      <div className="flex items-center gap-2 mb-4">
        <Calculator size={16} className="text-primary" />
        <h3 className="font-semibold text-ds-mid text-sm">Preview do Cálculo Fiscal</h3>
      </div>

      {loading && (
        <div className="flex items-center justify-center py-8">
          <Spinner size="md" />
        </div>
      )}

      {!loading && !preview && (
        <div className="text-center text-ds-light py-8 text-sm">
          <Calculator size={32} className="mx-auto mb-2 opacity-30" />
          Preencha médico, tomador, serviço e valor para ver o cálculo
        </div>
      )}

      {!loading && preview && (
        <div className="space-y-2.5">
          <PreviewRow label="Valor Bruto" value={formatBRL(preview.valorBruto)} bold />
          <div className="border-t border-ds-border pt-2.5 space-y-2">
            <p className="text-xs font-semibold text-ds-light uppercase tracking-wide">Deduções</p>
            <PreviewRow label="Taxa Pin Saúde (15%)" value={formatBRL(preview.taxaPin)} negative />
            {preview.issRetido > 0 && <PreviewRow label="ISS Retido" value={formatBRL(preview.issRetido)} negative />}
            {preview.irRetido > 0 && <PreviewRow label="IR Retido" value={formatBRL(preview.irRetido)} negative />}
            {preview.csllRetido > 0 && <PreviewRow label="CSLL Retido" value={formatBRL(preview.csllRetido)} negative />}
            {preview.pisRetido > 0 && <PreviewRow label="PIS Retido" value={formatBRL(preview.pisRetido)} negative />}
            {preview.cofinsRetido > 0 && <PreviewRow label="COFINS Retido" value={formatBRL(preview.cofinsRetido)} negative />}
            {preview.totalRetencoes > 0 && (
              <PreviewRow label="Total Retenções" value={formatBRL(preview.totalRetencoes)} negative sub />
            )}
          </div>
          <div className="border-t-2 border-primary/30 pt-3">
            <div className="flex items-center justify-between">
              <span className="font-bold text-ds-mid text-sm">Valor Líquido Médico</span>
              <span className="font-bold text-green-600 text-lg">{formatBRL(preview.valorLiquidoMedico)}</span>
            </div>
            <p className="text-xs text-ds-light mt-1 text-right">
              {preview.valorBruto > 0
                ? `${((preview.valorLiquidoMedico / preview.valorBruto) * 100).toFixed(1)}% do bruto`
                : '—'}
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

function PreviewRow({ label, value, bold, negative, sub }: {
  label: string; value: string; bold?: boolean; negative?: boolean; sub?: boolean
}) {
  return (
    <div className={`flex items-center justify-between ${sub ? 'pl-2' : ''}`}>
      <span className={`text-sm ${bold ? 'font-semibold text-ds-mid' : sub ? 'font-medium text-ds-light' : 'text-ds-light'}`}>
        {label}
      </span>
      <span className={`text-sm font-semibold ${negative ? 'text-red-500' : bold ? 'text-ds-mid' : 'text-ds-mid'}`}>
        {negative ? '−' : ''}{value}
      </span>
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

// ─── Page ─────────────────────────────────────────────────────────────────────

export function ProducaoNovaPage() {
  const navigate = useNavigate()

  // dados carregados
  const [medicos, setMedicos]     = useState<Medico[]>([])
  const [tomadores, setTomadores] = useState<Tomador[]>([])
  const [servicos, setServicos]   = useState<Servico[]>([])
  const [loadingData, setLoadingData] = useState(true)

  // seleções do formulário
  const [medico,   setMedico]   = useState<AutocompleteItem | null>(null)
  const [tomador,  setTomador]  = useState<AutocompleteItem | null>(null)
  const [servico,  setServico]  = useState<AutocompleteItem | null>(null)
  const [valorStr, setValorStr] = useState('')
  const [competencia, setCompetencia] = useState(currentCompetencia())
  const [descricao, setDescricao] = useState('')

  // estado
  const [preview, setPreview]           = useState<PreviewCalculoResponse | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [errors, setErrors]             = useState<Record<string, string>>({})
  const [submitLoading, setSubmitLoading] = useState(false)
  const [globalError, setGlobalError]   = useState<string | null>(null)

  const previewTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const competencias    = generateCompetencias()

  // ─── Carrega dados iniciais ────────────────────────────────────────────────

  useEffect(() => {
    Promise.all([
      medicosApi.listar(0, 1000, 'ATIVO').catch(() => ({ content: [] as Medico[] })),
      tomadoresApi.listar().catch(() => [] as Tomador[]),
      servicosApi.listar().catch(() => [] as Servico[]),
    ]).then(([mp, ts, ss]) => {
      setMedicos(mp.content)
      setTomadores(ts)
      setServicos(ss)
    }).finally(() => setLoadingData(false))
  }, [])

  // ─── Preview automático ────────────────────────────────────────────────────

  const valorCentavos = parseBRL(valorStr)

  useEffect(() => {
    if (previewTimerRef.current) clearTimeout(previewTimerRef.current)

    const servicoId = servico?.id
    const tomadorId = tomador?.id

    if (!servicoId || !tomadorId || valorCentavos <= 0) {
      setPreview(null)
      return
    }

    previewTimerRef.current = setTimeout(() => {
      setPreviewLoading(true)
      producoesApi.previewCalculo({ servicoId, tomadorId, valorBruto: valorCentavos })
        .then(setPreview)
        .catch(() => setPreview(null))
        .finally(() => setPreviewLoading(false))
    }, 400)

    return () => { if (previewTimerRef.current) clearTimeout(previewTimerRef.current) }
  }, [servico?.id, tomador?.id, valorCentavos])

  // ─── Submissão ────────────────────────────────────────────────────────────

  function validate(): boolean {
    const e: Record<string, string> = {}
    if (!medico)           e.medico    = 'Selecione um médico'
    if (!tomador)          e.tomador   = 'Selecione um tomador'
    if (!servico)          e.servico   = 'Selecione um serviço'
    if (valorCentavos <= 0) e.valor    = 'Valor deve ser maior que R$ 0,00'
    if (!competencia)      e.competencia = 'Selecione a competência'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function handleSubmit() {
    if (!validate()) return

    const req: ProducaoRequest = {
      medicoId:              medico!.id,
      tomadorId:             tomador!.id,
      servicoId:             servico!.id,
      valorBruto:            valorCentavos,
      competencia,
      descricaoComplementar: descricao || undefined,
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

  // ─── Listas para autocomplete ──────────────────────────────────────────────

  const medicoItems: AutocompleteItem[] = medicos.map(m => ({
    id: m.id,
    label: m.nome,
    sublabel: `CRM ${m.crm}-${m.crmUf}${m.especialidade ? ` · ${m.especialidade}` : ''}`,
  }))

  const tomadorItems: AutocompleteItem[] = tomadores.map(t => ({
    id: t.id,
    label: t.razaoSocialNome,
    sublabel: t.municipio ?? undefined,
  }))

  const canConfirm = !!medico && !!tomador && !!servico && valorCentavos > 0 && !!competencia

  if (loadingData) return (
    <div className="flex items-center justify-center h-64">
      <Spinner size="lg" />
    </div>
  )

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <button onClick={() => navigate('/producao')}
          className="p-2 rounded-lg hover:bg-ds-input text-ds-light hover:text-ds-mid transition-colors">
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-ds-mid">Nova Produção Médica</h1>
          <p className="text-sm text-ds-light mt-0.5">Registre a produção e veja o breakdown fiscal antes de confirmar</p>
        </div>
      </div>

      {globalError && <Alert variant="error" onClose={() => setGlobalError(null)}>{globalError}</Alert>}

      {/* Layout em duas colunas */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">

        {/* Formulário (3/5) */}
        <div className="lg:col-span-3 space-y-5">
          <div className="bg-white rounded-xl border border-ds-border shadow-sm p-6 space-y-5">
            <h2 className="font-semibold text-ds-mid text-sm uppercase tracking-wide border-b border-ds-border pb-3">
              Dados da Produção
            </h2>

            {/* Médico */}
            <Field label="Médico" required error={errors.medico}>
              <Autocomplete
                items={medicoItems}
                value={medico}
                onChange={item => { setMedico(item); setErrors(e => ({ ...e, medico: '' })) }}
                onClear={() => setMedico(null)}
                placeholder="Buscar por nome ou CRM..."
              />
              {medicos.length === 0 && (
                <p className="text-xs text-amber-600 flex items-center gap-1 mt-1">
                  <AlertCircle size={11} />
                  Nenhum médico ativo encontrado. Ative um médico primeiro.
                </p>
              )}
            </Field>

            {/* Tomador */}
            <Field label="Tomador (Hospital / Clínica / Operadora)" required error={errors.tomador}>
              <Autocomplete
                items={tomadorItems}
                value={tomador}
                onChange={item => { setTomador(item); setErrors(e => ({ ...e, tomador: '' })) }}
                onClear={() => { setTomador(null); setPreview(null) }}
                placeholder="Buscar por nome ou CNPJ..."
              />
            </Field>

            {/* Serviço */}
            <Field label="Serviço (LC 116/2003)" required error={errors.servico}>
              <select
                value={servico?.id ?? ''}
                onChange={e => {
                  const s = servicos.find(s => s.id === e.target.value)
                  setServico(s ? { id: s.id, label: `${s.codigoLc116} — ${s.descricaoPadrao}` } : null)
                  setErrors(ex => ({ ...ex, servico: '' }))
                }}
                className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid"
              >
                <option value="">Selecione o serviço...</option>
                {servicos.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.codigoLc116} — {s.descricaoPadrao}
                  </option>
                ))}
              </select>
              {servico && (() => {
                const s = servicos.find(s => s.id === servico.id)
                return s ? (
                  <div className="mt-1.5 px-3 py-2 bg-ds-surface rounded-lg text-xs text-ds-light flex gap-4">
                    <span>ISS {s.aliquotaIss}%</span>
                    <span>IR {s.aliquotaIr}%</span>
                    <span>CSLL {s.aliquotaCsll}%</span>
                    <span>PIS {s.aliquotaPis}%</span>
                    <span>COFINS {s.aliquotaCofins}%</span>
                  </div>
                ) : null
              })()}
            </Field>

            {/* Valor bruto */}
            <Field label="Valor Bruto" required error={errors.valor}>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-medium text-ds-mid">R$</span>
                <input
                  value={valorStr}
                  onChange={e => {
                    const raw = e.target.value.replace(/\D/g, '')
                    const cents = parseInt(raw || '0', 10)
                    setValorStr(maskBRL(cents))
                    setErrors(ex => ({ ...ex, valor: '' }))
                  }}
                  placeholder="0,00"
                  className="w-full pl-9 pr-3 py-2 border border-ds-border rounded-lg text-sm text-right focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>
              {valorCentavos > 0 && (
                <p className="text-xs text-ds-light mt-1">= {formatBRL(valorCentavos)}</p>
              )}
            </Field>

            {/* Competência */}
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

            {/* Descrição */}
            <Field label="Descrição Complementar">
              <textarea
                value={descricao}
                onChange={e => setDescricao(e.target.value)}
                placeholder="Informações adicionais sobre a produção (opcional)"
                rows={3}
                className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
              />
            </Field>
          </div>

          {/* Botões */}
          <div className="flex items-center justify-between">
            <Button variant="ghost" onClick={() => navigate('/producao')} disabled={submitLoading}>
              Cancelar
            </Button>
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

        {/* Preview (2/5) */}
        <div className="lg:col-span-2">
          <PreviewCard preview={preview} loading={previewLoading} />
        </div>
      </div>
    </div>
  )
}
