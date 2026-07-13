import { useEffect, useState } from 'react'
import { Percent, CheckCircle, AlertTriangle, XCircle, Clock } from 'lucide-react'
import { Modal, Input, Button, Alert, Spinner } from '@pinsaude/ui'
import {
  configuracaoFiscalApi,
  ConfiguracaoFiscal,
  ConfiguracaoFiscalRequest,
  RegimePresuncao,
  StatusCertificado,
} from '../api/configuracaoFiscalApi'

// ─── Types ────────────────────────────────────────────────────────────────────

interface FiscalForm {
  vencimentoCertificadoA1: string
  competencia: string
  regimePresuncao: RegimePresuncao
  iss: string
  ir: string
  csll: string
  pis: string
  cofins: string
}

type FormErrors = Partial<Record<keyof FiscalForm, string>>

// ─── Wizard steps ─────────────────────────────────────────────────────────────

const STEPS = [
  { label: 'Alíquotas', Icon: Percent     },
  { label: 'Revisão',   Icon: CheckCircle },
] as const

// ─── Helpers ──────────────────────────────────────────────────────────────────

function mesAtual(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function formFromConfig(config: ConfiguracaoFiscal, comp: string): FiscalForm {
  const aq = config.historicoAliquotas.find(a => a.competencia === comp)
  return {
    vencimentoCertificadoA1: config.vencimentoCertificadoA1 ?? '',
    competencia: comp,
    regimePresuncao: aq?.regimePresuncao ?? 'CHEIA',
    iss:    aq ? Number(aq.iss).toFixed(2)    : '0.00',
    ir:     aq ? Number(aq.ir).toFixed(2)     : '0.00',
    csll:   aq ? Number(aq.csll).toFixed(2)   : '0.00',
    pis:    aq ? Number(aq.pis).toFixed(2)    : '0.00',
    cofins: aq ? Number(aq.cofins).toFixed(2) : '0.00',
  }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function CertificadoBadge({ status }: { status: StatusCertificado }) {
  const map: Record<StatusCertificado, { label: string; cls: string; Icon: typeof CheckCircle }> = {
    VALIDO:           { label: 'Cert. válido',       cls: 'bg-green-50 text-green-700',   Icon: CheckCircle },
    EXPIRANDO:        { label: 'Cert. expirando',    cls: 'bg-amber-50 text-amber-700',   Icon: AlertTriangle },
    VENCIDO:          { label: 'Cert. vencido',      cls: 'bg-red-50 text-red-700',       Icon: XCircle },
    NAO_CONFIGURADO:  { label: 'Cert. não config.',  cls: 'bg-ds-input text-ds-light',    Icon: Clock },
  }
  const { label, cls, Icon } = map[status]
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${cls}`}>
      <Icon size={12} />
      {label}
    </span>
  )
}

function AliquotaInput({
  label, value, onChange, disabled,
}: { label: string; value: string; onChange: (v: string) => void; disabled?: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs font-semibold text-ds-light uppercase tracking-wide">{label}</label>
      <div className="relative">
        <input
          type="number"
          min="0"
          max="100"
          step="0.01"
          value={value}
          onChange={e => onChange(e.target.value)}
          disabled={disabled}
          className={[
            'block w-full pr-7 pl-3 py-2 text-sm border rounded-lg text-ds-text',
            'focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary',
            'disabled:bg-ds-input disabled:text-ds-light disabled:cursor-not-allowed',
            'border-ds-border bg-white',
          ].join(' ')}
        />
        <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-ds-light text-xs select-none">%</span>
      </div>
    </div>
  )
}

function ReviewField({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="rounded-lg border border-ds-border bg-ds-input px-4 py-3">
      <p className="text-xs font-semibold text-ds-light uppercase tracking-wide">{label}</p>
      <p className={['mt-0.5 text-sm font-semibold text-ds-text', mono ? 'font-mono' : ''].join(' ')}>
        {value || '—'}
      </p>
    </div>
  )
}

// ─── Wizard step indicator ────────────────────────────────────────────────────

function WizardSteps({
  current,
  maxVisited,
  onGo,
}: {
  current: number
  maxVisited: number
  onGo: (s: number) => void
}) {
  return (
    <nav className="flex items-start justify-center">
      {STEPS.map(({ label, Icon }, i) => {
        const isDone     = i < current
        const isActive   = i === current
        const clickable  = i <= maxVisited

        return (
          <div key={i} className="flex items-start">
            <button
              type="button"
              onClick={() => clickable && onGo(i)}
              disabled={!clickable}
              className="flex flex-col items-center gap-1 w-14 sm:w-24"
            >
              <div className={[
                'w-9 h-9 sm:w-11 sm:h-11 rounded-full flex items-center justify-center border-2 transition-all duration-300',
                isDone   ? 'bg-secondary-100 border-secondary-400 text-secondary-700'
                : isActive ? 'bg-primary border-primary text-white shadow-lg shadow-primary/20'
                           : 'bg-white border-ds-border text-ds-light',
              ].join(' ')}>
                {isDone
                  ? <CheckCircle className="w-4 h-4 sm:w-5 sm:h-5 text-secondary-600" />
                  : <Icon className="w-4 h-4 sm:w-5 sm:h-5" />
                }
              </div>
              <span className={[
                'hidden sm:block text-xs font-medium text-center leading-tight w-20',
                isDone ? 'text-secondary-700' : isActive ? 'text-primary-700' : 'text-ds-light',
              ].join(' ')}>
                {label}
              </span>
              <span className={[
                'sm:hidden text-[10px] font-semibold text-center leading-tight',
                isDone ? 'text-secondary-600' : isActive ? 'text-primary' : 'text-ds-light',
              ].join(' ')}>
                {i + 1}
              </span>
            </button>

            {i < STEPS.length - 1 && (
              <div className="flex-1 h-0.5 mt-[18px] sm:mt-5 mx-1 bg-ds-border rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary rounded-full transition-all duration-500"
                  style={{ width: i < current ? '100%' : '0%' }}
                />
              </div>
            )}
          </div>
        )
      })}
    </nav>
  )
}

// ─── Step 0: Alíquotas ───────────────────────────────────────────────────────

function StepAliquotas({
  form,
  errors,
  onChange,
  onCompetenciaChange,
  config,
}: {
  form: FiscalForm
  errors: FormErrors
  onChange: <K extends keyof FiscalForm>(k: K, v: FiscalForm[K]) => void
  onCompetenciaChange: (comp: string) => void
  config: ConfiguracaoFiscal
}) {
  const atual = mesAtual()
  const isPassada = form.competencia < atual
  const temHistorico = config.historicoAliquotas.some(a => a.competencia === form.competencia)

  return (
    <div className="flex flex-col gap-5">
      {/* Certificado A1 */}
      <div className="rounded-xl border border-ds-border p-4">
        <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-3">Certificado A1</p>
        <div className="max-w-xs">
          <Input
            label="Vencimento"
            type="date"
            value={form.vencimentoCertificadoA1}
            onChange={e => onChange('vencimentoCertificadoA1', e.target.value)}
          />
        </div>
      </div>

      {/* Alíquotas por competência */}
      <div className="flex flex-col gap-4">
        <p className="text-xs sm:text-sm text-ds-light">
          Defina as alíquotas para a competência selecionada. Cada mês é independente.
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700">Competência *</label>
            <input
              type="month"
              value={form.competencia}
              onChange={e => onCompetenciaChange(e.target.value)}
              className="block w-full px-3 py-2 text-sm border border-ds-border rounded-lg text-ds-text bg-white focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            />
            {isPassada && (
              <p className="text-xs text-amber-600 flex items-center gap-1">
                <AlertTriangle size={11} />
                Competência passada — alteração afeta retroativamente.
              </p>
            )}
            {!isPassada && temHistorico && (
              <p className="text-xs text-primary">Editando alíquotas existentes para esta competência.</p>
            )}
            {!isPassada && !temHistorico && (
              <p className="text-xs text-ds-light">Nova competência — alíquotas iniciadas zeradas.</p>
            )}
            {errors.competencia && <p className="text-xs text-red-600">{errors.competencia}</p>}
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700">Regime de Presunção</label>
            <select
              value={form.regimePresuncao}
              onChange={e => onChange('regimePresuncao', e.target.value as RegimePresuncao)}
              className="block w-full px-3 py-2 text-sm border border-ds-border rounded-lg text-ds-text bg-white focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            >
              <option value="CHEIA">Cheia</option>
              <option value="REDUZIDA">Reduzida (equiparação)</option>
            </select>
          </div>
        </div>

        <div className="rounded-xl border border-ds-border bg-ds-input p-3 sm:p-4">
          <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-3">Alíquotas (%)</p>
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 sm:gap-3">
            {(['iss', 'ir', 'csll', 'pis'] as const).map(campo => (
              <AliquotaInput
                key={campo}
                label={campo.toUpperCase()}
                value={form[campo]}
                onChange={v => onChange(campo, v)}
              />
            ))}
            <div className="col-span-2 sm:col-span-1">
              <AliquotaInput
                label="COFINS"
                value={form.cofins}
                onChange={v => onChange('cofins', v)}
              />
            </div>
          </div>
          <p className="mt-3 text-xs text-ds-light">
            Valores entre 0% e 100%. Alterações não retroagem em notas já emitidas.
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── Step 1: Revisão ─────────────────────────────────────────────────────────

function StepRevisao({
  form,
  config,
}: {
  form: FiscalForm
  config: ConfiguracaoFiscal
}) {
  const historico = [...config.historicoAliquotas].sort((a, b) =>
    b.competencia.localeCompare(a.competencia)
  )

  return (
    <div className="flex flex-col gap-4 sm:gap-5">
      <p className="text-xs sm:text-sm text-ds-light">Confira os dados antes de salvar.</p>

      {/* Certificado A1 */}
      <div>
        <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-2">Certificado A1</p>
        <ReviewField label="Vencimento" value={
          form.vencimentoCertificadoA1
            ? new Date(form.vencimentoCertificadoA1 + 'T00:00:00').toLocaleDateString('pt-BR')
            : ''
        } />
      </div>

      {/* Alíquotas */}
      <div>
        <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-2">
          Alíquotas — {form.competencia}
        </p>

        {/* Mobile: cards */}
        <div className="sm:hidden grid grid-cols-3 gap-2">
          {(['iss', 'ir', 'csll', 'pis', 'cofins'] as const).map(c => (
            <div key={c} className="rounded-lg bg-ds-input border border-ds-border px-2 py-2.5 text-center">
              <p className="text-[10px] font-semibold text-ds-light uppercase">{c.toUpperCase()}</p>
              <p className="font-mono font-bold text-ds-text text-sm mt-0.5">{parseFloat(form[c]).toFixed(2)}%</p>
            </div>
          ))}
          <div className="rounded-lg bg-ds-input border border-ds-border px-2 py-2.5 text-center">
            <p className="text-[10px] font-semibold text-ds-light uppercase">Regime</p>
            <p className={[
              'text-xs font-bold mt-0.5',
              form.regimePresuncao === 'REDUZIDA' ? 'text-primary' : 'text-ds-mid',
            ].join(' ')}>
              {form.regimePresuncao === 'REDUZIDA' ? 'Reduzida' : 'Cheia'}
            </p>
          </div>
        </div>

        {/* Desktop: table */}
        <div className="hidden sm:block rounded-xl border border-ds-border overflow-hidden">
          <div className="grid grid-cols-6 bg-ds-input px-4 py-2 text-xs font-semibold text-ds-light uppercase tracking-wide">
            {['ISS', 'IR', 'CSLL', 'PIS', 'COFINS', 'Regime'].map(h => (
              <span key={h}>{h}</span>
            ))}
          </div>
          <div className="grid grid-cols-6 px-4 py-3 text-sm">
            {(['iss', 'ir', 'csll', 'pis', 'cofins'] as const).map(c => (
              <span key={c} className="font-mono font-semibold text-ds-text">
                {parseFloat(form[c]).toFixed(2)}%
              </span>
            ))}
            <span className={[
              'text-xs font-semibold px-2 py-0.5 rounded self-center',
              form.regimePresuncao === 'REDUZIDA'
                ? 'bg-primary-50 text-primary'
                : 'bg-ds-input text-ds-mid',
            ].join(' ')}>
              {form.regimePresuncao === 'REDUZIDA' ? 'Reduzida' : 'Cheia'}
            </span>
          </div>
        </div>
      </div>

      {/* Histórico */}
      {historico.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-2">
            Histórico de Competências
          </p>
          <div className="rounded-xl border border-ds-border overflow-hidden">
            <div className="overflow-x-auto">
              <div className="min-w-[420px]">
                <div className="grid grid-cols-7 bg-ds-input px-3 sm:px-4 py-2 text-xs font-semibold text-ds-light uppercase tracking-wide">
                  {['Competência', 'ISS', 'IR', 'CSLL', 'PIS', 'COFINS', 'Regime'].map(h => (
                    <span key={h}>{h}</span>
                  ))}
                </div>
                <div className="divide-y divide-ds-border">
                  {historico.map(a => {
                    const isAtual = a.competencia === mesAtual()
                    return (
                      <div key={a.id} className={[
                        'grid grid-cols-7 px-3 sm:px-4 py-2.5 text-xs',
                        isAtual ? 'bg-primary-50' : 'hover:bg-ds-hover',
                      ].join(' ')}>
                        <span className="font-mono font-semibold text-ds-text">{a.competencia}</span>
                        {(['iss', 'ir', 'csll', 'pis', 'cofins'] as const).map(c => (
                          <span key={c} className="text-ds-mid font-mono">
                            {Number(a[c]).toFixed(2)}%
                          </span>
                        ))}
                        <span className={[
                          'text-xs font-semibold px-1.5 py-0.5 rounded self-center w-fit',
                          a.regimePresuncao === 'REDUZIDA'
                            ? 'bg-primary-50 text-primary'
                            : 'bg-ds-input text-ds-mid',
                        ].join(' ')}>
                          {a.regimePresuncao === 'REDUZIDA' ? 'Red.' : 'Cheia'}
                        </span>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Main Modal ───────────────────────────────────────────────────────────────

interface Props {
  empresaId: string
  empresaNome: string
  onClose: () => void
}

export function ConfiguracaoFiscalModal({ empresaId, empresaNome, onClose }: Props) {
  const [config, setConfig] = useState<ConfiguracaoFiscal | null>(null)
  const [loadingConfig, setLoadingConfig] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [step, setStep] = useState(0)
  const [maxVisited, setMaxVisited] = useState(0)
  const [form, setForm] = useState<FiscalForm | null>(null)
  const [errors, setErrors] = useState<FormErrors>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    configuracaoFiscalApi.buscar(empresaId)
      .then(data => {
        setConfig(data)
        setForm(formFromConfig(data, mesAtual()))
      })
      .catch(e => setLoadError(e instanceof Error ? e.message : 'Erro ao carregar configuração'))
      .finally(() => setLoadingConfig(false))
  }, [empresaId])

  function setField<K extends keyof FiscalForm>(key: K, value: FiscalForm[K]) {
    setForm(f => f ? { ...f, [key]: value } : f)
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function handleCompetenciaChange(comp: string) {
    if (!config || !form) return
    const aq = config.historicoAliquotas.find(a => a.competencia === comp)
    setForm({
      ...form,
      competencia: comp,
      regimePresuncao: aq?.regimePresuncao ?? 'CHEIA',
      iss:    aq ? Number(aq.iss).toFixed(2)    : '0.00',
      ir:     aq ? Number(aq.ir).toFixed(2)     : '0.00',
      csll:   aq ? Number(aq.csll).toFixed(2)   : '0.00',
      pis:    aq ? Number(aq.pis).toFixed(2)    : '0.00',
      cofins: aq ? Number(aq.cofins).toFixed(2) : '0.00',
    })
    setErrors(e => ({ ...e, competencia: undefined }))
  }

  function validateStep(s: number): boolean {
    if (!form) return false
    const errs: FormErrors = {}
    if (s === 0) {
      if (!form.competencia) errs.competencia = 'Obrigatório'
      const check = (k: 'iss' | 'ir' | 'csll' | 'pis' | 'cofins') => {
        const v = parseFloat(form[k])
        if (isNaN(v) || v < 0 || v > 100) errs[k] = 'Entre 0 e 100'
      }
      ;(['iss', 'ir', 'csll', 'pis', 'cofins'] as const).forEach(check)
    }
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  function goTo(target: number) {
    setErrors({})
    setApiError(null)
    setStep(target)
  }

  function handleNext() {
    if (!validateStep(step)) return
    const next = step + 1
    if (next > maxVisited) setMaxVisited(next)
    setStep(next)
    setApiError(null)
  }

  async function handleSave() {
    if (!form) return
    setSaving(true)
    setApiError(null)
    try {
      const payload: ConfiguracaoFiscalRequest = {
        cnaeCodigo: null,
        cnaeDescricao: null,
        codigoLc116: null,
        indicadorEquiparacaoHospitalar: false,
        vencimentoCertificadoA1: form.vencimentoCertificadoA1 || null,
        aliquota: {
          competencia: form.competencia,
          iss:    parseFloat(form.iss)    || 0,
          ir:     parseFloat(form.ir)     || 0,
          csll:   parseFloat(form.csll)   || 0,
          pis:    parseFloat(form.pis)    || 0,
          cofins: parseFloat(form.cofins) || 0,
          regimePresuncao: form.regimePresuncao,
        },
      }
      const updated = await configuracaoFiscalApi.salvar(empresaId, payload)
      setConfig(updated)
      setForm(formFromConfig(updated, form.competencia))
      onClose()
    } catch (e) {
      setApiError(e instanceof Error ? e.message : 'Erro ao salvar configuração')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={
        <div className="flex items-center justify-between w-full">
          <div>
            <p className="text-base font-bold text-ds-text leading-tight">Configuração Fiscal</p>
            <p className="text-xs text-ds-light">{empresaNome}</p>
          </div>
          {config && <CertificadoBadge status={config.statusCertificado} />}
        </div>
      }
      size="xl"
    >
      {loadingConfig ? (
        <div className="flex justify-center items-center py-20">
          <Spinner size="lg" />
        </div>
      ) : loadError ? (
        <div className="flex flex-col items-center gap-4 py-12">
          <Alert variant="error">{loadError}</Alert>
          <Button variant="outline" onClick={onClose}>Fechar</Button>
        </div>
      ) : form && config ? (
        <div className="flex flex-col gap-6">
          <WizardSteps current={step} maxVisited={maxVisited} onGo={goTo} />

          <div className="min-h-[200px] sm:min-h-[300px]">
            {step === 0 && (
              <StepAliquotas
                form={form}
                errors={errors}
                onChange={setField}
                onCompetenciaChange={handleCompetenciaChange}
                config={config}
              />
            )}
            {step === 1 && (
              <StepRevisao form={form} config={config} />
            )}
          </div>

          {apiError && (
            <Alert variant="error" onClose={() => setApiError(null)}>{apiError}</Alert>
          )}

          <div className="flex justify-between pt-3 border-t border-ds-border">
            <Button
              variant="outline"
              onClick={step === 0 ? onClose : () => goTo(step - 1)}
            >
              {step === 0 ? 'Cancelar' : '← Voltar'}
            </Button>
            {step < 1 ? (
              <Button onClick={handleNext}>
                Próximo →
              </Button>
            ) : (
              <Button onClick={handleSave} loading={saving}>
                Salvar configuração
              </Button>
            )}
          </div>
        </div>
      ) : null}
    </Modal>
  )
}
