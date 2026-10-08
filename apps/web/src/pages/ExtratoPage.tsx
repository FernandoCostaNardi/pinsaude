import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ElementType } from 'react'
import { RefreshCw, Wallet, Clock, FileCheck, CheckCircle2, TrendingUp } from 'lucide-react'
import { Spinner, Alert } from '@pinsaude/ui'
import { portalApi, ExtratoPortal, ExtratoLancamento, ExtratoStatus } from '../api/portalApi'

// Atualiza sozinho para refletir novos lançamentos e mudanças de status (nota emitida, repasse).
const REFRESH_MS = 60_000

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro']

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const idx = parseInt(mes, 10) - 1
  return MESES[idx] ? `${MESES[idx]}/${ano}` : comp
}

function competenciaAtual(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const STATUS_CONFIG: Record<ExtratoStatus, { label: string; cls: string; icon: ElementType }> = {
  PROVISIONADO: { label: 'Provisionado', cls: 'bg-amber-50 text-amber-700 border-amber-200', icon: Clock },
  FATURADO:     { label: 'Faturado',     cls: 'bg-blue-50 text-blue-700 border-blue-200',    icon: FileCheck },
  PAGO:         { label: 'Pago',         cls: 'bg-green-50 text-green-700 border-green-200', icon: CheckCircle2 },
}

// ─── Componente principal ────────────────────────────────────────────────────

export function ExtratoPage() {
  const [extrato, setExtrato]       = useState<ExtratoPortal | null>(null)
  const [competencia, setCompetencia] = useState<string | null>(null)
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState<string | null>(null)

  const carregar = useCallback(async (comp: string | null, silencioso = false) => {
    if (!silencioso) setLoading(true)
    setError(null)
    try {
      let data = await portalApi.getExtrato(comp ? { competencia: comp } : {})
      // Primeira carga: abre no mês atual (ou no mais recente com lançamentos).
      if (!comp && data.competenciasDisponiveis.length > 0) {
        const atual = competenciaAtual()
        const inicial = data.competenciasDisponiveis.includes(atual) ? atual : data.competenciasDisponiveis[0]
        data = await portalApi.getExtrato({ competencia: inicial })
        setCompetencia(inicial)
      }
      setExtrato(data)
    } catch (e) {
      if (!silencioso) setError(e instanceof Error ? e.message : 'Erro ao carregar o extrato')
    } finally {
      if (!silencioso) setLoading(false)
    }
  }, [])

  useEffect(() => { carregar(null) }, [carregar])

  useEffect(() => {
    if (!competencia) return
    const timer = setInterval(() => { carregar(competencia, true) }, REFRESH_MS)
    return () => clearInterval(timer)
  }, [competencia, carregar])

  const handleCompetencia = (comp: string) => {
    setCompetencia(comp)
    carregar(comp)
  }

  const competencias = useMemo(() => {
    const lista = extrato?.competenciasDisponiveis ?? []
    return competencia && !lista.includes(competencia) ? [competencia, ...lista] : lista
  }, [extrato, competencia])

  const lancamentos = extrato?.lancamentos ?? []

  return (
    <div className="flex flex-col h-full -m-6">
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="px-6 pt-6 pb-4 bg-white border-b border-ds-border shrink-0">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-xl font-black text-ds-text">Extrato</h1>
            <p className="text-sm text-ds-light mt-0.5">
              O que você lançou em produção e frequência, e quanto tem previsto para receber
            </p>
          </div>
          <div className="flex items-center gap-2">
            {competencias.length > 0 && (
              <select
                value={competencia ?? ''}
                onChange={e => handleCompetencia(e.target.value)}
                className="h-8 rounded-lg border border-ds-border bg-white px-2 text-xs font-medium text-ds-text"
              >
                {competencias.map(c => (
                  <option key={c} value={c}>{formatCompetencia(c)}</option>
                ))}
              </select>
            )}
            <button
              onClick={() => carregar(competencia)}
              disabled={loading}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors shrink-0"
            >
              <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
              Atualizar
            </button>
          </div>
        </div>

        {!loading && extrato && lancamentos.length > 0 && (
          <div className="mt-4 grid grid-cols-2 lg:grid-cols-4 gap-3">
            <ResumoCard icon={TrendingUp} label="Previsto no mês" valor={extrato.totalPrevisto}
              cls="bg-primary-50 border-primary-100 text-primary-700" />
            <ResumoCard icon={Clock} label="Provisionado" valor={extrato.totalProvisionado}
              cls="bg-amber-50 border-amber-200 text-amber-700" />
            <ResumoCard icon={FileCheck} label="Faturado" valor={extrato.totalFaturado}
              cls="bg-blue-50 border-blue-200 text-blue-700" />
            <ResumoCard icon={CheckCircle2} label="Pago" valor={extrato.totalPago}
              cls="bg-green-50 border-green-200 text-green-700" />
          </div>
        )}
      </div>

      {/* ── Body ────────────────────────────────────────────────────────── */}
      <div className="flex-1 overflow-auto p-6">
        {error && (
          <Alert variant="error" onClose={() => setError(null)} className="mb-4">
            {error}
          </Alert>
        )}

        {loading ? (
          <div className="flex items-center justify-center h-48">
            <Spinner size="lg" />
          </div>
        ) : lancamentos.length === 0 ? (
          <EmptyState />
        ) : (
          <div className="flex flex-col gap-3">
            <LancamentosLista lancamentos={lancamentos} />
            <p className="text-[11px] text-ds-light">
              Provisionado: lançado por você, aguardando a nota fiscal. Faturado: nota fiscal emitida.
              Pago: repasse feito para sua conta.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Componentes ─────────────────────────────────────────────────────────────

function ResumoCard({ icon: Icon, label, valor, cls }: {
  icon: ElementType; label: string; valor: number; cls: string
}) {
  return (
    <div className={`rounded-xl border px-3 py-2.5 ${cls}`}>
      <div className="flex items-center gap-1.5">
        <Icon size={13} />
        <p className="text-[10px] font-bold uppercase tracking-wide">{label}</p>
      </div>
      <p className="text-base font-black tabular-nums mt-0.5">{formatBRL(valor)}</p>
    </div>
  )
}

function StatusBadge({ status }: { status: ExtratoStatus }) {
  const { label, cls, icon: Icon } = STATUS_CONFIG[status]
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[11px] font-semibold ${cls}`}>
      <Icon size={11} />
      {label}
    </span>
  )
}

function OrigemBadge({ origem }: { origem: ExtratoLancamento['origem'] }) {
  return origem === 'PRODUCAO'
    ? <span className="px-1.5 py-0.5 rounded bg-primary-50 text-primary-700 text-[10px] font-bold uppercase">Produção</span>
    : <span className="px-1.5 py-0.5 rounded bg-purple-50 text-purple-700 text-[10px] font-bold uppercase">Frequência</span>
}

function detalhe(l: ExtratoLancamento): string {
  const partes = [l.descricao]
  if (l.origem === 'FREQUENCIA') partes.push(`${l.quantidade} ${l.quantidade === 1 ? 'lançamento' : 'lançamentos'}`)
  if (l.numeroNota) partes.push(`NFS-e ${l.numeroNota}`)
  return partes.filter(Boolean).join(' · ')
}

function LancamentosLista({ lancamentos }: { lancamentos: ExtratoLancamento[] }) {
  return (
    <div className="rounded-xl border border-ds-border bg-white shadow-sm divide-y divide-ds-border">
      {lancamentos.map(l => (
        <div key={`${l.origem}-${l.id}`} className="flex flex-col sm:flex-row sm:items-center gap-2 px-4 py-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <OrigemBadge origem={l.origem} />
              <p className="text-sm font-semibold text-ds-text truncate">{l.tomadorNome ?? '—'}</p>
            </div>
            <p className="text-xs text-ds-light mt-0.5 truncate">{detalhe(l)}</p>
          </div>
          <div className="flex items-center justify-between sm:justify-end gap-3 shrink-0">
            <StatusBadge status={l.status} />
            <div className="text-right">
              <p className="text-sm font-black tabular-nums text-ds-text">{formatBRL(l.valorPrevisto)}</p>
              <p className="text-[10px] text-ds-light tabular-nums">bruto {formatBRL(l.valorBruto)}</p>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center min-h-64 py-16">
      <div className="w-16 h-16 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
        <Wallet size={28} className="text-primary" />
      </div>
      <p className="text-base font-bold text-ds-text">Nenhum lançamento neste mês</p>
      <p className="text-sm text-ds-light mt-1.5 text-center max-w-xs">
        Assim que você lançar uma produção ou frequência, ela aparece aqui com o valor previsto a receber.
      </p>
    </div>
  )
}
