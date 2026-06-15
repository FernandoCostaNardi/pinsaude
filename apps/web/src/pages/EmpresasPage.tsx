import { useEffect, useMemo, useState } from 'react'
import { Plus, Search, Pencil, Trash2, Building2, Landmark, FileText, CheckCircle2, BarChart3 } from 'lucide-react'
import {
  Badge, Button, Spinner, Alert,
  Table, THead, TBody, TRow, TH, TD,
} from '@pinsaude/ui'
import { Empresa, RegimeTributario, empresasApi } from '../api/empresasApi'
import { formatCnpj } from '../utils/cnpj'
import { EmpresaWizardModal } from '../components/EmpresaWizardModal'
import { EmpresaDeleteModal } from '../components/EmpresaDeleteModal'
import { ContasBancariasModal } from '../components/ContasBancariasModal'
import { ConfiguracaoFiscalModal } from '../components/ConfiguracaoFiscalModal'
import { useAuth } from '../auth/useAuth'

// ─── Regime ──────────────────────────────────────────────────────────────────

const REGIME_LABELS: Record<RegimeTributario, string> = {
  SIMPLES_NACIONAL: 'Simples Nacional',
  LUCRO_PRESUMIDO:  'Lucro Presumido',
  LUCRO_REAL:       'Lucro Real',
}

const REGIME_BADGE: Record<RegimeTributario, string> = {
  SIMPLES_NACIONAL: 'bg-primary-50 text-primary',
  LUCRO_PRESUMIDO:  'bg-amber-50 text-amber-600',
  LUCRO_REAL:       'bg-violet-50 text-violet-600',
}

function RegimeBadge({ regime }: { regime: RegimeTributario }) {
  return (
    <span className={`inline-block px-2 py-0.5 rounded-md text-xs font-semibold ${REGIME_BADGE[regime]}`}>
      {REGIME_LABELS[regime]}
    </span>
  )
}

// ─── Company avatar ───────────────────────────────────────────────────────────

