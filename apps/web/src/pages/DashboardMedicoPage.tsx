import { useCallback, useEffect, useRef, useState, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  Banknote, TrendingUp, FileText, ClipboardList,
  RefreshCw, Bell, CheckCircle2, Clock, XCircle,
  AlertTriangle, Loader2, ShieldCheck, PlusCircle,
} from 'lucide-react'
import { Spinner, Alert } from '@pinsaude/ui'
import { portalApi, DashboardPortal } from '../api/portalApi'
import { useAuth } from '../auth/useAuth'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

function getLast12Months(): string[] {
  const months: string[] = []
  const now = new Date()
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return months
}

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_CFG: Record<string, { label: string; cls: string; Icon: React.ElementType }> = {
  PENDENTE:                  { label: 'Na fila',            cls: 'bg-blue-50 text-blue-700',      Icon: Clock          },
  PROCESSANDO:               { label: 'Processando',        cls: 'bg-primary-50 text-primary',    Icon: Loader2        },
  EMITIDA:                   { label: 'Emitida',            cls: 'bg-green-50 text-green-700',    Icon: CheckCircle2   },
  CANCELADA:                 { label: 'Cancelada',          cls: 'bg-gray-100 text-gray-500',     Icon: XCircle        },
  ERRO:                      { label: 'Erro',               cls: 'bg-red-50 text-red-700',        Icon: XCircle        },
  REJEITADA:                 { label: 'Rejeitada',          cls: 'bg-red-50 text-red-700',        Icon: XCircle        },
  AGUARDANDO_EMISSAO_MANUAL: { label: 'Emissão Manual',    cls: 'bg-orange-50 text-orange-700',  Icon: AlertTriangle  },
  AGUARDANDO_VALIDACAO:      { label: 'Aguard. Validação', cls: 'bg-yellow-50 text-yellow-700',  Icon: ShieldCheck    },
}

