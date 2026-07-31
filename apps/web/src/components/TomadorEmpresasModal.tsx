import { useCallback, useEffect, useState } from 'react'
import { Building2, XCircle } from 'lucide-react'
import { Modal, Button, Alert, Spinner } from '@pinsaude/ui'
import { Tomador, TomadorEmpresa, tomadoresApi } from '../api/tomadoresApi'
import { Empresa, empresasApi } from '../api/empresasApi'

interface Props {
  tomador: Tomador
  canWrite: boolean
  onClose: () => void
}

export function TomadorEmpresasModal({ tomador, canWrite, onClose }: Props) {
  const [vinculadas,   setVinculadas]   = useState<TomadorEmpresa[]>([])
  const [todasEmpresas, setTodasEmpresas] = useState<Empresa[]>([])
  const [loading,      setLoading]      = useState(true)
  const [error,        setError]        = useState<string | null>(null)

  const [addEmpresaId,     setAddEmpresaId]     = useState('')
  const [adding,           setAdding]           = useState(false)
  const [removingEmpresaId, setRemovingEmpresaId] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [empresasDoTomador, empresas] = await Promise.all([
        tomadoresApi.listarEmpresas(tomador.id),
        empresasApi.listar(0, 1000).then(p => p.content).catch(() => [] as Empresa[]),
      ])
      setVinculadas(empresasDoTomador)
      setTodasEmpresas(empresas)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar empresas vinculadas')
    } finally {
      setLoading(false)
    }
  }, [tomador.id])

  useEffect(() => { carregar() }, [carregar])

  const empresaPorId = new Map(todasEmpresas.map(e => [e.id, e]))

  async function handleAdicionar() {
    if (!addEmpresaId) return
    setAdding(true)
    setError(null)
    try {
      const nova = await tomadoresApi.adicionarEmpresa(tomador.id, addEmpresaId)
      setVinculadas(prev => [...prev, nova])
      setAddEmpresaId('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao adicionar empresa')
    } finally {
      setAdding(false)
    }
  }

  async function handleRemover(empresaId: string) {
    setRemovingEmpresaId(empresaId)
    setError(null)
    try {
      await tomadoresApi.removerEmpresa(tomador.id, empresaId)
      setVinculadas(prev => prev.filter(v => v.empresaId !== empresaId))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao remover empresa')
    } finally {
      setRemovingEmpresaId(null)
    }
  }

  return (
    <Modal
      open
      title={
        <div>
          <div className="flex items-center gap-2">
            <Building2 size={16} className="text-primary" />
            <span className="text-base font-bold">Empresas Pin Vinculadas</span>
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
            <p className="text-xs font-semibold text-ds-mid uppercase tracking-wide">Empresas vinculadas</p>
            <span className="text-xs text-ds-light">{vinculadas.length} empresa(s)</span>
          </div>
          <p className="text-xs text-ds-light -mt-1">
            A empresa emissora é pré-selecionada automaticamente ao criar Produção/Nota para este tomador.
          </p>

          <div className="flex flex-col gap-2">
            {vinculadas.map(v => {
              const empresa = empresaPorId.get(v.empresaId)
              return (
                <div key={v.empresaId}
                  className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-ds-input border border-ds-border">
                  <div className="flex items-center gap-3 min-w-0">
                    <Building2 size={15} className="text-primary shrink-0" />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ds-text truncate">
                        {empresa?.razaoSocial ?? v.empresaId}
                      </p>
                      {empresa && (
                        <p className="text-xs text-ds-light">{empresa.cnpj}</p>
                      )}
                    </div>
                  </div>
                  {canWrite && (
                    <button
                      onClick={() => handleRemover(v.empresaId)}
                      disabled={removingEmpresaId === v.empresaId}
                      className="p-1 rounded text-ds-light hover:text-red-500 hover:bg-red-50 transition-colors disabled:opacity-50"
                      title="Remover empresa"
                    >
                      <XCircle size={15} />
                    </button>
                  )}
                </div>
              )
            })}
            {vinculadas.length === 0 && (
              <p className="text-sm text-ds-light">Nenhuma empresa Pin vinculada a este tomador.</p>
            )}
          </div>

          {canWrite && (
            <div className="mt-1 flex gap-2">
              <select
                value={addEmpresaId}
                onChange={e => setAddEmpresaId(e.target.value)}
                className="flex-1 text-sm border border-ds-border rounded-lg px-3 py-1.5 bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
              >
                <option value="">Adicionar empresa...</option>
                {todasEmpresas
                  .filter(e => !vinculadas.some(v => v.empresaId === e.id))
                  .map(e => (
                    <option key={e.id} value={e.id}>{e.razaoSocial} — {e.cnpj}</option>
                  ))
                }
              </select>
              <Button
                size="sm"
                onClick={handleAdicionar}
                disabled={!addEmpresaId || adding}
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
