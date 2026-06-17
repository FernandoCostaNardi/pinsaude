import { useEffect, useMemo, useState } from 'react'
import {
  Plus, Search, Pencil, UserX, Stethoscope,
  UserCheck, UserCircle, CircleDot, CheckCircle2, FolderOpen,
} from 'lucide-react'
import { Button, Spinner, Alert, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { Medico, StatusMedico, medicosApi } from '../api/medicosApi'
import { MedicoWizardModal, maskPixKey } from '../components/MedicoWizardModal'
import { BancoAvatar, bancos } from '../components/BancoSelect'
import { MedicoInativarModal } from '../components/MedicoInativarModal'
import { DocumentosModal } from '../components/DocumentosModal'
import { formatCpf } from '../utils/cpf'
import { useAuth } from '../auth/useAuth'

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_BADGE: Record<StatusMedico, string> = {
  RASCUNHO: 'bg-amber-50 text-amber-700',
  ATIVO:    'bg-green-50 text-green-700',
  INATIVO:  'bg-gray-100 text-gray-500',
  SUSPENSO: 'bg-red-50 text-red-600',
}

const STATUS_LABELS: Record<StatusMedico, string> = {
  RASCUNHO: 'Rascunho',
  ATIVO:    'Ativo',
  INATIVO:  'Inativo',
  SUSPENSO: 'Suspenso',
}

function StatusBadge({ status }: { status: StatusMedico }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold ${STATUS_BADGE[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  )
}

// ─── Avatar ───────────────────────────────────────────────────────────────────

function MedicoAvatar({ nome, size = 'sm' }: { nome: string; size?: 'sm' | 'md' }) {
  const initials = nome.trim().split(/\s+/).filter(w => w.length > 1).slice(0, 2)
    .map(w => w[0].toUpperCase()).join('') || nome.slice(0, 2).toUpperCase()
  return (
    <div className={[
      'rounded-lg bg-primary-50 flex items-center justify-center shrink-0',
      size === 'md' ? 'w-10 h-10' : 'w-8 h-8',
    ].join(' ')}>
      <span className={['font-black text-primary', size === 'md' ? 'text-sm' : 'text-xs'].join(' ')}>
        {initials}
      </span>
    </div>
  )
}

// ─── Stat card ────────────────────────────────────────────────────────────────

function StatCard({
  icon: Icon, label, value, sub, iconBg, iconColor,
}: {
  icon: React.ElementType; label: string; value: number
  sub: string; iconBg: string; iconColor: string
}) {
  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm p-4 flex items-center gap-4">
      <div className={`w-11 h-11 rounded-xl ${iconBg} flex items-center justify-center shrink-0`}>
        <Icon size={20} className={iconColor} />
      </div>
      <div>
        <p className="text-2xl font-black text-ds-text leading-none">{value}</p>
        <p className="text-xs font-semibold text-ds-mid mt-0.5">{label}</p>
        <p className="text-[11px] text-ds-light">{sub}</p>
      </div>
    </div>
  )
}

// ─── Dados bancários display ──────────────────────────────────────────────────

function DadosBancariosDisplay({ medico }: { medico: Medico }) {
  const db = medico.dadosBancarios
  if (!db) return <span className="text-ds-light text-xs">—</span>

  if (db.tipoRecebimento === 'TED' && db.bancoCodigo) {
    const bancoData = bancos.find(b => b.compe === db.bancoCodigo) ?? null
    return (
      <div className="flex flex-col gap-0.5">
        <span className="text-xs text-ds-light">TED · {db.tipoConta === 'POUPANCA' ? 'Poupança' : 'Corrente'}</span>
        <div className="flex items-center gap-1.5">
          {bancoData && <BancoAvatar banco={bancoData} size={16} />}
          <span className="text-xs font-mono text-ds-text">
            {db.bancoNome || `Banco ${db.bancoCodigo}`} · Ag. {db.agencia} · Cc. {db.conta}
          </span>
        </div>
      </div>
    )
  }

  if (!db.tipoPix || !db.chavePix) return <span className="text-ds-light text-xs">—</span>
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-ds-light">PIX · {db.tipoPix}</span>
      <span className="text-xs font-mono text-ds-text">{maskPixKey(db.tipoPix, db.chavePix)}</span>
    </div>
  )
}

