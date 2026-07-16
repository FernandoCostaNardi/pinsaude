import { useEffect, useState, useCallback } from 'react'
import {
  Wallet, TrendingUp, ArrowDownCircle, BarChart2,
  RefreshCw, AlertTriangle, Loader2, Clock,
} from 'lucide-react'
import { getPosicaoCaixa, type PosicaoCaixaResponse } from '../api/conciliacaoApi'

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function diasEmAbertoClass(dias: number): string {
  if (dias > 30) return 'text-red-600 font-bold'
  if (dias > 15) return 'text-yellow-600 font-semibold'
  return 'text-ds-mid'
}

// ─── KPI Card ─────────────────────────────────────────────────────────────────

interface KpiCardProps {
  label: string
  value: number
  icon: React.ElementType
  color: string
  sub?: string
}

function KpiCard({ label, value, icon: Icon, color, sub }: KpiCardProps) {
  return (
    <div className="bg-white rounded-xl border border-ds-border p-5 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-ds-mid">{label}</span>
        <div className={`p-2 rounded-lg ${color}`}>
          <Icon size={18} className="text-white" />
        </div>
      </div>
      <p className="text-2xl font-bold text-ds-text">{formatBRL(value)}</p>
      {sub && <p className="text-xs text-ds-light">{sub}</p>}
    </div>
  )
}

// ─── Gráfico de barras simples (SVG-free, CSS divs) ──────────────────────────

interface BarChartProps {
  data: PosicaoCaixaResponse['recebimentosPorSemana']
}

function BarChart({ data }: BarChartProps) {
  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center h-40 text-ds-light text-sm">
        Sem recebimentos conciliados nos últimos 3 meses
      </div>
    )
  }

  const max = Math.max(...data.map(d => d.valor), 1)

  return (
    <div className="flex items-end gap-1.5 h-40 px-1">
      {data.map((d) => {
        const pct = Math.max((d.valor / max) * 100, 2)
        return (
          <div key={d.semanaKey} className="flex-1 flex flex-col items-center gap-1 group relative">
            {/* Tooltip */}
            <div className="absolute bottom-full mb-2 left-1/2 -translate-x-1/2 bg-ds-text text-white text-[10px] rounded px-2 py-1 whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10">
              {formatBRL(d.valor)}
            </div>
            {/* Barra */}
            <div className="w-full flex-1 flex items-end">
              <div
                className="w-full bg-primary hover:bg-primary-700 rounded-t transition-colors"
                style={{ height: `${pct}%` }}
              />
            </div>
            {/* Label */}
            <span className="text-[9px] text-ds-light text-center leading-tight">{d.semanaLabel}</span>
          </div>
        )
      })}
    </div>
  )
}

// ─── Página ───────────────────────────────────────────────────────────────────

