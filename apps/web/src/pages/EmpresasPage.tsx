import { useEffect, useMemo, useState } from 'react'
import { Plus, Search, Pencil, Trash2, Building2, Landmark, FileText } from 'lucide-react'
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

const REGIME_LABELS: Record<RegimeTributario, string> = {
  SIMPLES_NACIONAL: 'Simples Nacional',
  LUCRO_PRESUMIDO: 'Lucro Presumido',
  LUCRO_REAL: 'Lucro Real',
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

const PAGE_SIZES = [10, 25, 50]

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
  const safePage = Math.min(currentPage, totalPages - 1)
  const paginated = filtered.slice(safePage * pageSize, (safePage + 1) * pageSize)

  const from = filtered.length === 0 ? 0 : safePage * pageSize + 1
  const to = Math.min((safePage + 1) * pageSize, filtered.length)

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

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Empresas</h1>
          <p className="mt-0.5 text-sm text-gray-500">
            Cadastro e gestão de empresas (CNPJs)
          </p>
        </div>
        {isGestao && (
          <Button onClick={openNew} size="md">
            <Plus size={16} />
            Nova Empresa
          </Button>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Buscar por razão social ou CNPJ..."
            className="block w-full pl-9 pr-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
          />
        </div>

        <select
          value={filterRegime}
          onChange={e => setFilterRegime(e.target.value as RegimeTributario | '')}
          className="py-2 px-3 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
        >
          <option value="">Todos os regimes</option>
          <option value="SIMPLES_NACIONAL">Simples Nacional</option>
          <option value="LUCRO_PRESUMIDO">Lucro Presumido</option>
          <option value="LUCRO_REAL">Lucro Real</option>
        </select>

        <select
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value as 'ativo' | 'inativo' | '')}
          className="py-2 px-3 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-300 focus:border-primary"
        >
          <option value="">Todos os status</option>
          <option value="ativo">Ativo</option>
          <option value="inativo">Inativo</option>
        </select>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex justify-center items-center py-20">
          <Spinner size="lg" />
        </div>
      ) : error ? (
        <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Building2 size={40} className="text-gray-300 mb-3" />
          <p className="text-sm font-medium text-gray-500">
            {empresas.length === 0
              ? 'Nenhuma empresa cadastrada'
              : 'Nenhuma empresa corresponde aos filtros'}
          </p>
          {empresas.length === 0 && isGestao && (
            <Button variant="outline" size="sm" className="mt-4" onClick={openNew}>
              <Plus size={14} /> Cadastrar primeira empresa
            </Button>
          )}
        </div>
      ) : (
        <>
          {/* Mobile: cards */}
          <div className="flex flex-col gap-3 sm:hidden">
            {paginated.map(empresa => (
              <div key={empresa.id} className="rounded-xl border border-ds-border bg-white shadow-sm p-4">
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
                    className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-gray-600 hover:bg-blue-50 hover:text-primary transition-colors"
                  >
                    <Landmark size={13} /> Contas
                  </button>
                  {isGestao && (
                    <button
                      onClick={() => setFiscalEmpresa(empresa)}
                      className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs text-gray-600 hover:bg-green-50 hover:text-green-600 transition-colors"
                    >
                      <FileText size={13} /> Fiscal
                    </button>
                  )}
                  {isGestao && (
                    <>
                      <button
                        onClick={() => openEdit(empresa)}
                        className="ml-auto p-1.5 rounded-lg text-gray-500 hover:bg-gray-100 hover:text-primary transition-colors"
                        title="Editar"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => setDeleting(empresa)}
                        className="p-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-600 transition-colors"
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
            <Table>
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
                    <TD>
                      <RegimeBadge regime={empresa.regimeTributario} />
                    </TD>
                    <TD>
                      <Badge variant={empresa.ativo ? 'success' : 'error'}>
                        {empresa.ativo ? 'Ativo' : 'Inativo'}
                      </Badge>
                    </TD>
                    <TD className="text-right">
                      <div className="flex justify-end gap-1">
                        <button
                          onClick={() => setContasEmpresa(empresa)}
                          className="p-1.5 rounded hover:bg-blue-50 text-gray-500 hover:text-primary transition-colors"
                          title="Contas bancárias"
                        >
                          <Landmark size={15} />
                        </button>
                        {isGestao && (
                          <button
                            onClick={() => setFiscalEmpresa(empresa)}
                            className="p-1.5 rounded hover:bg-green-50 text-gray-500 hover:text-green-600 transition-colors"
                            title="Configuração fiscal"
                          >
                            <FileText size={15} />
                          </button>
                        )}
                        {isGestao && (
                          <>
                            <button
                              onClick={() => openEdit(empresa)}
                              className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-primary transition-colors"
                              title="Editar"
                            >
                              <Pencil size={15} />
                            </button>
                            <button
                              onClick={() => setDeleting(empresa)}
                              className="p-1.5 rounded hover:bg-red-50 text-gray-500 hover:text-red-600 transition-colors"
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

          {/* Pagination */}
          <div className="flex items-center justify-between text-sm text-gray-500">
            <div className="flex items-center gap-2">
              <span>Exibindo {from}–{to} de {filtered.length}</span>
              <select
                value={pageSize}
                onChange={e => setPageSize(Number(e.target.value))}
                className="ml-2 py-1 px-2 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-primary-300"
              >
                {PAGE_SIZES.map(s => <option key={s} value={s}>{s} por página</option>)}
              </select>
            </div>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setCurrentPage(p => Math.max(0, p - 1))}
                disabled={safePage === 0}
                className="px-2 py-1 rounded border border-gray-300 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed text-xs"
              >
                ‹ Anterior
              </button>
              <span className="px-3 py-1 text-xs font-medium">
                {safePage + 1} / {totalPages}
              </span>
              <button
                onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={safePage >= totalPages - 1}
                className="px-2 py-1 rounded border border-gray-300 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed text-xs"
              >
                Próxima ›
              </button>
            </div>
          </div>
        </>
      )}

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
