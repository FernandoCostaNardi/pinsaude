import { useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  AlertTriangle, CheckCircle2, ChevronDown, ChevronUp, Clock, Pencil, Power, PowerOff,
  Settings2, X, Zap,
} from 'lucide-react'
import { Modal } from '@pinsaude/ui'
import {
  CronogramaReformaAno, ParametroFiscal, parametrosFiscaisApi,
} from '../api/parametrosFiscaisApi'
import { Empresa, empresasApi } from '../api/empresasApi'
import { useAuth } from '../auth/useAuth'

// CBS-23 — aba "Reforma tributária" da Configuração Fiscal: chave CBS/IBS da empresa
// (ligar a partir de uma competência, desligar, homologar) e cronograma 2026–2033.

const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']
const MESES_LONGOS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto',
  'setembro', 'outubro', 'novembro', 'dezembro']

const fmtComp = (comp: string) => {
  const [y, m] = comp.split('-')
  return `${MESES[+m - 1]}/${y}`
}
const fmtCompLonga = (comp: string) => {
  const [y, m] = comp.split('-')
  return `${MESES_LONGOS[+m - 1]} de ${y}`
}
const fmtPct = (v: number | null | undefined) =>
  v == null ? '—' : `${(Number(v) * 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}%`
const fmtDataHora = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
const soDigitos = (s: string | null | undefined) => (s ?? '').replace(/\D/g, '')

