import { useCallback, useEffect, useState } from 'react'
import { Stethoscope, XCircle, ChevronDown, ChevronUp, Loader2 } from 'lucide-react'
import { Modal, Button, Alert, Spinner } from '@pinsaude/ui'
import { Tomador, MedicoTomador, TomadorServicoOperacional, tomadoresApi } from '../api/tomadoresApi'
import { Medico, medicosApi } from '../api/medicosApi'

interface Props {
  tomador: Tomador
  canWrite: boolean
  onClose: () => void
}

const SEM_CATEGORIA = 'Sem categoria'

// Duplicado de TomadorGruposModal.tsx (mesmo padrão do projeto — pequenos helpers de exibição
// não são extraídos para um módulo compartilhado só por aparecerem em duas telas).
function agruparPorCategoria(setores: TomadorServicoOperacional[]): [string, TomadorServicoOperacional[]][] {
  const porCategoria = new Map<string, TomadorServicoOperacional[]>()
  for (const s of setores) {
    const chave = s.categoria?.trim() || SEM_CATEGORIA
    if (!porCategoria.has(chave)) porCategoria.set(chave, [])
    porCategoria.get(chave)!.push(s)
  }
  for (const lista of porCategoria.values()) lista.sort((a, b) => a.nome.localeCompare(b.nome))
  return Array.from(porCategoria.entries()).sort(([a], [b]) => {
    if (a === SEM_CATEGORIA) return 1
    if (b === SEM_CATEGORIA) return -1
    return a.localeCompare(b)
  })
}

// Switch estilo pílula (padrão do resto do app, duplicado de TomadorGruposModal.tsx).
function Switch({
  checked, onChange, disabled,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={[
        'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors',
        'focus:outline-none focus:ring-2 focus:ring-primary-300 focus:ring-offset-1',
        disabled ? 'opacity-50 cursor-not-allowed' : '',
        checked ? 'bg-primary' : 'bg-gray-300',
      ].join(' ')}
    >
      <span
        className={[
          'inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform',
          checked ? 'translate-x-[18px]' : 'translate-x-0.5',
        ].join(' ')}
      />
    </button>
  )
}

