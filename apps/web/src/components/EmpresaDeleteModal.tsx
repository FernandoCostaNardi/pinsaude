import { useState } from 'react'
import { Trash2 } from 'lucide-react'
import { Modal, Button, Alert } from '@pinsaude/ui'
import { Empresa, empresasApi } from '../api/empresasApi'

interface Props {
  empresa: Empresa
  onClose: () => void
  onDeleted: (id: string) => void
}

export function EmpresaDeleteModal({ empresa, onClose, onDeleted }: Props) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleDelete() {
    setLoading(true)
    setError(null)
    try {
      await empresasApi.remover(empresa.id)
      onDeleted(empresa.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao excluir empresa')
      setLoading(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Excluir Empresa" size="sm">
      <div className="flex flex-col gap-4">
        {error && <Alert variant="error">{error}</Alert>}

        <div className="flex items-start gap-3">
          <div className="flex-shrink-0 w-10 h-10 rounded-full bg-red-100 flex items-center justify-center">
            <Trash2 size={18} className="text-red-600" />
          </div>
          <div>
            <p className="text-sm font-medium text-gray-900">
              Tem certeza que deseja excluir esta empresa?
            </p>
            <p className="mt-1 text-sm text-gray-500">
              <span className="font-medium">{empresa.razaoSocial}</span>
              <span className="ml-1 text-gray-400">({empresa.cnpj})</span>
            </p>
            <p className="mt-2 text-xs text-gray-400">
              Esta ação desativa o cadastro. Os dados históricos são preservados.
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
