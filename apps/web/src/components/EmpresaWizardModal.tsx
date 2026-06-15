import { useEffect, useState } from 'react'
import { Building2, FileText, CheckCircle } from 'lucide-react'
import { Modal, Input, Button, Alert } from '@pinsaude/ui'
import { CnpjInput } from './CnpjInput'
import { MunicipioSelect } from './MunicipioSelect'
import { Empresa, EmpresaRequest, RegimeTributario, empresasApi } from '../api/empresasApi'
import { isValidCnpj, formatCnpj } from '../utils/cnpj'

const STEPS = [
  { label: 'Identificação', Icon: Building2 },
  { label: 'Dados Fiscais', Icon: FileText },
  { label: 'Revisão', Icon: CheckCircle },
] as const

const REGIME_OPTIONS: { value: RegimeTributario; label: string }[] = [
  { value: 'SIMPLES_NACIONAL', label: 'Simples Nacional' },
  { value: 'LUCRO_PRESUMIDO', label: 'Lucro Presumido' },
  { value: 'LUCRO_REAL', label: 'Lucro Real' },
]

const REGIME_LABELS: Record<RegimeTributario, string> = {
  SIMPLES_NACIONAL: 'Simples Nacional',
  LUCRO_PRESUMIDO: 'Lucro Presumido',
  LUCRO_REAL: 'Lucro Real',
}

interface Props {
  empresa: Empresa | null
  onClose: () => void
  onSaved: (empresa: Empresa) => void
}

const emptyForm = (): EmpresaRequest => ({
  cnpj: '',
  razaoSocial: '',
  inscricaoMunicipal: '',
  municipio: '',
  codigoMunicipioIbge: '',
  regimeTributario: 'SIMPLES_NACIONAL',
})

