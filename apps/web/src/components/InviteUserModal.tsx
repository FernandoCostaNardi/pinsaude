import { useState } from 'react'
import { X } from 'lucide-react'
import { usersApi, type ConvitePayload, type Usuario } from '../api/usersApi'

const PERFIS = [
  { value: 'medico',     label: 'Médico'      },
  { value: 'operacao',   label: 'Operação'    },
  { value: 'financeiro', label: 'Financeiro'  },
  { value: 'contabil',   label: 'Contábil'    },
  { value: 'gestao',     label: 'Gestão'      },
]

interface InviteUserModalProps {
  onClose:  () => void
  onSaved:  (usuario: Usuario) => void
}

export function InviteUserModal({ onClose, onSaved }: InviteUserModalProps) {
  const [form, setForm]     = useState<ConvitePayload>({ email: '', nome: '', perfil: 'operacao' })
  const [loading, setLoading] = useState(false)
  const [error, setError]   = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const usuario = await usersApi.convidar(form)
      onSaved(usuario)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao enviar convite')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-900">Convidar usuário</h2>
          <button
            onClick={onClose}
            className="rounded p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100"
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Nome completo</label>
            <input
              type="text"
              required
              value={form.nome}
              onChange={e => setForm(f => ({ ...f, nome: e.target.value }))}
              placeholder="Ex: João da Silva"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">E-mail</label>
            <input
              type="email"
              required
              value={form.email}
              onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
              placeholder="usuario@empresa.com.br"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Perfil de acesso</label>
            <select
              value={form.perfil}
              onChange={e => setForm(f => ({ ...f, perfil: e.target.value }))}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            >
              {PERFIS.map(p => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </select>
          </div>

          {error && (
            <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>
          )}

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-600 disabled:opacity-60"
            >
              {loading ? 'Enviando…' : 'Enviar convite'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
