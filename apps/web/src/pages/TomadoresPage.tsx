import { useEffect, useMemo, useState } from 'react'
import {
  Plus, Search, Pencil, Trash2, Hospital,
  CheckCircle2, Minus, AlertTriangle
} from 'lucide-react'
import {
  Button, Spinner, Alert,
  Table, THead, TBody, TRow, TH, TD,
} from '@pinsaude/ui'
import { Tomador, TipoTomador, tomadoresApi } from '../api/tomadoresApi'
import { formatCnpj } from '../utils/cnpj'
import { formatCpf } from '../utils/cpf'
import { TomadorFormModal } from '../components/TomadorFormModal'
import { useAuth } from '../auth/useAuth'

// ─── Tipo badges ──────────────────────────────────────────────────────────────

const TIPO_LABEL: Record<TipoTomador, string> = {
  HOSPITAL:    'Hospital',
  CLINICA:     'Clínica',
  OPERADORA:   'Operadora',
  PACIENTE_PF: 'Paciente PF',
}

const TIPO_BADGE: Record<TipoTomador, string> = {
  HOSPITAL:    'bg-primary-50 text-primary',
  CLINICA:     'bg-green-50 text-green-700',
  OPERADORA:   'bg-violet-50 text-violet-700',
  PACIENTE_PF: 'bg-orange-50 text-orange-700',
}

function TipoBadge({ tipo }: { tipo: TipoTomador }) {
  return (
    <span className={`inline-block px-2 py-0.5 rounded-md text-xs font-semibold ${TIPO_BADGE[tipo]}`}>
      {TIPO_LABEL[tipo]}
    </span>
  )
}

// ─── Retention indicator ──────────────────────────────────────────────────────

