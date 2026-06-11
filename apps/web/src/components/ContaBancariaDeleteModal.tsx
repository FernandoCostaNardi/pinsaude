import { useState } from 'react'
import { Trash2 } from 'lucide-react'
import { Modal, Button, Alert } from '@pinsaude/ui'
import { ContaBancaria, contasBancariasApi } from '../api/contasBancariasApi'

interface Props {
  empresaId: string
  conta: ContaBancaria
  onClose: () => void
  onDeleted: (id: string) => void
}

export function ContaBancariaDeleteModal({ empresaId, conta, onClose, onDeleted }: Props) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleDelete() {
    setLoading(true)
    setError(null)
    try {
      await contasBancariasApi.remover(empresaId, conta.id)
      onDeleted(conta.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao excluir conta')
      setLoading(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Excluir Conta Bancária" size="sm">
      <div className="flex flex-col gap-4">
        {error && <Alert variant="error">{error}</Alert>}

        <div className="flex items-start gap-3">
          <div className="flex-shrink-0 w-10 h-10 rounded-full bg-red-100 flex items-center justify-center">
            <Trash2 size={18} className="text-red-600" />
          </div>
          <div>
            <p className="text-sm font-medium text-gray-900">
              Tem certeza que deseja excluir esta conta?
            </p>
            <p className="mt-1 text-sm text-gray-500">
              <span className="font-medium">{conta.banco}</span>
              {' '}— Ag. {conta.agencia} / Cc. {conta.conta}
            </p>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-2 border-t">
          <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" loading={loading} onClick={handleDelete}>
            Excluir
          </Button>
        </div>
      </div>
    </Modal>
  )
}
