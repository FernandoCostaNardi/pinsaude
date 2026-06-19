import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Search, ClipboardList, CheckCircle2, Clock, FileText, XCircle } from 'lucide-react'
import { Button, Spinner, Alert, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { Producao, producoesApi } from '../api/producoesApi'

// ─── Helpers de formatação ────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

// ─── Status badge ─────────────────────────────────────────────────────────────

type StatusProducao = Producao['status']

const STATUS_CFG: Record<StatusProducao, { label: string; cls: string; Icon: React.ElementType }> = {
  RASCUNHO:  { label: 'Rascunho',   cls: 'bg-gray-100 text-gray-600',    Icon: Clock        },
  CONFIRMADA:{ label: 'Confirmada', cls: 'bg-primary-50 text-primary',    Icon: CheckCircle2 },
  EMITIDA:   { label: 'Emitida',    cls: 'bg-green-50 text-green-700',    Icon: FileText     },
  CANCELADA: { label: 'Cancelada',  cls: 'bg-red-50 text-red-700',        Icon: XCircle      },
}

function StatusBadge({ status }: { status: StatusProducao }) {
  const { label, cls, Icon } = STATUS_CFG[status]
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold ${cls}`}>
      <Icon size={11} />
      {label}
    </span>
  )
}

// ─── Stat card ────────────────────────────────────────────────────────────────

function StatCard({ icon: Icon, label, value, sub, iconBg, iconColor }: {
  icon: React.ElementType; label: string; value: number | string
  sub: string; iconBg: string; iconColor: string
}) {
  return (
    <div className="bg-white rounded-xl border border-ds-border p-5 flex items-center gap-4 shadow-sm">
      <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${iconBg}`}>
        <Icon size={20} className={iconColor} />
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium text-ds-light uppercase tracking-wide truncate">{label}</p>
        <p className="text-2xl font-bold text-ds-mid leading-none mt-0.5">{value}</p>
        <p className="text-xs text-ds-light mt-0.5">{sub}</p>
      </div>
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function ProducoesPage() {
  const navigate = useNavigate()
  const [producoes, setProducoes] = useState<Producao[]>([])
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState<string | null>(null)
  const [q, setQ]                 = useState('')
  const [filtroStatus, setFiltroStatus] = useState('')
  const [filtroComp, setFiltroComp]     = useState('')

  // paginação
  const [page, setPage]     = useState(0)
  const [pageSize]          = useState(10)

  useEffect(() => {
    setLoading(true)
    producoesApi.listar()
      .then(setProducoes)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(() => {
    return producoes.filter(p => {
      const matchQ = !q ||
        p.tomador.razaoSocialNome.toLowerCase().includes(q.toLowerCase()) ||
        p.competencia.includes(q)
      const matchStatus = !filtroStatus || p.status === filtroStatus
      const matchComp   = !filtroComp  || p.competencia === filtroComp
      return matchQ && matchStatus && matchComp
    })
  }, [producoes, q, filtroStatus, filtroComp])

  const paginated = filtered.slice(page * pageSize, (page + 1) * pageSize)
  const totalPages = Math.ceil(filtered.length / pageSize)

  // stats
  const totalBruto      = producoes.reduce((s, p) => s + p.valorBruto, 0)
  const confirmadas     = producoes.filter(p => p.status === 'CONFIRMADA').length
  const emitidas        = producoes.filter(p => p.status === 'EMITIDA').length
  const competencias    = [...new Set(producoes.map(p => p.competencia))].sort().reverse()

  function limparFiltros() {
    setQ(''); setFiltroStatus(''); setFiltroComp(''); setPage(0)
  }

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <Spinner size="lg" />
    </div>
  )

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-ds-mid">Produção Médica</h1>
          <p className="text-sm text-ds-light mt-1">Registros de produção e emissão de NFS-e</p>
        </div>
        <Button onClick={() => navigate('/producao/nova')}>
          <Plus size={16} className="mr-1" />
          Nova Produção
        </Button>
      </div>

      {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon={ClipboardList} label="Total de Registros" value={producoes.length}
          sub="todas as competências" iconBg="bg-primary-50" iconColor="text-primary" />
        <StatCard icon={CheckCircle2} label="Confirmadas" value={confirmadas}
          sub="aguardando emissão" iconBg="bg-blue-50" iconColor="text-blue-600" />
        <StatCard icon={FileText} label="Emitidas" value={emitidas}
          sub="NFS-e gerada" iconBg="bg-green-50" iconColor="text-green-600" />
        <StatCard icon={ClipboardList} label="Volume Total" value={formatBRL(totalBruto)}
          sub="valor bruto acumulado" iconBg="bg-violet-50" iconColor="text-violet-600" />
      </div>

      {/* Filtros */}
      <div className="bg-white rounded-xl border border-ds-border p-4 flex flex-wrap gap-3 items-center">
        <div className="relative flex-1 min-w-48">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light" />
          <input
            value={q}
            onChange={e => { setQ(e.target.value); setPage(0) }}
            placeholder="Buscar por tomador ou competência..."
            className="w-full pl-9 pr-3 py-2 text-sm border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        <select
          value={filtroStatus}
          onChange={e => { setFiltroStatus(e.target.value); setPage(0) }}
          className="text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid"
        >
          <option value="">Todos os status</option>
          <option value="RASCUNHO">Rascunho</option>
          <option value="CONFIRMADA">Confirmada</option>
          <option value="EMITIDA">Emitida</option>
          <option value="CANCELADA">Cancelada</option>
        </select>

        <select
          value={filtroComp}
          onChange={e => { setFiltroComp(e.target.value); setPage(0) }}
          className="text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid"
        >
          <option value="">Todas as competências</option>
          {competencias.map(c => (
            <option key={c} value={c}>{formatCompetencia(c)}</option>
          ))}
        </select>

        {(q || filtroStatus || filtroComp) && (
          <Button variant="ghost" size="sm" onClick={limparFiltros}>Limpar</Button>
        )}
      </div>

      {/* Tabela */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <Table>
          <THead>
            <TRow>
              <TH>Tomador</TH>
              <TH>Serviço</TH>
              <TH>Competência</TH>
              <TH className="text-right">Valor Bruto</TH>
              <TH>Status</TH>
              <TH>Data</TH>
            </TRow>
          </THead>
          <TBody>
            {paginated.length === 0 ? (
              <TRow>
                <TD colSpan={6} className="text-center text-ds-light py-12">
                  {producoes.length === 0
                    ? 'Nenhuma produção registrada ainda. Clique em "Nova Produção" para começar.'
                    : 'Nenhum registro corresponde aos filtros aplicados.'}
                </TD>
              </TRow>
            ) : paginated.map(p => (
              <TRow key={p.id}>
                <TD>
                  <div className="font-medium text-ds-mid text-sm">{p.tomador.razaoSocialNome}</div>
                  {p.tomador.municipio && (
                    <div className="text-xs text-ds-light">{p.tomador.municipio}</div>
                  )}
                </TD>
                <TD>
                  <div className="text-sm text-ds-mid">{p.servico.codigoLc116}</div>
                  <div className="text-xs text-ds-light truncate max-w-48" title={p.servico.descricaoPadrao}>
                    {p.servico.descricaoPadrao}
                  </div>
                </TD>
                <TD className="font-medium text-ds-mid text-sm">{formatCompetencia(p.competencia)}</TD>
                <TD className="text-right font-semibold text-ds-mid text-sm">{formatBRL(p.valorBruto)}</TD>
                <TD><StatusBadge status={p.status} /></TD>
                <TD className="text-xs text-ds-light">
                  {new Date(p.createdAt).toLocaleDateString('pt-BR')}
                </TD>
              </TRow>
            ))}
          </TBody>
        </Table>

        {/* Paginação */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-ds-border bg-ds-surface text-sm text-ds-light">
            <span>{filtered.length} registro{filtered.length !== 1 ? 's' : ''}</span>
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                Anterior
              </Button>
              <span className="px-2">{page + 1} / {totalPages}</span>
              <Button variant="ghost" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                Próximo
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
