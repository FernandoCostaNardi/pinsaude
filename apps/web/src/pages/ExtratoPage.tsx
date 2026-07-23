import { useCallback, useEffect, useState } from 'react'
import { Banknote, RefreshCw, ArrowUpRight, Wallet } from 'lucide-react'
import { Spinner, Alert } from '@pinsaude/ui'
import { portalApi, ExtratoPortal, ExtratoLancamento } from '../api/portalApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
}

// ─── Componente principal ────────────────────────────────────────────────────

export function ExtratoPage() {
  const [extrato, setExtrato] = useState<ExtratoPortal | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await portalApi.getExtrato({})
      setExtrato(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar repasses')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { carregar() }, [carregar])

  const repasses = extrato?.lancamentos ?? []
  const totalRepasses = extrato?.totalCreditos ?? 0

  return (
    <div className="flex flex-col h-full -m-6">
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="px-6 pt-6 pb-4 bg-white border-b border-ds-border shrink-0">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-xl font-black text-ds-text">Meus Repasses</h1>
            <p className="text-sm text-ds-light mt-0.5">
              Transferências efetuadas para sua conta bancária
            </p>
          </div>
          <button
            onClick={carregar}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-ds-border text-xs font-medium text-ds-mid hover:bg-ds-input disabled:opacity-40 transition-colors shrink-0"
          >
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
            Atualizar
          </button>
        </div>

        {/* Total acumulado — só exibe se houver dados */}
        {!loading && repasses.length > 0 && (
          <div className="mt-4 inline-flex items-center gap-3 bg-green-50 border border-green-200 rounded-xl px-4 py-3">
            <div className="w-8 h-8 rounded-lg bg-green-100 flex items-center justify-center shrink-0">
              <Banknote size={15} className="text-green-700" />
            </div>
            <div>
              <p className="text-[10px] font-bold text-green-700 uppercase tracking-wide">Total recebido</p>
              <p className="text-lg font-black text-green-800 tabular-nums">{formatBRL(totalRepasses)}</p>
            </div>
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
        ) : repasses.length === 0 ? (
          <EmptyState />
        ) : (
          <RepassesTable repasses={repasses} totalRepasses={totalRepasses} />
        )}
      </div>
    </div>
  )
}

// ─── Estado vazio ─────────────────────────────────────────────────────────────

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center min-h-64 py-16">
      <div className="w-16 h-16 rounded-2xl bg-primary-50 flex items-center justify-center mb-4">
        <Wallet size={28} className="text-primary" />
      </div>
      <p className="text-base font-bold text-ds-text">Nenhum repasse registrado</p>
      <p className="text-sm text-ds-light mt-1.5 text-center max-w-xs">
        Seus repasses aparecerão aqui após serem processados e liquidados pelo financeiro da Pin Saúde.
      </p>
    </div>
  )
}

// ─── Tabela de repasses ───────────────────────────────────────────────────────

function RepassesTable({ repasses, totalRepasses }: {
  repasses: ExtratoLancamento[]
  totalRepasses: number
}) {
  return (
    <div className="overflow-x-auto rounded-xl border border-ds-border bg-white shadow-sm">
      <table className="w-full text-sm min-w-[480px]">
        <thead>
          <tr className="bg-ds-surface border-b border-ds-border">
            {[
              { label: 'Data',           cls: 'text-left'  },
              { label: 'Descrição',      cls: 'text-left'  },
              { label: 'Competência',    cls: 'text-center hidden sm:table-cell' },
              { label: 'Valor',          cls: 'text-right text-green-700' },
            ].map(({ label, cls }) => (
              <th
                key={label}
                className={`px-4 py-3 text-[10px] font-bold uppercase tracking-wider text-ds-light ${cls}`}
              >
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-ds-border">
          {repasses.map((r, i) => (
            <RepasseRow key={i} repasse={r} />
          ))}
        </tbody>
        <tfoot className="border-t-2 border-ds-border">
          <tr className="bg-ds-surface">
            <td colSpan={2} className="px-4 py-3 text-xs font-bold text-ds-text">
              Total ({repasses.length} repasse{repasses.length !== 1 ? 's' : ''})
            </td>
            <td className="hidden sm:table-cell" />
            <td className="px-4 py-3 text-right text-xs font-black tabular-nums text-green-700">
              {formatBRL(totalRepasses)}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

function RepasseRow({ repasse: r }: { repasse: ExtratoLancamento }) {
  return (
    <tr className="hover:bg-ds-surface/50 transition-colors">
      <td className="px-4 py-3 whitespace-nowrap">
        <p className="text-xs font-semibold text-ds-text">{formatDate(r.dataRef)}</p>
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <ArrowUpRight size={13} className="shrink-0 text-green-600" />
          <span className="text-xs text-ds-text">{r.descricao}</span>
        </div>
      </td>
      <td className="px-4 py-3 text-center hidden sm:table-cell">
        <span className="text-xs text-ds-mid">{r.competencia}</span>
      </td>
      <td className="px-4 py-3 text-right">
        <span className="text-xs font-bold tabular-nums text-green-700">
          {formatBRL(r.valor)}
        </span>
      </td>
    </tr>
  )
}
