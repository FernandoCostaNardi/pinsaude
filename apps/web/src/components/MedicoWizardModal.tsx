import { useEffect, useState } from 'react'
import { User, Stethoscope } from 'lucide-react'

function WhatsAppIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z"/>
    </svg>
  )
}
import { Modal, Input, Button, Alert, StepWizard } from '@pinsaude/ui'
import { CpfInput } from './CpfInput'
import { Medico, MedicoRequest, medicosApi } from '../api/medicosApi'
import { TipoPix } from '../api/medicosApi'

import { isValidCpf, formatCpf } from '../utils/cpf'

// ─── Constants ────────────────────────────────────────────────────────────────

const STEPS = [
  { label: 'Dados Pessoais',      icon: User },
  { label: 'Dados Profissionais', icon: Stethoscope },
]

const UFS = [
  'AC','AL','AM','AP','BA','CE','DF','ES','GO','MA',
  'MG','MS','MT','PA','PB','PE','PI','PR','RJ','RN',
  'RO','RR','RS','SC','SE','SP','TO',
]

// ─── Types ────────────────────────────────────────────────────────────────────

interface Props {
  medico: Medico | null
  onClose: () => void
  onSaved: (medico: Medico) => void
}

const emptyMedico = (): MedicoRequest => ({
  cpf: '', nome: '', crm: '', crmUf: '', especialidade: '',
  email: '', telefone: '', taxaPinPct: 0.15,
})

// ─── PIX masking ─────────────────────────────────────────────────────────────
// Reaproveitado por MedicoPerfilPage / ContasBancariasSection para mascarar a
// chave PIX na exibição (o backend nunca retorna a chave em claro fora do
// próprio fluxo de edição).

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
// Cadastra apenas os dados pessoais/profissionais do médico. Contas bancárias
// (PIX e/ou TED — o médico pode ter várias) são geridas depois, na aba
// "Dados Bancários" do perfil (ContasBancariasSection), assim como
// documentos/vínculos/tomadores.

export function MedicoWizardModal({ medico, onClose, onSaved }: Props) {
  const isEditing = medico !== null
  const [step, setStep]             = useState(0)
  const [maxVisited, setMaxVisited] = useState(0)
  const [form, setForm]             = useState<MedicoRequest>(emptyMedico)
  const [errors, setErrors]         = useState<Partial<Record<string, string>>>({})
  const [apiError, setApiError]     = useState<string | null>(null)
  const [loading, setLoading]       = useState(false)

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
        taxaPinPct:    medico.taxaPinPct ?? 0.15,
      })
      setMaxVisited(1)
    } else {
      setForm(emptyMedico())
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

  function validateStep(s: number): boolean {
    const errs: Record<string, string> = {}
    if (s === 0) {
      if (!form.nome.trim()) errs.nome = 'Obrigatório'
      if (!form.cpf || !isValidCpf(form.cpf)) errs.cpf = 'CPF inválido'
    }
    if (s === 1) {
      if (!form.crm.trim()) errs.crm = 'Obrigatório'
      if (!form.crmUf)       errs.crmUf = 'Obrigatório'
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
    if (!validateStep(step)) return
    setLoading(true)
    setApiError(null)
    try {
      const saved = isEditing
        ? await medicosApi.atualizar(medico.id, form)
        : await medicosApi.criar(form)
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
        <StepWizard steps={STEPS} current={step} maxVisited={maxVisited} onStepClick={goTo} />

        <div className="min-h-[260px]">
          {step === 0 && (
            <StepDadosPessoais
              form={form}
              errors={errors}
              onChange={setField}
            />
          )}
          {step === 1 && (
            <StepDadosProfissionais
              form={form}
              errors={errors}
              onChange={setField}
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
          {step < 1 ? (
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

// ─── Step 0: Dados Pessoais ───────────────────────────────────────────────────

function StepDadosPessoais({
  form,
  errors,
  onChange,
}: {
  form: MedicoRequest
  errors: Partial<Record<string, string>>
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
        />
        <Input
          label={
            <span className="flex items-center gap-2">
              Telefone
              <span className="flex items-center gap-1 text-[#25D366]">
                <WhatsAppIcon className="w-3.5 h-3.5" />
                WhatsApp
              </span>
            </span>
          }
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
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Taxa Pin negociada (%)
          </label>
          <div className="relative">
            <input
              type="number"
              min="1"
              max="50"
              step="0.5"
              value={form.taxaPinPct != null ? +(form.taxaPinPct * 100).toFixed(4) : 15}
              onChange={e => {
                const v = parseFloat(e.target.value)
                onChange('taxaPinPct', isNaN(v) ? 0.15 : +(v / 100).toFixed(4))
              }}
              className="block w-full rounded-lg border border-gray-300 px-3 py-2 pr-8 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-500 pointer-events-none">%</span>
          </div>
          {errors.taxaPinPct && <p className="text-xs text-red-600 mt-1">{errors.taxaPinPct}</p>}
          <p className="text-xs text-gray-400 mt-1">Padrão: 15%. O médico recebe sempre 100% − esta taxa.</p>
        </div>
      </div>
    </div>
  )
}

// ─── Step 1: Dados Profissionais ──────────────────────────────────────────────

function StepDadosProfissionais({
  form,
  errors,
  onChange,
}: {
  form: MedicoRequest
  errors: Partial<Record<string, string>>
  onChange: <K extends keyof MedicoRequest>(k: K, v: MedicoRequest[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-gray-500">Informe os dados profissionais do médico.</p>
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
      </div>
    </div>
  )
}

// ─── Shared select ────────────────────────────────────────────────────────────

export function SelectField({
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