function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CFG[status] ?? { label: status, cls: 'bg-gray-100 text-gray-500', Icon: Clock }
  const { label, cls, Icon } = cfg
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold ${cls}`}>
      <Icon size={11} className={status === 'PROCESSANDO' ? 'animate-spin' : ''} />
      {label}
    </span>
  )
}

// ─── KPI card ─────────────────────────────────────────────────────────────────

function KpiCard({ icon: Icon, label, value, sub, iconBg, iconColor }: {
  icon: React.ElementType
  label: string
  value: string
  sub: ReactNode
  iconBg: string
  iconColor: string
}) {
  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm p-4 flex items-center gap-4">
      <div className={`w-11 h-11 rounded-xl ${iconBg} flex items-center justify-center shrink-0`}>
        <Icon size={20} className={iconColor} />
      </div>
      <div className="min-w-0">
        <p className="text-lg font-black text-ds-text leading-none truncate">{value}</p>
        <p className="text-xs font-semibold text-ds-mid mt-0.5">{label}</p>
        <p className="text-[11px] text-ds-light">{sub}</p>
      </div>
    </div>
  )
}

// ─── Skeleton card ────────────────────────────────────────────────────────────

function SkeletonKpiCard() {
  return (
    <div className="bg-white rounded-xl border border-ds-border shadow-sm p-4 animate-pulse">
      <div className="flex items-center gap-4">
        <div className="w-11 h-11 rounded-xl bg-gray-200 shrink-0" />
        <div className="flex-1 space-y-2">
          <div className="h-5 bg-gray-200 rounded w-28" />
          <div className="h-3 bg-gray-100 rounded w-36" />
          <div className="h-3 bg-gray-100 rounded w-20" />
        </div>
      </div>
    </div>
  )
}

// ─── SVG Line chart ───────────────────────────────────────────────────────────

interface ChartPoint { month: string; value: number }

function LineChart({ points }: { points: ChartPoint[] }) {
  const W = 600
  const H = 100
  const padL = 0
  const padR = 0
  const padT = 10
  const padB = 24

  const maxVal = Math.max(...points.map(p => p.value), 1)
  const innerW = W - padL - padR
  const innerH = H - padT - padB

  const coords = points.map((p, i) => ({
    x: padL + (i / (points.length - 1)) * innerW,
    y: padT + innerH - (p.value / maxVal) * innerH,
    value: p.value,
    month: p.month,
  }))

  const polyline = coords.map(c => `${c.x},${c.y}`).join(' ')
  const area = `${coords[0].x},${H - padB} ` +
    coords.map(c => `${c.x},${c.y}`).join(' ') +
    ` ${coords[coords.length - 1].x},${H - padB}`

  return (
    <div className="w-full">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-28" preserveAspectRatio="none">
        <defs>
          <linearGradient id="portalGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#02A9F7" stopOpacity="0.18" />
            <stop offset="100%" stopColor="#02A9F7" stopOpacity="0.01" />
          </linearGradient>
        </defs>
        {/* Grid lines */}
        {[0.25, 0.5, 0.75].map(f => (
          <line
            key={f}
            x1={padL} y1={padT + innerH * (1 - f)}
            x2={W - padR} y2={padT + innerH * (1 - f)}
            stroke="#e5e7eb" strokeWidth="1" strokeDasharray="4 3"
          />
        ))}
        {/* Area fill */}
        <polygon points={area} fill="url(#portalGrad)" />
        {/* Line */}
        <polyline
          points={polyline}
          fill="none"
          stroke="#02A9F7"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {/* Dots */}
        {coords.map((c, i) => (
          <circle key={i} cx={c.x} cy={c.y} r={3} fill="#02A9F7" />
        ))}
      </svg>
      {/* X-axis labels — mostrar só início, meio e fim para não sobrecarregar */}
      <div className="flex justify-between mt-1 px-0.5">
        {points.map((p, i) => {
          const show = i === 0 || i === Math.floor(points.length / 2) || i === points.length - 1
          return (
            <span
              key={p.month}
              className={`text-[9px] text-ds-light ${show ? 'opacity-100' : 'opacity-0'} w-[${Math.round(100/points.length)}%] text-center`}
            >
              {formatCompetencia(p.month)}
            </span>
          )
        })}
      </div>
    </div>
  )
}

// ─── Main page ────────────────────────────────────────────────────────────────

export function DashboardMedicoPage() {
  const { user } = useAuth()
  const [dashboard, setDashboard] = useState<DashboardPortal | null>(null)
  const [chartPoints, setChartPoints] = useState<ChartPoint[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    else setRefreshing(true)
    setError(null)
    try {
      const [dash, notas] = await Promise.all([
        portalApi.getDashboard(),
        portalApi.getNotas(),
      ])
      setDashboard(dash)

      const months = getLast12Months()
      const byMonth: Record<string, number> = {}
      notas
        .filter(n => n.status === 'EMITIDA')
        .forEach(n => {
          byMonth[n.competencia] = (byMonth[n.competencia] ?? 0) + n.valorLiquidoMedicoCentavos
        })
      setChartPoints(months.map(m => ({ month: m, value: byMonth[m] ?? 0 })))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    load()
    intervalRef.current = setInterval(() => load(true), 60_000)
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [load])

  const firstName = user?.given_name ?? user?.name?.split(' ')[0] ?? 'Médico'

  return (
    <div className="flex flex-col gap-5">

      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-black text-ds-text">
            Olá, {firstName}! 👋
          </h1>
          <p className="text-sm text-ds-light mt-0.5">Visão financeira do seu portal</p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/portal/producao/nova"
            className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-white bg-primary hover:bg-primary-700 rounded-lg transition-colors"
          >
            <PlusCircle size={13} />
            Informar Produção
          </Link>
          <button
            onClick={() => load(true)}
            disabled={refreshing || loading}
            className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-ds-mid border border-ds-border rounded-lg hover:bg-ds-input transition-colors disabled:opacity-50"
          >
            <RefreshCw size={13} className={refreshing ? 'animate-spin' : ''} />
            Atualizar
          </button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      {/* KPI cards */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        {loading ? (
          <>
            <SkeletonKpiCard />
            <SkeletonKpiCard />
            <SkeletonKpiCard />
            <SkeletonKpiCard />
          </>
        ) : dashboard ? (
          <>
            <KpiCard
              icon={Banknote}
              label="Saldo Disponível"
              value={formatBRL(dashboard.saldoDisponivelCentavos)}
              sub={<Link to="/portal/extrato" className="hover:text-primary transition-colors">Ver extrato →</Link>}
              iconBg="bg-green-50"
              iconColor="text-green-600"
            />
            <KpiCard
              icon={TrendingUp}
              label="A Receber"
              value={formatBRL(dashboard.valorAReceberCentavos)}
              sub="Notas em processamento"
              iconBg="bg-primary-50"
              iconColor="text-primary"
            />
            <KpiCard
              icon={ClipboardList}
              label="Total Produzido"
              value={formatBRL(dashboard.totalProduzidoCentavos)}
              sub={`${dashboard.totalProducoes} produção${dashboard.totalProducoes !== 1 ? 'ões' : ''}`}
              iconBg="bg-purple-50"
              iconColor="text-purple-600"
            />
            <KpiCard
              icon={FileText}
              label="Notas Emitidas"
              value={String(dashboard.totalNotasEmitidas)}
              sub="Total acumulado"
              iconBg="bg-blue-50"
              iconColor="text-blue-600"
            />
          </>
        ) : null}
      </div>

      {/* Gráfico */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-ds-text">Valor Líquido por Competência</p>
            <p className="text-xs text-ds-light mt-0.5">Últimas 12 competências — notas emitidas</p>
          </div>
          {refreshing && <Spinner size="sm" />}
        </div>
        <div className="px-5 pt-4 pb-2">
          {loading ? (
            <div className="h-28 bg-gray-100 rounded-lg animate-pulse" />
          ) : chartPoints.every(p => p.value === 0) ? (
            <div className="h-28 flex items-center justify-center text-ds-light text-sm">
              Sem notas emitidas nos últimos 12 meses
            </div>
          ) : (
            <LineChart points={chartPoints} />
          )}
        </div>
      </div>

      {/* Últimas notas */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-ds-text">Últimas Notas Fiscais</p>
            <p className="text-xs text-ds-light mt-0.5">5 mais recentes</p>
          </div>
          <Link
            to="/portal/notas"
            className="shrink-0 text-xs font-semibold text-primary hover:text-primary-700 transition-colors"
          >
            Ver todas →
          </Link>
        </div>

        {loading ? (
          <div className="divide-y divide-ds-border animate-pulse">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-4 px-5 py-3">
                <div className="h-4 bg-gray-200 rounded w-16" />
                <div className="h-4 bg-gray-100 rounded flex-1" />
                <div className="h-4 bg-gray-200 rounded w-24" />
                <div className="h-5 bg-gray-100 rounded w-20" />
              </div>
            ))}
          </div>
        ) : !dashboard?.ultimasNotas?.length ? (
          <div className="px-5 py-10 text-center text-ds-light text-sm">
            Nenhuma nota fiscal encontrada
          </div>
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden sm:block overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-ds-surface">
                    <th className="text-left px-5 py-2.5 text-xs font-semibold text-ds-mid">Competência</th>
                    <th className="text-left px-4 py-2.5 text-xs font-semibold text-ds-mid">Tomador</th>
                    <th className="text-right px-4 py-2.5 text-xs font-semibold text-ds-mid">Valor Bruto</th>
                    <th className="text-right px-4 py-2.5 text-xs font-semibold text-ds-mid">Líquido</th>
                    <th className="text-center px-4 py-2.5 text-xs font-semibold text-ds-mid">Status</th>
                    <th className="text-right px-5 py-2.5 text-xs font-semibold text-ds-mid">Emissão</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-ds-border">
                  {dashboard.ultimasNotas.map(nota => (
                    <tr key={nota.id} className="hover:bg-ds-surface/50 transition-colors">
                      <td className="px-5 py-3 text-xs font-semibold text-ds-mid">
                        {formatCompetencia(nota.competencia)}
                      </td>
                      <td className="px-4 py-3 text-xs text-ds-text max-w-[180px] truncate">
                        {nota.tomadorNome ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-xs text-right text-ds-text font-medium">
                        {formatBRL(nota.valorBrutoCentavos)}
                      </td>
                      <td className="px-4 py-3 text-xs text-right font-bold text-green-700">
                        {formatBRL(nota.valorLiquidoMedicoCentavos)}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <StatusBadge status={nota.status} />
                      </td>
                      <td className="px-5 py-3 text-xs text-right text-ds-light">
                        {formatDate(nota.emitidaAt ?? nota.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile cards */}
            <div className="sm:hidden divide-y divide-ds-border">
              {dashboard.ultimasNotas.map(nota => (
                <div key={nota.id} className="px-4 py-3 space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-ds-text">{formatCompetencia(nota.competencia)}</span>
                    <StatusBadge status={nota.status} />
                  </div>
                  <p className="text-xs text-ds-mid truncate">{nota.tomadorNome ?? '—'}</p>
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] text-ds-light">Bruto: {formatBRL(nota.valorBrutoCentavos)}</span>
                    <span className="text-xs font-bold text-green-700">{formatBRL(nota.valorLiquidoMedicoCentavos)}</span>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* Notificações */}
      <div className="bg-white rounded-xl border border-ds-border shadow-sm overflow-hidden">
        <div className="flex items-center gap-4 px-5 py-4 border-b border-ds-border">
          <div className="w-8 h-8 rounded-lg bg-yellow-50 flex items-center justify-center shrink-0">
            <Bell size={15} className="text-yellow-600" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-bold text-ds-text">Notificações</p>
            <p className="text-xs text-ds-light mt-0.5">Ações pendentes</p>
          </div>
        </div>
        <div className="px-5 py-6 text-center">
          <div className="w-10 h-10 rounded-full bg-green-50 flex items-center justify-center mx-auto mb-2">
            <CheckCircle2 size={20} className="text-green-600" />
          </div>
          <p className="text-sm font-semibold text-ds-text">Tudo em dia!</p>
          <p className="text-xs text-ds-light mt-0.5">Sem pendências no momento</p>
        </div>
      </div>

      {/* Auto-refresh indicator */}
      <p className="text-[10px] text-center text-ds-light pb-1">
        Atualização automática a cada 60 segundos
      </p>
    </div>
  )
}