const PAGE_SIZES = [10, 25, 50]

// ─── Page ────────────────────────────────────────────────────────────────────

export function MedicosPage() {
  const { user } = useAuth()
  const isGestao   = user?.realm_access?.roles.includes('gestao') ?? false
  const isOperacao = user?.realm_access?.roles.includes('operacao') ?? false
  const canEdit    = isGestao || isOperacao

  const [medicos, setMedicos] = useState<Medico[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)

  const [search, setSearch]               = useState('')
  const [debouncedSearch, setDebounced]   = useState('')
  const [filterStatus, setFilterStatus]   = useState<StatusMedico | ''>('')

  const [pageSize, setPageSize]       = useState(10)
  const [currentPage, setCurrentPage] = useState(0)

  const [showForm, setShowForm]           = useState(false)
  const [editing, setEditing]             = useState<Medico | null>(null)
  const [loadingEdit, setLoadingEdit]     = useState<string | null>(null)
  const [inativando, setInativando]       = useState<Medico | null>(null)
  const [activatingId, setActivatingId]   = useState<string | null>(null)
  const [viewingDocs, setViewingDocs]     = useState<Medico | null>(null)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(search), 300)
    return () => clearTimeout(t)
  }, [search])

  useEffect(() => { setCurrentPage(0) }, [debouncedSearch, filterStatus, pageSize])

  async function load(status?: string) {
    setLoading(true)
    setError(null)
    try {
      const page = await medicosApi.listar(0, 1000, status)
      setMedicos(page.content)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar médicos')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(filterStatus === 'INATIVO' ? 'INATIVO' : undefined)
  }, [filterStatus])

  const filtered = useMemo(() => {
    const q = debouncedSearch.toLowerCase()
    const qDigits = q.replace(/\D/g, '')
    return medicos.filter(m => {
      const matchSearch = !q
        || m.nome.toLowerCase().includes(q)
        || m.crm.toLowerCase().includes(q)
        || (m.especialidade?.toLowerCase().includes(q) ?? false)
        || (qDigits.length > 0 && (m.cpf ?? '').replace(/\D/g, '').includes(qDigits))
      const matchStatus = !filterStatus || m.status === filterStatus
      return matchSearch && matchStatus
    })
  }, [medicos, debouncedSearch, filterStatus])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage   = Math.min(currentPage, totalPages - 1)
  const paginated  = filtered.slice(safePage * pageSize, (safePage + 1) * pageSize)
  const from       = filtered.length === 0 ? 0 : safePage * pageSize + 1
  const to         = Math.min((safePage + 1) * pageSize, filtered.length)

  async function handleEditar(medico: Medico) {
    setLoadingEdit(medico.id)
    try {
      const full = await medicosApi.buscarPorId(medico.id)
      setEditing(full)
      setShowForm(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar médico')
    } finally {
      setLoadingEdit(null)
    }
  }

  function handleSaved(saved: Medico) {
    setMedicos(prev => {
      const idx = prev.findIndex(m => m.id === saved.id)
      return idx >= 0 ? prev.map(m => m.id === saved.id ? saved : m) : [...prev, saved]
    })
    setShowForm(false)
    setEditing(null)
  }

  function handleInativado(updated: Medico) {
    setMedicos(prev => prev.map(m => m.id === updated.id ? updated : m))
    setInativando(null)
  }

  async function handleAtivar(medico: Medico) {
    setActivatingId(medico.id)
    try {
      const updated = await medicosApi.ativar(medico.id)
      setMedicos(prev => prev.map(m => m.id === updated.id ? updated : m))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao ativar médico')
    } finally {
      setActivatingId(null)
    }
  }

  const stats = useMemo(() => ({
    total:    medicos.length,
    ativos:   medicos.filter(m => m.status === 'ATIVO').length,
    rascunho: medicos.filter(m => m.status === 'RASCUNHO').length,
    suspensos: medicos.filter(m => m.status === 'SUSPENSO').length,
  }), [medicos])

  const hasFilters = !!(search || filterStatus)

  return (
    <div className="flex flex-col gap-5">

      {/* Stats */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard icon={Stethoscope}  label="Total de Médicos" value={stats.total}    sub="Cadastrados no sistema" iconBg="bg-primary-50"  iconColor="text-primary" />
        <StatCard icon={CheckCircle2} label="Médicos Ativos"   value={stats.ativos}   sub="Com checklist aprovado"  iconBg="bg-green-50"   iconColor="text-green-600" />
        <StatCard icon={CircleDot}    label="Em Rascunho"       value={stats.rascunho} sub="Aguardando ativação"     iconBg="bg-amber-50"   iconColor="text-amber-600" />
        <StatCard icon={UserCircle}   label="Suspensos"         value={stats.suspensos} sub="Temporariamente inativos" iconBg="bg-red-50"    iconColor="text-red-500" />
      </div>

      {/* Main card */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">

        {/* Header */}
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-ds-text">Médicos Cadastrados</p>
            <p className="text-xs text-ds-light">Cadastro, vínculos e dados bancários para repasse</p>
          </div>
          {canEdit && (
            <Button size="sm" onClick={() => { setEditing(null); setShowForm(true) }}>
              <Plus size={14} /> Novo Médico
            </Button>
          )}
        </div>

        {/* Filters */}
        <div className="flex flex-wrap gap-3 items-center px-5 py-3 border-b border-ds-border bg-ds-input">
          <div className="relative flex-1 min-w-[180px] max-w-xs">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light pointer-events-none" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Nome, CRM, especialidade ou CPF..."
              className="block w-full pl-9 pr-3 py-1.5 text-sm border border-ds-border rounded-lg bg-white text-ds-text placeholder-ds-light focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            />
          </div>
          <select
            value={filterStatus}
            onChange={e => setFilterStatus(e.target.value as StatusMedico | '')}
            className="py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
          >
            <option value="">Ativos / Rascunho</option>
            <option value="RASCUNHO">Rascunho</option>
            <option value="ATIVO">Ativo</option>
            <option value="SUSPENSO">Suspenso</option>
            <option value="INATIVO">Inativos</option>
          </select>
          {hasFilters && (
            <button
              onClick={() => { setSearch(''); setFilterStatus('') }}
              className="px-3 py-1.5 text-xs font-medium text-ds-mid border border-ds-border rounded-lg bg-white hover:bg-ds-hover transition-colors"
            >
              Limpar filtros
            </button>
          )}
        </div>

        {/* Content */}
        {loading ? (
          <div className="flex justify-center items-center py-20"><Spinner size="lg" /></div>
        ) : error ? (
          <div className="p-5"><Alert variant="error" onClose={() => setError(null)}>{error}</Alert></div>
        ) : medicos.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
              <Stethoscope size={28} className="text-primary-200" />
            </div>
            <p className="text-sm font-semibold text-ds-mid">Nenhum médico cadastrado</p>
            <p className="text-xs text-ds-light mt-1">Comece cadastrando o primeiro médico</p>
            {canEdit && (
              <Button size="sm" className="mt-5" onClick={() => { setEditing(null); setShowForm(true) }}>
                <Plus size={14} /> Cadastrar primeiro médico
              </Button>
            )}
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <p className="text-sm font-medium text-ds-mid">Nenhum médico corresponde aos filtros</p>
            <button onClick={() => { setSearch(''); setFilterStatus('') }} className="mt-2 text-xs text-primary hover:underline">
              Limpar filtros
            </button>
          </div>
        ) : (
          <>
            {/* Mobile cards */}
            <div className="flex flex-col gap-3 p-4 sm:hidden">
              {paginated.map(m => (
                <div key={m.id} className="rounded-xl border border-ds-border bg-white p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-start gap-3 min-w-0">
                      <MedicoAvatar nome={m.nome} size="md" />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ds-text truncate">{m.nome}</p>
                        <p className="mt-0.5 font-mono text-xs text-ds-light">CPF {m.cpf ? formatCpf(m.cpf).slice(0, 7) + '***.***-**' : '—'}</p>
                      </div>
                    </div>
                    <StatusBadge status={m.status} />
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                    <div>
                      <p className="text-xs font-medium text-ds-light">CRM</p>
                      <p className="text-ds-text font-medium">{m.crm}/{m.crmUf.trim()}</p>
                    </div>
                    <div>
                      <p className="text-xs font-medium text-ds-light">Especialidade</p>
                      <p className="text-ds-text font-medium truncate">{m.especialidade || '—'}</p>
                    </div>
                    <div className="col-span-2">
                      <p className="text-xs font-medium text-ds-light mb-0.5">Recebimento</p>
                      <DadosBancariosDisplay medico={m} />
                    </div>
                  </div>
                  <div className="mt-3 flex items-center gap-1 border-t border-ds-border pt-3">
                    {canEdit && (m.status === 'RASCUNHO' || m.status === 'INATIVO') && (
                      <button
                        onClick={() => handleAtivar(m)}
                        disabled={activatingId === m.id}
                        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-ds-mid hover:bg-green-50 hover:text-green-600 transition-colors disabled:opacity-50"
                      >
                        <UserCheck size={13} /> {m.status === 'INATIVO' ? 'Reativar' : 'Ativar'}
                      </button>
                    )}
                    <button
                      onClick={() => setViewingDocs(m)}
                      className="p-1.5 rounded-lg text-ds-light hover:bg-primary-50 hover:text-primary transition-colors"
                      title="Documentos"
                    >
                      <FolderOpen size={14} />
                    </button>
                    {canEdit && (
                      <button
                        onClick={() => handleEditar(m)}
                        disabled={loadingEdit === m.id}
                        className="p-1.5 rounded-lg text-ds-light hover:bg-ds-input hover:text-primary transition-colors disabled:opacity-50"
                        title="Editar"
                      >
                        <Pencil size={14} />
                      </button>
                    )}
                    {isGestao && m.status !== 'INATIVO' && (
                      <button
                        onClick={() => setInativando(m)}
                        className="p-1.5 rounded-lg text-ds-light hover:bg-red-50 hover:text-red-600 transition-colors"
                        title="Inativar"
                      >
                        <UserX size={14} />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {/* Desktop table */}
            <div className="hidden sm:block">
              <Table className="!border-0 !rounded-none">
                <THead>
                  <TRow>
                    <TH>Nome</TH>
                    <TH>CPF</TH>
                    <TH>CRM / UF</TH>
                    <TH>Especialidade</TH>
                    <TH>Recebimento</TH>
                    <TH>Status</TH>
                    <TH className="text-right">Ações</TH>
                  </TRow>
                </THead>
                <TBody>
                  {paginated.map(m => (
                    <TRow key={m.id}>
                      <TD>
                        <div className="flex items-center gap-3">
                          <MedicoAvatar nome={m.nome} />
                          <span className="font-semibold text-ds-text">{m.nome}</span>
                        </div>
                      </TD>
                      <TD className="font-mono text-xs">
                        {m.cpf ? formatCpf(m.cpf).replace(/(\d{3})\.\d{3}\.\d{3}-(\d{2})/, '$1.***.**-$2') : '—'}
                      </TD>
                      <TD className="font-semibold text-xs">{m.crm} / {m.crmUf.trim()}</TD>
                      <TD className="text-xs">{m.especialidade || '—'}</TD>
                      <TD><DadosBancariosDisplay medico={m} /></TD>
                      <TD><StatusBadge status={m.status} /></TD>
                      <TD className="text-right">
                        <div className="flex justify-end gap-1">
                          {canEdit && (m.status === 'RASCUNHO' || m.status === 'INATIVO') && (
                            <button
                              onClick={() => handleAtivar(m)}
                              disabled={activatingId === m.id}
                              className="p-1.5 rounded hover:bg-green-50 text-ds-light hover:text-green-600 transition-colors disabled:opacity-50"
                              title={m.status === 'INATIVO' ? 'Reativar médico' : 'Ativar médico'}
                            >
                              <UserCheck size={15} />
                            </button>
                          )}
                          <button
                            onClick={() => setViewingDocs(m)}
                            className="p-1.5 rounded hover:bg-primary-50 text-ds-light hover:text-primary transition-colors"
                            title="Documentos"
                          >
                            <FolderOpen size={15} />
                          </button>
                          {canEdit && (
                            <button
                              onClick={() => handleEditar(m)}
                              disabled={loadingEdit === m.id}
                              className="p-1.5 rounded hover:bg-ds-input text-ds-light hover:text-primary transition-colors disabled:opacity-50"
                              title="Editar"
                            >
                              <Pencil size={15} />
                            </button>
                          )}
                          {isGestao && m.status !== 'INATIVO' && (
                            <button
                              onClick={() => setInativando(m)}
                              className="p-1.5 rounded hover:bg-red-50 text-ds-light hover:text-red-600 transition-colors"
                              title="Inativar"
                            >
                              <UserX size={15} />
                            </button>
                          )}
                        </div>
                      </TD>
                    </TRow>
                  ))}
                </TBody>
              </Table>
            </div>
          </>
        )}

        {/* Pagination */}
        {filtered.length > 0 && (
          <div className="flex items-center justify-between px-5 py-3 border-t border-ds-border text-xs text-ds-light">
            <div className="flex items-center gap-2">
              <span>
                Exibindo <strong className="text-ds-mid">{from}–{to}</strong> de{' '}
                <strong className="text-ds-mid">{filtered.length}</strong> médicos
              </span>
              <select
                value={pageSize}
                onChange={e => setPageSize(Number(e.target.value))}
                className="ml-2 py-1 px-2 text-xs border border-ds-border rounded bg-white text-ds-mid focus:outline-none"
              >
                {PAGE_SIZES.map(s => <option key={s} value={s}>{s} por página</option>)}
              </select>
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setCurrentPage(p => Math.max(0, p - 1))}
                disabled={safePage === 0}
                className="px-2.5 py-1 rounded border border-ds-border hover:bg-ds-hover disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                ‹ Anterior
              </button>
              <span className="px-3 py-1 rounded border border-primary bg-primary text-white font-semibold text-xs">
                {safePage + 1}
              </span>
              <span className="px-2 text-ds-light">de {totalPages}</span>
              <button
                onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={safePage >= totalPages - 1}
                className="px-2.5 py-1 rounded border border-ds-border hover:bg-ds-hover disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Próxima ›
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Modals */}
      {showForm && (
        <MedicoWizardModal
          medico={editing}
          onClose={() => { setShowForm(false); setEditing(null) }}
          onSaved={handleSaved}
        />
      )}
      {inativando && (
        <MedicoInativarModal
          medico={inativando}
          onClose={() => setInativando(null)}
          onInativado={handleInativado}
        />
      )}
      {viewingDocs && (
        <DocumentosModal
          medico={viewingDocs}
          onClose={() => setViewingDocs(null)}
        />
      )}
    </div>
  )
}
