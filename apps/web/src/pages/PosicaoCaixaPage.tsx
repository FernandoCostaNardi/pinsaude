import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Landmark, Stethoscope, Building2, RefreshCw, AlertTriangle, Loader2,
  Search, ChevronRight, Info, CalendarDays, Receipt,
} from 'lucide-react'
import { Button } from '@pinsaude/ui'
import {
  getPosicaoCaixa, type PosicaoCaixaResponse, type RecebimentoMes,
} from '../api/conciliacaoApi'

const PAGE_SIZE = 10
const MESES_CURTOS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
const MESES_LONGOS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho',
  'agosto', 'setembro', 'outubro', 'novembro', 'dezembro']

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** R$ 9,4 mil / R$ 1,2 mi — para rótulos curtos do gráfico. */
function formatBRLCurto(centavos: number): string {
  const reais = centavos / 100
  if (reais >= 1_000_000) return `R$ ${(reais / 1_000_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`
  if (reais >= 1_000) return `R$ ${(reais / 1_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil`
  return formatBRL(centavos)
}

function formatData(iso: string): string {
  return new Date(iso + 'T00:00:00').toLocaleDateString('pt-BR')
}

function mesCurto(ym: string): string {
  const [a, m] = ym.split('-')
  return `${MESES_CURTOS[Number(m) - 1]}/${a.slice(2)}`
}

function mesLongo(ym: string): string {
  const [a, m] = ym.split('-')
  return `${MESES_LONGOS[Number(m) - 1]} de ${a}`
}

