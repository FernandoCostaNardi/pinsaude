import { useEffect, useMemo, useState } from 'react'
import {
  Plus, Search, Users, UserCheck, UserX, Clock, Shield, Send,
} from 'lucide-react'
import {
  Badge, Button, Spinner, Alert,
  Table, THead, TBody, TRow, TH, TD,
} from '@pinsaude/ui'
import { usersApi, type Usuario } from '../api/usersApi'
import { InviteUserModal } from '../components/InviteUserModal'

// ─── Perfil config ────────────────────────────────────────────────────────────

const PERFIS: Record<string, { label: string; cls: string }> = {
  medico:     { label: 'Médico',      cls: 'bg-primary-50 text-primary'     },
  operacao:   { label: 'Operação',    cls: 'bg-green-50 text-green-700'     },
  financeiro: { label: 'Financeiro',  cls: 'bg-amber-50 text-amber-700'     },
  contabil:   { label: 'Contábil',    cls: 'bg-violet-50 text-violet-700'   },
  gestao:     { label: 'Gestão',      cls: 'bg-rose-50 text-rose-700'       },
}

const PERFIL_OPTIONS = Object.entries(PERFIS).map(([value, { label }]) => ({ value, label }))

function PerfilBadge({ perfil }: { perfil: string }) {
  const cfg = PERFIS[perfil] ?? { label: perfil, cls: 'bg-ds-input text-ds-mid' }
  return (
    <span className={`inline-block px-2 py-0.5 rounded-md text-xs font-semibold ${cfg.cls}`}>
      {cfg.label}
    </span>
  )
}

// ─── Status badge ─────────────────────────────────────────────────────────────

function StatusBadge({ ativo, pendente }: { ativo: boolean; pendente: boolean }) {
  if (pendente && ativo)  return <Badge variant="warning">Convite enviado</Badge>
  if (pendente && !ativo) return <Badge variant="error">Recusado</Badge>
  if (ativo)              return <Badge variant="success">Ativo</Badge>
  return                         <Badge variant="error">Inativo</Badge>
}

// ─── User avatar ──────────────────────────────────────────────────────────────

const AVATAR_COLORS: [string, string][] = [
  ['bg-primary-50', 'text-primary'],
  ['bg-green-50',   'text-green-700'],
  ['bg-amber-50',   'text-amber-700'],
  ['bg-violet-50',  'text-violet-700'],
  ['bg-rose-50',    'text-rose-700'],
]

function UserAvatar({ nome, size = 'sm' }: { nome: string; size?: 'sm' | 'md' }) {
  const initials = nome.split(/\s+/).filter(w => w.length > 1).slice(0, 2)
    .map(w => w[0].toUpperCase()).join('') || nome.slice(0, 2).toUpperCase()
  const [bg, fg] = AVATAR_COLORS[nome.charCodeAt(0) % AVATAR_COLORS.length]
  return (
    <div className={[
      'rounded-lg flex items-center justify-center shrink-0', bg,
      size === 'md' ? 'w-10 h-10' : 'w-8 h-8',
    ].join(' ')}>
      <span className={['font-black', fg, size === 'md' ? 'text-sm' : 'text-xs'].join(' ')}>
        {initials}
      </span>
    </div>
  )
}

// ─── Stat card ────────────────────────────────────────────────────────────────

interface StatCardProps {
  icon: React.ElementType
  label: string
  value: number
  sub: string
  iconBg: string
  iconColor: string
}

