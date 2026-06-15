import { useState } from 'react'
import { Modal, Button, Input, Alert } from '@pinsaude/ui'
import { usersApi, type ConvitePayload, type Usuario } from '../api/usersApi'

const PERFIS = [
  { value: 'medico',     label: 'Médico'      },
  { value: 'operacao',   label: 'Operação'    },
  { value: 'financeiro', label: 'Financeiro'  },
  { value: 'contabil',   label: 'Contábil'    },
  { value: 'gestao',     label: 'Gestão'      },
]

interface Props {
  onClose: () => void
  onSaved: (usuario: Usuario) => void
}

export function InviteUserModal({ onClose, onSaved }: Props) {
  const [form, setForm]     = useState<ConvitePayload>({ email: '', nome: '', perfil: 'operacao' })
  const [loading, setLoading] = useState(false)
  const [error, setError]   = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      onSaved(await usersApi.convidar(form))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao enviar convite')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Convidar Usuário" size="sm">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input
          label="Nome completo"
          type="text"
          required
          value={form.nome}
          onChange={e => setForm(f => ({ ...f, nome: e.target.value }))}
          placeholder="Ex: João da Silva"
        />

        <Input
          label="E-mail"
          type="email"
          required
          value={form.email}
          onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
          placeholder="usuario@empresa.com.br"
        />

        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700">Perfil de acesso</label>
          <select
            value={form.perfil}
            onChange={e => setForm(f => ({ ...f, perfil: e.target.value }))}
            className="block w-full rounded-lg border border-ds-border px-3 py-2.5 text-sm text-ds-text bg-white focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
          >
            {PERFIS.map(p => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        </div>

        {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

        <div className="flex gap-3 pt-2 border-t border-ds-border">
          <Button type="button" variant="outline" onClick={onClose} className="flex-1">
            Cancelar
          </Button>
          <Button type="submit" loading={loading} className="flex-1">
            Enviar convite
          </Button>
        </div>
      </form>
    </Modal>
  )
}
