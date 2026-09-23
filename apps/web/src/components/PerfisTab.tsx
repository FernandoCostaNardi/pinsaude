import { useEffect, useMemo, useState } from 'react'
import { Plus, Shield, Pencil, Trash2, Users } from 'lucide-react'
import { Button, Spinner, Alert, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { perfisApi, type PerfilCustomizado } from '../api/perfisApi'
import { usersApi, type Usuario } from '../api/usersApi'
import { PerfilFormModal } from './PerfilFormModal'

export function PerfisTab() {
  const [perfis, setPerfis] = useState<PerfilCustomizado[]>([])
  const [usuarios, setUsuarios] = useState<Usuario[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [loadingId, setLoadingId] = useState<string | null>(null)
  const [modal, setModal] = useState<'novo' | PerfilCustomizado | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const [perfisData, usuariosData] = await Promise.all([perfisApi.listar(), usersApi.listar()])
      setPerfis(perfisData)
      setUsuarios(usuariosData)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar perfis')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  // Quantidade de colaboradores por perfil — calculada no cliente cruzando com a lista já
  // carregada de usuários, sem chamada extra ao backend.
  const colaboradoresPorPerfil = useMemo(() => {
    const mapa = new Map<string, number>()
    for (const u of usuarios) {
      mapa.set(u.perfil, (mapa.get(u.perfil) ?? 0) + 1)
    }
    return mapa
  }, [usuarios])

  function handleSaved(perfil: PerfilCustomizado) {
    setPerfis(prev => {
      const existe = prev.some(p => p.id === perfil.id)
      return existe ? prev.map(p => (p.id === perfil.id ? perfil : p)) : [perfil, ...prev]
    })
    setModal(null)
  }

  async function handleExcluir(perfil: PerfilCustomizado) {
    if (!window.confirm(`Excluir o perfil "${perfil.nome}"? Essa ação não pode ser desfeita.`)) return
    setActionError(null)
    setLoadingId(perfil.id)
    try {
      await perfisApi.excluir(perfil.id)
      setPerfis(prev => prev.filter(p => p.id !== perfil.id))
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Erro ao excluir perfil')
    } finally {
      setLoadingId(null)
    }
  }

  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
      {/* Card header */}
      <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
        <div className="flex-1 min-w-0">
          <p className="text-base font-bold text-ds-text">Perfis de Acesso Customizados</p>
          <p className="text-xs text-ds-light">Combine permissões de tela para criar perfis próprios da organização</p>
        </div>
        <Button size="sm" onClick={() => setModal('novo')}>
          <Plus size={14} /> Novo Perfil
        </Button>
      </div>

      {actionError && (
        <div className="px-5 pt-3">
          <Alert variant="error" onClose={() => setActionError(null)}>{actionError}</Alert>
        </div>
      )}

      {loading ? (
        <div className="flex justify-center items-center py-20">
          <Spinner size="lg" />
        </div>
      ) : error ? (
        <div className="p-5">
          <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>
        </div>
      ) : perfis.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
            <Shield size={28} className="text-primary-200" />
          </div>
          <p className="text-sm font-semibold text-ds-mid">Nenhum perfil customizado cadastrado</p>
          <p className="text-xs text-ds-light mt-1">Crie o primeiro perfil combinando as permissões desejadas</p>
          <Button size="sm" className="mt-5" onClick={() => setModal('novo')}>
            <Plus size={14} /> Criar primeiro perfil
          </Button>
        </div>
      ) : (
        <>
          {/* Mobile: cards */}
          <div className="flex flex-col gap-3 p-4 sm:hidden">
            {perfis.map(p => {
              const emUso = colaboradoresPorPerfil.get(p.keycloakRoleName) ?? 0
              return (
                <div key={p.id} className="rounded-xl border border-ds-border bg-white p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ds-text truncate">{p.nome}</p>
                      <p className="mt-0.5 text-xs text-ds-light">{p.permissoes.length} permissão{p.permissoes.length !== 1 ? 'ões' : ''}</p>
                    </div>
                    <span className="flex items-center gap-1 text-xs text-ds-mid shrink-0">
                      <Users size={13} /> {emUso}
                    </span>
                  </div>
                  <div className="mt-3 flex items-center gap-2 pt-3 border-t border-ds-border">
                    <button
                      onClick={() => setModal(p)}
                      className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-ds-mid hover:bg-primary-50 hover:text-primary transition-colors"
                    >
                      <Pencil size={13} /> Editar
                    </button>
                    <button
                      disabled={emUso > 0 || loadingId === p.id}
                      title={emUso > 0 ? `Não é possível excluir um perfil em uso por ${emUso} colaborador(es)` : undefined}
                      onClick={() => handleExcluir(p)}
                      className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-ds-mid hover:bg-red-50 hover:text-red-600 disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-ds-mid transition-colors"
                    >
                      <Trash2 size={13} /> Excluir
                    </button>
                  </div>
                </div>
              )
            })}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table className="!border-0 !rounded-none">
              <THead>
                <TRow>
                  <TH>Perfil</TH>
                  <TH>Permissões</TH>
                  <TH>Colaboradores</TH>
                  <TH className="text-right">Ações</TH>
                </TRow>
              </THead>
              <TBody>
                {perfis.map(p => {
                  const emUso = colaboradoresPorPerfil.get(p.keycloakRoleName) ?? 0
                  return (
                    <TRow key={p.id}>
                      <TD>
                        <p className="font-semibold text-ds-text">{p.nome}</p>
                      </TD>
                      <TD>
                        <span className="text-ds-mid">{p.permissoes.length} permissão{p.permissoes.length !== 1 ? 'ões' : ''}</span>
                      </TD>
                      <TD>
                        <span className="flex items-center gap-1.5 text-ds-mid">
                          <Users size={14} /> {emUso}
                        </span>
                      </TD>
                      <TD className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            title="Editar perfil"
                            onClick={() => setModal(p)}
                            className="p-1.5 rounded hover:bg-primary-50 text-ds-light hover:text-primary transition-colors"
                          >
                            <Pencil size={15} />
                          </button>
                          <button
                            title={emUso > 0 ? `Não é possível excluir um perfil em uso por ${emUso} colaborador(es)` : 'Excluir perfil'}
                            disabled={emUso > 0 || loadingId === p.id}
                            onClick={() => handleExcluir(p)}
                            className="p-1.5 rounded hover:bg-red-50 text-ds-light hover:text-red-600 disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-ds-light transition-colors"
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </TD>
                    </TRow>
                  )
                })}
              </TBody>
            </Table>
          </div>
        </>
      )}

      {modal && (
        <PerfilFormModal
          perfil={modal === 'novo' ? null : modal}
          onClose={() => setModal(null)}
          onSaved={handleSaved}
        />
      )}
    </div>
  )
}