function competenciaAtual(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** Competências do mês atual até dez/2033, para ligar a chave. */
function competenciasFuturas(): string[] {
  const out: string[] = []
  const d = new Date()
  d.setDate(1)
  while (d.getFullYear() <= 2033) {
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
    d.setMonth(d.getMonth() + 1)
  }
  return out
}

/** Sugestão: janeiro de 2027 (início da CBS) enquanto não chega; depois, o próximo mês. */
function competenciaSugerida(): string {
  const atual = competenciaAtual()
  if (atual < '2027-01') return '2027-01'
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() + 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function erroMsg(e: unknown, padrao: string) {
  return e instanceof Error && e.message ? e.message : padrao
}

// ─── O que muda ──────────────────────────────────────────────────────────────

function OQueMuda() {
  return (
    <div className="grid gap-3 sm:grid-cols-3 text-sm">
      <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-red-700">Deixam de incidir</p>
        <p className="mt-1 font-semibold text-red-800">PIS e COFINS</p>
        <p className="mt-1 text-xs text-red-700">Extintos a partir de 2027, substituídos pela CBS.</p>
      </div>
      <div className="rounded-lg border border-violet-200 bg-violet-50 px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-violet-700">Passam a incidir</p>
        <p className="mt-1 font-semibold text-violet-800">CBS e IBS</p>
        <p className="mt-1 text-xs text-violet-700">
          Alíquotas do cronograma abaixo, com a redução do serviço (60% para saúde). Débito da Pin, não são retidos pelo tomador.
        </p>
      </div>
      <div className="rounded-lg border border-ds-border bg-white px-4 py-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-ds-mid">Continuam</p>
        <p className="mt-1 font-semibold text-ds-nav">ISS, IR e CSLL</p>
        <p className="mt-1 text-xs text-ds-light">
          IR e CSLL não mudam. O ISS cai de 2029 a 2032 (fator do cronograma) e acaba em 2033.
        </p>
      </div>
    </div>
  )
}

// ─── Card da chave ───────────────────────────────────────────────────────────

function CardChave({
  chave, podeAlterar, onLigar, onDesligar, onHomologar,
}: {
  chave: ParametroFiscal | null
  podeAlterar: boolean
  onLigar: () => void
  onDesligar: (p: ParametroFiscal) => void
  onHomologar: (p: ParametroFiscal) => void
}) {
  const ligada = chave != null
  const homologada = chave?.homologado ?? false
  const atual = competenciaAtual()
  const jaVale = ligada && homologada && chave.competenciaInicio <= atual

  return (
    <div className={[
      'rounded-xl border bg-white shadow-sm overflow-hidden',
      ligada ? (homologada ? 'border-violet-300' : 'border-amber-300') : 'border-ds-border',
    ].join(' ')}>
      <div className="flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center">
        <div className={[
          'flex h-11 w-11 shrink-0 items-center justify-center rounded-full',
          ligada ? (homologada ? 'bg-violet-100 text-violet-700' : 'bg-amber-100 text-amber-700') : 'bg-ds-input text-ds-light',
        ].join(' ')}>
          {ligada ? <Zap size={20} /> : <PowerOff size={20} />}
        </div>

        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-ds-light">Chave CBS/IBS da empresa</p>
          {!ligada && (
            <>
              <p className="mt-0.5 text-lg font-bold text-ds-nav">Desligada</p>
              <p className="text-sm text-ds-mid">
                Todas as competências seguem o regime atual (ISS, IR, CSLL, PIS e COFINS).
              </p>
            </>
          )}
          {ligada && !homologada && (
            <>
              <p className="mt-0.5 text-lg font-bold text-amber-700">
                Ligada a partir de {fmtComp(chave.competenciaInicio)}, aguardando homologação
              </p>
              <p className="text-sm text-ds-mid">
                Enquanto a contabilidade não homologar, as notas continuam no regime atual.
              </p>
            </>
          )}
          {ligada && homologada && (
            <>
              <p className="mt-0.5 text-lg font-bold text-violet-800">
                {jaVale ? 'Em vigor' : 'Ligada'} a partir de {fmtComp(chave.competenciaInicio)}
              </p>
              <p className="text-sm text-ds-mid">
                Notas de {fmtCompLonga(chave.competenciaInicio)} em diante são calculadas com CBS/IBS.
                {' '}Homologada por {chave.homologadoPor ?? '—'} em {fmtDataHora(chave.homologadoEm)}.
              </p>
            </>
          )}
        </div>

        {podeAlterar && (
          <div className="flex shrink-0 flex-wrap gap-2">
            {!ligada && (
              <button onClick={onLigar}
                className="inline-flex items-center gap-1.5 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700">
                <Power size={15} /> Ligar chave
              </button>
            )}
            {ligada && !homologada && (
              <button onClick={() => onHomologar(chave)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-amber-500 px-4 py-2 text-sm font-medium text-white hover:bg-amber-600">
                <CheckCircle2 size={15} /> Homologar
              </button>
            )}
            {ligada && (
              <button onClick={() => onDesligar(chave)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-ds-border px-4 py-2 text-sm font-medium text-ds-mid hover:bg-ds-input">
                <PowerOff size={15} /> Desligar
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Modais de confirmação ───────────────────────────────────────────────────

function ModalLigar({
  empresaNome, onClose, onConfirmar,
}: {
  empresaNome: string | null
  onClose: () => void
  onConfirmar: (competencia: string) => Promise<void>
}) {
  const opcoes = useMemo(competenciasFuturas, [])
  const [competencia, setCompetencia] = useState(competenciaSugerida())
  const [ciente, setCiente] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')

  async function confirmar() {
    setSalvando(true); setErro('')
    try { await onConfirmar(competencia) } catch (e) { setErro(erroMsg(e, 'Erro ao ligar a chave')) } finally { setSalvando(false) }
  }

  return (
    <Modal open onClose={onClose} title="Ligar a chave CBS/IBS" size="lg">
      <div className="space-y-4 p-4 sm:p-6">
        {empresaNome && <p className="text-sm text-ds-mid">Empresa: <strong className="text-ds-nav">{empresaNome}</strong></p>}
        <div className="space-y-1">
          <label className="block text-xs font-semibold text-ds-mid">Primeira competência no regime novo</label>
          <select value={competencia} onChange={e => setCompetencia(e.target.value)}
            className="w-full rounded-lg border border-ds-border bg-white px-3 py-2 text-sm sm:w-64">
            {opcoes.map(c => <option key={c} value={c}>{fmtComp(c)} ({c})</option>)}
          </select>
          <p className="text-xs text-ds-light">
            Não pode haver nota emitida a partir dessa competência. Competências anteriores não mudam.
          </p>
        </div>

        <OQueMuda />

        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          <AlertTriangle size={16} className="mt-0.5 shrink-0 text-amber-500" />
          <span>
            A chave nasce <strong>aguardando homologação</strong>. Só depois que a contabilidade homologar é que as notas
            de {fmtCompLonga(competencia)} em diante passam a ser calculadas com CBS/IBS.
          </span>
        </div>

        <label className="flex items-start gap-2 text-sm text-ds-nav">
          <input type="checkbox" checked={ciente} onChange={e => setCiente(e.target.checked)} className="mt-1" />
          <span>Confirmo que a empresa passa ao regime CBS/IBS a partir de {fmtCompLonga(competencia)}.</span>
        </label>

        {erro && <p className="text-sm text-red-600">{erro}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} disabled={salvando}
            className="rounded-lg border border-ds-border px-4 py-2 text-sm text-ds-mid hover:bg-ds-input">Cancelar</button>
          <button onClick={confirmar} disabled={!ciente || salvando}
            className="inline-flex items-center gap-1.5 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50">
            <Power size={15} /> {salvando ? 'Ligando...' : 'Ligar chave'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function ModalConfirmar({
  titulo, texto, botao, perigo, onClose, onConfirmar,
}: {
  titulo: string
  texto: ReactNode
  botao: string
  perigo?: boolean
  onClose: () => void
  onConfirmar: () => Promise<void>
}) {
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')

  async function confirmar() {
    setSalvando(true); setErro('')
    try { await onConfirmar() } catch (e) { setErro(erroMsg(e, 'Erro ao salvar')) } finally { setSalvando(false) }
  }

  return (
    <Modal open onClose={onClose} title={titulo} size="md">
      <div className="space-y-4 p-4 sm:p-6">
        <div className="text-sm text-ds-mid">{texto}</div>
        {erro && <p className="text-sm text-red-600">{erro}</p>}
        <div className="flex justify-end gap-2">
          <button onClick={onClose} disabled={salvando}
            className="rounded-lg border border-ds-border px-4 py-2 text-sm text-ds-mid hover:bg-ds-input">Cancelar</button>
          <button onClick={confirmar} disabled={salvando}
            className={[
              'rounded-lg px-4 py-2 text-sm font-medium text-white disabled:opacity-50',
              perigo ? 'bg-red-600 hover:bg-red-700' : 'bg-amber-500 hover:bg-amber-600',
            ].join(' ')}>
            {salvando ? 'Salvando...' : botao}
          </button>
        </div>
      </div>
    </Modal>
  )
}

// ─── Cronograma ──────────────────────────────────────────────────────────────

/** Fração → texto em % para o campo ("0.112" → "11,2"). */
const pctInput = (v: number | null) =>
  v == null ? '' : String(+(Number(v) * 100).toFixed(4)).replace('.', ',')
/** Texto em % → fração, nulo se vazio ("11,2" → 0.112). */
const pctParse = (s: string): number | null => {
  const t = s.trim().replace(',', '.')
  if (!t) return null
  const n = Number(t)
  return Number.isFinite(n) ? +(n / 100).toFixed(6) : NaN
}

function LinhaCronogramaEdicao({
  linha, onCancelar, onSalvo,
}: {
  linha: CronogramaReformaAno
  onCancelar: () => void
  onSalvo: (l: CronogramaReformaAno) => void
}) {
  const [cbs, setCbs] = useState(pctInput(linha.aliqCbs))
  const [ibsUf, setIbsUf] = useState(pctInput(linha.aliqIbsUf))
  const [ibsMun, setIbsMun] = useState(pctInput(linha.aliqIbsMun))
  const [fator, setFator] = useState(pctInput(linha.fatorIss))
  const [definitivas, setDefinitivas] = useState(linha.aliquotasDefinitivas)
  const [fonte, setFonte] = useState(linha.fonte ?? '')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')

  async function salvar() {
    const valores = [pctParse(cbs), pctParse(ibsUf), pctParse(ibsMun), pctParse(fator)]
    if (valores.some(v => v != null && (Number.isNaN(v) || v < 0 || v > 1))) {
      setErro('Use percentuais entre 0 e 100.'); return
    }
    const [aliqCbs, aliqIbsUf, aliqIbsMun, fatorIss] = valores
    if (fatorIss == null) { setErro('Informe o fator do ISS (100% até 2028, 0% em 2033).'); return }
    if (definitivas && (aliqCbs == null || aliqIbsUf == null || aliqIbsMun == null)) {
      setErro('Para marcar como definitivo, preencha CBS, IBS estadual e IBS municipal.'); return
    }
    setSalvando(true); setErro('')
    try {
      const salvo = await parametrosFiscaisApi.atualizarCronograma(linha.ano, {
        aliqCbs, aliqIbsUf, aliqIbsMun, fatorIss, aliquotasDefinitivas: definitivas, fonte: fonte.trim() || null,
      })
      onSalvo(salvo)
    } catch (e) { setErro(erroMsg(e, 'Erro ao salvar o ano')) } finally { setSalvando(false) }
  }

  const inputCls = 'w-20 rounded border border-ds-border px-2 py-1 text-right text-sm'
  return (
    <tr className="bg-primary-50/40 align-top">
      <td className="px-3 py-2 font-bold text-ds-nav">{linha.ano}</td>
      <td className="px-3 py-2"><input value={cbs} onChange={e => setCbs(e.target.value)} className={inputCls} placeholder="—" /> %</td>
      <td className="px-3 py-2 whitespace-nowrap">
        <input value={ibsUf} onChange={e => setIbsUf(e.target.value)} className={inputCls} placeholder="UF" title="IBS estadual" /> +{' '}
        <input value={ibsMun} onChange={e => setIbsMun(e.target.value)} className={inputCls} placeholder="Mun." title="IBS municipal" /> %
      </td>
      <td className="px-3 py-2"><input value={fator} onChange={e => setFator(e.target.value)} className={inputCls} /> %</td>
      <td className="px-3 py-2 text-center">
        <input type="checkbox" checked={definitivas} onChange={e => setDefinitivas(e.target.checked)} />
      </td>
      <td className="px-3 py-2" colSpan={2}>
        <textarea value={fonte} onChange={e => setFonte(e.target.value)} rows={2}
          className="w-full min-w-[14rem] rounded border border-ds-border px-2 py-1 text-xs" placeholder="Fonte (norma, resolução...)" />
        {erro && <p className="mt-1 text-xs text-red-600">{erro}</p>}
        <div className="mt-1 flex gap-2">
          <button onClick={salvar} disabled={salvando}
            className="rounded bg-primary px-3 py-1 text-xs font-medium text-white hover:bg-primary-600 disabled:opacity-50">
            {salvando ? 'Salvando...' : 'Salvar'}
          </button>
          <button onClick={onCancelar} disabled={salvando}
            className="rounded border border-ds-border px-3 py-1 text-xs text-ds-mid hover:bg-ds-input">Cancelar</button>
        </div>
      </td>
    </tr>
  )
}

function TabelaCronograma({ podeEditar }: { podeEditar: boolean }) {
  const [linhas, setLinhas] = useState<CronogramaReformaAno[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState('')
  const [editAno, setEditAno] = useState<number | null>(null)

  useEffect(() => {
    parametrosFiscaisApi.listarCronograma()
      .then(l => setLinhas([...l].sort((a, b) => a.ano - b.ano)))
      .catch(e => setErro(erroMsg(e, 'Erro ao carregar o cronograma')))
      .finally(() => setLoading(false))
  }, [])

  const anoAtual = new Date().getFullYear()

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-ds-mid">Cronograma da reforma 2026–2033</h2>
        <p className="text-xs text-ds-light">
          Alíquotas de referência, antes da redução do serviço. {podeEditar ? 'Gestão e contabilidade podem ajustar.' : 'Somente leitura.'}
        </p>
      </div>
      {loading && <p className="py-6 text-center text-sm text-ds-mid">Carregando cronograma...</p>}
      {erro && <p className="py-2 text-sm text-red-600">{erro}</p>}
      {!loading && !erro && (
        <div className="overflow-x-auto rounded-xl border border-ds-border bg-white">
          <table className="w-full text-sm">
            <thead className="bg-ds-surface text-xs uppercase tracking-wide text-ds-light">
              <tr>
                <th className="px-3 py-2 text-left">Ano</th>
                <th className="px-3 py-2 text-right">CBS</th>
                <th className="px-3 py-2 text-right">IBS (UF + mun.)</th>
                <th className="px-3 py-2 text-right">ISS cobrado</th>
                <th className="px-3 py-2 text-center">Situação</th>
                <th className="px-3 py-2 text-left">Fonte</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-ds-border">
              {linhas.map(l => editAno === l.ano ? (
                <LinhaCronogramaEdicao key={l.ano} linha={l} onCancelar={() => setEditAno(null)}
                  onSalvo={s => { setLinhas(ls => ls.map(x => x.ano === s.ano ? s : x)); setEditAno(null) }} />
              ) : (
                <tr key={l.ano} className={l.ano === anoAtual ? 'bg-primary-50/30' : ''}>
                  <td className="px-3 py-2 font-bold text-ds-nav">{l.ano}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtPct(l.aliqCbs)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {fmtPct(l.aliqIbs)}
                    {l.aliqIbs != null && (
                      <span className="block whitespace-nowrap text-[11px] text-ds-light">{fmtPct(l.aliqIbsUf)} + {fmtPct(l.aliqIbsMun)}</span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {Number(l.fatorIss) === 0 ? <span className="text-red-600">extinto</span> : fmtPct(l.fatorIss)}
                  </td>
                  <td className="px-3 py-2 text-center">
                    {l.aliquotasDefinitivas
                      ? <span className="rounded-md bg-green-50 px-2 py-0.5 text-xs font-semibold text-green-700">Definitivo</span>
                      : <span className="rounded-md bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700">
                          {l.aliqCbs == null ? 'A definir' : 'Provisório'}
                        </span>}
                  </td>
                  <td className="max-w-md px-3 py-2 text-xs text-ds-mid">
                    <span className="line-clamp-2" title={l.fonte ?? ''}>{l.fonte ?? '—'}</span>
                    {l.atualizadoPor && (
                      <span className="block text-[11px] text-ds-light">Atualizado por {l.atualizadoPor} em {fmtDataHora(l.atualizadoEm)}</span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {podeEditar && editAno == null && (
                      <button onClick={() => setEditAno(l.ano)} title={`Editar ${l.ano}`} className="text-ds-mid hover:text-primary">
                        <Pencil className="h-3.5 w-3.5" />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

// ─── Histórico de parâmetros ─────────────────────────────────────────────────

function ParametroLinha({ param }: { param: ParametroFiscal }) {
  const [aberto, setAberto] = useState(false)
  const tributos = param.ibsCbsAtivo
    ? [{ l: 'ISS', v: param.aliqIss }, { l: 'IR', v: param.aliqIr }, { l: 'CSLL', v: param.aliqCsll }]
    : [{ l: 'ISS', v: param.aliqIss }, { l: 'IR', v: param.aliqIr }, { l: 'CSLL', v: param.aliqCsll },
       { l: 'PIS', v: param.aliqPis }, { l: 'COFINS', v: param.aliqCofins }]
  return (
    <div className="rounded-xl border border-ds-border bg-white">
      <button onClick={() => setAberto(a => !a)} className="flex w-full flex-wrap items-center gap-2 px-4 py-3 text-left">
        <span className="font-bold text-ds-nav">{fmtComp(param.competenciaInicio)}</span>
        {param.ibsCbsAtivo
          ? <span className="inline-flex items-center gap-1 rounded-md bg-violet-50 px-2 py-0.5 text-xs font-semibold text-violet-700"><Zap size={10} /> CBS/IBS</span>
          : <span className="rounded-md bg-primary-50 px-2 py-0.5 text-xs font-semibold text-primary">Regime atual</span>}
        {param.homologado
          ? <span className="inline-flex items-center gap-1 text-xs text-green-600"><CheckCircle2 size={12} /> Homologado</span>
          : <span className="inline-flex items-center gap-1 text-xs text-amber-600"><Clock size={12} /> A homologar</span>}
        <span className="ml-auto text-ds-light">{aberto ? <ChevronUp size={16} /> : <ChevronDown size={16} />}</span>
      </button>
      {aberto && (
        <div className="space-y-2 border-t border-ds-border bg-ds-surface px-4 py-3">
          <div className="flex flex-wrap gap-2">
            {tributos.map(t => (
              <div key={t.l} className="min-w-[5rem] rounded-lg border border-ds-border bg-white px-3 py-2 text-center">
                <p className="text-xs text-ds-light">{t.l}</p>
                <p className="font-bold text-ds-mid">{fmtPct(t.v)}</p>
              </div>
            ))}
          </div>
          {param.ibsCbsAtivo && (
            <p className="text-xs italic text-violet-700">
              PIS e COFINS não incidem. CBS e IBS vêm do cronograma do ano, com a redução do serviço; o ISS segue o fator do ano.
            </p>
          )}
          {param.observacoes && <p className="text-xs text-ds-light">{param.observacoes}</p>}
          {param.homologadoPor && (
            <p className="text-xs text-ds-light">Homologado por {param.homologadoPor} em {fmtDataHora(param.homologadoEm)}</p>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Aba ─────────────────────────────────────────────────────────────────────

export function ReformaTributariaAba() {
  const { user } = useAuth()
  const roles = user?.realm_access?.roles ?? []
  const podeAlterar = ['contabil', 'gestao', 'perm_fiscal'].some(r => roles.includes(r))
  const podeEditarCronograma = ['contabil', 'gestao'].some(r => roles.includes(r))
  const cnpjToken = soDigitos(user?.cnpj_id)
  const semEmpresaNoToken = cnpjToken.length !== 14

  const [params, setParams] = useState<ParametroFiscal[]>([])
  const [empresas, setEmpresas] = useState<Empresa[]>([])
  const [cnpjSel, setCnpjSel] = useState('')
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState('')
  const [modal, setModal] = useState<
    { tipo: 'ligar' } | { tipo: 'desligar'; p: ParametroFiscal } | { tipo: 'homologar'; p: ParametroFiscal } | null
  >(null)

  async function carregar() {
    try {
      const r = await parametrosFiscaisApi.listar()
      setParams(Array.isArray(r) ? r : [r])
    } catch (e) { setErro(erroMsg(e, 'Erro ao carregar os parâmetros')) }
  }

  useEffect(() => {
    const tarefas: Promise<unknown>[] = [carregar()]
    if (semEmpresaNoToken) {
      tarefas.push(empresasApi.listar(0, 1000)
        .then(p => setEmpresas(p.content.filter(e => e.ativo)))
        .catch(() => setEmpresas([])))
    }
    Promise.all(tarefas).finally(() => setLoading(false))
  }, [])

  // Gestão sem empresa no token: começa pela empresa que já tem parâmetro, senão a primeira.
  useEffect(() => {
    if (!semEmpresaNoToken || cnpjSel || empresas.length === 0) return
    const comParam = empresas.find(e => params.some(p => soDigitos(p.cnpjId) === soDigitos(e.cnpj)))
    setCnpjSel(soDigitos((comParam ?? empresas[0]).cnpj))
  }, [empresas, params, semEmpresaNoToken, cnpjSel])

  const cnpjEmpresa = semEmpresaNoToken ? cnpjSel : cnpjToken
  const empresaSel = empresas.find(e => soDigitos(e.cnpj) === cnpjSel) ?? null
  const daEmpresa = useMemo(
    () => params
      .filter(p => !semEmpresaNoToken || soDigitos(p.cnpjId) === cnpjEmpresa)
      .sort((a, b) => b.competenciaInicio.localeCompare(a.competenciaInicio)),
    [params, semEmpresaNoToken, cnpjEmpresa],
  )
  // A chave é o parâmetro mais recente da empresa, quando ligado.
  const chave = daEmpresa[0]?.ibsCbsAtivo ? daEmpresa[0] : null

  async function ligar(competencia: string) {
    await parametrosFiscaisApi.criarIbsCbs({
      competenciaInicio: competencia,
      ...(semEmpresaNoToken ? { cnpj: cnpjEmpresa } : {}),
    })
    setModal(null)
    await carregar()
  }

  async function desligar(p: ParametroFiscal) {
    await parametrosFiscaisApi.desligarIbsCbs(p.id)
    setModal(null)
    await carregar()
  }

  async function homologar(p: ParametroFiscal) {
    await parametrosFiscaisApi.homologar(p.id)
    setModal(null)
    await carregar()
  }

  if (loading) return <p className="py-8 text-center text-sm text-ds-mid">Carregando...</p>

  return (
    <div className="space-y-6">
      {erro && (
        <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          <AlertTriangle size={15} className="shrink-0" /> {erro}
          <button onClick={() => setErro('')} className="ml-auto"><X size={14} /></button>
        </div>
      )}

      <p className="text-sm text-ds-mid">
        A chave liga o regime da reforma tributária (CBS e IBS) para a empresa a partir de uma competência.
        Ela só vale depois de homologada pela contabilidade; notas de competências anteriores não mudam.
      </p>

      {semEmpresaNoToken && (
        <div className="flex flex-wrap items-center gap-2">
          <label className="text-xs font-semibold text-ds-mid">Empresa</label>
          {empresas.length === 0
            ? <span className="text-sm text-ds-light">Nenhuma empresa ativa encontrada.</span>
            : (
              <select value={cnpjSel} onChange={e => setCnpjSel(e.target.value)}
                className="w-full max-w-md rounded-lg border border-ds-border bg-white px-3 py-2 text-sm">
                {empresas.map(e => <option key={e.id} value={soDigitos(e.cnpj)}>{e.razaoSocial} ({e.cnpj})</option>)}
              </select>
            )}
        </div>
      )}

      {chave && !chave.homologado && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 px-5 py-3 text-sm text-amber-800">
          <AlertTriangle size={18} className="mt-0.5 shrink-0 text-amber-500" />
          <span>
            <strong>Chave ligada e ainda não homologada.</strong> As notas de {fmtCompLonga(chave.competenciaInicio)} em diante
            continuam sendo calculadas no regime atual até a contabilidade homologar.
          </span>
        </div>
      )}

      {(cnpjEmpresa || !semEmpresaNoToken) && (
        <CardChave
          chave={chave}
          podeAlterar={podeAlterar && cnpjEmpresa.length === 14}
          onLigar={() => setModal({ tipo: 'ligar' })}
          onDesligar={p => setModal({ tipo: 'desligar', p })}
          onHomologar={p => setModal({ tipo: 'homologar', p })}
        />
      )}

      <div>
        <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-ds-mid">O que muda quando a chave vale</h2>
        <OQueMuda />
      </div>

      <TabelaCronograma podeEditar={podeEditarCronograma} />

      <div>
        <div className="mb-2 flex items-center gap-2">
          <Settings2 size={15} className="text-ds-light" />
          <h2 className="text-sm font-semibold uppercase tracking-wide text-ds-mid">
            Histórico de parâmetros da empresa ({daEmpresa.length})
          </h2>
        </div>
        {daEmpresa.length === 0
          ? <p className="rounded-xl border border-ds-border p-6 text-center text-sm text-ds-light">Nenhum parâmetro cadastrado para a empresa.</p>
          : <div className="space-y-2">{daEmpresa.map(p => <ParametroLinha key={p.id} param={p} />)}</div>}
      </div>

      {modal?.tipo === 'ligar' && (
        <ModalLigar empresaNome={empresaSel?.razaoSocial ?? null} onClose={() => setModal(null)} onConfirmar={ligar} />
      )}
      {modal?.tipo === 'desligar' && (
        <ModalConfirmar
          titulo="Desligar a chave CBS/IBS"
          perigo
          botao="Desligar chave"
          texto={<>
            As competências a partir de {fmtCompLonga(modal.p.competenciaInicio)} voltam ao regime atual
            (ISS, IR, CSLL, PIS e COFINS) e a homologação é desfeita. Não é possível desligar se já houver nota
            emitida no regime CBS/IBS nesse período.
          </>}
          onClose={() => setModal(null)}
          onConfirmar={() => desligar(modal.p)}
        />
      )}
      {modal?.tipo === 'homologar' && (
        <ModalConfirmar
          titulo="Homologar a chave CBS/IBS"
          botao="Homologar"
          texto={<>
            Ao homologar, as notas de {fmtCompLonga(modal.p.competenciaInicio)} em diante passam a ser calculadas
            com CBS e IBS, sem PIS e COFINS. Confirme que a contabilidade validou a competência de início e as
            alíquotas do cronograma.
          </>}
          onClose={() => setModal(null)}
          onConfirmar={() => homologar(modal.p)}
        />
      )}
    </div>
  )
}
