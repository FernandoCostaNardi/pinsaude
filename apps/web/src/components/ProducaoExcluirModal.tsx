import { useState } from 'react'
import { Trash2 } from 'lucide-react'
import { Modal, Button, Alert } from '@pinsaude/ui'
import { producoesApi } from '../api/producoesApi'

interface Props {
  producaoId: string
  // Resumo mostrado na confirmação (ex.: "Hospital X · Outubro/2026 · R$ 1.000,00")
  resumo: string
  onClose: () => void
  onExcluida: (id: string) => void
}

// Confirmação de exclusão de produção lançada errado — usada no backoffice (Produções) e no
// Portal do Médico (Minhas Solicitações). Motivo é opcional e fica na auditoria.
export function ProducaoExcluirModal({ producaoId, resumo, onClose, onExcluida }: Props) {
  const [motivo, setMotivo] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleExcluir() {
    setLoading(true)
    setError(null)
    try {
      await producoesApi.excluir(producaoId, motivo)
      onExcluida(producaoId)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao excluir produção')
      setLoading(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Excluir produção" size="sm">
      <div className="flex flex-col gap-4">
        {error && <Alert variant="error">{error}</Alert>}

        <div className="flex items-start gap-3">
          <div className="flex-shrink-0 w-10 h-10 rounded-full bg-red-100 flex items-center justify-center">
            <Trash2 size={18} className="text-red-600" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-medium text-gray-900">
              Tem certeza que deseja excluir esta produção?
            </p>
            <p className="mt-1 text-sm text-gray-500 break-words">{resumo}</p>
            <p className="mt-2 text-xs text-gray-400">
              Use quando a produção foi lançada errado. Ela deixa de aparecer em todas as telas e
              não pode ser recuperada por aqui.
            </p>
          </div>
        </div>

        <div>
          <label htmlFor="motivo-exclusao" className="block text-xs font-medium text-gray-600 mb-1">
            Motivo (opcional)
          </label>
          <textarea
            id="motivo-exclusao"
            value={motivo}
            onChange={e => setMotivo(e.target.value)}
            maxLength={500}
            rows={2}
            placeholder="Ex.: lançada em duplicidade, valor errado, tomador errado"
            className="w-full rounded-lg border border-ds-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        <div className="flex justify-end gap-3 pt-2 border-t">
          <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" loading={loading} onClick={handleExcluir}>
            Excluir
          </Button>
        </div>
      </div>
    </Modal>
  )
}
