import { FormEvent, useEffect, useState } from 'react'
import { Modal, Input, Button, Alert } from '@pinsaude/ui'
import { ContaBancaria, ContaBancariaRequest, TipoConta, contasBancariasApi } from '../api/contasBancariasApi'

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

export function ContaBancariaFormModal({ empresaId, conta, onClose, onSaved }: Props) {
  const [form, setForm] = useState<ContaBancariaRequest>(emptyForm)
  const [errors, setErrors] = useState<Partial<Record<keyof ContaBancariaRequest, string>>>({})
  const [apiError, setApiError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const isEditing = conta !== null

  useEffect(() => {
    if (conta) {
      setForm({
        banco: conta.banco,
        agencia: conta.agencia,
        conta: conta.conta,
        tipoConta: conta.tipoConta,
        chavePix: conta.chavePix ?? '',
        principal: conta.principal,
      })
    } else {
      setForm(emptyForm())
    }
    setErrors({})
    setApiError(null)
  }, [conta])

  function set<K extends keyof ContaBancariaRequest>(key: K, value: ContaBancariaRequest[K]) {
    setForm(f => ({ ...f, [key]: value }))
    setErrors(e => ({ ...e, [key]: undefined }))
  }

  function validate(): boolean {
    const errs: Partial<Record<keyof ContaBancariaRequest, string>> = {}
    if (!form.banco.trim()) errs.banco = 'Obrigatório'
    if (!form.agencia.trim()) errs.agencia = 'Obrigatório'
    if (!form.conta.trim()) errs.conta = 'Obrigatório'
    if (!form.tipoConta) errs.tipoConta = 'Obrigatório'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!validate()) return
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
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {apiError && <Alert variant="error">{apiError}</Alert>}

        <Input
          label="Banco *"
          value={form.banco}
          onChange={e => set('banco', e.target.value)}
          error={errors.banco}
          placeholder="Ex: Itaú, Bradesco, Nubank, 001 - Banco do Brasil"
        />

        <div className="grid grid-cols-2 gap-3">
          <Input
            label="Agência *"
            value={form.agencia}
            onChange={e => set('agencia', e.target.value)}
            error={errors.agencia}
            placeholder="Ex: 1234"
          />
          <Input
            label="Conta *"
            value={form.conta}
            onChange={e => set('conta', e.target.value)}
            error={errors.conta}
            placeholder="Ex: 56789-0"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700">Tipo de Conta *</label>
          <div className="flex gap-3">
            {(['CORRENTE', 'POUPANCA'] as TipoConta[]).map(tipo => (
              <label key={tipo} className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="tipoConta"
                  value={tipo}
                  checked={form.tipoConta === tipo}
                  onChange={() => set('tipoConta', tipo)}
                  className="accent-primary"
                />
                <span className="text-sm text-gray-700">
                  {tipo === 'CORRENTE' ? 'Corrente' : 'Poupança'}
                </span>
              </label>
            ))}
          </div>
        </div>

        <Input
          label="Chave PIX"
          value={form.chavePix}
          onChange={e => set('chavePix', e.target.value)}
          placeholder="CPF, e-mail, telefone ou chave aleatória"
          hint="Opcional"
        />

        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={form.principal}
            onChange={e => set('principal', e.target.checked)}
            className="w-4 h-4 accent-primary rounded"
          />
          <span className="text-sm text-gray-700">
            Definir como conta principal
          </span>
          <span className="text-xs text-gray-400">(desmarca a atual)</span>
        </label>

        <div className="flex justify-end gap-3 pt-2 border-t">
          <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="submit" loading={loading}>
            {isEditing ? 'Salvar alterações' : 'Adicionar conta'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
