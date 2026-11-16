import {
  ROTULO_TRIBUTO, totalTributos, type TipoTributo, type ValoresTributo,
} from '../fiscal/tributos'

// CBS-20 — linhas de tributo de uma nota ou prévia. A tela decide QUAIS tributos mostrar
// (useRegimeFiscal para prévia, tributosDaNota para nota emitida); o componente só desenha.

export type SituacaoTributo = 'RETIDO' | 'PRESTADORA'

export interface LinhasTributoProps {
  /** Tributos visíveis, na ordem de exibição */
  tributos: readonly TipoTributo[]
  /** Valores em centavos */
  valores: ValoresTributo
  /** Alíquotas em percentual (5 = 5%). Sem a chave, a linha sai sem alíquota. */
  aliquotasPct?: Partial<Record<TipoTributo, number | null>>
  /** Selo RETIDO (pelo tomador) / PRESTADORA (Pin recolhe). Sem a chave, sem selo. */
  situacao?: Partial<Record<TipoTributo, SituacaoTributo>>
  /** Esconde tributos com valor 0 (útil em listas de retenção) */
  ocultarZerados?: boolean
  /** Linha final com a soma dos tributos mostrados */
  mostrarTotal?: boolean
  rotuloTotal?: string
  /** Valores com sinal de menos e em vermelho (descontos) */
  negativo?: boolean
  className?: string
}

const brl = (centavos: number) =>
  (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

const pct = (v: number) =>
  `${v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}%`

const SELO: Record<SituacaoTributo, string> = {
  RETIDO: 'bg-orange-100 text-orange-700',
  PRESTADORA: 'bg-blue-50 text-blue-600',
}

export default function LinhasTributo({
  tributos, valores, aliquotasPct, situacao, ocultarZerados, mostrarTotal,
  rotuloTotal = 'Total de tributos', negativo, className,
}: LinhasTributoProps) {
  const linhas = tributos.filter(t => !ocultarZerados || (valores[t] ?? 0) !== 0)
  const total = totalTributos(linhas, valores)
  const valorCls = negativo ? 'text-red-600' : 'text-gray-700'
  const fmt = (v: number) => (negativo && v > 0 ? `− ${brl(v)}` : brl(v))

  return (
    <div className={`space-y-1.5 ${className ?? ''}`} data-testid="linhas-tributo">
      {linhas.map(t => {
        const aliq = aliquotasPct?.[t]
        const sel = situacao?.[t]
        return (
          <div key={t} className="flex justify-between gap-2" data-tributo={t}>
            <span>
              {ROTULO_TRIBUTO[t]}
              {aliq != null && ` (${pct(Number(aliq))})`}
              {sel && (
                <>
                  {' '}
                  <span className={`px-1 rounded text-[8px] font-bold ${SELO[sel]}`}>{sel}</span>
                </>
              )}
            </span>
            <span className={`tabular-nums ${valorCls}`}>{fmt(valores[t] ?? 0)}</span>
          </div>
        )
      })}
      {mostrarTotal && (
        <div className="flex justify-between gap-2 border-t border-gray-200 pt-1.5 font-semibold" data-tributo="TOTAL">
          <span>{rotuloTotal}</span>
          <span className={`tabular-nums ${valorCls}`}>{fmt(total)}</span>
        </div>
      )}
    </div>
  )
}
