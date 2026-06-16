import { useState } from 'react'
import { UserX } from 'lucide-react'
import { Modal, Button, Alert } from '@pinsaude/ui'
import { Medico, medicosApi } from '../api/medicosApi'

interface Props {
  medico: Medico
  onClose: () => void
  onInativado: (medico: Medico) => void
}

export function MedicoInativarModal({ medico, onClose, onInativado }: Props) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleInativar() {
    setLoading(true)
    setError(null)
    try {
      const updated = await medicosApi.inativar(medico.id)
      onInativado(updated)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao inativar médico')
      setLoading(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Inativar Médico" size="sm">
      <div className="flex flex-col gap-4">
        {error && <Alert variant="error">{error}</Alert>}

        <div className="flex items-start gap-3">
          <div className="flex-shrink-0 w-10 h-10 rounded-full bg-red-100 flex items-center justify-center">
            <UserX size={18} className="text-red-600" />
          </div>
          <div>
            <p className="text-sm font-medium text-gray-900">
              Tem certeza que deseja inativar este médico?
            </p>
            <p className="mt-1 text-sm text-gray-500 font-semibold">{medico.nome}</p>
            <p className="text-xs text-gray-400 mt-0.5">CRM {medico.crm}/{medico.crmUf.trim()}</p>
            <p className="mt-2 text-xs text-gray-400">
              O médico não aparecerá mais na listagem. O histórico de dados é preservado.
            </p>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-2 border-t">
          <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" loading={loading} onClick={handleInativar}>
            Inativar
          </Button>
        </div>
      </div>
    </Modal>
  )
}
