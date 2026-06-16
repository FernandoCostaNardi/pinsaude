import { useEffect, useState } from 'react'
import { User, Stethoscope, CreditCard, CheckCircle } from 'lucide-react'
import { Modal, Input, Button, Alert } from '@pinsaude/ui'
import { CpfInput } from './CpfInput'
import {
  Medico, MedicoRequest, DadosBancariosMedicoRequest,
  TipoPix, medicosApi,
} from '../api/medicosApi'
import { Empresa, empresasApi } from '../api/empresasApi'
import { isValidCpf, formatCpf } from '../utils/cpf'

// ─── Constants ────────────────────────────────────────────────────────────────

const STEPS = [
  { label: 'Dados Pessoais',      Icon: User },
  { label: 'Dados Profissionais', Icon: Stethoscope },
  { label: 'Dados Bancários',     Icon: CreditCard },
] as const

const UFS = [
  'AC','AL','AM','AP','BA','CE','DF','ES','GO','MA',
  'MG','MS','MT','PA','PB','PE','PI','PR','RJ','RN',
  'RO','RR','RS','SC','SE','SP','TO',
]

const TIPO_PIX_OPTIONS: { value: TipoPix; label: string }[] = [
  { value: 'CPF',       label: 'CPF' },
  { value: 'CNPJ',      label: 'CNPJ' },
  { value: 'EMAIL',     label: 'E-mail' },
  { value: 'TELEFONE',  label: 'Telefone' },
  { value: 'ALEATORIA', label: 'Chave Aleatória' },
]

// ─── Types ────────────────────────────────────────────────────────────────────

interface BankForm {
  tipoPix: TipoPix | ''
  chavePix: string
  cpfsAdicionaisSplit: string
}

interface Props {
  medico: Medico | null
  onClose: () => void
  onSaved: (medico: Medico) => void
}

const emptyMedico = (): MedicoRequest => ({
  cpf: '', nome: '', crm: '', crmUf: '', especialidade: '',
  email: '', telefone: '', empresaId: '',
})

const emptyBank = (): BankForm => ({ tipoPix: '', chavePix: '', cpfsAdicionaisSplit: '' })

// ─── PIX masking ─────────────────────────────────────────────────────────────

export function maskPixKey(tipo: TipoPix, chave: string): string {
  if (!chave) return '—'
  switch (tipo) {
    case 'EMAIL': {
      const at = chave.indexOf('@')
      const local = at > 0 ? chave.slice(0, at) : chave
      const domain = at > 0 ? chave.slice(at) : ''
      return `${local.slice(0, Math.min(3, local.length))}***${domain}`
    }
    case 'CPF':
      return `${chave.slice(0, 3)}.***.**${chave.slice(-3)}`
    case 'CNPJ':
      return `${chave.slice(0, 2)}.***.***/****-${chave.slice(-2)}`
    case 'TELEFONE':
      return `(${chave.slice(0, 2)}) *****-${chave.slice(-4)}`
    case 'ALEATORIA':
      return `${chave.slice(0, 4)}...${chave.slice(-4)}`
    default:
      return `${chave.slice(0, 3)}***`
  }
}

// ─── Wizard ──────────────────────────────────────────────────────────────────

