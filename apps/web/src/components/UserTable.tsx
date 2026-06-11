import { useState } from 'react'
import { Send, UserCheck, UserX } from 'lucide-react'
import { usersApi, type Usuario } from '../api/usersApi'
import { UserStatusBadge } from './UserStatusBadge'

const PERFIS: Record<string, string> = {
  medico:     'Médico',
  operacao:   'Operação',
  financeiro: 'Financeiro',
  contabil:   'Contábil',
  gestao:     'Gestão',
}

interface UserTableProps {
  usuarios:   Usuario[]
  onUpdated:  (usuario: Usuario) => void
}

export function UserTable({ usuarios, onUpdated }: UserTableProps) {
  const [loadingId, setLoadingId] = useState<string | null>(null)
  const [errorMsg, setErrorMsg]   = useState<string | null>(null)

  const withLoading = async (id: string, fn: () => Promise<void>) => {
    setLoadingId(id)
    setErrorMsg(null)
    try { await fn() } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : 'Erro inesperado')
    } finally { setLoadingId(null) }
  }

  const toggleStatus = (u: Usuario) =>
    withLoading(u.id, async () => {
      const updated = await usersApi.alterarStatus(u.id, !u.ativo)
      onUpdated(updated)
    })

  const reenviar = (u: Usuario) =>
    withLoading(u.id, async () => {
      await usersApi.reenviarConvite(u.id)
    })

  const handlePerfilChange = (u: Usuario, perfil: string) =>
    withLoading(u.id, async () => {
      const updated = await usersApi.alterarPerfil(u.id, perfil)
      onUpdated(updated)
    })

  return (
    <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white">
      {errorMsg && (
        <p className="px-4 py-2 text-sm text-red-600 bg-red-50 border-b border-red-100">{errorMsg}</p>
      )}
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left font-medium text-gray-500 uppercase tracking-wider text-xs">Nome</th>
            <th className="px-4 py-3 text-left font-medium text-gray-500 uppercase tracking-wider text-xs">E-mail</th>
            <th className="px-4 py-3 text-left font-medium text-gray-500 uppercase tracking-wider text-xs">Perfil</th>
            <th className="px-4 py-3 text-left font-medium text-gray-500 uppercase tracking-wider text-xs">Status</th>
            <th className="px-4 py-3 text-right font-medium text-gray-500 uppercase tracking-wider text-xs">Ações</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {usuarios.length === 0 && (
            <tr>
              <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                Nenhum usuário encontrado.
              </td>
            </tr>
          )}
          {usuarios.map(u => (
            <tr key={u.id} className="hover:bg-gray-50">
              <td className="px-4 py-3 font-medium text-gray-900 whitespace-nowrap">{u.nome || '—'}</td>
              <td className="px-4 py-3 text-gray-600">{u.email}</td>
              <td className="px-4 py-3">
                <select
                  value={u.perfil}
                  disabled={loadingId === u.id}
                  onChange={e => handlePerfilChange(u, e.target.value)}
                  className="rounded border border-gray-200 bg-transparent px-2 py-1 text-xs focus:border-primary focus:outline-none"
                >
                  {Object.entries(PERFIS).map(([v, l]) => (
                    <option key={v} value={v}>{l}</option>
                  ))}
                </select>
              </td>
              <td className="px-4 py-3">
                <UserStatusBadge ativo={u.ativo} pendente={u.pendente} />
              </td>
              <td className="px-4 py-3 text-right">
                <div className="flex items-center justify-end gap-1">
                  {u.pendente && u.ativo && (
                    <button
                      title="Reenviar convite"
                      disabled={loadingId === u.id}
                      onClick={() => reenviar(u)}
                      className="rounded p-1.5 text-gray-400 hover:text-primary hover:bg-primary-50 disabled:opacity-40"
                    >
                      <Send size={14} />
                    </button>
                  )}
                  <button
                    title={u.ativo ? 'Desativar usuário' : 'Ativar usuário'}
                    disabled={loadingId === u.id}
                    onClick={() => toggleStatus(u)}
                    className="rounded p-1.5 text-gray-400 hover:text-gray-700 hover:bg-gray-100 disabled:opacity-40"
                  >
                    {u.ativo ? <UserX size={14} /> : <UserCheck size={14} />}
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