function CompanyAvatar({ name, size = 'sm' }: { name: string; size?: 'sm' | 'md' }) {
  const initials = name.split(/\s+/).filter(w => w.length > 2).slice(0, 2)
    .map(w => w[0].toUpperCase()).join('') || name.slice(0, 2).toUpperCase()
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

export function EmpresasPage() {
  const { user } = useAuth()
  const isGestao = user?.realm_access?.roles.includes('gestao') ?? false

  const [empresas, setEmpresas] = useState<Empresa[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [filterRegime, setFilterRegime] = useState<RegimeTributario | ''>('')
  const [filterStatus, setFilterStatus] = useState<'ativo' | 'inativo' | ''>('')

  const [pageSize, setPageSize] = useState(10)
  const [currentPage, setCurrentPage] = useState(0)

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Empresa | null>(null)
  const [deleting, setDeleting] = useState<Empresa | null>(null)
  const [contasEmpresa, setContasEmpresa] = useState<Empresa | null>(null)
  const [fiscalEmpresa, setFiscalEmpresa] = useState<Empresa | null>(null)

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(t)
  }, [search])

  useEffect(() => { setCurrentPage(0) }, [debouncedSearch, filterRegime, filterStatus, pageSize])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const page = await empresasApi.listar(0, 1000)
      setEmpresas(page.content)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar empresas')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const filtered = useMemo(() => {
    const q = debouncedSearch.toLowerCase()
    const qDigits = q.replace(/\D/g, '')
    return empresas.filter(e => {
      const matchSearch = !q
        || e.razaoSocial.toLowerCase().includes(q)
        || (qDigits.length > 0 && e.cnpj.replace(/\D/g, '').includes(qDigits))
      const matchRegime = !filterRegime || e.regimeTributario === filterRegime
      const matchStatus = !filterStatus
        || (filterStatus === 'ativo' ? e.ativo : !e.ativo)
      return matchSearch && matchRegime && matchStatus
    })
  }, [empresas, debouncedSearch, filterRegime, filterStatus])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage   = Math.min(currentPage, totalPages - 1)
  const paginated  = filtered.slice(safePage * pageSize, (safePage + 1) * pageSize)
  const from       = filtered.length === 0 ? 0 : safePage * pageSize + 1
  const to         = Math.min((safePage + 1) * pageSize, filtered.length)

  function handleSaved(saved: Empresa) {
    setEmpresas(prev => {
      const idx = prev.findIndex(e => e.id === saved.id)
      return idx >= 0 ? prev.map(e => e.id === saved.id ? saved : e) : [...prev, saved]
    })
    setShowForm(false)
    setEditing(null)
  }

  function handleDeleted(id: string) {
    setEmpresas(prev => prev.filter(e => e.id !== id))
    setDeleting(null)
  }

  function openEdit(empresa: Empresa) {
    setEditing(empresa)
    setShowForm(true)
  }

  function openNew() {
    setEditing(null)
    setShowForm(true)
  }

  // ── Stats derived from full list (not filtered) ──
  const stats = useMemo(() => ({
    total:   empresas.length,
    ativas:  empresas.filter(e => e.ativo).length,
    inativas: empresas.filter(e => !e.ativo).length,
    simples: empresas.filter(e => e.regimeTributario === 'SIMPLES_NACIONAL').length,
    outros:  empresas.filter(e => e.regimeTributario !== 'SIMPLES_NACIONAL').length,
  }), [empresas])

  const hasFilters = !!(search || filterRegime || filterStatus)

  return (
    <div className="flex flex-col gap-5">

      {/* ── Stats cards ──────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          icon={Building2}
          label="Total de Empresas"
          value={stats.total}
          sub="CNPJs cadastrados"
          iconBg="bg-primary-50"
          iconColor="text-primary"
        />
        <StatCard
          icon={CheckCircle2}
          label="Empresas Ativas"
          value={stats.ativas}
          sub={`${stats.inativas} inativa${stats.inativas !== 1 ? 's' : ''}`}
          iconBg="bg-green-50"
          iconColor="text-green-600"
        />
        <StatCard
          icon={FileText}
          label="Simples Nacional"
          value={stats.simples}
          sub="Regime simplificado"
          iconBg="bg-violet-50"
          iconColor="text-violet-600"
        />
        <StatCard
          icon={BarChart3}
          label="Lucro Pres. / Real"
          value={stats.outros}
          sub="Outros regimes"
          iconBg="bg-amber-50"
          iconColor="text-amber-600"
        />
      </div>

      {/* ── Main card ────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">

        {/* Card header */}
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-ds-text">Empresas Cadastradas</p>
            <p className="text-xs text-ds-light">Gestão de CNPJs vinculados ao sistema</p>
          </div>
          {isGestao && (
            <Button size="sm" onClick={openNew}>
              <Plus size={14} /> Nova Empresa
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
              placeholder="Razão social ou CNPJ..."
              className="block w-full pl-9 pr-3 py-1.5 text-sm border border-ds-border rounded-lg bg-white text-ds-text placeholder-ds-light focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            />
          </div>
          <select
            value={filterRegime}
            onChange={e => setFilterRegime(e.target.value as RegimeTributario | '')}
            className="py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
          >
            <option value="">Todos os regimes</option>
            <option value="SIMPLES_NACIONAL">Simples Nacional</option>
            <option value="LUCRO_PRESUMIDO">Lucro Presumido</option>
            <option value="LUCRO_REAL">Lucro Real</option>
          </select>
          <select
            value={filterStatus}
            onChange={e => setFilterStatus(e.target.value as 'ativo' | 'inativo' | '')}
            className="py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
          >
            <option value="">Todos os status</option>
            <option value="ativo">Ativo</option>
            <option value="inativo">Inativo</option>
          </select>
          {hasFilters && (
            <button
              onClick={() => { setSearch(''); setFilterRegime(''); setFilterStatus('') }}
              className="px-3 py-1.5 text-xs font-medium text-ds-mid border border-ds-border rounded-lg bg-white hover:bg-ds-hover transition-colors"
            >
              Limpar filtros
            </button>
          )}
        </div>

        {/* Content */}
        {loading ? (
          <div className="flex justify-center items-center py-20">
            <Spinner size="lg" />
          </div>
        ) : error ? (
          <div className="p-5">
            <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>
          </div>
        ) : empresas.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
              <Building2 size={28} className="text-primary-200" />
            </div>
            <p className="text-sm font-semibold text-ds-mid">Nenhuma empresa cadastrada</p>
            <p className="text-xs text-ds-light mt-1">Comece cadastrando o primeiro CNPJ</p>
            {isGestao && (
              <Button size="sm" className="mt-5" onClick={openNew}>
                <Plus size={14} /> Cadastrar primeira empresa
              </Button>
            )}
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <p className="text-sm font-medium text-ds-mid">Nenhuma empresa corresponde aos filtros</p>
            <button
              onClick={() => { setSearch(''); setFilterRegime(''); setFilterStatus('') }}
              className="mt-2 text-xs text-primary hover:underline"
            >
              Limpar filtros
            </button>
          </div>
        ) : (
          <>
            {/* Mobile: cards */}
            <div className="flex flex-col gap-3 p-4 sm:hidden">
              {paginated.map(empresa => (
                <div key={empresa.id} className="rounded-xl border border-ds-border bg-white p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-start gap-3 min-w-0">
                      <CompanyAvatar name={empresa.razaoSocial} size="md" />
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ds-text truncate">{empresa.razaoSocial}</p>
                        <p className="mt-0.5 font-mono text-xs text-ds-light">{formatCnpj(empresa.cnpj)}</p>
                      </div>
                    </div>
                    <Badge variant={empresa.ativo ? 'success' : 'error'} className="shrink-0">
                      {empresa.ativo ? 'Ativo' : 'Inativo'}
                    </Badge>
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                    <div>
                      <p className="text-xs font-medium text-ds-light">Município</p>
                      <p className="text-ds-text font-medium truncate">{empresa.municipio ?? '—'}</p>
                    </div>
                    <div>
                      <p className="text-xs font-medium text-ds-light mb-1">Regime</p>
                      <RegimeBadge regime={empresa.regimeTributario} />
                    </div>
                  </div>
                  <div className="mt-3 flex items-center gap-1 border-t border-ds-border pt-3">
                    <button
                      onClick={() => setContasEmpresa(empresa)}
                      className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-ds-mid hover:bg-primary-50 hover:text-primary transition-colors"
                    >
                      <Landmark size={13} /> Contas
                    </button>
                    {isGestao && (
                      <button
                        onClick={() => setFiscalEmpresa(empresa)}
                        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-ds-mid hover:bg-green-50 hover:text-green-600 transition-colors"
                      >
                        <FileText size={13} /> Fiscal
                      </button>
                    )}
                    {isGestao && (
                      <>
                        <button
                          onClick={() => openEdit(empresa)}
                          className="ml-auto p-1.5 rounded-lg text-ds-light hover:bg-ds-input hover:text-primary transition-colors"
                          title="Editar"
                        >
                          <Pencil size={14} />
                        </button>
                        <button
                          onClick={() => setDeleting(empresa)}
                          className="p-1.5 rounded-lg text-ds-light hover:bg-red-50 hover:text-red-600 transition-colors"
                          title="Excluir"
                        >
                          <Trash2 size={14} />
                        </button>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {/* Desktop: table */}
            <div className="hidden sm:block">
              <Table className="!border-0 !rounded-none">
                <THead>
                  <TRow>
                    <TH>Razão Social</TH>
                    <TH>CNPJ</TH>
                    <TH>Município</TH>
                    <TH>Regime</TH>
                    <TH>Status</TH>
                    <TH className="text-right">Ações</TH>
                  </TRow>
                </THead>
                <TBody>
                  {paginated.map(empresa => (
                    <TRow key={empresa.id}>
                      <TD>
                        <div className="flex items-center gap-3">
                          <CompanyAvatar name={empresa.razaoSocial} />
                          <span className="font-semibold text-ds-text">{empresa.razaoSocial}</span>
                        </div>
                      </TD>
                      <TD className="font-mono text-xs">{formatCnpj(empresa.cnpj)}</TD>
                      <TD>{empresa.municipio ?? '—'}</TD>
                      <TD><RegimeBadge regime={empresa.regimeTributario} /></TD>
                      <TD>
                        <Badge variant={empresa.ativo ? 'success' : 'error'}>
                          {empresa.ativo ? 'Ativo' : 'Inativo'}
                        </Badge>
                      </TD>
                      <TD className="text-right">
                        <div className="flex justify-end gap-1">
                          <button
                            onClick={() => setContasEmpresa(empresa)}
                            className="p-1.5 rounded hover:bg-primary-50 text-ds-light hover:text-primary transition-colors"
                            title="Contas bancárias"
                          >
                            <Landmark size={15} />
                          </button>
                          {isGestao && (
                            <button
                              onClick={() => setFiscalEmpresa(empresa)}
                              className="p-1.5 rounded hover:bg-green-50 text-ds-light hover:text-green-600 transition-colors"
                              title="Configuração fiscal"
                            >
                              <FileText size={15} />
                            </button>
                          )}
                          {isGestao && (
                            <>
                              <button
                                onClick={() => openEdit(empresa)}
                                className="p-1.5 rounded hover:bg-ds-input text-ds-light hover:text-primary transition-colors"
                                title="Editar"
                              >
                                <Pencil size={15} />
                              </button>
                              <button
                                onClick={() => setDeleting(empresa)}
                                className="p-1.5 rounded hover:bg-red-50 text-ds-light hover:text-red-600 transition-colors"
                                title="Excluir"
                              >
                                <Trash2 size={15} />
                              </button>
                            </>
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
                <strong className="text-ds-mid">{filtered.length}</strong> empresas
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

      {/* ── Modals ───────────────────────────────────────────────────────── */}
      {showForm && (
        <EmpresaWizardModal
          empresa={editing}
          onClose={() => { setShowForm(false); setEditing(null) }}
          onSaved={handleSaved}
        />
      )}
      {deleting && (
        <EmpresaDeleteModal
          empresa={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={handleDeleted}
        />
      )}
      {contasEmpresa && (
        <ContasBancariasModal
          empresaId={contasEmpresa.id}
          empresaNome={contasEmpresa.razaoSocial}
          isGestao={isGestao}
          onClose={() => setContasEmpresa(null)}
        />
      )}
      {fiscalEmpresa && (
        <ConfiguracaoFiscalModal
          empresaId={fiscalEmpresa.id}
          empresaNome={fiscalEmpresa.razaoSocial}
          onClose={() => setFiscalEmpresa(null)}
        />
      )}
    </div>
  )
}
