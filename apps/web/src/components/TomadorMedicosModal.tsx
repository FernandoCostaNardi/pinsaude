import { useCallback, useEffect, useState } from 'react'
import { Stethoscope, XCircle } from 'lucide-react'
import { Modal, Button, Alert, Spinner } from '@pinsaude/ui'
import { Tomador, MedicoTomador, tomadoresApi } from '../api/tomadoresApi'
import { Medico, medicosApi } from '../api/medicosApi'

interface Props {
  tomador: Tomador
  canWrite: boolean
  onClose: () => void
}

export function TomadorMedicosModal({ tomador, canWrite, onClose }: Props) {
  const [alocados,     setAlocados]     = useState<MedicoTomador[]>([])
  const [todosMedicos, setTodosMedicos] = useState<Medico[]>([])
  const [loading,      setLoading]      = useState(true)
  const [error,        setError]        = useState<string | null>(null)

  const [addMedicoId,     setAddMedicoId]     = useState('')
  const [adding,           setAdding]           = useState(false)
  const [removingMedicoId, setRemovingMedicoId] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [medicosDoTomador, medicos] = await Promise.all([
        tomadoresApi.listarMedicos(tomador.id),
        // Fallback silencioso: se o papel logado não tiver acesso a GET /api/medicos
        // (ex.: role "medico"), os nomes exibem o UUID em vez de travar o modal.
        medicosApi.listar(0, 1000).then(p => p.content).catch(() => [] as Medico[]),
      ])
      setAlocados(medicosDoTomador)
      setTodosMedicos(medicos)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar médicos alocados')
    } finally {
      setLoading(false)
    }
  }, [tomador.id])

  useEffect(() => { carregar() }, [carregar])

  const medicoPorId = new Map(todosMedicos.map(m => [m.id, m]))

  async function handleAdicionar() {
    if (!addMedicoId) return
    setAdding(true)
    setError(null)
    try {
      const novo = await tomadoresApi.adicionarMedico(tomador.id, addMedicoId)
      setAlocados(prev => [...prev, novo])
      setAddMedicoId('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao adicionar médico')
    } finally {
      setAdding(false)
    }
  }

  async function handleRemover(medicoId: string) {
    setRemovingMedicoId(medicoId)
    setError(null)
    try {
      await tomadoresApi.removerMedico(tomador.id, medicoId)
      setAlocados(prev => prev.filter(a => a.medicoId !== medicoId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao remover médico')
    } finally {
      setRemovingMedicoId(null)
    }
  }

  return (
    <Modal
      open
      title={
        <div>
          <div className="flex items-center gap-2">
            <Stethoscope size={16} className="text-primary" />
            <span className="text-base font-bold">Médicos Alocados</span>
          </div>
          <p className="text-xs text-ds-light mt-0.5 truncate">{tomador.razaoSocialNome}</p>
        </div>
      }
      onClose={onClose}
      size="lg"
    >
      {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

      {loading ? (
        <div className="flex justify-center py-10"><Spinner /></div>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Médicos alocados</p>
            <span className="text-xs text-ds-light">{alocados.length} médico(s)</span>
          </div>

          <div className="flex flex-col gap-2">
            {alocados.map(a => {
              const medico = medicoPorId.get(a.medicoId)
              return (
                <div key={a.medicoId}
                  className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-ds-input border border-ds-border">
                  <div className="flex items-center gap-3 min-w-0">
                    <Stethoscope size={15} className="text-primary shrink-0" />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ds-text truncate">
                        {medico?.nome ?? a.medicoId}
                      </p>
                      {medico && (
                        <p className="text-xs text-ds-light">
                          CRM {medico.crm}/{medico.crmUf}
                          {medico.especialidade && ` · ${medico.especialidade}`}
                        </p>
                      )}
                    </div>
                  </div>
                  {canWrite && (
                    <button
                      onClick={() => handleRemover(a.medicoId)}
                      disabled={removingMedicoId === a.medicoId}
                      className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors disabled:opacity-50"
                      title="Remover médico"
                    >
                      <XCircle size={15} />
                    </button>
                  )}
                </div>
              )
            })}
            {alocados.length === 0 && (
              <p className="text-sm text-ds-light">Nenhum médico alocado a este tomador.</p>
            )}
          </div>

          {canWrite && (
            <div className="mt-1 flex gap-2">
              <select
                value={addMedicoId}
                onChange={e => setAddMedicoId(e.target.value)}
                className="flex-1 text-sm border border-ds-border rounded-lg px-3 py-1.5 bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
              >
                <option value="">Adicionar médico...</option>
                {todosMedicos
                  .filter(m => !alocados.some(a => a.medicoId === m.id))
                  .map(m => (
                    <option key={m.id} value={m.id}>{m.nome} — CRM {m.crm}/{m.crmUf}</option>
                  ))
                }
              </select>
              <Button
                size="sm"
                onClick={handleAdicionar}
                disabled={!addMedicoId || adding}
              >
                {adding ? 'Adicionando...' : 'Adicionar'}
              </Button>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}