export function MedicoWizardModal({ medico, onClose, onSaved }: Props) {
  const isEditing = medico !== null
  const [step, setStep]             = useState(0)
  const [maxVisited, setMaxVisited] = useState(0)
  const [form, setForm]             = useState<MedicoRequest>(emptyMedico)
  const [bank, setBank]             = useState<BankForm>(emptyBank)
  const [errors, setErrors]         = useState<Partial<Record<string, string>>>({})
  const [apiError, setApiError]     = useState<string | null>(null)
  const [loading, setLoading]       = useState(false)
  const [empresas, setEmpresas]     = useState<Empresa[]>([])

  useEffect(() => {
    empresasApi.listar(0, 1000).then(p => setEmpresas(p.content)).catch(() => {})
  }, [])

  useEffect(() => {
    if (medico) {
      setForm({
        cpf:           formatCpf(medico.cpf),
        nome:          medico.nome,
        crm:           medico.crm,
        crmUf:         medico.crmUf.trim(),
        especialidade: medico.especialidade ?? '',
        email:         medico.email ?? '',
        telefone:      medico.telefone ?? '',
        empresaId:     '',
      })
      setBank({
        tipoPix:             medico.dadosBancarios?.tipoPix ?? '',
        chavePix:            '',
        cpfsAdicionaisSplit: medico.dadosBancarios?.cpfsAdicionaisSplit ?? '',
      })
      setMaxVisited(2)
    } else {
      setForm(emptyMedico())
      setBank(emptyBank())
      setMaxVisited(0)
    }
    setStep(0)
    setErrors({})
    setApiError(null)
  }, [medico])

  function setField<K extends keyof MedicoRequest>(key: K, value: MedicoRequest[K]) {
    setForm(f => ({ ...f, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function setBankField<K extends keyof BankForm>(key: K, value: BankForm[K]) {
    setBank(b => ({ ...b, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function validateStep(s: number): boolean {
    const errs: Record<string, string> = {}
    if (s === 0) {
      if (!form.nome.trim()) errs.nome = 'Obrigatório'
      if (!form.cpf || !isValidCpf(form.cpf)) errs.cpf = 'CPF inválido'
    }
    if (s === 1) {
      if (!form.crm.trim()) errs.crm = 'Obrigatório'
      if (!form.crmUf)       errs.crmUf = 'Obrigatório'
      if (!form.empresaId)   errs.empresaId = 'Obrigatório'
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
        ? await medicosApi.atualizar(medico.id, form)
        : await medicosApi.criar(form)

      if (bank.tipoPix && bank.chavePix.trim()) {
        const bankReq: DadosBancariosMedicoRequest = {
          tipoPix:             bank.tipoPix,
          chavePix:            bank.chavePix.trim(),
          cpfsAdicionaisSplit: bank.cpfsAdicionaisSplit.trim() || null,
          confirmarAlteracao:  true,
        }
        const updatedBank = await medicosApi.atualizarDadosBancarios(saved.id, bankReq)
        saved.dadosBancarios = updatedBank
      }

      onSaved(saved)
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar médico')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={isEditing ? 'Editar Médico' : 'Novo Médico'}
      size="lg"
    >
      <div className="flex flex-col gap-6">
        <WizardSteps current={step} maxVisited={maxVisited} onGo={goTo} />

        <div className="min-h-[260px]">
          {step === 0 && (
            <StepDadosPessoais
              form={form}
              errors={errors}
              isEditing={isEditing}
              onChange={setField}
            />
          )}
          {step === 1 && (
            <StepDadosProfissionais
              form={form}
              errors={errors}
              empresas={empresas}
              isEditing={isEditing}
              onChange={setField}
            />
          )}
          {step === 2 && (
            <StepDadosBancarios
              bank={bank}
              errors={errors}
              isEditing={isEditing}
              existingTipoPix={medico?.dadosBancarios?.tipoPix}
              onChange={setBankField}
            />
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
              {isEditing ? 'Salvar alterações' : 'Cadastrar médico'}
            </Button>
          )}
        </div>
      </div>
    </Modal>
  )
}

// ─── Step indicator ───────────────────────────────────────────────────────────

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
        const isDone      = i < current
        const isActive    = i === current
        const isClickable = i <= maxVisited
        return (
          <div key={i} className="flex items-start">
            <button
              type="button"
              onClick={() => isClickable && onGo(i)}
              disabled={!isClickable}
              className="flex flex-col items-center gap-1.5 w-28"
            >
              <div className={[
                'w-11 h-11 rounded-full flex items-center justify-center border-2 transition-all duration-300',
                isDone
                  ? 'bg-secondary-100 border-secondary-400 text-secondary-700'
                  : isActive
                    ? 'bg-primary border-primary text-white shadow-lg shadow-primary/20'
                    : 'bg-white border-gray-200 text-gray-300',
              ].join(' ')}>
                {isDone
                  ? <CheckCircle className="w-5 h-5 text-secondary-600" />
                  : <Icon className="w-5 h-5" />
                }
              </div>
              <span className={[
                'text-xs font-medium text-center leading-tight',
                isDone ? 'text-secondary-700' : isActive ? 'text-primary-700' : 'text-gray-400',
              ].join(' ')}>
                {label}
              </span>
            </button>
            {i < STEPS.length - 1 && (
              <div className="w-14 sm:w-20 h-0.5 mt-5 mx-1 bg-gray-200 rounded-full overflow-hidden">
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

// ─── Step 0: Dados Pessoais ───────────────────────────────────────────────────

function StepDadosPessoais({
  form,
  errors,
  isEditing,
  onChange,
}: {
  form: MedicoRequest
  errors: Partial<Record<string, string>>
  isEditing: boolean
  onChange: <K extends keyof MedicoRequest>(k: K, v: MedicoRequest[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Informe os dados pessoais do médico.</p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <Input
            label="Nome completo *"
            value={form.nome}
            onChange={e => onChange('nome', e.target.value)}
            error={errors.nome}
            placeholder="Nome do médico"
          />
        </div>
        <CpfInput
          label="CPF *"
          value={form.cpf}
          onChange={v => onChange('cpf', v)}
          error={errors.cpf}
          disabled={isEditing}
        />
        <Input
          label="Telefone"
          value={form.telefone}
          onChange={e => onChange('telefone', e.target.value)}
          error={errors.telefone}
          placeholder="(00) 00000-0000"
        />
        <div className="sm:col-span-2">
          <Input
            label="E-mail"
            type="email"
            value={form.email}
            onChange={e => onChange('email', e.target.value)}
            error={errors.email}
            placeholder="medico@exemplo.com.br"
          />
        </div>
      </div>
    </div>
  )
}

// ─── Step 1: Dados Profissionais ──────────────────────────────────────────────

function StepDadosProfissionais({
  form,
  errors,
  empresas,
  isEditing,
  onChange,
}: {
  form: MedicoRequest
  errors: Partial<Record<string, string>>
  empresas: Empresa[]
  isEditing: boolean
  onChange: <K extends keyof MedicoRequest>(k: K, v: MedicoRequest[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Informe os dados profissionais e o vínculo com a empresa.</p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input
          label="CRM *"
          value={form.crm}
          onChange={e => onChange('crm', e.target.value)}
          error={errors.crm}
          placeholder="Ex: 123456"
        />
        <SelectField
          label="UF do CRM *"
          value={form.crmUf}
          onChange={v => onChange('crmUf', v)}
          error={errors.crmUf}
          placeholder="Selecione a UF"
        >
          {UFS.map(uf => <option key={uf} value={uf}>{uf}</option>)}
        </SelectField>
        <div className="sm:col-span-2">
          <Input
            label="Especialidade"
            value={form.especialidade}
            onChange={e => onChange('especialidade', e.target.value)}
            error={errors.especialidade}
            placeholder="Ex: Cardiologia"
          />
        </div>
        <div className="sm:col-span-2">
          <SelectField
            label="Empresa vinculada *"
            value={form.empresaId}
            onChange={v => onChange('empresaId', v)}
            error={errors.empresaId}
            placeholder="Selecione a empresa"
            disabled={isEditing}
          >
            {empresas.map(e => (
              <option key={e.id} value={e.id}>{e.razaoSocial}</option>
            ))}
          </SelectField>
        </div>
      </div>
    </div>
  )
}

// ─── Step 2: Dados Bancários ──────────────────────────────────────────────────

function StepDadosBancarios({
  bank,
  errors,
  isEditing,
  existingTipoPix,
  onChange,
}: {
  bank: BankForm
  errors: Partial<Record<string, string>>
  isEditing: boolean
  existingTipoPix?: TipoPix
  onChange: <K extends keyof BankForm>(k: K, v: BankForm[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">
        Dados bancários para repasse via PIX.{' '}
        <span className="text-gray-400">Passo opcional — pode ser configurado depois.</span>
      </p>

      {isEditing && existingTipoPix && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          Já existe uma chave PIX cadastrada. Preencha os campos abaixo apenas se desejar alterá-la.
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <SelectField
          label="Tipo de chave PIX"
          value={bank.tipoPix}
          onChange={v => onChange('tipoPix', v as TipoPix | '')}
          error={errors.tipoPix}
          placeholder="Selecione o tipo"
        >
          {TIPO_PIX_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </SelectField>
        <Input
          label="Chave PIX"
          value={bank.chavePix}
          onChange={e => onChange('chavePix', e.target.value)}
          error={errors.chavePix}
          placeholder={pixPlaceholder(bank.tipoPix)}
          disabled={!bank.tipoPix}
        />
        <div className="sm:col-span-2">
          <Input
            label="CPFs para split acima de R$ 40.000 (separados por vírgula)"
            value={bank.cpfsAdicionaisSplit}
            onChange={e => onChange('cpfsAdicionaisSplit', e.target.value)}
            error={errors.cpfsAdicionaisSplit}
            placeholder="Ex: 000.000.000-00, 111.111.111-11"
          />
        </div>
      </div>

      {bank.tipoPix && bank.chavePix && (
        <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Pré-visualização (mascarado após salvar)</p>
          <p className="mt-1 text-sm font-semibold text-gray-900 font-mono">
            {maskPixKey(bank.tipoPix as TipoPix, bank.chavePix)}
          </p>
        </div>
      )}
    </div>
  )
}

function pixPlaceholder(tipo: TipoPix | ''): string {
  switch (tipo) {
    case 'CPF':       return '000.000.000-00'
    case 'CNPJ':      return '00.000.000/0001-00'
    case 'EMAIL':     return 'email@exemplo.com'
    case 'TELEFONE':  return '+5511999999999'
    case 'ALEATORIA': return 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
    default:          return 'Selecione o tipo primeiro'
  }
}

// ─── Shared select ────────────────────────────────────────────────────────────

function SelectField({
  label,
  value,
  onChange,
  error,
  placeholder,
  disabled,
  children,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  error?: string
  placeholder?: string
  disabled?: boolean
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-sm font-medium text-gray-700">{label}</label>
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        disabled={disabled}
        className={[
          'block w-full rounded-lg border px-3 py-2 text-sm',
          'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary',
          'disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed',
          error ? 'border-red-400' : 'border-gray-300',
        ].join(' ')}
      >
        <option value="">{placeholder ?? 'Selecione...'}</option>
        {children}
      </select>
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  )
}