function RetencaoIcon({ ativo, tooltip }: { ativo: boolean; tooltip: string }) {
  return (
    <span className="cursor-help" title={tooltip}>
      {ativo
        ? <CheckCircle2 size={15} className="text-green-500" />
        : <Minus size={15} className="text-ds-light" />}
    </span>
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

// ─── Delete confirm dialog ────────────────────────────────────────────────────

function ConfirmDeleteDialog({
  tomador,
  onConfirm,
  onCancel,
  loading,
}: {
  tomador: Tomador
  onConfirm: () => void
  onCancel: () => void
  loading: boolean
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCancel} />
      <div className="relative bg-white rounded-2xl shadow-xl p-6 max-w-sm w-full">
        <div className="flex flex-col items-center text-center gap-3">
          <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center">
            <AlertTriangle size={24} className="text-red-500" />
          </div>
          <p className="text-base font-bold text-ds-text">Remover tomador?</p>
          <p className="text-sm text-ds-mid">
            <strong>{tomador.razaoSocialNome}</strong> será removido permanentemente.
            Registros de produção vinculados serão afetados.
          </p>
        </div>
        <div className="flex gap-3 mt-5">
          <Button variant="ghost" className="flex-1" onClick={onCancel} disabled={loading}>
            Cancelar
          </Button>
          <Button variant="danger" className="flex-1" onClick={onConfirm} loading={loading}>
            Remover
          </Button>
        </div>
      </div>
    </div>
  )
}

// ─── Formatters ───────────────────────────────────────────────────────────────

function formatDocumento(tipo: TipoTomador, cnpjCpf: string): string {
  const digits = cnpjCpf.replace(/\D/g, '')
  if (tipo === 'PACIENTE_PF') return formatCpf(digits)
  return formatCnpj(digits)
}

// ─── Constants ────────────────────────────────────────────────────────────────

const PAGE_SIZES = [10, 25, 50]

// ─── Page ────────────────────────────────────────────────────────────────────

export function TomadoresPage() {
  const { user } = useAuth()
  const roles = user?.realm_access?.roles ?? []
  const canWrite = roles.some(r => ['operacao', 'gestao'].includes(r))

  const [tomadores, setTomadores] = useState<Tomador[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [filterTipo, setFilterTipo] = useState<TipoTomador | ''>('')

  const [pageSize, setPageSize] = useState(10)
  const [currentPage, setCurrentPage] = useState(0)

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Tomador | null>(null)
  const [deleting, setDeleting] = useState<Tomador | null>(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(t)
  }, [search])

  useEffect(() => { setCurrentPage(0) }, [debouncedSearch, filterTipo, pageSize])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setTomadores(await tomadoresApi.listar())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar tomadores')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const filtered = useMemo(() => {
    const q = debouncedSearch.toLowerCase()
    const qDigits = q.replace(/\D/g, '')
    return tomadores.filter(t => {
      const matchSearch = !q
        || t.razaoSocialNome.toLowerCase().includes(q)
        || (t.nomeFantasia?.toLowerCase().includes(q) ?? false)
        || (qDigits.length >= 3 && t.cnpjCpf.replace(/\D/g, '').includes(qDigits))
      const matchTipo = !filterTipo || t.tipo === filterTipo
      return matchSearch && matchTipo
    })
  }, [tomadores, debouncedSearch, filterTipo])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage   = Math.min(currentPage, totalPages - 1)
  const paginated  = filtered.slice(safePage * pageSize, (safePage + 1) * pageSize)
  const from       = filtered.length === 0 ? 0 : safePage * pageSize + 1
  const to         = Math.min((safePage + 1) * pageSize, filtered.length)

  function handleSaved(saved: Tomador) {
    setTomadores(prev => {
      const idx = prev.findIndex(t => t.id === saved.id)
      return idx >= 0 ? prev.map(t => t.id === saved.id ? saved : t) : [...prev, saved]
    })
    setShowForm(false)
    setEditing(null)
  }

  async function handleDelete() {
    if (!deleting) return
    setDeleteLoading(true)
    try {
      await tomadoresApi.deletar(deleting.id)
      setTomadores(prev => prev.filter(t => t.id !== deleting.id))
      setDeleting(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao remover tomador')
      setDeleting(null)
    } finally {
      setDeleteLoading(false)
    }
  }

  const stats = useMemo(() => ({
    total:       tomadores.length,
    hospitais:   tomadores.filter(t => t.tipo === 'HOSPITAL').length,
    clinicas:    tomadores.filter(t => t.tipo === 'CLINICA').length,
    operadoras:  tomadores.filter(t => t.tipo === 'OPERADORA').length,
    pacientes:   tomadores.filter(t => t.tipo === 'PACIENTE_PF').length,
  }), [tomadores])

  const hasFilters = !!(search || filterTipo)

  return (
    <div className="flex flex-col gap-5">

      {/* ── Stats ── */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          icon={Hospital}
          label="Total de Tomadores"
          value={stats.total}
          sub="Todas as categorias"
          iconBg="bg-primary-50"
          iconColor="text-primary"
        />
        <StatCard
          icon={Hospital}
          label="Hospitais"
          value={stats.hospitais}
          sub={`${stats.clinicas} clínica${stats.clinicas !== 1 ? 's' : ''}`}
          iconBg="bg-blue-50"
          iconColor="text-blue-600"
        />
        <StatCard
          icon={Hospital}
          label="Operadoras"
          value={stats.operadoras}
          sub="Planos de saúde"
          iconBg="bg-violet-50"
          iconColor="text-violet-600"
        />
        <StatCard
          icon={Hospital}
          label="Pacientes PF"
          value={stats.pacientes}
          sub="Atendimentos particulares"
          iconBg="bg-orange-50"
          iconColor="text-orange-600"
        />
      </div>

      {/* ── Main card ── */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">

        {/* Card header */}
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="flex-1 min-w-0">
            <p className="text-base font-bold text-ds-text">Tomadores de Serviços</p>
            <p className="text-xs text-ds-light">Hospitais, clínicas, operadoras e pacientes particulares</p>
          </div>
          {canWrite && (
            <Button size="sm" onClick={() => { setEditing(null); setShowForm(true) }}>
              <Plus size={14} /> Novo Tomador
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
              placeholder="Buscar por nome ou CNPJ/CPF..."
              className="block w-full pl-9 pr-3 py-1.5 text-sm border border-ds-border rounded-lg bg-white text-ds-text placeholder-ds-light focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
            />
          </div>
          <select
            value={filterTipo}
            onChange={e => setFilterTipo(e.target.value as TipoTomador | '')}
            className="py-1.5 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-mid focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary"
          >
            <option value="">Todos os tipos</option>
            <option value="HOSPITAL">Hospital</option>
            <option value="CLINICA">Clínica</option>
            <option value="OPERADORA">Operadora</option>
            <option value="PACIENTE_PF">Paciente PF</option>
          </select>
          {hasFilters && (
            <button
              onClick={() => { setSearch(''); setFilterTipo('') }}
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
        ) : tomadores.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="w-14 h-14 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
              <Hospital size={28} className="text-primary-200" />
            </div>
            <p className="text-sm font-semibold text-ds-mid">Nenhum tomador cadastrado</p>
            <p className="text-xs text-ds-light mt-1">Comece cadastrando o primeiro hospital ou clínica</p>
            {canWrite && (
              <Button size="sm" className="mt-5" onClick={() => { setEditing(null); setShowForm(true) }}>
                <Plus size={14} /> Cadastrar primeiro tomador
              </Button>
            )}
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <p className="text-sm font-medium text-ds-mid">Nenhum tomador corresponde aos filtros</p>
            <button
              onClick={() => { setSearch(''); setFilterTipo('') }}
              className="mt-2 text-xs text-primary hover:underline"
            >
              Limpar filtros
            </button>
          </div>
        ) : (
          <>
            {/* Mobile: cards */}
            <div className="flex flex-col gap-3 p-4 sm:hidden">
              {paginated.map(t => (
                <div key={t.id} className="rounded-xl border border-ds-border bg-white p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ds-text truncate">{t.razaoSocialNome}</p>
                      <p className="mt-0.5 font-mono text-xs text-ds-light">{formatDocumento(t.tipo, t.cnpjCpf)}</p>
                    </div>
                    <TipoBadge tipo={t.tipo} />
                  </div>
                  {t.municipio && (
                    <p className="mt-2 text-xs text-ds-mid">{t.municipio}</p>
                  )}
                  <div className="mt-3 flex items-center gap-3 text-xs text-ds-mid border-t border-ds-border pt-2">
                    <RetencaoIcon ativo={t.indicadorRetencaoFederal} tooltip="Retenção Federal (IR/CSLL/PIS/COFINS)" />
                    <span>Federal</span>
                    <RetencaoIcon ativo={t.indicadorRetencaoIss} tooltip="Retenção ISS" />
                    <span>ISS</span>
                    {canWrite && (
                      <>
                        <button
                          onClick={() => { setEditing(t); setShowForm(true) }}
                          className="ml-auto p-1.5 rounded text-ds-light hover:bg-ds-input hover:text-primary transition-colors"
                        >
                          <Pencil size={14} />
                        </button>
                        <button
                          onClick={() => setDeleting(t)}
                          className="p-1.5 rounded text-ds-light hover:bg-red-50 hover:text-red-600 transition-colors"
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
                    <TH>Nome Fantasia</TH>
                    <TH>Razão Social / Nome</TH>
                    <TH>CNPJ / CPF</TH>
                    <TH>Tipo</TH>
                    <TH>Município</TH>
                    <TH className="text-center">
                      <span title="Retenção Federal e ISS">Retenções</span>
                    </TH>
                    {canWrite && <TH className="text-right">Ações</TH>}
                  </TRow>
                </THead>
                <TBody>
                  {paginated.map(t => (
                    <TRow key={t.id}>
                      <TD>
                        <span className="text-ds-mid">{t.nomeFantasia ?? '—'}</span>
                      </TD>
                      <TD>
                        <span className="font-semibold text-ds-text">{t.razaoSocialNome}</span>
                      </TD>
                      <TD className="font-mono text-xs">{formatDocumento(t.tipo, t.cnpjCpf)}</TD>
                      <TD><TipoBadge tipo={t.tipo} /></TD>
                      <TD>{t.municipio ?? '—'}</TD>
                      <TD>
                        <div className="flex items-center justify-center gap-3">
                          <RetencaoIcon
                            ativo={t.indicadorRetencaoFederal}
                            tooltip="Retenção Federal (IR, CSLL, PIS e COFINS)"
                          />
                          <RetencaoIcon
                            ativo={t.indicadorRetencaoIss}
                            tooltip="Retenção ISS na fonte"
                          />
                        </div>
                      </TD>
                      {canWrite && (
                        <TD className="text-right">
                          <div className="flex justify-end gap-1">
                            <button
                              onClick={() => { setEditing(t); setShowForm(true) }}
                              className="p-1.5 rounded hover:bg-ds-input text-ds-light hover:text-primary transition-colors"
                              title="Editar"
                            >
                              <Pencil size={15} />
                            </button>
                            <button
                              onClick={() => setDeleting(t)}
                              className="p-1.5 rounded hover:bg-red-50 text-ds-light hover:text-red-600 transition-colors"
                              title="Remover"
                            >
                              <Trash2 size={15} />
                            </button>
                          </div>
                        </TD>
                      )}
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
                <strong className="text-ds-mid">{filtered.length}</strong> tomadores
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

      {/* ── Modals ── */}
      {showForm && (
        <TomadorFormModal
          tomador={editing}
          onClose={() => { setShowForm(false); setEditing(null) }}
          onSaved={handleSaved}
        />
      )}
      {deleting && (
        <ConfirmDeleteDialog
          tomador={deleting}
          onConfirm={handleDelete}
          onCancel={() => setDeleting(null)}
          loading={deleteLoading}
        />
      )}
    </div>
  )
}
