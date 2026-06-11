import { useEffect, useState } from 'react'
import { UserPlus, RefreshCw } from 'lucide-react'
import { usersApi, type Usuario } from '../api/usersApi'
import { UserTable } from '../components/UserTable'
import { InviteUserModal } from '../components/InviteUserModal'

export function UsersPage() {
  const [usuarios, setUsuarios]     = useState<Usuario[]>([])
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState<string | null>(null)
  const [showModal, setShowModal]   = useState(false)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await usersApi.listar()
      setUsuarios(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar usuários')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const handleUpdated = (updated: Usuario) => {
    setUsuarios(prev => prev.map(u => u.id === updated.id ? updated : u))
  }

  const handleSaved = (novo: Usuario) => {
    setUsuarios(prev => [novo, ...prev])
    setShowModal(false)
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Gestão de Usuários</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Usuários da sua organização no sistema Pin Saúde
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={load}
            disabled={loading}
            className="flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
            Atualizar
          </button>
          <button
            onClick={() => setShowModal(true)}
            className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-600"
          >
            <UserPlus size={14} />
            Convidar usuário
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {loading && !error && (
        <div className="flex items-center justify-center py-16 text-gray-400 text-sm">
          <RefreshCw size={16} className="animate-spin mr-2" />
          Carregando usuários…
        </div>
      )}

      {!loading && (
        <UserTable usuarios={usuarios} onUpdated={handleUpdated} />
      )}

      {showModal && (
        <InviteUserModal
          onClose={() => setShowModal(false)}
          onSaved={handleSaved}
        />
      )}
    </div>
  )
}
