import { useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle, Calculator, CheckCircle2, ChevronDown, ChevronUp, Clock, Copy, Pencil, Plus, Trash2, X,
} from 'lucide-react'
import { Modal } from '@pinsaude/ui'
import {
  regrasApuracaoApi, TRIBUTOS_APURACAO,
  type AjusteVencimento, type AplicacaoRegime, type BaseCalculo, type MesSimulacao, type Periodicidade,
  type RegimeReconhecimento, type RegraApuracao, type RegraApuracaoRequest, type ResultadoApuracao,
  type TributoApuracao, type TributoRegra, type ReferenciaCompetencia,
} from '../api/regrasApuracaoApi'
import { Empresa, empresasApi } from '../api/empresasApi'
import { useAuth } from '../auth/useAuth'

// APU-00 — aba "Apuração" da Configuração Fiscal: regras da apuração mensal (presunções, alíquotas,
// adicional de IRPJ, regime caixa/competência, periodicidade, retenções e vencimentos), versionadas
// por competência e homologadas pela contabilidade, e um simulador para conferir as fórmulas.

const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']
const fmtComp = (c: string) => { const [y, m] = c.split('-'); return `${MESES[+m - 1]}/${y}` }
const fmtBRL = (cents: number) => (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const fmtPct = (f: number | null | undefined) =>
  f == null ? '—' : `${(Number(f) * 100).toLocaleString('pt-BR', { maximumFractionDigits: 4 })}%`
const fmtData = (iso: string | null) => (iso ? new Date(iso + (iso.length === 10 ? 'T12:00:00' : '')).toLocaleDateString('pt-BR') : '—')
const fmtDataHora = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
const soDigitos = (s: string | null | undefined) => (s ?? '').replace(/\D/g, '')
const erroMsg = (e: unknown, padrao: string) => (e instanceof Error && e.message ? e.message : padrao)

function competenciaAtual(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** % digitado (texto pt-BR) → fração; vazio → null. */
function pctParaFracao(v: string): number | null {
  const t = v.trim().replace(/\./g, '').replace(',', '.')
  if (t === '') return null
  const n = Number(t)
  return Number.isFinite(n) ? Math.round(n * 10000) / 1000000 : null
}
const fracaoParaPct = (f: number | null | undefined) =>
  f == null ? '' : (Math.round(Number(f) * 1000000) / 10000).toString().replace('.', ',')

const ROTULO_BASE: Record<BaseCalculo, string> = {
  RECEITA_BRUTA: 'Receita bruta', BASE_PRESUMIDA: 'Base presumida', SOMA_NOTAS: 'Soma das notas',
}
const ROTULO_APLICA: Record<AplicacaoRegime, string> = {
  AMBOS: 'Sempre', LEGADO: 'Só sem CBS/IBS', IBS_CBS: 'Só com CBS/IBS',
}
const ROTULO_REGIME: Record<RegimeReconhecimento, string> = { CAIXA: 'Caixa', COMPETENCIA: 'Competência' }
const ROTULO_PER: Record<Periodicidade, string> = { MENSAL: 'Mensal', TRIMESTRAL: 'Trimestral' }
const ROTULO_AJUSTE: Record<AjusteVencimento, string> = {
  ANTECIPA: 'Antecipa (dia útil anterior)', POSTERGA: 'Posterga (dia útil seguinte)', NENHUM: 'Sem ajuste',
}

// ─── Campos ─────────────────────────────────────────────────────────────────

const inputCls = 'w-full rounded-lg border border-ds-border bg-white px-3 py-2 text-sm focus:border-primary focus:outline-none'
const labelCls = 'mb-1 block text-xs font-semibold text-ds-mid'

function MoneyInput({ value, onChange, id }: { value: number; onChange: (c: number) => void; id?: string }) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs text-ds-light">R$</span>
      <input id={id} inputMode="numeric" className={`${inputCls} pl-8 text-right`}
        value={(value / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2 })}
        onChange={e => onChange(parseInt(e.target.value.replace(/\D/g, '') || '0', 10))} />
    </div>
  )
}

function PctInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="relative">
      <input inputMode="decimal" className={`${inputCls} pr-7 text-right`} value={value} placeholder={placeholder}
        onChange={e => onChange(e.target.value.replace(/[^\d,]/g, ''))} />
      <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-xs text-ds-light">%</span>
    </div>
  )
}

// ─── Formulário da regra ────────────────────────────────────────────────────

interface TributoForm extends Omit<TributoRegra, 'aliquota' | 'vencimentoDia' | 'vencimentoMesesApos'> {
  aliquota: string
  vencimentoDia: string
  vencimentoMesesApos: string
}

interface RegraForm {
  competenciaInicio: string
  presIrpjEq: string; presCsllEq: string; presIrpjPad: string; presCsllPad: string
  aliqAdicional: string
  limiteAdicional: number
  teto: number
  alertaTeto: string
  minimoGuia: number
  referencia: ReferenciaCompetencia
  fonte: string
  observacoes: string
  tributos: TributoForm[]
}

function tributoPadrao(t: TributoApuracao): TributoRegra {
  const reforma = t === 'CBS' || t === 'IBS'
  return {
    tributo: t, ativo: true,
    aplicaEm: reforma ? 'IBS_CBS' : t === 'PIS' || t === 'COFINS' ? 'LEGADO' : 'AMBOS',
    aliquota: null,
    baseCalculo: reforma ? 'SOMA_NOTAS' : t === 'IRPJ' || t === 'CSLL' ? 'BASE_PRESUMIDA' : 'RECEITA_BRUTA',
    regimeReconhecimento: 'COMPETENCIA', periodicidade: 'MENSAL', deduzRetencao: true,
    codigoReceita: null, guia: null, vencimentoDia: null, vencimentoMesesApos: null, vencimentoAjuste: 'NENHUM',
  }
}