function mesAtual(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** Últimos 12 meses, do mais recente para o mais antigo. */
function ultimosMeses(qtd = 12): string[] {
  const d = new Date()
  const out: string[] = []
  for (let i = 0; i < qtd; i++) {
    const x = new Date(d.getFullYear(), d.getMonth() - i, 1)
    out.push(`${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}`)
  }
  return out
}

function pluralNotas(n: number): string {
  return n === 1 ? '1 nota' : `${n} notas`
}

// ─── Cards do fluxo do dinheiro ──────────────────────────────────────────────

interface FluxoCardProps {
  titulo: string
  valor: number
  icon: React.ElementType
  tom: 'azul' | 'teal' | 'verde'
  explicacao: string
  detalhe?: React.ReactNode
}

const TONS: Record<FluxoCardProps['tom'], { icone: string; valor: string; borda: string }> = {
  azul:  { icone: 'bg-primary-50 text-primary-700', valor: 'text-ds-text',   borda: 'border-ds-border' },
  teal:  { icone: 'bg-teal-50 text-teal-700',       valor: 'text-teal-700',  borda: 'border-ds-border' },
  verde: { icone: 'bg-green-50 text-green-700',     valor: 'text-green-700', borda: 'border-ds-border' },
}

function FluxoCard({ titulo, valor, icon: Icon, tom, explicacao, detalhe }: FluxoCardProps) {
  const t = TONS[tom]
  return (
    <div className={`bg-white rounded-xl border ${t.borda} p-5 flex flex-col gap-2 min-w-0`}>
      <div className="flex items-center gap-2.5">
        <div className={`p-2 rounded-lg ${t.icone}`}>
          <Icon size={18} />
        </div>
        <span className="text-sm font-semibold text-ds-text">{titulo}</span>
      </div>
      <p className={`text-2xl sm:text-3xl font-bold tabular-nums ${t.valor}`}>{formatBRL(valor)}</p>
      <p className="text-xs text-ds-mid leading-relaxed">{explicacao}</p>
      {detalhe && <div className="text-xs text-ds-light pt-1 border-t border-ds-border mt-1">{detalhe}</div>}
    </div>
  )
}

function SetaFluxo() {
  return (
    <div className="hidden lg:flex items-center justify-center text-ds-light" aria-hidden>
      <ChevronRight size={22} />
    </div>
  )
}

// ─── Gráfico mensal ───────────────────────────────────────────────────────────

interface GraficoMensalProps {
  meses: RecebimentoMes[]
  selecionado: string
  onSelecionar: (mes: string) => void
}

function GraficoMensal({ meses, selecionado, onSelecionar }: GraficoMensalProps) {
  const max = Math.max(...meses.map(m => m.valor), 1)
  const vazio = meses.every(m => m.valor === 0)

  return (
    <div className="bg-white rounded-xl border border-ds-border p-5">
      <div className="flex items-center gap-2 mb-1">
        <CalendarDays size={16} className="text-primary" />
        <h2 className="font-semibold text-ds-text text-sm">Quanto entrou por mês</h2>
      </div>
      <p className="text-xs text-ds-light mb-4">Últimos 6 meses, pela data do depósito. Clique num mês para ver só ele.</p>

      {vazio ? (
        <div className="flex items-center justify-center h-36 text-ds-light text-sm">
          Nenhum recebimento conciliado nos últimos 6 meses
        </div>
      ) : (
        <div className="flex items-end gap-2 sm:gap-4 h-44">
          {meses.map(m => {
            const pct = m.valor > 0 ? Math.max((m.valor / max) * 100, 4) : 0
            const ativo = selecionado === m.mes
            return (
              <button
                key={m.mes}
                type="button"
                onClick={() => onSelecionar(ativo ? '' : m.mes)}
                className="flex-1 h-full flex flex-col items-center gap-1 group min-w-0"
                title={`${mesLongo(m.mes)}: ${formatBRL(m.valor)} em ${pluralNotas(m.quantidade)}`}
              >
                <span className={`h-4 text-[10px] sm:text-[11px] tabular-nums whitespace-nowrap ${
                  ativo ? 'text-primary-700 font-semibold' : 'text-ds-mid invisible sm:visible'}`}>
                  {m.valor > 0 ? formatBRLCurto(m.valor) : '—'}
                </span>
                <div className="w-full flex-1 flex items-end">
                  <div
                    className={`w-full rounded-t transition-colors ${
                      ativo ? 'bg-primary-700' : 'bg-primary/70 group-hover:bg-primary'}`}
                    style={{ height: `${pct}%` }}
                  />
                </div>
                <span className={`text-[11px] ${ativo ? 'text-primary-700 font-semibold' : 'text-ds-light'}`}>
                  {mesCurto(m.mes)}
                </span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ─── Página ───────────────────────────────────────────────────────────────────

export function PosicaoCaixaPage() {
  const [mes, setMes] = useState<string>(mesAtual())
  const [busca, setBusca] = useState('')
  const [buscaAplicada, setBuscaAplicada] = useState('')
  const [page, setPage] = useState(0)
  const [recarregar, setRecarregar] = useState(0)

  const [dados, setDados] = useState<PosicaoCaixaResponse | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const ultimaReq = useRef(0)

  const opcoesMes = useMemo(() => ultimosMeses(12), [])

  // Busca por tomador com debounce
  useEffect(() => {
    const t = setTimeout(() => setBuscaAplicada(busca), 400)
    return () => clearTimeout(t)
  }, [busca])

  useEffect(() => { setPage(0) }, [mes, buscaAplicada])

  useEffect(() => {
    const id = ++ultimaReq.current
    setCarregando(true)
    setErro(null)
    getPosicaoCaixa({ mes, q: buscaAplicada, page, size: PAGE_SIZE })
      .then(resp => { if (id === ultimaReq.current) setDados(resp) })
      .catch((e: unknown) => {
        if (id === ultimaReq.current) setErro(e instanceof Error ? e.message : 'Erro ao carregar posição de caixa')
      })
      .finally(() => { if (id === ultimaReq.current) setCarregando(false) })
  }, [mes, buscaAplicada, page, recarregar])

  const periodoTexto = mes ? mesLongo(mes) : 'todos os períodos'
  const r = dados?.resumo
  const pagina = dados?.recebimentos
  const inicio = pagina && pagina.totalElements > 0 ? pagina.page * pagina.size + 1 : 0
  const fim = pagina ? Math.min((pagina.page + 1) * pagina.size, pagina.totalElements) : 0

  return (
    <div className="flex-1 overflow-auto p-4 sm:p-5 space-y-5">

      {/* Cabeçalho + filtro de período */}
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-ds-text">Posição de Caixa</h1>
          <p className="text-sm text-ds-light mt-0.5">
            Dinheiro que já entrou na conta: créditos do extrato conciliados com uma nota.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={mes}
            onChange={e => setMes(e.target.value)}
            className="h-9 px-3 text-sm border border-ds-border rounded-lg bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30"
            aria-label="Período"
          >
            {opcoesMes.map(m => (
              <option key={m} value={m}>{mesLongo(m).replace(/^./, c => c.toUpperCase())}</option>
            ))}
            <option value="">Todos os períodos</option>
          </select>
          <button
            onClick={() => setRecarregar(n => n + 1)}
            disabled={carregando}
            className="h-9 flex items-center gap-2 px-3 text-sm text-ds-mid border border-ds-border rounded-lg bg-white hover:bg-ds-input transition-colors disabled:opacity-50"
            title="Atualizar"
          >
            <RefreshCw size={14} className={carregando ? 'animate-spin' : ''} />
            <span className="hidden sm:inline">Atualizar</span>
          </button>
        </div>
      </div>

      {erro && (
        <div className="flex items-center gap-3 p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 text-sm">
          <AlertTriangle size={18} className="shrink-0" />
          {erro}
        </div>
      )}

      {!dados && carregando && (
        <div className="flex justify-center py-20">
          <Loader2 className="animate-spin text-primary" size={32} />
        </div>
      )}

      {dados && r && pagina && (
        <>
          {/* Fluxo: entrou → médicos → Pin */}
          <div>
            <p className="text-xs font-medium text-ds-mid uppercase tracking-wide mb-2">
              Resumo de {periodoTexto}{buscaAplicada.trim() && ` · tomador contém “${buscaAplicada.trim()}”`}
            </p>
            <div className="grid grid-cols-1 lg:grid-cols-[1fr_auto_1fr_auto_1fr] gap-3 lg:gap-2">
              <FluxoCard
                titulo="Entrou no banco"
                valor={r.recebido}
                icon={Landmark}
                tom="azul"
                explicacao={r.quantidade === 0
                  ? 'Nenhum pagamento conciliado neste período.'
                  : `${pluralNotas(r.quantidade)} paga${r.quantidade === 1 ? '' : 's'} pelos tomadores.`}
                detalhe={r.quantidade > 0 && (
                  <>Notas somam {formatBRL(r.valorNotas)}; o tomador reteve {formatBRL(r.retencoes)} de impostos.</>
                )}
              />
              <SetaFluxo />
              <FluxoCard
                titulo="Parte dos médicos"
                valor={r.aRepassar}
                icon={Stethoscope}
                tom="teal"
                explicacao="O que precisa ser repassado aos médicos (valor da nota menos a taxa Pin)."
                detalhe={<>Repassado até agora: {formatBRL(r.repassado)}. O repasse ainda não está no sistema.</>}
              />
              <SetaFluxo />
              <FluxoCard
                titulo="Fica com a Pin"
                valor={r.resultadoPin}
                icon={Building2}
                tom={r.resultadoPin >= 0 ? 'verde' : 'azul'}
                explicacao="O que sobra depois de separar a parte dos médicos. Os impostos retidos já saíram."
              />
            </div>
          </div>

          {/* Lembrete do que ainda não entrou */}
          {dados.notasAguardandoPagamento > 0 && (
            <div className="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl text-sm">
              <Info size={16} className="text-amber-700 shrink-0 hidden sm:block" />
              <p className="text-amber-900 flex-1">
                Ainda faltam entrar <strong>{formatBRL(dados.aReceber)}</strong> de{' '}
                {pluralNotas(dados.notasAguardandoPagamento)} emitida{dados.notasAguardandoPagamento === 1 ? '' : 's'}.
                {' '}Elas aparecem aqui quando o pagamento for conciliado.
              </p>
              <Link to="/conciliacao/assistida" className="text-amber-800 font-medium hover:underline whitespace-nowrap">
                Ir para Conciliação →
              </Link>
            </div>
          )}

          <GraficoMensal meses={dados.recebimentosPorMes} selecionado={mes} onSelecionar={setMes} />

          {/* Recebimentos */}
          <div className="bg-white rounded-xl border border-ds-border">
            <div className="px-4 sm:px-5 py-4 border-b border-ds-border flex flex-col sm:flex-row sm:items-center gap-3">
              <div className="flex items-center gap-2">
                <Receipt size={16} className="text-primary" />
                <h2 className="font-semibold text-ds-text text-sm">Pagamentos recebidos</h2>
                {carregando && <Loader2 size={14} className="animate-spin text-ds-light" />}
              </div>
              <div className="relative sm:ml-auto sm:w-72">
                <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ds-light" />
                <input
                  value={busca}
                  onChange={e => setBusca(e.target.value)}
                  placeholder="Buscar tomador"
                  className="w-full h-9 pl-8 pr-3 text-sm border border-ds-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>
            </div>

            {pagina.content.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-ds-light gap-2 px-4 text-center">
                <Landmark size={26} className="text-ds-border" />
                <p className="text-sm">
                  {buscaAplicada.trim()
                    ? 'Nenhum pagamento encontrado para essa busca.'
                    : `Nenhum pagamento conciliado em ${periodoTexto}.`}
                </p>
              </div>
            ) : (
              <>
                {/* Celular: cartões */}
                <ul className="md:hidden divide-y divide-ds-border">
                  {pagina.content.map(rec => (
                    <li key={rec.producaoId} className="px-4 py-3 space-y-2">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="font-medium text-ds-text truncate">{rec.tomadorNome || '—'}</p>
                          <p className="text-[11px] text-ds-light">
                            Depósito em {formatData(rec.recebidoEm)} · competência {mesCurto(rec.competencia)}
                          </p>
                        </div>
                        <p className="font-semibold text-ds-text tabular-nums whitespace-nowrap">{formatBRL(rec.valorRecebido)}</p>
                      </div>
                      <div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                        <span className="text-ds-light">Valor da nota</span>
                        <span className="text-right tabular-nums text-ds-mid">{formatBRL(rec.valorNota)}</span>
                        <span className="text-ds-light">Impostos retidos</span>
                        <span className="text-right tabular-nums text-ds-mid">{formatBRL(rec.retencoes)}</span>
                        <span className="text-ds-light">Médicos</span>
                        <span className="text-right tabular-nums text-teal-700 font-medium">{formatBRL(rec.parteMedicos)}</span>
                        <span className="text-ds-light">Pin</span>
                        <span className="text-right tabular-nums text-green-700 font-medium">{formatBRL(rec.resultadoPin)}</span>
                      </div>
                      <StatusRepasse />
                    </li>
                  ))}
                </ul>

                {/* Desktop: tabela */}
                <div className="hidden md:block overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-ds-border bg-ds-input text-xs">
                        <th className="text-left px-5 py-3 font-medium text-ds-light">Depósito</th>
                        <th className="text-left px-3 py-3 font-medium text-ds-light">Tomador</th>
                        <th className="text-right px-3 py-3 font-medium text-ds-light">Valor da nota</th>
                        <th className="text-right px-3 py-3 font-medium text-ds-light">Impostos retidos</th>
                        <th className="text-right px-3 py-3 font-medium text-ds-light">Entrou no banco</th>
                        <th className="text-right px-3 py-3 font-medium text-ds-light">Médicos</th>
                        <th className="text-right px-3 py-3 font-medium text-ds-light">Pin</th>
                        <th className="text-center px-5 py-3 font-medium text-ds-light">Repasse</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pagina.content.map(rec => (
                        <tr key={rec.producaoId} className="border-b border-ds-border last:border-0 hover:bg-ds-hover transition-colors">
                          <td className="px-5 py-3 text-ds-mid whitespace-nowrap">{formatData(rec.recebidoEm)}</td>
                          <td className="px-3 py-3">
                            <p className="font-medium text-ds-text">{rec.tomadorNome || '—'}</p>
                            <p className="text-[11px] text-ds-light">
                              Competência {mesCurto(rec.competencia)} · {rec.qtdMedicos > 1 ? `${rec.qtdMedicos} médicos` : '1 médico'}
                            </p>
                          </td>
                          <td className="px-3 py-3 text-right tabular-nums text-ds-mid">{formatBRL(rec.valorNota)}</td>
                          <td className="px-3 py-3 text-right tabular-nums text-ds-mid">{formatBRL(rec.retencoes)}</td>
                          <td className="px-3 py-3 text-right tabular-nums font-semibold text-ds-text">{formatBRL(rec.valorRecebido)}</td>
                          <td className="px-3 py-3 text-right tabular-nums text-teal-700">{formatBRL(rec.parteMedicos)}</td>
                          <td className="px-3 py-3 text-right tabular-nums text-green-700">{formatBRL(rec.resultadoPin)}</td>
                          <td className="px-5 py-3 text-center"><StatusRepasse /></td>
                        </tr>
                      ))}
                    </tbody>
                    {pagina.totalPages > 1 && (
                      <tfoot>
                        <tr className="border-t border-ds-border bg-ds-input text-xs font-semibold">
                          <td className="px-5 py-2.5 text-ds-mid" colSpan={2}>Total do período ({pluralNotas(r.quantidade)})</td>
                          <td className="px-3 py-2.5 text-right tabular-nums text-ds-mid">{formatBRL(r.valorNotas)}</td>
                          <td className="px-3 py-2.5 text-right tabular-nums text-ds-mid">{formatBRL(r.retencoes)}</td>
                          <td className="px-3 py-2.5 text-right tabular-nums text-ds-text">{formatBRL(r.recebido)}</td>
                          <td className="px-3 py-2.5 text-right tabular-nums text-teal-700">{formatBRL(r.parteMedicos)}</td>
                          <td className="px-3 py-2.5 text-right tabular-nums text-green-700">{formatBRL(r.resultadoPin)}</td>
                          <td />
                        </tr>
                      </tfoot>
                    )}
                  </table>
                </div>

                {/* Paginação */}
                <div className="flex flex-col sm:flex-row items-center justify-between gap-2 px-4 sm:px-5 py-3 border-t border-ds-border text-sm">
                  <span className="text-ds-light text-xs">
                    Mostrando {inicio}–{fim} de {pagina.totalElements}
                  </span>
                  {pagina.totalPages > 1 && (
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="sm" disabled={pagina.page === 0 || carregando} onClick={() => setPage(p => p - 1)}>
                        Anterior
                      </Button>
                      <span className="px-2 text-ds-mid font-medium">{pagina.page + 1} / {pagina.totalPages}</span>
                      <Button variant="ghost" size="sm" disabled={pagina.page >= pagina.totalPages - 1 || carregando} onClick={() => setPage(p => p + 1)}>
                        Próximo
                      </Button>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        </>
      )}
    </div>
  )
}

/** Repasse (EPIC-09) ainda não existe: todo recebimento está aguardando. */
function StatusRepasse() {
  return (
    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
      Aguardando repasse
    </span>
  )
}