export function TomadorMedicosModal({ tomador, canWrite, onClose }: Props) {
  const exigeFrequencia = tomador.exigeFrequencia

  const [alocados,     setAlocados]     = useState<MedicoTomador[]>([])
  const [todosMedicos, setTodosMedicos] = useState<Medico[]>([])
  const [todosSetores, setTodosSetores] = useState<TomadorServicoOperacional[]>([])
  const [setoresPorMedico, setSetoresPorMedico] = useState<Record<string, TomadorServicoOperacional[]>>({})
  const [loading,      setLoading]      = useState(true)
  const [error,        setError]        = useState<string | null>(null)

  const [addMedicoId,     setAddMedicoId]     = useState('')
  const [adding,           setAdding]           = useState(false)
  const [removingMedicoId, setRemovingMedicoId] = useState<string | null>(null)
  const [expandidos,       setExpandidos]       = useState<Set<string>>(new Set())
  const [setorTogglingKey, setSetorTogglingKey] = useState<string | null>(null)

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

      if (exigeFrequencia) {
        const [setores, setoresPorMedicoList] = await Promise.all([
          tomadoresApi.listarServicosOperacionais(tomador.id).catch(() => [] as TomadorServicoOperacional[]),
          Promise.all(medicosDoTomador.map(a =>
            tomadoresApi.listarSetoresDoMedico(tomador.id, a.medicoId).catch(() => [] as TomadorServicoOperacional[]),
          )),
        ])
        setTodosSetores(setores)
        const map: Record<string, TomadorServicoOperacional[]> = {}
        medicosDoTomador.forEach((a, i) => { map[a.medicoId] = setoresPorMedicoList[i] })
        setSetoresPorMedico(map)
      } else {
        setTodosSetores([])
        setSetoresPorMedico({})
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar médicos alocados')
    } finally {
      setLoading(false)
    }
  }, [tomador.id, exigeFrequencia])

  useEffect(() => { carregar() }, [carregar])

  const medicoPorId = new Map(todosMedicos.map(m => [m.id, m]))

  async function handleAdicionar() {
    if (!addMedicoId) return
    setAdding(true)
    setError(null)
    try {
      const novo = await tomadoresApi.adicionarMedico(tomador.id, addMedicoId)
      setAlocados(prev => [...prev, novo])
      setSetoresPorMedico(prev => ({ ...prev, [novo.medicoId]: [] }))
      if (exigeFrequencia) {
        setExpandidos(prev => new Set(prev).add(novo.medicoId))
      }
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
      setSetoresPorMedico(prev => {
        const resto = { ...prev }
        delete resto[medicoId]
        return resto
      })
      setExpandidos(prev => {
        const next = new Set(prev)
        next.delete(medicoId)
        return next
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao remover médico')
    } finally {
      setRemovingMedicoId(null)
    }
  }

  function toggleExpandido(medicoId: string) {
    setExpandidos(prev => {
      const next = new Set(prev)
      if (next.has(medicoId)) next.delete(medicoId)
      else next.add(medicoId)
      return next
    })
  }

  async function toggleSetorDoMedico(medicoId: string, setorId: string, marcar: boolean) {
    const key = `${medicoId}:${setorId}`
    setSetorTogglingKey(key)
    setError(null)
    try {
      if (marcar) {
        await tomadoresApi.adicionarSetorAoMedico(tomador.id, medicoId, setorId)
      } else {
        await tomadoresApi.removerSetorDoMedico(tomador.id, medicoId, setorId)
      }
      setSetoresPorMedico(prev => {
        const atual = prev[medicoId] ?? []
        if (marcar) {
          const setor = todosSetores.find(s => s.id === setorId)
          return { ...prev, [medicoId]: setor ? [...atual, setor] : atual }
        }
        return { ...prev, [medicoId]: atual.filter(s => s.id !== setorId) }
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao atualizar setor do médico')
    } finally {
      setSetorTogglingKey(null)
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

          {exigeFrequencia && (
            <p className="text-xs text-ds-light bg-primary-50 border border-primary/20 rounded-lg px-3 py-2">
              Este tomador exige controle de frequência — atribua abaixo os setores operacionais que cada médico exerce aqui.
              Somente esses setores aparecerão para o médico no Portal ao criar uma nova competência.
            </p>
          )}

          <div className="flex flex-col gap-2">
            {alocados.map(a => {
              const medico = medicoPorId.get(a.medicoId)
              const setoresAtribuidos = setoresPorMedico[a.medicoId] ?? []
              const expandido = expandidos.has(a.medicoId)
              return (
                <div key={a.medicoId}
                  className="rounded-lg bg-ds-input border border-ds-border overflow-hidden">
                  <div className="flex items-center justify-between px-3 py-2.5">
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
                    <div className="flex items-center gap-1 shrink-0">
                      {exigeFrequencia && (
                        <button
                          type="button"
                          onClick={() => toggleExpandido(a.medicoId)}
                          className="flex items-center gap-1 px-2 py-1 rounded text-xs font-medium text-ds-mid hover:text-primary hover:bg-white transition-colors"
                          title="Setores operacionais"
                        >
                          {setoresAtribuidos.length} setor{setoresAtribuidos.length !== 1 ? 'es' : ''}
                          {expandido ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
                        </button>
                      )}
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
                  </div>

                  {exigeFrequencia && expandido && (
                    <div className="border-t border-ds-border bg-white px-3 py-2.5">
                      {todosSetores.length === 0 ? (
                        <p className="text-xs text-ds-light">
                          Nenhum setor operacional cadastrado neste tomador ainda.
                        </p>
                      ) : !canWrite ? (
                        setoresAtribuidos.length === 0 ? (
                          <p className="text-xs text-ds-light">Nenhum setor atribuído a este médico.</p>
                        ) : (
                          <div className="flex flex-wrap gap-1.5">
                            {setoresAtribuidos.map(s => (
                              <span key={s.id} className="px-2 py-0.5 rounded-full text-[11px] font-medium bg-primary-50 text-primary">
                                {s.nome}
                              </span>
                            ))}
                          </div>
                        )
                      ) : (
                        <div className="space-y-2">
                          {agruparPorCategoria(todosSetores).map(([categoria, setoresCategoria]) => (
                            <div key={categoria}>
                              <p className="text-[10px] font-bold uppercase tracking-wide text-ds-light mb-1">{categoria}</p>
                              <div className="space-y-1">
                                {setoresCategoria.map(s => {
                                  const vinculado = setoresAtribuidos.some(x => x.id === s.id)
                                  const key = `${a.medicoId}:${s.id}`
                                  return (
                                    <div key={s.id} className="flex items-center gap-2">
                                      <Switch
                                        checked={vinculado}
                                        disabled={setorTogglingKey === key}
                                        onChange={marcar => toggleSetorDoMedico(a.medicoId, s.id, marcar)}
                                      />
                                      <span className="text-xs text-ds-text">{s.nome}</span>
                                      {!s.ativo && (
                                        <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-gray-100 text-gray-500">
                                          INATIVO
                                        </span>
                                      )}
                                      {setorTogglingKey === key && <Loader2 size={11} className="animate-spin text-ds-light" />}
                                    </div>
                                  )
                                })}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
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
