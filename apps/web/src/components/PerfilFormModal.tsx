import { useState } from 'react'
import { Modal, Button, Input, Alert } from '@pinsaude/ui'
import { perfisApi, type PerfilCustomizado, type PerfilCustomizadoPayload } from '../api/perfisApi'
import { permCatalog, type Area } from '../config/menuCatalog'

const AREAS: Area[] = ['Cadastros', 'Faturamento', 'Fiscal', 'Financeiro', 'Gestão']

interface Props {
  perfil: PerfilCustomizado | null
  onClose: () => void
  onSaved: (perfil: PerfilCustomizado) => void
}

export function PerfilFormModal({ perfil, onClose, onSaved }: Props) {
  const editando = perfil !== null
  const [nome, setNome] = useState(perfil?.nome ?? '')
  const [permissoes, setPermissoes] = useState<Set<string>>(new Set(perfil?.permissoes ?? []))
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function toggle(perm: string) {
    setPermissoes(prev => {
      const next = new Set(prev)
      if (next.has(perm)) next.delete(perm)
      else next.add(perm)
      return next
    })
  }

  function toggleArea(area: Area, marcar: boolean) {
    setPermissoes(prev => {
      const next = new Set(prev)
      for (const entry of permCatalog) {
        if (entry.area !== area) continue
        if (marcar) next.add(entry.perm)
        else next.delete(entry.perm)
      }
      return next
    })
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (!nome.trim()) {
      setError('Nome é obrigatório')
      return
    }
    if (permissoes.size === 0) {
      setError('Selecione ao menos uma permissão')
      return
    }
    const payload: PerfilCustomizadoPayload = { nome: nome.trim(), permissoes: Array.from(permissoes) }
    setLoading(true)
    try {
      const salvo = editando
        ? await perfisApi.atualizar(perfil.id, payload)
        : await perfisApi.criar(payload)
      onSaved(salvo)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao salvar perfil')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={editando ? 'Editar Perfil' : 'Novo Perfil'} size="lg">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input
          label="Nome do perfil"
          type="text"
          required
          value={nome}
          onChange={e => setNome(e.target.value)}
          placeholder="Ex: Operador Financeiro"
        />

        <div className="flex flex-col gap-1">
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium text-gray-700">Permissões de acesso</label>
            <span className="text-xs text-ds-light">{permissoes.size} selecionada{permissoes.size !== 1 ? 's' : ''}</span>
          </div>

          <div className="flex flex-col gap-3 max-h-[50vh] overflow-y-auto pr-1 border border-ds-border rounded-lg p-3">
            {AREAS.map(area => {
              const entries = permCatalog.filter(p => p.area === area)
              if (entries.length === 0) return null
              const todasMarcadas = entries.every(e => permissoes.has(e.perm))
              return (
                <div key={area} className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <p className="text-xs font-bold text-ds-mid uppercase tracking-wide">{area}</p>
                    <button
                      type="button"
                      onClick={() => toggleArea(area, !todasMarcadas)}
                      className="text-[11px] font-medium text-primary hover:underline"
                    >
                      {todasMarcadas ? 'Desmarcar todos' : 'Marcar todos'}
                    </button>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                    {entries.map(entry => (
                      <label key={entry.perm} className="flex items-center gap-2 text-sm text-ds-text cursor-pointer">
                        <input
                          type="checkbox"
                          checked={permissoes.has(entry.perm)}
                          onChange={() => toggle(entry.perm)}
                          className="rounded border-ds-border text-primary focus:ring-primary-100"
                        />
                        {entry.label}
                      </label>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

        <div className="flex gap-3 pt-2 border-t border-ds-border">
          <Button type="button" variant="outline" onClick={onClose} className="flex-1">
            Cancelar
          </Button>
          <Button type="submit" loading={loading} className="flex-1">
            {editando ? 'Salvar alterações' : 'Criar perfil'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