function paraForm(r: RegraApuracao | null, competencia: string): RegraForm {
  const tributos = TRIBUTOS_APURACAO.map(t => r?.tributos.find(x => x.tributo === t) ?? { ...tributoPadrao(t), ativo: !r })
  return {
    competenciaInicio: competencia,
    presIrpjEq: fracaoParaPct(r?.presuncaoIrpjEquiparado ?? 0.08),
    presCsllEq: fracaoParaPct(r?.presuncaoCsllEquiparado ?? 0.12),
    presIrpjPad: fracaoParaPct(r?.presuncaoIrpjPadrao ?? 0.32),
    presCsllPad: fracaoParaPct(r?.presuncaoCsllPadrao ?? 0.32),
    aliqAdicional: fracaoParaPct(r?.aliqAdicionalIrpj ?? 0.1),
    limiteAdicional: r?.limiteAdicionalIrpjMensal ?? 2000000,
    teto: r?.tetoReceitaAnual ?? 7800000000,
    alertaTeto: fracaoParaPct(r?.alertaTetoPercentual ?? 0.8),
    minimoGuia: r?.valorMinimoGuia ?? 1000,
    referencia: r?.referenciaCompetencia ?? 'COMPETENCIA_NOTA',
    fonte: r?.fonte ?? '',
    observacoes: r?.observacoes ?? '',
    tributos: tributos.map(t => ({
      ...t,
      aliquota: fracaoParaPct(t.aliquota),
      vencimentoDia: t.vencimentoDia == null ? '' : String(t.vencimentoDia),
      vencimentoMesesApos: t.vencimentoMesesApos == null ? '' : String(t.vencimentoMesesApos),
    })),
  }
}

function paraRequest(f: RegraForm, cnpj: string | null): RegraApuracaoRequest {
  const obrig = (v: string, nome: string) => {
    const n = pctParaFracao(v)
    if (n == null) throw new Error(`Informe ${nome}.`)
    return n
  }
  return {
    cnpj,
    competenciaInicio: f.competenciaInicio,
    presuncaoIrpjEquiparado: obrig(f.presIrpjEq, 'a presunção do IRPJ com equiparação'),
    presuncaoCsllEquiparado: obrig(f.presCsllEq, 'a presunção da CSLL com equiparação'),
    presuncaoIrpjPadrao: obrig(f.presIrpjPad, 'a presunção do IRPJ sem equiparação'),
    presuncaoCsllPadrao: obrig(f.presCsllPad, 'a presunção da CSLL sem equiparação'),
    aliqAdicionalIrpj: obrig(f.aliqAdicional, 'a alíquota do adicional de IRPJ'),
    limiteAdicionalIrpjMensal: f.limiteAdicional,
    tetoReceitaAnual: f.teto,
    alertaTetoPercentual: obrig(f.alertaTeto, 'o percentual de alerta do teto'),
    valorMinimoGuia: f.minimoGuia,
    referenciaCompetencia: f.referencia,
    fonte: f.fonte.trim() || null,
    observacoes: f.observacoes.trim() || null,
    tributos: f.tributos.map(t => ({
      ...t,
      aliquota: pctParaFracao(t.aliquota),
      codigoReceita: t.codigoReceita?.trim() || null,
      guia: t.guia?.trim() || null,
      vencimentoDia: t.vencimentoDia === '' ? null : Number(t.vencimentoDia),
      vencimentoMesesApos: t.vencimentoMesesApos === '' ? null : Number(t.vencimentoMesesApos),
    })),
  }
}