function StatCard({ icon: Icon, label, value, sub, iconBg, iconColor }: StatCardProps) {
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

// ─── Constants ────────────────────────────────────────────────────────────────

const PAGE_SIZES = [10, 25, 50]

// ─── Page ────────────────────────────────────────────────────────────────────

export function UsersPage() {
  const [usuarios, setUsuarios] = useState<Usuario[]>([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState<string | null>(null)
  const [loadingId, setLoadingId]     = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [search, setSearch]               = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [filterPerfil, setFilterPerfil]   = useState('')
  const [filterStatus, setFilterStatus]   = useState<'ativo' | 'inativo' | 'pendente' | ''>('')

  const [pageSize, setPageSize]       = useState(10)
  const [currentPage, setCurrentPage] = useState(0)

  const [showInvite, setShowInvite] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(t)
  }, [search])

  useEffect(() => { setCurrentPage(0) }, [debouncedSearch, filterPerfil, filterStatus, pageSize])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setUsuarios(await usersApi.listar())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar usuários')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function withLoading(id: string, fn: () => Promise<void>) {
    setLoadingId(id)
    setActionError(null)
    try { await fn() } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Erro inesperado')
    } finally { setLoadingId(null) }
  }

  function handleUpdated(updated: Usuario) {
    setUsuarios(prev => prev.map(u => u.id === updated.id ? updated : u))
  }

  function toggleStatus(u: Usuario) {
    withLoading(u.id, async () => handleUpdated(await usersApi.alterarStatus(u.id, !u.ativo)))
  }

  function reenviar(u: Usuario) {
    withLoading(u.id, async () => { await usersApi.reenviarConvite(u.id) })
  }

  function handlePerfilChange(u: Usuario, perfil: string) {
    withLoading(u.id, async () => handleUpdated(await usersApi.alterarPerfil(u.id, perfil)))
  }

  function handleSaved(novo: Usuario) {
    setUsuarios(prev => [novo, ...prev])
    setShowInvite(false)
  }

  // ── Stats ──
  const stats = useMemo(() => ({
    total:     usuarios.length,
    ativos:    usuarios.filter(u =>  u.ativo && !u.pendente).length,
    pendentes: usuarios.filter(u =>  u.ativo &&  u.pendente).length,
    perfis:    new Set(usuarios.map(u => u.perfil)).size,
  }), [usuarios])

  // ── Filter ──
  const filtered = useMemo(() => {
    const q = debouncedSearch.toLowerCase()
    return usuarios.filter(u => {
      const matchSearch  = !q || u.nome.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)
      const matchPerfil  = !filterPerfil || u.perfil === filterPerfil
      const matchStatus  = !filterStatus
        || (filterStatus === 'ativo'    &&  u.ativo && !u.pendente)
        || (filterStatus === 'inativo'  && !u.ativo)
        || (filterStatus === 'pendente' &&  u.ativo &&  u.pendente)
      return matchSearch && matchPerfil && matchStatus
    })
  }, [usuarios, debouncedSearch, filterPerfil, filterStatus])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage   = Math.min(currentPage, totalPages - 1)
  const paginated  = filtered.slice(safePage * pageSize, (safePage + 1) * pageSize)
  const from       = filtered.length === 0 ? 0 : safePage * pageSize + 1
  const to         = Math.min((safePage + 1) * pageSize, filtered.length)
  const hasFilters = !!(search || filterPerfil || filterStatus)

  function clearFilters() {
    setSearch('')
    setFilterPerfil('')
    setFilterStatus('')
  }

  return (
    <div className="flex flex-col gap-5">

      {/* ── Stat cards ───────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          icon={Users}     label="Total de Usuários"   value={stats.total}
          sub="Contas no sistema"         iconBg="bg-primary-50" iconColor="text-primary"
        />
        <StatCard
          icon={UserCheck} label="Usuários Ativos"      value={stats.ativos}
          sub="Com acesso liberado"        iconBg="bg-green-50"   iconColor="text-green-600"
        />
        <StatCard
          icon={Clock}     label="Convites Pendentes"   value={stats.pendentes}
          sub="Aguardando confirmação"     iconBg="bg-amber-50"   iconColor="text-amber-600"
        />
        <StatCard
          icon={Shield}    label="Perfis em Uso"        value={stats.perfis}
          sub={`de ${PERFIL_OPTIONS.length} disponíveis`} iconBg="bg-violet-50" iconColor="text-violet-600"
        />
      </div>

      {/* ── Main card ────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">

        {/* Card header */}
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-ds-text">Usuários do Sistema</p>
            <p className="text-xs text-ds-light">Gestão de acessos e perfis da organização</p>
          </div>
          <Button size="sm" onClick={() => setShowInvite(true)}>
            <Plus size={14} /> Convidar
          </Button>
        </div>

        {/* Filters */}
        <div className="flex flex-wrap gap-3 items-center px-5 py-3 border-b border-ds-border bg-ds-input">
          <div className="relative flex-1 min-w-[180px] max-w-xs">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light pointer-events-none" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Nome ou e-mail..."
              className="block w-full pl-9 pr-3 py-1.5 text-sm border border-ds-border rounded-lg bg-white text-ds-text placeholder-ds-light focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            />
          </div>
          <select
            value={filterPerfil}
            onChange={e => setFilterPerfil(e.target.value)}
            className="py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
          >
            <option value="">Todos os perfis</option>
            {PERFIL_OPTIONS.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
          </select>
          <select
            value={filterStatus}
            onChange={e => setFilterStatus(e.target.value as typeof filterStatus)}
            className="py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
          >
            <option value="">Todos os status</option>
            <option value="ativo">Ativo</option>
            <option value="inativo">Inativo</option>
            <option value="pendente">Convite enviado</option>
          </select>
          {hasFilters && (
            <button
              onClick={clearFilters}
              className="px-3 py-1.5 text-xs font-medium text-ds-mid border border-ds-border rounded-lg bg-white hover:bg-ds-hover transition-colors"
            >
              Limpar filtros
            </button>
          )}
        </div>

        {/* Action error */}
        {actionError && (
          <div className="px-5 pt-3">
            <Alert variant="error" onClose={() => setActionError(null)}>{actionError}</Alert>
          </div>
        )}

        {/* Content */}
        {loading ? (
          <div className="flex justify-center items-center py-20">
            <Spinner size="lg" />
          </div>
        ) : error ? (
          <div className="p-5">
            <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>
          </div>
        ) : usuarios.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
              <Users size={28} className="text-primary-200" />
            </div>
            <p className="text-sm font-semibold text-ds-mid">Nenhum usuário cadastrado</p>
            <p className="text-xs text-ds-light mt-1">Convide o primeiro membro da equipe</p>
            <Button size="sm" className="mt-5" onClick={() => setShowInvite(true)}>
              <Plus size={14} /> Convidar primeiro usuário
            </Button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <p className="text-sm font-medium text-ds-mid">Nenhum usuário corresponde aos filtros</p>
            <button onClick={clearFilters} className="mt-2 text-xs text-primary hover:underline">
              Limpar filtros
            </button>
          </div>
        ) : (
          <>
            {/* Mobile: cards */}
            <div className="flex flex-col gap-3 p-4 sm:hidden">
              {paginated.map(u => (
                <div key={u.id} className="rounded-xl border border-ds-border bg-white p-4">
                  {/* Header */}
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-start gap-3 min-w-0">
                      <UserAvatar nome={u.nome || u.email} size="md" />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ds-text truncate">{u.nome || '—'}</p>
                        <p className="mt-0.5 text-xs text-ds-light truncate">{u.email}</p>
                      </div>
                    </div>
                    <StatusBadge ativo={u.ativo} pendente={u.pendente} />
                  </div>

                  {/* Perfil + ações */}
                  <div className="mt-3 flex items-end justify-between gap-3 pt-3 border-t border-ds-border">
                    <div className="flex flex-col gap-1">
                      <p className="text-xs font-medium text-ds-light">Perfil</p>
                      <div className="flex items-center gap-2">
                        <PerfilBadge perfil={u.perfil} />
                        <select
                          value={u.perfil}
                          disabled={loadingId === u.id}
                          onChange={e => handlePerfilChange(u, e.target.value)}
                          className="py-1 px-2 text-xs border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:border-primary disabled:opacity-50 max-w-[7rem]"
                        >
                          {PERFIL_OPTIONS.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
                        </select>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 shrink-0">
                      {u.pendente && u.ativo && (
                        <button
                          title="Reenviar convite"
                          disabled={loadingId === u.id}
                          onClick={() => reenviar(u)}
                          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-ds-mid hover:bg-primary-50 hover:text-primary disabled:opacity-40 transition-colors"
                        >
                          <Send size={13} /> Reenviar
                        </button>
                      )}
                      <button
                        disabled={loadingId === u.id}
                        onClick={() => toggleStatus(u)}
                        className={[
                          'flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs disabled:opacity-40 transition-colors',
                          u.ativo
                            ? 'text-ds-mid hover:bg-red-50 hover:text-red-600'
                            : 'text-ds-mid hover:bg-green-50 hover:text-green-600',
                        ].join(' ')}
                      >
                        {u.ativo
                          ? <><UserX size={13} /> Desativar</>
                          : <><UserCheck size={13} /> Ativar</>
                        }
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Desktop: table */}
            <div className="hidden sm:block">
              <Table className="!border-0 !rounded-none">
                <THead>
                  <TRow>
                    <TH>Usuário</TH>
                    <TH>Perfil</TH>
                    <TH>Status</TH>
                    <TH className="text-right">Ações</TH>
                  </TRow>
                </THead>
                <TBody>
                  {paginated.map(u => (
                    <TRow key={u.id}>
                      <TD>
                        <div className="flex items-center gap-3">
                          <UserAvatar nome={u.nome || u.email} />
                          <div>
                            <p className="font-semibold text-ds-text leading-snug">{u.nome || '—'}</p>
                            <p className="text-xs text-ds-light">{u.email}</p>
                          </div>
                        </div>
                      </TD>
                      <TD>
                        <select
                          value={u.perfil}
                          disabled={loadingId === u.id}
                          onChange={e => handlePerfilChange(u, e.target.value)}
                          className="py-1 px-2 text-xs border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:border-primary disabled:opacity-50"
                        >
                          {PERFIL_OPTIONS.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
                        </select>
                      </TD>
                      <TD>
                        <StatusBadge ativo={u.ativo} pendente={u.pendente} />
                      </TD>
                      <TD className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          {u.pendente && u.ativo && (
                            <button
                              title="Reenviar convite"
                              disabled={loadingId === u.id}
                              onClick={() => reenviar(u)}
                              className="p-1.5 rounded hover:bg-primary-50 text-ds-light hover:text-primary disabled:opacity-40 transition-colors"
                            >
                              <Send size={15} />
                            </button>
                          )}
                          <button
                            title={u.ativo ? 'Desativar usuário' : 'Ativar usuário'}
                            disabled={loadingId === u.id}
                            onClick={() => toggleStatus(u)}
                            className={[
                              'p-1.5 rounded disabled:opacity-40 transition-colors',
                              u.ativo
                                ? 'text-ds-light hover:bg-red-50 hover:text-red-600'
                                : 'text-ds-light hover:bg-green-50 hover:text-green-600',
                            ].join(' ')}
                          >
                            {u.ativo ? <UserX size={15} /> : <UserCheck size={15} />}
                          </button>
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
                <strong className="text-ds-mid">{filtered.length}</strong> usuários
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

      {/* ── Modal ────────────────────────────────────────────────────────── */}
      {showInvite && (
        <InviteUserModal
          onClose={() => setShowInvite(false)}
          onSaved={handleSaved}
        />
      )}
    </div>
  )
}