export function EmpresaWizardModal({ empresa, onClose, onSaved }: Props) {
  const isEditing = empresa !== null
  const [step, setStep] = useState(0)
  const [maxVisited, setMaxVisited] = useState(0)
  const [form, setForm] = useState<EmpresaRequest>(emptyForm)
  const [municipioUf, setMunicipioUf] = useState('')
  const [errors, setErrors] = useState<Partial<Record<keyof EmpresaRequest, string>>>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (empresa) {
      setForm({
        cnpj: empresa.cnpj,
        razaoSocial: empresa.razaoSocial,
        inscricaoMunicipal: empresa.inscricaoMunicipal ?? '',
        municipio: empresa.municipio ?? '',
        codigoMunicipioIbge: empresa.codigoMunicipioIbge ?? '',
        regimeTributario: empresa.regimeTributario,
      })
      setMunicipioUf('')
      setMaxVisited(2)
    } else {
      setForm(emptyForm())
      setMunicipioUf('')
      setMaxVisited(0)
    }
    setStep(0)
    setErrors({})
    setApiError(null)
  }, [empresa])

  function setField<K extends keyof EmpresaRequest>(key: K, value: EmpresaRequest[K]) {
    setForm(f => ({ ...f, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function handleMunicipioChange(municipio: string, codigo: string, uf: string) {
    setForm(f => ({ ...f, municipio, codigoMunicipioIbge: codigo }))
    setMunicipioUf(uf)
  }

  function validateStep(s: number): boolean {
    const errs: Partial<Record<keyof EmpresaRequest, string>> = {}
    if (s === 0) {
      if (!isEditing && (!form.cnpj || !isValidCnpj(form.cnpj))) errs.cnpj = 'CNPJ inválido'
      if (!form.razaoSocial.trim()) errs.razaoSocial = 'Obrigatório'
    }
    if (s === 1) {
      if (!form.inscricaoMunicipal.trim()) errs.inscricaoMunicipal = 'Obrigatório'
      if (!form.regimeTributario) errs.regimeTributario = 'Obrigatório'
    }
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  function goTo(target: number) {
    if (target > maxVisited) return
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
    setLoading(true)
    setApiError(null)
    try {
      const saved = isEditing
        ? await empresasApi.atualizar(empresa.id, form)
        : await empresasApi.criar(form)
      onSaved(saved)
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar empresa')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={isEditing ? 'Editar Empresa' : 'Nova Empresa'}
      size="lg"
    >
      <div className="flex flex-col gap-6">
        <WizardSteps current={step} maxVisited={maxVisited} onGo={goTo} />

        <div className="min-h-[220px]">
          {step === 0 && (
            <StepIdentificacao
              form={form}
              errors={errors}
              isEditing={isEditing}
              onChange={setField}
            />
          )}
          {step === 1 && (
            <StepDadosFiscais
              form={form}
              errors={errors}
              onChange={setField}
              onMunicipioChange={handleMunicipioChange}
            />
          )}
          {step === 2 && (
            <StepRevisao form={form} municipioUf={municipioUf} />
          )}
        </div>

        {apiError && <Alert variant="error">{apiError}</Alert>}

        <div className="flex justify-between pt-3 border-t border-gray-200">
          <Button
            type="button"
            variant="outline"
            onClick={step === 0 ? onClose : () => goTo(step - 1)}
          >
            {step === 0 ? 'Cancelar' : '← Voltar'}
          </Button>
          {step < 2 ? (
            <Button type="button" onClick={handleNext}>
              Próximo →
            </Button>
          ) : (
            <Button type="button" onClick={handleSave} loading={loading}>
              {isEditing ? 'Salvar alterações' : 'Cadastrar empresa'}
            </Button>
          )}
        </div>
      </div>
    </Modal>
  )
}

// ─── Step Indicator ───────────────────────────────────────────────────────────

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
        const isDone = i < current
        const isActive = i === current
        const isClickable = i <= maxVisited

        return (
          <div key={i} className="flex items-start">
            <button
              type="button"
              onClick={() => isClickable && onGo(i)}
              disabled={!isClickable}
              className="flex flex-col items-center gap-1.5 w-24"
            >
              <div
                className={[
                  'w-11 h-11 rounded-full flex items-center justify-center border-2 transition-all duration-300',
                  isDone
                    ? 'bg-secondary-100 border-secondary-400 text-secondary-700'
                    : isActive
                    ? 'bg-primary border-primary text-white shadow-lg shadow-primary/20'
                    : 'bg-white border-gray-200 text-gray-300',
                ].join(' ')}
              >
                {isDone
                  ? <CheckCircle className="w-5 h-5 text-secondary-600" />
                  : <Icon className="w-5 h-5" />
                }
              </div>
              <span
                className={[
                  'text-xs font-medium text-center leading-tight',
                  isDone ? 'text-secondary-700' : isActive ? 'text-primary-700' : 'text-gray-400',
                ].join(' ')}
              >
                {label}
              </span>
            </button>

            {i < STEPS.length - 1 && (
              <div className="w-16 sm:w-20 h-0.5 mt-5 mx-1 bg-gray-200 rounded-full overflow-hidden">
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

// ─── Step 1: Identificação ────────────────────────────────────────────────────

function StepIdentificacao({
  form,
  errors,
  isEditing,
  onChange,
}: {
  form: EmpresaRequest
  errors: Partial<Record<keyof EmpresaRequest, string>>
  isEditing: boolean
  onChange: <K extends keyof EmpresaRequest>(key: K, value: EmpresaRequest[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Informe os dados de identificação da empresa.</p>
      <CnpjInput
        label="CNPJ *"
        value={form.cnpj}
        onChange={v => onChange('cnpj', v)}
        error={errors.cnpj}
        disabled={isEditing}
      />
      <Input
        label="Razão Social *"
        value={form.razaoSocial}
        onChange={e => onChange('razaoSocial', e.target.value)}
        error={errors.razaoSocial}
        placeholder="Nome completo da empresa"
      />
    </div>
  )
}

// ─── Step 2: Dados Fiscais ────────────────────────────────────────────────────

function StepDadosFiscais({
  form,
  errors,
  onChange,
  onMunicipioChange,
}: {
  form: EmpresaRequest
  errors: Partial<Record<keyof EmpresaRequest, string>>
  onChange: <K extends keyof EmpresaRequest>(key: K, value: EmpresaRequest[K]) => void
  onMunicipioChange: (municipio: string, codigo: string, uf: string) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Configure os dados fiscais e localização da empresa.</p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700">Regime Tributário *</label>
          <select
            value={form.regimeTributario}
            onChange={e => onChange('regimeTributario', e.target.value as RegimeTributario)}
            className={[
              'block w-full rounded-lg border px-3 py-2 text-sm',
              'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary',
              errors.regimeTributario ? 'border-red-400' : 'border-gray-300',
            ].join(' ')}
          >
            {REGIME_OPTIONS.map(o => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          {errors.regimeTributario && (
            <p className="text-xs text-red-500">{errors.regimeTributario}</p>
          )}
        </div>

        <Input
          label="Inscrição Municipal *"
          value={form.inscricaoMunicipal}
          onChange={e => onChange('inscricaoMunicipal', e.target.value)}
          error={errors.inscricaoMunicipal}
          placeholder="Ex: 1234/2024"
        />

        <div className="sm:col-span-2">
          <MunicipioSelect
            municipio={form.municipio}
            codigo={form.codigoMunicipioIbge}
            onChange={onMunicipioChange}
            error={errors.municipio}
          />
        </div>
      </div>
    </div>
  )
}

// ─── Step 3: Revisão ──────────────────────────────────────────────────────────

function StepRevisao({ form, municipioUf }: { form: EmpresaRequest; municipioUf: string }) {
  const municipioDisplay = form.municipio
    ? municipioUf ? `${form.municipio} — ${municipioUf}` : form.municipio
    : '—'

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">
        Confira os dados antes de salvar. Clique em um passo acima para editar.
      </p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <ReviewField label="CNPJ" value={form.cnpj ? formatCnpj(form.cnpj) : '—'} />
        <ReviewField label="Razão Social" value={form.razaoSocial || '—'} />
        <ReviewField label="Regime Tributário" value={REGIME_LABELS[form.regimeTributario]} />
        <ReviewField label="Inscrição Municipal" value={form.inscricaoMunicipal || '—'} />
        <ReviewField
          label="Município"
          value={municipioDisplay}
          hint={form.codigoMunicipioIbge ? `IBGE ${form.codigoMunicipioIbge}` : undefined}
          className="sm:col-span-2"
        />
      </div>
    </div>
  )
}

function ReviewField({
  label,
  value,
  hint,
  className,
}: {
  label: string
  value: string
  hint?: string
  className?: string
}) {
  return (
    <div className={['rounded-lg border border-gray-200 bg-gray-50 px-4 py-3', className].join(' ')}>
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</p>
      <p className="mt-0.5 text-sm font-semibold text-gray-900">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-gray-500 font-mono">{hint}</p>}
    </div>
  )
}