export function PosicaoCaixaPage() {
  const [dados, setDados] = useState<PosicaoCaixaResponse | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      const resp = await getPosicaoCaixa()
      setDados(resp)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao carregar posição de caixa')
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => { carregar() }, [carregar])

  return (
    <div className="flex-1 overflow-auto p-5 space-y-5">

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-ds-text">Posição de Caixa</h1>
          <p className="text-sm text-ds-light mt-0.5">Visão financeira em tempo real da operação</p>
        </div>
        <button
          onClick={carregar}
          disabled={carregando}
          className="flex items-center gap-2 px-3 py-2 text-sm text-ds-mid border border-ds-border rounded-lg hover:bg-ds-input transition-colors disabled:opacity-50"
        >
          <RefreshCw size={14} className={carregando ? 'animate-spin' : ''} />
          Atualizar
        </button>
      </div>

      {/* Estado de carregamento */}
      {carregando && (
        <div className="flex justify-center py-20">
          <Loader2 className="animate-spin text-primary" size={32} />
        </div>
      )}

      {/* Erro */}
      {erro && !carregando && (
        <div className="flex items-center gap-3 p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 text-sm">
          <AlertTriangle size={18} className="shrink-0" />
          {erro}
        </div>
      )}

      {/* Conteúdo */}
      {dados && !carregando && (
        <>
          {/* KPI Cards */}
          <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
            <KpiCard
              label="A Receber"
              value={dados.aReceber}
              icon={Wallet}
              color="bg-blue-500"
              sub={`${dados.notasEmAberto.length} nota(s) em aberto`}
            />
            <KpiCard
              label="Recebido Não Repassado"
              value={dados.recebidoNaoRepassado}
              icon={TrendingUp}
              color="bg-green-500"
              sub="Aguardando liquidação EPIC-09"
            />
            <KpiCard
              label="Repassado no Mês"
              value={dados.repassadoNoMes}
              icon={ArrowDownCircle}
              color="bg-purple-500"
              sub="Disponível após EPIC-09"
            />
            <KpiCard
              label="Saldo Estimado"
              value={dados.saldoEstimado}
              icon={BarChart2}
              color={dados.saldoEstimado >= 0 ? 'bg-emerald-500' : 'bg-red-500'}
              sub="Recebido − Repassado"
            />
          </div>

          {/* Gráfico de recebimentos */}
          <div className="bg-white rounded-xl border border-ds-border p-5">
            <div className="flex items-center gap-2 mb-4">
              <BarChart2 size={16} className="text-primary" />
              <h2 className="font-semibold text-ds-text text-sm">Recebimentos Conciliados por Semana</h2>
              <span className="ml-auto text-xs text-ds-light">Últimos 3 meses</span>
            </div>
            <BarChart data={dados.recebimentosPorSemana} />
          </div>

          {/* Tabela de notas em aberto */}
          <div className="bg-white rounded-xl border border-ds-border">
            <div className="px-5 py-4 border-b border-ds-border flex items-center gap-2">
              <Clock size={16} className="text-primary" />
              <h2 className="font-semibold text-ds-text text-sm">Notas Emitidas Não Recebidas</h2>
              {dados.notasEmAberto.length > 0 && (
                <span className="ml-auto bg-ds-surface text-ds-mid text-xs px-2 py-0.5 rounded-full font-medium">
                  {dados.notasEmAberto.length}
                </span>
              )}
            </div>

            {dados.notasEmAberto.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-ds-light gap-2">
                <TrendingUp size={28} className="text-green-400" />
                <p className="text-sm font-medium">Todas as notas emitidas foram recebidas!</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-ds-border bg-ds-surface/50">
                      <th className="text-left px-5 py-3 font-medium text-ds-light">Tomador</th>
                      <th className="text-right px-5 py-3 font-medium text-ds-light">Valor Bruto</th>
                      <th className="text-center px-5 py-3 font-medium text-ds-light">Data Emissão</th>
                      <th className="text-center px-5 py-3 font-medium text-ds-light">Dias em Aberto</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dados.notasEmAberto.map((nota) => (
                      <tr
                        key={nota.producaoId}
                        className={`border-b border-ds-border last:border-0 hover:bg-ds-surface/30 transition-colors ${
                          nota.diasEmAberto > 30 ? 'bg-red-50/50' : ''
                        }`}
                      >
                        <td className="px-5 py-3 font-medium text-ds-text">
                          {nota.tomadorNome || '—'}
                        </td>
                        <td className="px-5 py-3 text-right font-mono text-ds-text">
                          {formatBRL(nota.valorBruto)}
                        </td>
                        <td className="px-5 py-3 text-center text-ds-mid">
                          {nota.dataReferencia
                            ? new Date(nota.dataReferencia + 'T00:00:00').toLocaleDateString('pt-BR')
                            : '—'}
                        </td>
                        <td className="px-5 py-3 text-center">
                          <span className={`inline-flex items-center gap-1 ${diasEmAbertoClass(nota.diasEmAberto)}`}>
                            {nota.diasEmAberto > 30 && <AlertTriangle size={12} />}
                            {nota.diasEmAberto}d
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
