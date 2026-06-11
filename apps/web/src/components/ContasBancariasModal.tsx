import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Star } from 'lucide-react'
import { Modal, Button, Spinner, Alert, Badge, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { ContaBancaria, contasBancariasApi } from '../api/contasBancariasApi'
import { ContaBancariaFormModal } from './ContaBancariaFormModal'
import { ContaBancariaDeleteModal } from './ContaBancariaDeleteModal'

interface Props {
  empresaId: string
  empresaNome: string
  isGestao: boolean
  onClose: () => void
}

export function ContasBancariasModal({ empresaId, empresaNome, isGestao, onClose }: Props) {
  const [contas, setContas] = useState<ContaBancaria[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<ContaBancaria | null>(null)
  const [deleting, setDeleting] = useState<ContaBancaria | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setContas(await contasBancariasApi.listar(empresaId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar contas')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [empresaId])

  function handleSaved(saved: ContaBancaria) {
    setContas(prev => {
      const list = saved.principal
        ? prev.map(c => ({ ...c, principal: false }))
        : prev
      const idx = list.findIndex(c => c.id === saved.id)
      return idx >= 0 ? list.map(c => c.id === saved.id ? saved : c) : [...list, saved]
    })
    setShowForm(false)
    setEditing(null)
  }

  function handleDeleted(id: string) {
    setContas(prev => prev.filter(c => c.id !== id))
    setDeleting(null)
  }

  function openEdit(c: ContaBancaria) {
    setEditing(c)
    setShowForm(true)
  }

  return (
    <>
      <Modal open onClose={onClose} title="Contas Bancárias" size="xl">
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-gray-500">{empresaNome}</p>
            {isGestao && (
              <Button size="sm" onClick={() => { setEditing(null); setShowForm(true) }}>
                <Plus size={14} /> Adicionar Conta
              </Button>
            )}
          </div>

          {loading ? (
            <div className="flex justify-center py-10">
              <Spinner />
            </div>
          ) : error ? (
            <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>
          ) : contas.length === 0 ? (
            <div className="text-center py-10 text-sm text-gray-400">
              Nenhuma conta bancária cadastrada.
              {isGestao && (
                <button
                  onClick={() => { setEditing(null); setShowForm(true) }}
                  className="ml-1 text-primary hover:underline"
                >
                  Adicionar agora
                </button>
              )}
            </div>
          ) : (
            <Table>
              <THead>
                <TRow>
                  <TH>Banco</TH>
                  <TH>Agência</TH>
                  <TH>Conta</TH>
                  <TH>Tipo</TH>
                  <TH>Chave PIX</TH>
                  <TH>Principal</TH>
                  {isGestao && <TH className="text-right">Ações</TH>}
                </TRow>
              </THead>
              <TBody>
                {contas.map(c => (
                  <TRow key={c.id}>
                    <TD className="font-medium">{c.banco}</TD>
                    <TD>{c.agencia}</TD>
                    <TD className="font-mono text-xs">{c.conta}</TD>
                    <TD>
                      <span className="text-xs text-gray-600">
                        {c.tipoConta === 'CORRENTE' ? 'Corrente' : 'Poupança'}
                      </span>
                    </TD>
                    <TD className="text-xs text-gray-500">{c.chavePix ?? '—'}</TD>
                    <TD>
                      {c.principal
                        ? <Badge variant="success"><Star size={11} className="inline mr-1" />Principal</Badge>
                        : <span className="text-xs text-gray-400">—</span>
                      }
                    </TD>
                    {isGestao && (
                      <TD className="text-right">
                        <div className="flex justify-end gap-1">
                          <button
                            onClick={() => openEdit(c)}
                            className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-primary transition-colors"
                            title="Editar"
                          >
                            <Pencil size={14} />
                          </button>
                          <button
                            onClick={() => setDeleting(c)}
                            className="p-1.5 rounded hover:bg-red-50 text-gray-500 hover:text-red-600 transition-colors"
                            title="Excluir"
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </TD>
                    )}
                  </TRow>
                ))}
              </TBody>
            </Table>
          )}

          <div className="flex justify-end pt-2 border-t">
            <Button variant="outline" onClick={onClose}>Fechar</Button>
          </div>
        </div>
      </Modal>

      {showForm && (
        <ContaBancariaFormModal
          empresaId={empresaId}
          conta={editing}
          onClose={() => { setShowForm(false); setEditing(null) }}
          onSaved={handleSaved}
        />
      )}

      {deleting && (
        <ContaBancariaDeleteModal
          empresaId={empresaId}
          conta={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={handleDeleted}
        />
      )}
    </>
  )
}
