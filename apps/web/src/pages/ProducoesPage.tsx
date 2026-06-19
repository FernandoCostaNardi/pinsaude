import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Plus, Search, ClipboardList, CheckCircle2, Clock, FileText, XCircle,
  Download, FilterX,
} from 'lucide-react'
import { Button, Spinner, Alert, Table, THead, TBody, TRow, TH, TD } from '@pinsaude/ui'
import { Producao, producoesApi } from '../api/producoesApi'
import { medicosApi, Medico } from '../api/medicosApi'
import { tomadoresApi, Tomador } from '../api/tomadoresApi'
import { ProducaoDetalheModal } from './ProducaoDetalheModal'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function gerarCompetencias(n = 24): string[] {
  const result: string[] = []
  const now = new Date()
  for (let i = 0; i < n; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    result.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return result
}

const COMPETENCIAS = gerarCompetencias(24)

// ─── Status badge ─────────────────────────────────────────────────────────────

type StatusProducao = Producao['status']

const STATUS_CFG: Record<StatusProducao, { label: string; cls: string; Icon: React.ElementType }> = {
  RASCUNHO:   { label: 'Rascunho',   cls: 'bg-gray-100 text-gray-600',   Icon: Clock        },
  CONFIRMADA: { label: 'Confirmada', cls: 'bg-primary-50 text-primary',   Icon: CheckCircle2 },
  EMITIDA:    { label: 'Emitida',    cls: 'bg-green-50 text-green-700',   Icon: FileText     },
  CANCELADA:  { label: 'Cancelada',  cls: 'bg-red-50 text-red-700',       Icon: XCircle      },
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

// ─── CSV export ───────────────────────────────────────────────────────────────

function exportarCSV(producoes: Producao[], medicoNomeMap: Record<string, string>) {
  const header = ['Data', 'Médico', 'Tomador', 'Município', 'Cód. LC 116', 'Serviço', 'Competência', 'Valor Bruto (R$)', 'Status', 'Descrição Complementar']
  const rows = producoes.map(p => [
    new Date(p.createdAt).toLocaleDateString('pt-BR'),
    medicoNomeMap[p.medicoId] ?? p.medicoId,
    p.tomador.razaoSocialNome,
    p.tomador.municipio ?? '',
    p.servico.codigoLc116,
    p.servico.descricaoPadrao,
    formatCompetencia(p.competencia),
    (p.valorBruto / 100).toFixed(2).replace('.', ','),
    STATUS_CFG[p.status].label,
    p.descricaoComplementar ?? '',
  ])
  const csv = [header, ...rows].map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(';')).join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `producao_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function ProducoesPage() {
  const navigate = useNavigate()

  const [producoes, setProducoes]   = useState<Producao[]>([])
  const [medicos, setMedicos]       = useState<Medico[]>([])
  const [tomadores, setTomadores]   = useState<Tomador[]>([])
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState<string | null>(null)

  // filtros
  const [q, setQ]                     = useState('')
  const [filtroStatus, setFiltroStatus] = useState('')
  const [filtroMedico, setFiltroMedico] = useState('')
  const [filtroTomador, setFiltroTomador] = useState('')
  const [periodoInicio, setPeriodoInicio] = useState('')
  const [periodoFim, setPeriodoFim]       = useState('')

  // paginação
  const [page, setPage]   = useState(0)
  const pageSize          = 10

  // detalhe modal
  const [selectedProducao, setSelectedProducao] = useState<Producao | null>(null)

  useEffect(() => {
    Promise.all([
      producoesApi.listar(),
      medicosApi.listar(0, 1000, 'ATIVO').catch(() => ({ content: [] })),
      tomadoresApi.listar().catch(() => [] as Tomador[]),
    ])
      .then(([prods, medPage, toms]) => {
        setProducoes(prods)
        setMedicos(medPage.content ?? [])
        setTomadores(toms)
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const medicoNomeMap = useMemo<Record<string, string>>(() =>
    Object.fromEntries(medicos.map(m => [m.id, m.nome])),
    [medicos]
  )

  const filtered = useMemo(() => {
    const ql = q.toLowerCase()
    return producoes.filter(p => {
      const matchQ = !q ||
        p.tomador.razaoSocialNome.toLowerCase().includes(ql) ||
        (medicoNomeMap[p.medicoId] ?? '').toLowerCase().includes(ql) ||
        p.competencia.includes(q)
      const matchStatus  = !filtroStatus  || p.status === filtroStatus
      const matchMedico  = !filtroMedico  || p.medicoId === filtroMedico
      const matchTomador = !filtroTomador || p.tomador.id === filtroTomador
      const matchDe  = !periodoInicio || p.competencia >= periodoInicio
      const matchAte = !periodoFim    || p.competencia <= periodoFim
      return matchQ && matchStatus && matchMedico && matchTomador && matchDe && matchAte
    })
  }, [producoes, q, filtroStatus, filtroMedico, filtroTomador, periodoInicio, periodoFim, medicoNomeMap])

  const paginated  = filtered.slice(page * pageSize, (page + 1) * pageSize)
  const totalPages = Math.ceil(filtered.length / pageSize)

  // stats globais (independente de filtro)
  const totalBruto  = producoes.reduce((s, p) => s + p.valorBruto, 0)
  const confirmadas = producoes.filter(p => p.status === 'CONFIRMADA').length
  const emitidas    = producoes.filter(p => p.status === 'EMITIDA').length

  // totalizadores do filtro atual
  const totalFiltradoBruto  = filtered.reduce((s, p) => s + p.valorBruto, 0)
  const totalFiltradoLiq    = Math.round(totalFiltradoBruto * 0.85)

  const temFiltroAtivo = q || filtroStatus || filtroMedico || filtroTomador || periodoInicio || periodoFim

  function limparFiltros() {
    setQ(''); setFiltroStatus(''); setFiltroMedico(''); setFiltroTomador('')
    setPeriodoInicio(''); setPeriodoFim(''); setPage(0)
  }

  const selectCls = "text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid bg-white"

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
          <p className="text-sm text-ds-light mt-1">Consulta, filtros e exportação de registros de produção</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            onClick={() => exportarCSV(filtered, medicoNomeMap)}
            disabled={filtered.length === 0}
          >
            <Download size={15} className="mr-1" />
            Exportar CSV
          </Button>
          <Button onClick={() => navigate('/producao/nova')}>
            <Plus size={16} className="mr-1" />
            Nova Produção
          </Button>
        </div>
      </div>

      {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Stats globais */}
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
      <div className="bg-white rounded-xl border border-ds-border p-4 space-y-3">
        <div className="flex flex-wrap gap-3 items-center">
          {/* Busca textual */}
          <div className="relative flex-1 min-w-48">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light" />
            <input
              value={q}
              onChange={e => { setQ(e.target.value); setPage(0) }}
              placeholder="Buscar por tomador, médico ou competência..."
              className="w-full pl-9 pr-3 py-2 text-sm border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>

          {/* Status */}
          <select value={filtroStatus} onChange={e => { setFiltroStatus(e.target.value); setPage(0) }} className={selectCls}>
            <option value="">Todos os status</option>
            <option value="RASCUNHO">Rascunho</option>
            <option value="CONFIRMADA">Confirmada</option>
            <option value="EMITIDA">Emitida</option>
            <option value="CANCELADA">Cancelada</option>
          </select>
        </div>

        <div className="flex flex-wrap gap-3 items-center">
          {/* Médico */}
          <select value={filtroMedico} onChange={e => { setFiltroMedico(e.target.value); setPage(0) }} className={`${selectCls} flex-1 min-w-48`}>
            <option value="">Todos os médicos</option>
            {medicos.map(m => (
              <option key={m.id} value={m.id}>{m.nome}</option>
            ))}
          </select>

          {/* Tomador */}
          <select value={filtroTomador} onChange={e => { setFiltroTomador(e.target.value); setPage(0) }} className={`${selectCls} flex-1 min-w-48`}>
            <option value="">Todos os tomadores</option>
            {tomadores.map(t => (
              <option key={t.id} value={t.id}>{t.razaoSocialNome}</option>
            ))}
          </select>

          {/* Período de */}
          <div className="flex items-center gap-2">
            <label className="text-xs text-ds-light whitespace-nowrap">De</label>
            <select value={periodoInicio} onChange={e => { setPeriodoInicio(e.target.value); setPage(0) }} className={selectCls}>
              <option value="">—</option>
              {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
            </select>
          </div>

          {/* Período até */}
          <div className="flex items-center gap-2">
            <label className="text-xs text-ds-light whitespace-nowrap">Até</label>
            <select value={periodoFim} onChange={e => { setPeriodoFim(e.target.value); setPage(0) }} className={selectCls}>
              <option value="">—</option>
              {COMPETENCIAS.map(c => <option key={c} value={c}>{formatCompetencia(c)}</option>)}
            </select>
          </div>

          {temFiltroAtivo && (
            <Button variant="ghost" size="sm" onClick={limparFiltros}>
              <FilterX size={14} className="mr-1" />
              Limpar filtros
            </Button>
          )}
        </div>
      </div>

      {/* Totalizadores do filtro */}
      {temFiltroAtivo && (
        <div className="bg-primary-50 border border-primary/20 rounded-xl px-5 py-3 flex flex-wrap gap-6 items-center">
          <div>
            <p className="text-xs text-primary/70 font-medium uppercase tracking-wide">Registros filtrados</p>
            <p className="text-xl font-bold text-primary">{filtered.length}</p>
          </div>
          <div className="w-px h-10 bg-primary/20" />
          <div>
            <p className="text-xs text-primary/70 font-medium uppercase tracking-wide">Valor Bruto Total</p>
            <p className="text-xl font-bold text-primary">{formatBRL(totalFiltradoBruto)}</p>
          </div>
          <div className="w-px h-10 bg-primary/20" />
          <div>
            <p className="text-xs text-primary/70 font-medium uppercase tracking-wide">Estimativa Líquido (−15%)</p>
            <p className="text-xl font-bold text-primary">{formatBRL(totalFiltradoLiq)}</p>
          </div>
          <div className="ml-auto text-xs text-primary/60 italic">
            * Estimativa sem considerar retenções tributárias individuais
          </div>
        </div>
      )}

      {/* Tabela */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <Table>
          <THead>
            <TRow>
              <TH>Tomador</TH>
              <TH>Médico</TH>
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
                <TD colSpan={7} className="text-center text-ds-light py-12">
                  {producoes.length === 0
                    ? 'Nenhuma produção registrada ainda. Clique em "Nova Produção" para começar.'
                    : 'Nenhum registro corresponde aos filtros aplicados.'}
                </TD>
              </TRow>
            ) : paginated.map(p => (
              <TRow
                key={p.id}
                className="cursor-pointer hover:bg-primary-50/40 transition-colors"
                onClick={() => setSelectedProducao(p)}
              >
                <TD>
                  <div className="font-medium text-ds-mid text-sm">{p.tomador.razaoSocialNome}</div>
                  {p.tomador.municipio && (
                    <div className="text-xs text-ds-light">{p.tomador.municipio}</div>
                  )}
                </TD>
                <TD>
                  <div className="text-sm text-ds-mid">
                    {medicoNomeMap[p.medicoId] ?? <span className="font-mono text-xs text-ds-light">{p.medicoId.slice(0, 8)}…</span>}
                  </div>
                </TD>
                <TD>
                  <div className="text-sm text-ds-mid">{p.servico.codigoLc116}</div>
                  <div className="text-xs text-ds-light truncate max-w-40" title={p.servico.descricaoPadrao}>
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

        {/* Paginação + rodapé com totais */}
        <div className="px-4 py-3 border-t border-ds-border bg-ds-surface flex items-center justify-between text-sm text-ds-light">
          <span>
            {filtered.length} registro{filtered.length !== 1 ? 's' : ''}
            {temFiltroAtivo && filtered.length !== producoes.length && ` de ${producoes.length}`}
          </span>
          {totalPages > 1 && (
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                Anterior
              </Button>
              <span className="px-2">{page + 1} / {totalPages}</span>
              <Button variant="ghost" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                Próximo
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Modal detalhe */}
      <ProducaoDetalheModal
        producao={selectedProducao}
        medicoNome={selectedProducao ? (medicoNomeMap[selectedProducao.medicoId] ?? '') : ''}
        onClose={() => setSelectedProducao(null)}
      />
    </div>
  )
}