function CartaoTributo({ t, onChange }: { t: TributoForm; onChange: (t: TributoForm) => void }) {
  const set = <K extends keyof TributoForm>(k: K, v: TributoForm[K]) => onChange({ ...t, [k]: v })
  const reforma = t.tributo === 'CBS' || t.tributo === 'IBS'
  const presumida = t.tributo === 'IRPJ' || t.tributo === 'CSLL'
  const bases: BaseCalculo[] = presumida ? ['BASE_PRESUMIDA', 'RECEITA_BRUTA']
    : reforma ? ['SOMA_NOTAS', 'RECEITA_BRUTA'] : ['RECEITA_BRUTA']
  return (
    <div className={`rounded-lg border px-4 py-3 ${t.ativo ? 'border-ds-border bg-white' : 'border-dashed border-ds-border bg-ds-input/40'}`}>
      <div className="mb-3 flex items-center gap-3">
        <span className="text-sm font-bold text-ds-nav">{t.tributo}</span>
        <label className="ml-auto flex items-center gap-2 text-xs text-ds-mid">
          <input type="checkbox" checked={t.ativo} onChange={e => set('ativo', e.target.checked)} /> Apurar
        </label>
      </div>
      {t.ativo && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <div>
            <label className={labelCls}>Alíquota</label>
            <PctInput value={t.aliquota} onChange={v => set('aliquota', v)}
              placeholder={reforma ? 'cronograma' : ''} />
          </div>
          <div>
            <label className={labelCls}>Base</label>
            <select className={inputCls} value={t.baseCalculo} onChange={e => set('baseCalculo', e.target.value as BaseCalculo)}>
              {bases.map(b => <option key={b} value={b}>{ROTULO_BASE[b]}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>Receita por</label>
            <select className={inputCls} value={t.regimeReconhecimento}
              onChange={e => set('regimeReconhecimento', e.target.value as RegimeReconhecimento)}>
              {(['COMPETENCIA', 'CAIXA'] as const).map(r => <option key={r} value={r}>{ROTULO_REGIME[r]}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>Período</label>
            <select className={inputCls} value={t.periodicidade} onChange={e => set('periodicidade', e.target.value as Periodicidade)}>
              {(['MENSAL', 'TRIMESTRAL'] as const).map(p => <option key={p} value={p}>{ROTULO_PER[p]}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>Vale</label>
            <select className={inputCls} value={t.aplicaEm} onChange={e => set('aplicaEm', e.target.value as AplicacaoRegime)}>
              {(['AMBOS', 'LEGADO', 'IBS_CBS'] as const).map(a => <option key={a} value={a}>{ROTULO_APLICA[a]}</option>)}
            </select>
          </div>
          <div>
            <label className={labelCls}>Guia / código</label>
            <div className="flex gap-2">
              <input className={inputCls} value={t.guia ?? ''} placeholder="DARF" onChange={e => set('guia', e.target.value)} />
              <input className={`${inputCls} w-20`} value={t.codigoReceita ?? ''} placeholder="cód."
                onChange={e => set('codigoReceita', e.target.value.slice(0, 10))} />
            </div>
          </div>
          <div>
            <label className={labelCls}>Vence dia / meses depois</label>
            <div className="flex gap-2">
              <input inputMode="numeric" className={inputCls} value={t.vencimentoDia} placeholder="dia"
                onChange={e => set('vencimentoDia', e.target.value.replace(/\D/g, '').slice(0, 2))} />
              <input inputMode="numeric" className={inputCls} value={t.vencimentoMesesApos} placeholder="meses"
                onChange={e => set('vencimentoMesesApos', e.target.value.replace(/\D/g, '').slice(0, 2))} />
            </div>
          </div>
          <div>
            <label className={labelCls}>Fim de semana</label>
            <select className={inputCls} value={t.vencimentoAjuste ?? 'NENHUM'}
              onChange={e => set('vencimentoAjuste', e.target.value as AjusteVencimento)}>
              {(['ANTECIPA', 'POSTERGA', 'NENHUM'] as const).map(a => <option key={a} value={a}>{ROTULO_AJUSTE[a]}</option>)}
            </select>
          </div>
          <label className="col-span-2 flex items-center gap-2 text-xs text-ds-mid md:col-span-4">
            <input type="checkbox" checked={t.deduzRetencao} onChange={e => set('deduzRetencao', e.target.checked)} />
            Abater as retenções na fonte sofridas no período{t.tributo === 'IRPJ' ? ' (IR retido pelo tomador)' : ''}
          </label>
        </div>
      )}
    </div>
  )
}

function ModalRegra({
  titulo, inicial, escopoNome, onClose, onSalvar,
}: {
  titulo: string
  inicial: RegraForm
  escopoNome: string
  onClose: () => void
  onSalvar: (f: RegraForm) => Promise<void>
}) {
  const [f, setF] = useState<RegraForm>(inicial)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')
  const set = <K extends keyof RegraForm>(k: K, v: RegraForm[K]) => setF(prev => ({ ...prev, [k]: v }))

  async function salvar() {
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(f.competenciaInicio)) { setErro('Informe a competência de início.'); return }
    setSalvando(true); setErro('')
    try { await onSalvar(f) } catch (e) { setErro(erroMsg(e, 'Erro ao salvar a regra')) } finally { setSalvando(false) }
  }

  return (
    <Modal open onClose={onClose} title={titulo} size="3xl">
      <div className="max-h-[75vh] space-y-5 overflow-y-auto pr-1">
        <p className="text-xs text-ds-light">
          Escopo: <strong className="text-ds-mid">{escopoNome}</strong>. A regra vale da competência de início até a próxima
          regra do mesmo escopo. Salvar uma regra homologada volta ela para rascunho.
        </p>

        <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <div>
            <label className={labelCls}>Vale a partir de *</label>
            <input type="month" className={inputCls} value={f.competenciaInicio} onChange={e => set('competenciaInicio', e.target.value)} />
          </div>
          <div>
            <label className={labelCls}>Guia mínima</label>
            <MoneyInput value={f.minimoGuia} onChange={v => set('minimoGuia', v)} />
          </div>
          <div>
            <label className={labelCls}>Teto anual do Lucro Presumido</label>
            <MoneyInput value={f.teto} onChange={v => set('teto', v)} />
          </div>
          <div>
            <label className={labelCls}>Alertar ao atingir</label>
            <PctInput value={f.alertaTeto} onChange={v => set('alertaTeto', v)} />
          </div>
          <div className="col-span-2">
            <label className={labelCls}>Receita por competência: a nota entra no mês</label>
            <select className={inputCls} value={f.referencia} onChange={e => set('referencia', e.target.value as ReferenciaCompetencia)}>
              <option value="COMPETENCIA_NOTA">da competência da nota (prestação do serviço)</option>
              <option value="DATA_EMISSAO">da data de emissão da nota</option>
            </select>
          </div>
          <p className="col-span-2 self-end text-[11px] text-ds-light">
            Na receita por caixa a nota entra no mês do depósito conciliado.
          </p>
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-ds-mid">Presunção (IRPJ e CSLL)</h3>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <div><label className={labelCls}>IRPJ com equiparação</label><PctInput value={f.presIrpjEq} onChange={v => set('presIrpjEq', v)} /></div>
            <div><label className={labelCls}>CSLL com equiparação</label><PctInput value={f.presCsllEq} onChange={v => set('presCsllEq', v)} /></div>
            <div><label className={labelCls}>IRPJ sem equiparação</label><PctInput value={f.presIrpjPad} onChange={v => set('presIrpjPad', v)} /></div>
            <div><label className={labelCls}>CSLL sem equiparação</label><PctInput value={f.presCsllPad} onChange={v => set('presCsllPad', v)} /></div>
          </div>
        </section>

        <section>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-ds-mid">Adicional de IRPJ</h3>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <div><label className={labelCls}>Alíquota do adicional</label><PctInput value={f.aliqAdicional} onChange={v => set('aliqAdicional', v)} /></div>
            <div>
              <label className={labelCls}>Sobre a base acima de (por mês)</label>
              <MoneyInput value={f.limiteAdicional} onChange={v => set('limiteAdicional', v)} />
            </div>
            <p className="col-span-2 self-end text-xs text-ds-light">No período trimestral o limite é multiplicado por 3.</p>
          </div>
        </section>

        <section className="space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-ds-mid">Tributos</h3>
          <p className="text-xs text-ds-light">
            CBS e IBS sem alíquota usam a do cronograma da reforma; com base "Soma das notas" usam o valor já calculado em
            cada nota (com a redução do serviço). "Vale" segue a chave CBS/IBS da empresa.
          </p>
          {f.tributos.map((t, i) => (
            <CartaoTributo key={t.tributo} t={t}
              onChange={nt => set('tributos', f.tributos.map((x, j) => (j === i ? nt : x)))} />
          ))}
        </section>

        <section className="grid gap-3 md:grid-cols-2">
          <div>
            <label className={labelCls}>Fonte</label>
            <input className={inputCls} value={f.fonte} placeholder="Ex.: parecer do contador de 10/12/2026"
              onChange={e => set('fonte', e.target.value)} />
          </div>
          <div>
            <label className={labelCls}>Observações</label>
            <textarea rows={2} className={inputCls} value={f.observacoes} onChange={e => set('observacoes', e.target.value)} />
          </div>
        </section>
      </div>

      {erro && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>}
      <div className="mt-4 flex justify-end gap-2">
        <button onClick={onClose} className="rounded-lg border border-ds-border px-4 py-2 text-sm text-ds-mid hover:bg-ds-input">Cancelar</button>
        <button onClick={salvar} disabled={salvando}
          className="rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary-600 disabled:opacity-60">
          {salvando ? 'Salvando...' : 'Salvar rascunho'}
        </button>
      </div>
    </Modal>
  )
}

// ─── Lista de regras ────────────────────────────────────────────────────────

function CartaoRegra({
  r, podeAlterar, vigente, onEditar, onDuplicar, onHomologar, onExcluir,
}: {
  r: RegraApuracao
  podeAlterar: boolean
  vigente: boolean
  onEditar: () => void
  onDuplicar: () => void
  onHomologar: () => void
  onExcluir: () => void
}) {
  const [aberto, setAberto] = useState(false)
  const ativos = r.tributos.filter(t => t.ativo)
  return (
    <div className={`rounded-xl border bg-white shadow-sm ${r.homologado ? 'border-emerald-200' : 'border-amber-300'}`}>
      <div className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-ds-nav">A partir de {fmtComp(r.competenciaInicio)}</span>
            <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${r.global ? 'bg-ds-input text-ds-mid' : 'bg-primary-50 text-primary-700'}`}>
              {r.global ? 'Padrão global' : 'Da empresa'}
            </span>
            {r.homologado
              ? <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700"><CheckCircle2 size={12} /> Homologada</span>
              : <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-700"><Clock size={12} /> Rascunho</span>}
            {vigente && <span className="rounded-full bg-violet-50 px-2 py-0.5 text-[11px] font-semibold text-violet-700">Vigente hoje</span>}
          </div>
          <p className="mt-1 text-xs text-ds-light">
            {ativos.map(t => t.tributo).join(' · ')}
            {r.homologado && ` — homologada por ${r.homologadoPor ?? '—'} em ${fmtDataHora(r.homologadoEm)}`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button onClick={() => setAberto(a => !a)} className="inline-flex items-center gap-1 rounded-lg border border-ds-border px-3 py-1.5 text-xs text-ds-mid hover:bg-ds-input">
            {aberto ? <ChevronUp size={14} /> : <ChevronDown size={14} />} Detalhes
          </button>
          {podeAlterar && (
            <>
              <button onClick={onEditar} className="inline-flex items-center gap-1 rounded-lg border border-ds-border px-3 py-1.5 text-xs text-ds-mid hover:bg-ds-input">
                <Pencil size={13} /> Editar
              </button>
              <button onClick={onDuplicar} className="inline-flex items-center gap-1 rounded-lg border border-ds-border px-3 py-1.5 text-xs text-ds-mid hover:bg-ds-input" title="Nova vigência a partir desta">
                <Copy size={13} /> Nova vigência
              </button>
              {!r.homologado && (
                <>
                  <button onClick={onHomologar} className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700">
                    <CheckCircle2 size={13} /> Homologar
                  </button>
                  <button onClick={onExcluir} className="inline-flex items-center gap-1 rounded-lg border border-red-200 px-3 py-1.5 text-xs text-red-600 hover:bg-red-50">
                    <Trash2 size={13} />
                  </button>
                </>
              )}
            </>
          )}
        </div>
      </div>
      {aberto && (
        <div className="space-y-3 border-t border-ds-border px-4 py-3 text-xs">
          <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
            <Info rotulo="Presunção IRPJ (com / sem equip.)" valor={`${fmtPct(r.presuncaoIrpjEquiparado)} / ${fmtPct(r.presuncaoIrpjPadrao)}`} />
            <Info rotulo="Presunção CSLL (com / sem equip.)" valor={`${fmtPct(r.presuncaoCsllEquiparado)} / ${fmtPct(r.presuncaoCsllPadrao)}`} />
            <Info rotulo="Adicional de IRPJ" valor={`${fmtPct(r.aliqAdicionalIrpj)} acima de ${fmtBRL(r.limiteAdicionalIrpjMensal)}/mês`} />
            <Info rotulo="Teto / alerta / guia mínima" valor={`${fmtBRL(r.tetoReceitaAnual)} / ${fmtPct(r.alertaTetoPercentual)} / ${fmtBRL(r.valorMinimoGuia)}`} />
            <Info rotulo="Receita por competência" valor={r.referenciaCompetencia === 'DATA_EMISSAO' ? 'Pela data de emissão' : 'Pela competência da nota'} />
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left">
              <thead className="text-ds-light">
                <tr>
                  <th className="py-1 pr-3">Tributo</th><th className="pr-3">Alíquota</th><th className="pr-3">Base</th>
                  <th className="pr-3">Receita</th><th className="pr-3">Período</th><th className="pr-3">Vale</th>
                  <th className="pr-3">Retenção</th><th className="pr-3">Guia</th><th>Vencimento</th>
                </tr>
              </thead>
              <tbody className="text-ds-nav">
                {r.tributos.map(t => (
                  <tr key={t.tributo} className={`border-t border-ds-border ${t.ativo ? '' : 'text-ds-light line-through'}`}>
                    <td className="py-1 pr-3 font-semibold">{t.tributo}</td>
                    <td className="pr-3">{t.aliquota == null ? 'cronograma' : fmtPct(t.aliquota)}</td>
                    <td className="pr-3">{ROTULO_BASE[t.baseCalculo]}</td>
                    <td className="pr-3">{ROTULO_REGIME[t.regimeReconhecimento]}</td>
                    <td className="pr-3">{ROTULO_PER[t.periodicidade]}</td>
                    <td className="pr-3">{ROTULO_APLICA[t.aplicaEm]}</td>
                    <td className="pr-3">{t.deduzRetencao ? 'Abate' : 'Não abate'}</td>
                    <td className="pr-3">{[t.guia, t.codigoReceita].filter(Boolean).join(' ') || '—'}</td>
                    <td>{t.vencimentoDia == null ? <span className="text-amber-600">não cadastrado</span>
                      : `dia ${t.vencimentoDia === 31 ? 'último' : t.vencimentoDia}, ${t.vencimentoMesesApos} mês(es) depois`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {(r.fonte || r.observacoes) && (
            <div className="space-y-1 text-ds-mid">
              {r.fonte && <p><strong>Fonte:</strong> {r.fonte}</p>}
              {r.observacoes && <p><strong>Observações:</strong> {r.observacoes}</p>}
            </div>
          )}
          <p className="text-ds-light">Atualizada por {r.atualizadoPor ?? '—'} em {fmtDataHora(r.updatedAt)}</p>
        </div>
      )}
    </div>
  )
}

function Info({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="rounded-lg bg-ds-input/50 px-3 py-2">
      <p className="text-[11px] text-ds-light">{rotulo}</p>
      <p className="font-semibold text-ds-nav">{valor}</p>
    </div>
  )
}

// ─── Simulador ──────────────────────────────────────────────────────────────

interface MesForm {
  competencia: string
  compEq: number; compPad: number; caixaEq: number; caixaPad: number
  retencoes: Partial<Record<TributoApuracao, number>>
  cbs: number; ibs: number
}

const mesVazio = (c: string): MesForm => ({ competencia: c, compEq: 0, compPad: 0, caixaEq: 0, caixaPad: 0, retencoes: {}, cbs: 0, ibs: 0 })

/** Meses do trimestre civil até a competência (para tributos trimestrais). */
function mesesDoTrimestre(comp: string): string[] {
  const [y, m] = comp.split('-').map(Number)
  const inicio = Math.floor((m - 1) / 3) * 3 + 1
  return Array.from({ length: m - inicio + 1 }, (_, i) => `${y}-${String(inicio + i).padStart(2, '0')}`)
}

function Simulador({ regras, cnpj }: { regras: RegraApuracao[]; cnpj: string | null }) {
  const [competencia, setCompetencia] = useState(competenciaAtual())
  const [regraId, setRegraId] = useState('')
  const [regime, setRegime] = useState<'' | 'LEGADO' | 'IBS_CBS'>('')
  const [caixaIgual, setCaixaIgual] = useState(true)
  const [meses, setMeses] = useState<MesForm[]>(mesesDoTrimestre(competenciaAtual()).map(mesVazio))
  const [resultado, setResultado] = useState<ResultadoApuracao | null>(null)
  const [aberto, setAberto] = useState<string | null>(null)
  const [erro, setErro] = useState('')
  const [calculando, setCalculando] = useState(false)

  useEffect(() => {
    if (!/^\d{4}-\d{2}$/.test(competencia)) return
    setMeses(prev => mesesDoTrimestre(competencia).map(c => prev.find(p => p.competencia === c) ?? mesVazio(c)))
    setResultado(null)
  }, [competencia])

  const setMes = (i: number, patch: Partial<MesForm>) => setMeses(ms => ms.map((m, j) => (j === i ? { ...m, ...patch } : m)))

  async function simular() {
    setCalculando(true); setErro('')
    try {
      const payload: MesSimulacao[] = meses.map(m => ({
        competencia: m.competencia,
        receitaCompetenciaEquiparada: m.compEq,
        receitaCompetenciaPadrao: m.compPad,
        receitaCaixaEquiparada: caixaIgual ? m.compEq : m.caixaEq,
        receitaCaixaPadrao: caixaIgual ? m.compPad : m.caixaPad,
        retencoes: m.retencoes,
        cbsNotas: m.cbs,
        ibsNotas: m.ibs,
      }))
      setResultado(await regrasApuracaoApi.simular({
        competencia, cnpj, regraId: regraId || null, regime: regime || null, meses: payload,
      }))
    } catch (e) { setErro(erroMsg(e, 'Erro ao simular')); setResultado(null) } finally { setCalculando(false) }
  }

  return (
    <div className="space-y-4 rounded-xl border border-ds-border bg-white p-4 shadow-sm">
      <div className="flex items-center gap-2">
        <Calculator size={16} className="text-primary" />
        <h2 className="text-sm font-semibold text-ds-nav">Simulador</h2>
      </div>
      <p className="text-xs text-ds-light">
        Informe os números do mês (e dos meses anteriores do trimestre, para IRPJ e CSLL trimestrais) e veja a apuração
        passo a passo pela regra. Nada é gravado.
      </p>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div>
          <label className={labelCls}>Competência</label>
          <input type="month" className={inputCls} value={competencia} onChange={e => setCompetencia(e.target.value)} />
        </div>
        <div>
          <label className={labelCls}>Regra</label>
          <select className={inputCls} value={regraId} onChange={e => setRegraId(e.target.value)}>
            <option value="">Vigente na competência</option>
            {regras.map(r => (
              <option key={r.id} value={r.id}>
                {r.global ? 'Global' : 'Empresa'} — a partir de {fmtComp(r.competenciaInicio)} ({r.homologado ? 'homologada' : 'rascunho'})
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className={labelCls}>Regime</label>
          <select className={inputCls} value={regime} onChange={e => setRegime(e.target.value as '' | 'LEGADO' | 'IBS_CBS')}>
            <option value="">{cnpj ? 'O da empresa (chave CBS/IBS)' : 'Atual (sem CBS/IBS)'}</option>
            <option value="LEGADO">Sem CBS/IBS (PIS e COFINS)</option>
            <option value="IBS_CBS">Com CBS/IBS</option>
          </select>
        </div>
      </div>

      <label className="flex items-center gap-2 text-xs text-ds-mid">
        <input type="checkbox" checked={caixaIgual} onChange={e => setCaixaIgual(e.target.checked)} />
        Receita recebida no mês igual à emitida (caixa = competência)
      </label>

      <div className="space-y-3">
        {meses.map((m, i) => (
          <div key={m.competencia} className="rounded-lg border border-ds-border p-3">
            <p className="mb-2 text-xs font-semibold text-ds-nav">{fmtComp(m.competencia)}</p>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <div><label className={labelCls}>Emitido com equiparação</label><MoneyInput value={m.compEq} onChange={v => setMes(i, { compEq: v })} /></div>
              <div><label className={labelCls}>Emitido sem equiparação</label><MoneyInput value={m.compPad} onChange={v => setMes(i, { compPad: v })} /></div>
              {!caixaIgual && (
                <>
                  <div><label className={labelCls}>Recebido com equiparação</label><MoneyInput value={m.caixaEq} onChange={v => setMes(i, { caixaEq: v })} /></div>
                  <div><label className={labelCls}>Recebido sem equiparação</label><MoneyInput value={m.caixaPad} onChange={v => setMes(i, { caixaPad: v })} /></div>
                </>
              )}
              {(['IRPJ', 'CSLL', 'PIS', 'COFINS', 'ISS'] as const).map(t => (
                <div key={t}>
                  <label className={labelCls}>{t === 'IRPJ' ? 'IR' : t} retido</label>
                  <MoneyInput value={m.retencoes[t] ?? 0} onChange={v => setMes(i, { retencoes: { ...m.retencoes, [t]: v } })} />
                </div>
              ))}
              <div><label className={labelCls}>CBS das notas</label><MoneyInput value={m.cbs} onChange={v => setMes(i, { cbs: v })} /></div>
              <div><label className={labelCls}>IBS das notas</label><MoneyInput value={m.ibs} onChange={v => setMes(i, { ibs: v })} /></div>
            </div>
          </div>
        ))}
      </div>

      <div className="flex justify-end">
        <button onClick={simular} disabled={calculando}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary-600 disabled:opacity-60">
          <Calculator size={15} /> {calculando ? 'Calculando...' : 'Simular apuração'}
        </button>
      </div>

      {erro && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>}

      {resultado && (
        <div className="space-y-3">
          {resultado.aHomologar && (
            <div className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              <AlertTriangle size={15} className="mt-0.5 shrink-0" />
              <span><strong>A homologar.</strong> Regra {resultado.regraGlobal ? 'global' : 'da empresa'} de {fmtComp(resultado.regraCompetenciaInicio)} ainda é rascunho: valores são estimativa.</span>
            </div>
          )}
          <div className="grid grid-cols-3 gap-2">
            <Info rotulo="Devido no mês" valor={fmtBRL(resultado.totalDevido)} />
            <Info rotulo="(−) Retenções" valor={fmtBRL(resultado.totalRetencoes)} />
            <Info rotulo="A recolher" valor={fmtBRL(resultado.totalARecolher)} />
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-xs">
              <thead className="text-ds-light">
                <tr>
                  <th className="py-1 pr-3">Tributo</th><th className="pr-3">Período</th><th className="pr-3 text-right">Base</th>
                  <th className="pr-3 text-right">Alíquota</th><th className="pr-3 text-right">Devido</th>
                  <th className="pr-3 text-right">Adicional</th><th className="pr-3 text-right">Retido</th>
                  <th className="pr-3 text-right">A recolher</th><th className="pr-3">Vencimento</th><th />
                </tr>
              </thead>
              <tbody>
                {resultado.tributos.map(t => (
                  <FragmentoTributo key={t.tributo} t={t} aberto={aberto === t.tributo}
                    onToggle={() => setAberto(a => (a === t.tributo ? null : t.tributo))} />
                ))}
              </tbody>
            </table>
          </div>
          {resultado.avisos.filter(a => !a.includes('não homologada')).length > 0 && (
            <ul className="list-disc space-y-1 pl-5 text-xs text-amber-700">
              {resultado.avisos.filter(a => !a.includes('não homologada')).map(a => <li key={a}>{a}</li>)}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

function FragmentoTributo({ t, aberto, onToggle }: { t: ResultadoApuracao['tributos'][number]; aberto: boolean; onToggle: () => void }) {
  const periodo = t.periodicidade === 'MENSAL' ? fmtComp(t.periodoFim) : `${fmtComp(t.periodoInicio)}–${fmtComp(t.periodoFim)}`
  return (
    <>
      <tr className="border-t border-ds-border text-ds-nav">
        <td className="py-1.5 pr-3 font-semibold">{t.tributo}</td>
        <td className="pr-3">{periodo}{!t.fechaNesteMes && <span className="ml-1 text-ds-light">(parcial)</span>}</td>
        <td className="pr-3 text-right">{fmtBRL(t.base)}</td>
        <td className="pr-3 text-right">{t.baseCalculo === 'SOMA_NOTAS' ? 'notas' : fmtPct(t.aliquota)}</td>
        <td className="pr-3 text-right">{fmtBRL(t.valorDevido)}</td>
        <td className="pr-3 text-right">{t.adicional ? fmtBRL(t.adicional) : '—'}</td>
        <td className="pr-3 text-right">{t.retencoes ? fmtBRL(t.retencoes) : '—'}</td>
        <td className="pr-3 text-right font-semibold">
          {t.fechaNesteMes ? fmtBRL(t.valorARecolher) : <span className="font-normal text-ds-light">no fim do trimestre</span>}
          {t.abaixoMinimo && <span className="block font-normal text-amber-600">abaixo do mínimo</span>}
          {t.saldoCredor > 0 && <span className="block font-normal text-emerald-600">credor {fmtBRL(t.saldoCredor)}</span>}
        </td>
        <td className="pr-3">{fmtData(t.vencimento)}{t.codigoReceita && <span className="block text-ds-light">{t.guia} {t.codigoReceita}</span>}</td>
        <td><button onClick={onToggle} className="text-primary hover:underline">{aberto ? 'ocultar' : 'cálculo'}</button></td>
      </tr>
      {aberto && (
        <tr className="bg-ds-input/40">
          <td colSpan={10} className="px-3 py-2">
            <ol className="list-decimal space-y-0.5 pl-4 text-ds-mid">{t.memoria.map((l, i) => <li key={i}>{l}</li>)}</ol>
          </td>
        </tr>
      )}
    </>
  )
}

// ─── Aba ────────────────────────────────────────────────────────────────────

const GLOBAL = '__global__'

export function ApuracaoAba() {
  const { user } = useAuth()
  const roles = user?.realm_access?.roles ?? []
  const podeAlterar = ['contabil', 'gestao', 'perm_fiscal'].some(r => roles.includes(r))
  const cnpjToken = soDigitos(user?.cnpj_id)
  const semEmpresaNoToken = cnpjToken.length !== 14

  const [regras, setRegras] = useState<RegraApuracao[]>([])
  const [empresas, setEmpresas] = useState<Empresa[]>([])
  const [escopo, setEscopo] = useState<string>(GLOBAL)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState('')
  const [modal, setModal] = useState<
    | { tipo: 'nova'; base: RegraApuracao | null }
    | { tipo: 'editar'; r: RegraApuracao }
    | { tipo: 'homologar'; r: RegraApuracao }
    | { tipo: 'excluir'; r: RegraApuracao }
    | null
  >(null)

  async function carregar() {
    try { setRegras(await regrasApuracaoApi.listar()) } catch (e) { setErro(erroMsg(e, 'Erro ao carregar as regras')) }
  }

  useEffect(() => {
    const tarefas: Promise<unknown>[] = [carregar()]
    if (semEmpresaNoToken) {
      tarefas.push(empresasApi.listar(0, 1000).then(p => setEmpresas(p.content.filter(e => e.ativo))).catch(() => setEmpresas([])))
    }
    Promise.all(tarefas).finally(() => setLoading(false))
  }, [])

  // Empresa do escopo: a do token, ou a escolhida pela gestão (vazio = padrão global)
  const cnpjEscopo = semEmpresaNoToken ? (escopo === GLOBAL ? null : escopo) : cnpjToken
  const escopoNome = cnpjEscopo == null ? 'Padrão global (empresas sem regra própria)'
    : semEmpresaNoToken ? (empresas.find(e => soDigitos(e.cnpj) === cnpjEscopo)?.razaoSocial ?? cnpjEscopo) : 'Sua empresa'

  const visiveis = useMemo(() => regras
    .filter(r => r.global || soDigitos(r.cnpjId) === cnpjEscopo)
    .filter(r => cnpjEscopo != null || r.global)
    .sort((a, b) => b.competenciaInicio.localeCompare(a.competenciaInicio) || Number(a.global) - Number(b.global)),
  [regras, cnpjEscopo])

  // Regra vigente hoje: a do escopo mais recente até o mês atual; sem ela, a global
  const hoje = competenciaAtual()
  const vigenteId = useMemo(() => {
    const ate = (lista: RegraApuracao[]) => lista.filter(r => r.competenciaInicio <= hoje)
      .sort((a, b) => b.competenciaInicio.localeCompare(a.competenciaInicio))[0]
    const daEmpresa = cnpjEscopo ? ate(visiveis.filter(r => !r.global)) : undefined
    return (daEmpresa ?? ate(visiveis.filter(r => r.global)))?.id
  }, [visiveis, cnpjEscopo, hoje])

  // Quem tem empresa no token não altera o padrão global
  const podeAlterarRegra = (r: RegraApuracao) => podeAlterar && (semEmpresaNoToken || !r.global)

  async function salvarNova(f: RegraForm) {
    await regrasApuracaoApi.criar(paraRequest(f, cnpjEscopo))
    setModal(null); await carregar()
  }
  async function salvarEdicao(r: RegraApuracao, f: RegraForm) {
    await regrasApuracaoApi.atualizar(r.id, paraRequest(f, r.cnpjId))
    setModal(null); await carregar()
  }
  async function confirmar(acao: () => Promise<unknown>) {
    try { await acao(); setModal(null); await carregar() } catch (e) { setErro(erroMsg(e, 'Erro')); setModal(null) }
  }

  if (loading) return <p className="py-8 text-center text-sm text-ds-mid">Carregando...</p>

  const pendentes = visiveis.filter(r => !r.homologado)

  return (
    <div className="space-y-6">
      {erro && (
        <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          <AlertTriangle size={15} className="shrink-0" /> {erro}
          <button onClick={() => setErro('')} className="ml-auto"><X size={14} /></button>
        </div>
      )}

      <p className="text-sm text-ds-mid">
        Regras da apuração mensal da Pin (Lucro Presumido e reforma tributária). Nada fica fixo no sistema: presunções,
        alíquotas, adicional de IRPJ, caixa ou competência, periodicidade, retenções e vencimentos vêm daqui. Enquanto a
        regra não for homologada pela contabilidade, a apuração sai marcada como <strong>a homologar</strong>.
      </p>

      {semEmpresaNoToken && (
        <div className="flex flex-wrap items-center gap-2">
          <label className="text-xs font-semibold text-ds-mid">Escopo</label>
          <select value={escopo} onChange={e => setEscopo(e.target.value)}
            className="w-full max-w-md rounded-lg border border-ds-border bg-white px-3 py-2 text-sm">
            <option value={GLOBAL}>Padrão global (empresas sem regra própria)</option>
            {empresas.map(e => <option key={e.id} value={soDigitos(e.cnpj)}>{e.razaoSocial} ({e.cnpj})</option>)}
          </select>
        </div>
      )}

      {pendentes.length > 0 && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 px-5 py-3 text-sm text-amber-800">
          <AlertTriangle size={18} className="mt-0.5 shrink-0 text-amber-500" />
          <span>
            <strong>{pendentes.length === 1 ? '1 regra aguardando' : `${pendentes.length} regras aguardando`} homologação.</strong>{' '}
            Os valores iniciais são os do PRD. Confirme com a contabilidade, principalmente a periodicidade de IRPJ e CSLL
            (o PRD fala em mensal; a lei prevê trimestral) e os vencimentos de ISS, CBS e IBS.
          </span>
        </div>
      )}

      <div>
        <div className="mb-2 flex items-center gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-ds-mid">Regras ({visiveis.length})</h2>
          {podeAlterar && (semEmpresaNoToken || cnpjEscopo) && (
            <button onClick={() => setModal({ tipo: 'nova', base: visiveis.find(r => r.id === vigenteId) ?? visiveis[0] ?? null })}
              className="ml-auto inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-white hover:bg-primary-600">
              <Plus size={14} /> Nova regra
            </button>
          )}
        </div>
        {visiveis.length === 0
          ? <p className="rounded-xl border border-ds-border p-6 text-center text-sm text-ds-light">Nenhuma regra de apuração cadastrada.</p>
          : (
            <div className="space-y-2">
              {visiveis.map(r => (
                <CartaoRegra key={r.id} r={r} vigente={r.id === vigenteId}
                  podeAlterar={podeAlterarRegra(r)}
                  onEditar={() => setModal({ tipo: 'editar', r })}
                  onDuplicar={() => setModal({ tipo: 'nova', base: r })}
                  onHomologar={() => setModal({ tipo: 'homologar', r })}
                  onExcluir={() => setModal({ tipo: 'excluir', r })} />
              ))}
            </div>
          )}
      </div>

      <Simulador regras={visiveis} cnpj={cnpjEscopo} />

      {modal?.tipo === 'nova' && (
        <ModalRegra titulo="Nova regra de apuração" escopoNome={escopoNome}
          inicial={paraForm(modal.base, competenciaAtual())}
          onClose={() => setModal(null)} onSalvar={salvarNova} />
      )}
      {modal?.tipo === 'editar' && (
        <ModalRegra titulo={`Editar regra de ${fmtComp(modal.r.competenciaInicio)}`}
          escopoNome={modal.r.global ? 'Padrão global' : escopoNome}
          inicial={paraForm(modal.r, modal.r.competenciaInicio)}
          onClose={() => setModal(null)} onSalvar={f => salvarEdicao(modal.r, f)} />
      )}
      {modal?.tipo === 'homologar' && (
        <ModalSimNao titulo="Homologar regra de apuração" botao="Homologar"
          texto={`A regra ${modal.r.global ? 'global' : 'da empresa'} a partir de ${fmtComp(modal.r.competenciaInicio)} passa a valer como confirmada pela contabilidade e a apuração deixa de sair marcada como "a homologar". Confirme que o contador validou as fórmulas.`}
          onClose={() => setModal(null)} onConfirmar={() => confirmar(() => regrasApuracaoApi.homologar(modal.r.id))} />
      )}
      {modal?.tipo === 'excluir' && (
        <ModalSimNao titulo="Excluir rascunho" botao="Excluir" perigo
          texto={`O rascunho a partir de ${fmtComp(modal.r.competenciaInicio)} será excluído.`}
          onClose={() => setModal(null)} onConfirmar={() => confirmar(() => regrasApuracaoApi.excluir(modal.r.id))} />
      )}
    </div>
  )
}

function ModalSimNao({ titulo, texto, botao, perigo, onClose, onConfirmar }: {
  titulo: string; texto: string; botao: string; perigo?: boolean; onClose: () => void; onConfirmar: () => Promise<void>
}) {
  const [enviando, setEnviando] = useState(false)
  return (
    <Modal open onClose={onClose} title={titulo} size="md">
      <p className="text-sm text-ds-mid">{texto}</p>
      <div className="mt-4 flex justify-end gap-2">
        <button onClick={onClose} className="rounded-lg border border-ds-border px-4 py-2 text-sm text-ds-mid hover:bg-ds-input">Cancelar</button>
        <button disabled={enviando} onClick={async () => { setEnviando(true); try { await onConfirmar() } finally { setEnviando(false) } }}
          className={`rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:opacity-60 ${perigo ? 'bg-red-600 hover:bg-red-700' : 'bg-emerald-600 hover:bg-emerald-700'}`}>
          {enviando ? 'Aguarde...' : botao}
        </button>
      </div>
    </Modal>
  )
}
