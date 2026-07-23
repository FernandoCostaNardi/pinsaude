import { useState } from 'react'
import { Landmark, CreditCard, CheckCircle, Star } from 'lucide-react'
import { Modal, Input, Button, Alert, StepWizard } from '@pinsaude/ui'
import { BancoSelect, BancoAvatar, bancos } from './BancoSelect'
import { ContaBancaria, ContaBancariaRequest, TipoConta, contasBancariasApi } from '../api/contasBancariasApi'

const STEPS = [
  { label: 'Banco', icon: Landmark },
  { label: 'Conta e PIX', icon: CreditCard },
  { label: 'Revisão', icon: CheckCircle },
]

const TIPO_LABELS: Record<TipoConta, string> = {
  CORRENTE: 'Corrente',
  POUPANCA: 'Poupança',
}

interface Props {
  empresaId: string
  conta: ContaBancaria | null
  onClose: () => void
  onSaved: (conta: ContaBancaria) => void
}

const emptyForm = (): ContaBancariaRequest => ({
  banco: '',
  agencia: '',
  conta: '',
  tipoConta: 'CORRENTE',
  chavePix: '',
  principal: false,
})

export function ContaBancariaWizardModal({ empresaId, conta, onClose, onSaved }: Props) {
  const isEditing = conta !== null
  const [step, setStep] = useState(0)
  const [maxVisited, setMaxVisited] = useState(() => conta ? 2 : 0)
  const [form, setForm] = useState<ContaBancariaRequest>(() =>
    conta
      ? { banco: conta.banco, agencia: conta.agencia, conta: conta.conta,
          tipoConta: conta.tipoConta, chavePix: conta.chavePix ?? '', principal: conta.principal }
      : emptyForm()
  )
  const [errors, setErrors] = useState<Partial<Record<keyof ContaBancariaRequest, string>>>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  function setField<K extends keyof ContaBancariaRequest>(key: K, value: ContaBancariaRequest[K]) {
    setForm(f => ({ ...f, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function validateStep(s: number): boolean {
    const errs: Partial<Record<keyof ContaBancariaRequest, string>> = {}
    if (s === 0) {
      if (!form.banco.trim()) errs.banco = 'Obrigatório'
      if (!form.tipoConta) errs.tipoConta = 'Obrigatório'
    }
    if (s === 1) {
      if (!form.agencia.trim()) errs.agencia = 'Obrigatório'
      if (!form.conta.trim()) errs.conta = 'Obrigatório'
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
        ? await contasBancariasApi.atualizar(empresaId, conta.id, form)
        : await contasBancariasApi.criar(empresaId, form)
      onSaved(saved)
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Erro ao salvar conta bancária')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={isEditing ? 'Editar Conta Bancária' : 'Nova Conta Bancária'}
      size="md"
    >
      <div className="flex flex-col gap-6">
        <StepWizard steps={STEPS} current={step} maxVisited={maxVisited} onStepClick={goTo} />

        <div className="min-h-[180px]">
          {step === 0 && (
            <StepBanco form={form} errors={errors} onChange={setField} />
          )}
          {step === 1 && (
            <StepContaPix form={form} errors={errors} onChange={setField} />
          )}
          {step === 2 && (
            <StepRevisao form={form} />
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
              {isEditing ? 'Salvar alterações' : 'Adicionar conta'}
            </Button>
          )}
        </div>
      </div>
    </Modal>
  )
}

// ─── Step 1: Banco ────────────────────────────────────────────────────────────

function StepBanco({
  form,
  errors,
  onChange,
}: {
  form: ContaBancariaRequest
  errors: Partial<Record<keyof ContaBancariaRequest, string>>
  onChange: <K extends keyof ContaBancariaRequest>(key: K, value: ContaBancariaRequest[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-xs sm:text-sm text-gray-500">Selecione a instituição financeira e o tipo de conta.</p>
      <BancoSelect
        value={form.banco}
        onChange={(nome) => onChange('banco', nome)}
        error={errors.banco}
      />
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">Tipo de Conta *</label>
        <div className="flex gap-4">
          {(['CORRENTE', 'POUPANCA'] as TipoConta[]).map(tipo => (
            <label key={tipo} className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="tipoConta"
                value={tipo}
                checked={form.tipoConta === tipo}
                onChange={() => onChange('tipoConta', tipo)}
                className="accent-primary"
              />
              <span className="text-sm text-gray-700">{TIPO_LABELS[tipo]}</span>
            </label>
          ))}
        </div>
        {errors.tipoConta && <p className="text-xs text-red-600">{errors.tipoConta}</p>}
      </div>
    </div>
  )
}

// ─── Step 2: Conta e PIX ──────────────────────────────────────────────────────

function StepContaPix({
  form,
  errors,
  onChange,
}: {
  form: ContaBancariaRequest
  errors: Partial<Record<keyof ContaBancariaRequest, string>>
  onChange: <K extends keyof ContaBancariaRequest>(key: K, value: ContaBancariaRequest[K]) => void
}) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-xs sm:text-sm text-gray-500">Informe os dados da conta e, se disponível, a chave PIX.</p>
      <div className="grid grid-cols-2 gap-3">
        <Input
          label="Agência *"
          value={form.agencia}
          onChange={e => onChange('agencia', e.target.value)}
          error={errors.agencia}
          placeholder="Ex: 1234"
        />
        <Input
          label="Conta *"
          value={form.conta}
          onChange={e => onChange('conta', e.target.value)}
          error={errors.conta}
          placeholder="Ex: 56789-0"
        />
      </div>
      <Input
        label="Chave PIX"
        value={form.chavePix}
        onChange={e => onChange('chavePix', e.target.value)}
        placeholder="CPF, e-mail, telefone ou chave aleatória"
        hint="Opcional"
      />
      <label className="flex items-center gap-2 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={form.principal}
          onChange={e => onChange('principal', e.target.checked)}
          className="w-4 h-4 accent-primary rounded"
        />
        <span className="text-sm text-gray-700">Definir como conta principal</span>
        <span className="text-xs text-gray-400">(desmarca a atual)</span>
      </label>
    </div>
  )
}

// ─── Step 3: Revisão ──────────────────────────────────────────────────────────

function StepRevisao({ form }: { form: ContaBancariaRequest }) {
  const bancoData = bancos.find(b => b.nome === form.banco)

  return (
    <div className="flex flex-col gap-4">
      <p className="text-xs sm:text-sm text-gray-500">
        Confira os dados antes de salvar. Clique em um passo acima para editar.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="sm:col-span-2 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Banco</p>
          <div className="mt-1 flex items-center gap-2">
            {bancoData && <BancoAvatar banco={bancoData} size={24} />}
            <p className="text-sm font-medium text-gray-800">{form.banco || '—'}</p>
          </div>
        </div>
        <ReviewField label="Tipo de Conta" value={TIPO_LABELS[form.tipoConta]} />
        <ReviewField label="Agência" value={form.agencia || '—'} />
        <ReviewField label="Conta" value={form.conta || '—'} />
        <ReviewField label="Chave PIX" value={form.chavePix || '—'} />
      </div>
      {form.principal && (
        <div className="flex items-center gap-1.5 rounded-lg border border-secondary-300 bg-secondary-50 px-3 py-2 text-sm text-secondary-700">
          <Star size={13} className="shrink-0" />
          Esta será marcada como conta principal
        </div>
      )}
    </div>
  )
}

function ReviewField({
  label,
  value,
  className,
}: {
  label: string
  value: string
  className?: string
}) {
  return (
    <div className={['rounded-lg border border-gray-200 bg-gray-50 px-4 py-3', className].join(' ')}>
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</p>
      <p className="mt-0.5 text-sm font-medium text-gray-800">{value}</p>
    </div>
  )
}
