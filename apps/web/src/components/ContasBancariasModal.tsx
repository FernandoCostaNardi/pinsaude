import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Star } from 'lucide-react'
import { Modal, Button, Spinner, Alert, Badge, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { ContaBancaria, contasBancariasApi } from '../api/contasBancariasApi'
import { ContaBancariaWizardModal } from './ContaBancariaWizardModal'
import { ContaBancariaDeleteModal } from './ContaBancariaDeleteModal'
import { BancoAvatar, bancos } from './BancoSelect'

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
      <Modal open onClose={onClose} title="Contas Bancárias" size="2xl">
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
            <>
              {/* Mobile: cards */}
              <div className="flex flex-col gap-3 sm:hidden">
                {contas.map(c => {
                  const bancoData = bancos.find(b => b.nome === c.banco)
                  const bd = bancoData ?? { compe: '0', nome: c.banco, domain: '' }
                  return (
                    <div key={c.id} className="rounded-xl border border-gray-100 bg-gray-50 p-4">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-3 min-w-0">
                          <BancoAvatar banco={bd} size={36} />
                          <div className="min-w-0">
                            <p className="text-sm font-semibold text-gray-900 truncate">{c.banco}</p>
                            <p className="text-xs text-gray-400">
                              {bancoData ? `${bancoData.compe} · ` : ''}
                              {c.tipoConta === 'CORRENTE' ? 'Corrente' : 'Poupança'}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-1 shrink-0">
                          {c.principal && (
                            <Badge variant="success">
                              <Star size={10} className="inline mr-0.5" />Principal
                            </Badge>
                          )}
                          {isGestao && (
                            <>
                              <button
                                onClick={() => openEdit(c)}
                                className="p-1.5 rounded hover:bg-gray-200 text-gray-500 hover:text-primary transition-colors"
                                title="Editar"
                              >
                                <Pencil size={13} />
                              </button>
                              <button
                                onClick={() => setDeleting(c)}
                                className="p-1.5 rounded hover:bg-red-50 text-gray-500 hover:text-red-600 transition-colors"
                                title="Excluir"
                              >
                                <Trash2 size={13} />
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                      <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                        <div>
                          <p className="text-xs text-gray-400">Agência</p>
                          <p className="font-medium text-gray-700">{c.agencia}</p>
                        </div>
                        <div>
                          <p className="text-xs text-gray-400">Conta</p>
                          <p className="font-mono font-medium text-gray-700">{c.conta}</p>
                        </div>
                        {c.chavePix && (
                          <div className="col-span-2">
                            <p className="text-xs text-gray-400">Chave PIX</p>
                            <p className="text-xs text-gray-600 break-all">{c.chavePix}</p>
                          </div>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>

              {/* Desktop: table */}
              <div className="hidden sm:block">
                <Table>
                  <THead>
                    <TRow>
                      <TH className="w-14"></TH>
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
                    {contas.map(c => {
                      const bancoData = bancos.find(b => b.nome === c.banco)
                      return (
                        <TRow key={c.id}>
                          <TD>
                            <div className="flex flex-col items-center gap-0.5">
                              <BancoAvatar banco={bancoData ?? { compe: '0', nome: c.banco, domain: '' }} size={28} />
                              {bancoData && (
                                <span className="font-mono text-[10px] text-gray-400">{bancoData.compe}</span>
                              )}
                            </div>
                          </TD>
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
                      )
                    })}
                  </TBody>
                </Table>
              </div>
            </>
          )}

          <div className="flex justify-end pt-2 border-t">
            <Button variant="outline" onClick={onClose}>Fechar</Button>
          </div>
        </div>
      </Modal>

      {showForm && (
        <ContaBancariaWizardModal
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
