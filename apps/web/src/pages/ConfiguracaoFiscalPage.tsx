import { useEffect, useState } from 'react'
import {
  AlertTriangle, CheckCircle, CheckCircle2, ChevronDown, ChevronUp,
  ChevronRight, Clock, Plus, Pencil, Settings2, X, Zap,
} from 'lucide-react'
import {
  getParametrosFiscais, createParametroFiscal, verificarProximaCompetencia,
  getRegrasEquiparacao, createRegraEquiparacao, updateRegraEquiparacao,
  getServicos,
  type ParametroFiscalV2, type RegraEquiparacao, type ServicoLc116,
} from '../api/fiscalApi'
import { ParametroFiscal, IbsCbsRequest, parametrosFiscaisApi } from '../api/parametrosFiscaisApi'

type Tab = 'aliquotas' | 'ibscbs' | 'equiparacao' | 'servicos'

const TRIBUTOS = ['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'] as const
const fmtPct = (v: number | null) =>
  v == null ? '—' : `${(v * 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}%`
const fmtComp = (comp: string) => {
  const [y, m] = comp.split('-')
  return `${['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez'][+m - 1]}/${y}`
}
const fmtDate = (iso: string) =>
  new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

// ─── Aba Alíquotas ────────────────────────────────────────────────────────────

function AbaAliquotas() {
  const [aliquotas, setAliquotas] = useState<ParametroFiscalV2[]>([])
  const [alertaMissing, setAlertaMissing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState('')
  const [showForm, setShowForm] = useState(false)

  const [fTributo, setFTributo] = useState<string>('ISS')
  const [fInicio, setFInicio] = useState('')
  const [fFim, setFFim] = useState('')
  const [fValor, setFValor] = useState('')
  const [fDesc, setFDesc] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveErro, setSaveErro] = useState('')

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    try {
      const [lista, verif] = await Promise.all([
        getParametrosFiscais(),
        verificarProximaCompetencia(),
      ])
      setAliquotas(lista)
      setAlertaMissing(!verif.proximaCompetenciaConfigurada)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro')
    } finally { setLoading(false) }
  }

  async function handleSalvar() {
    if (!fInicio || !fValor) { setSaveErro('Preencha tributo, competência e alíquota.'); return }
    setSaving(true); setSaveErro('')
    try {
      await createParametroFiscal({
        tipoTributo: fTributo,
        competenciaInicio: fInicio,
        competenciaFim: fFim || null,
        valorAliquota: parseFloat(fValor) / 100,
        descricao: fDesc || null,
      })
      setShowForm(false); setFTributo('ISS'); setFInicio(''); setFFim(''); setFValor(''); setFDesc('')
      await load()
    } catch (e: unknown) { setSaveErro(e instanceof Error ? e.message : 'Erro') }
    finally { setSaving(false) }
  }

  const grouped = TRIBUTOS.map(t => ({
    tributo: t,
    items: aliquotas.filter(a => a.tipoTributo === t),
  }))

  if (loading) return <p className="text-ds-mid text-sm py-8 text-center">Carregando...</p>
  if (erro) return <p className="text-red-500 text-sm py-4">{erro}</p>

  return (
    <div className="space-y-4">
      {alertaMissing && (
        <div className="flex items-center gap-2 px-3 py-2 bg-yellow-50 border border-yellow-300 rounded-lg text-yellow-800 text-sm">
          <AlertTriangle size={16} className="shrink-0 text-yellow-500" />
          <span>
            <strong>Atenção:</strong> a próxima competência ({(() => {
              const d = new Date(); d.setMonth(d.getMonth() + 1)
              return `${d.toLocaleString('pt-BR', { month: 'short' })}/${d.getFullYear()}`
            })()}) não possui alíquotas configuradas para todos os tributos.
          </span>
        </div>
      )}

      <div className="flex justify-between items-center">
        <p className="text-sm text-ds-mid">
          Alíquotas em <span className="font-semibold">fração decimal</span> (ex: 0.0200 = 2%).
          Novas vigências não afetam cálculos de competências anteriores.
        </p>
        <button
          onClick={() => { setShowForm(v => !v); setSaveErro('') }}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-600 transition-colors"
        >
          <Plus size={15} /> Nova Alíquota
        </button>
      </div>

      {showForm && (
        <div className="border border-ds-border rounded-xl p-4 bg-ds-input space-y-3">
          <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
            <div>
              <label className="text-xs font-semibold text-ds-mid block mb-1">Tributo *</label>
              <select value={fTributo} onChange={e => setFTributo(e.target.value)}
                className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm bg-white">
                {TRIBUTOS.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="text-xs font-semibold text-ds-mid block mb-1">Início * (YYYY-MM)</label>
              <input value={fInicio} onChange={e => setFInicio(e.target.value)}
                placeholder="2025-01" maxLength={7}
                className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm" />
            </div>
            <div>
              <label className="text-xs font-semibold text-ds-mid block mb-1">Fim (YYYY-MM)</label>
              <input value={fFim} onChange={e => setFFim(e.target.value)}
                placeholder="2025-12 (opcional)" maxLength={7}
                className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm" />
            </div>
            <div>
              <label className="text-xs font-semibold text-ds-mid block mb-1">Alíquota % *</label>
              <input value={fValor} onChange={e => setFValor(e.target.value)}
                placeholder="2.00" type="number" step="0.01" min="0" max="100"
                className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm" />
            </div>
            <div>
              <label className="text-xs font-semibold text-ds-mid block mb-1">Descrição</label>
              <input value={fDesc} onChange={e => setFDesc(e.target.value)}
                placeholder="Observação (opcional)"
                className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm" />
            </div>
          </div>
          {saveErro && <p className="text-red-500 text-xs">{saveErro}</p>}
          <div className="flex gap-2">
            <button onClick={handleSalvar} disabled={saving}
              className="px-4 py-1.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors">
              {saving ? 'Salvando...' : 'Salvar'}
            </button>
            <button onClick={() => setShowForm(false)}
              className="px-4 py-1.5 border border-ds-border text-ds-mid rounded-lg text-sm hover:bg-ds-input transition-colors">
              Cancelar
            </button>
          </div>
        </div>
      )}

      {grouped.map(({ tributo, items }) => (
        <div key={tributo} className="border border-ds-border rounded-xl overflow-hidden">
          <div className="bg-ds-input px-4 py-2 border-b border-ds-border flex items-center justify-between">
            <span className="font-semibold text-sm text-ds-nav">{tributo}</span>
            {items.length === 0 && (
              <span className="text-xs text-yellow-600 font-medium">⚠ Sem alíquota configurada</span>
            )}
          </div>
          {items.length > 0 ? (
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-xs text-ds-mid uppercase">
                <tr>
                  <th className="px-4 py-2 text-left">Início</th>
                  <th className="px-4 py-2 text-left">Fim</th>
                  <th className="px-4 py-2 text-right">Alíquota</th>
                  <th className="px-4 py-2 text-left">Descrição</th>
                  <th className="px-4 py-2 text-left">Criado por</th>
                  <th className="px-4 py-2 text-left">Data</th>
                </tr>
              </thead>
              <tbody>
                {items.map(a => (
                  <tr key={a.id} className="border-t border-ds-border hover:bg-ds-input/30">
                    <td className="px-4 py-2 font-medium">{fmtComp(a.competenciaInicio)}</td>
                    <td className="px-4 py-2 text-ds-mid">{a.competenciaFim ? fmtComp(a.competenciaFim) : '—'}</td>
                    <td className="px-4 py-2 text-right font-semibold text-primary">{fmtPct(a.valorAliquota)}</td>
                    <td className="px-4 py-2 text-ds-mid text-xs">{a.descricao ?? '—'}</td>
                    <td className="px-4 py-2 text-ds-light text-xs">{a.createdBy ?? '—'}</td>
                    <td className="px-4 py-2 text-ds-light text-xs">{fmtDate(a.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="px-4 py-3 text-sm text-ds-light italic">Nenhuma alíquota cadastrada.</p>
          )}
        </div>
      ))}
    </div>
  )
}

// ─── Aba Regras de Equiparação ────────────────────────────────────────────────

function AbaEquiparacao() {
  const [regras, setRegras] = useState<RegraEquiparacao[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState('')
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [editing, setEditing] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)

  const [fCnae, setFCnae] = useState('')
  const [fLc116, setFLc116] = useState('')
  const [fEquiparado, setFEquiparado] = useState(false)
  const [fRegime, setFRegime] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveErro, setSaveErro] = useState('')

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    try { setRegras(await getRegrasEquiparacao()) }
    catch (e: unknown) { setErro(e instanceof Error ? e.message : 'Erro') }
    finally { setLoading(false) }
  }

  function toggleExpand(id: string) {
    setExpanded(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s })
  }

  function iniciarEdicao(r: RegraEquiparacao) {
    setEditing(r.id); setFCnae(r.cnae); setFLc116(r.codigoLc116)
    setFEquiparado(r.indicadorEquiparacao); setFRegime(r.regimePresuncao ?? '')
    setSaveErro(''); setShowForm(false)
  }

  async function handleSalvar() {
    setSaving(true); setSaveErro('')
    try {
      const req = { cnae: fCnae, codigoLc116: fLc116, indicadorEquiparacao: fEquiparado, regimePresuncao: fRegime || null }
      if (editing) {
        await updateRegraEquiparacao(editing, req)
      } else {
        await createRegraEquiparacao(req)
      }
      setEditing(null); setShowForm(false); resetForm(); await load()
    } catch (e: unknown) { setSaveErro(e instanceof Error ? e.message : 'Erro') }
    finally { setSaving(false) }
  }

  function resetForm() { setFCnae(''); setFLc116(''); setFEquiparado(false); setFRegime('') }

  if (loading) return <p className="text-ds-mid text-sm py-8 text-center">Carregando...</p>
  if (erro) return <p className="text-red-500 text-sm py-4">{erro}</p>

  const FormInline = () => (
    <div className="border border-ds-border rounded-xl p-4 bg-ds-input space-y-3">
      <h4 className="text-sm font-semibold text-ds-nav">{editing ? 'Editar Regra' : 'Nova Regra'}</h4>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div>
          <label className="text-xs font-semibold text-ds-mid block mb-1">CNAE *</label>
          <input value={fCnae} onChange={e => setFCnae(e.target.value)} disabled={!!editing}
            placeholder="8610100" className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm disabled:bg-gray-100" />
        </div>
        <div>
          <label className="text-xs font-semibold text-ds-mid block mb-1">Código LC 116 *</label>
          <input value={fLc116} onChange={e => setFLc116(e.target.value)} disabled={!!editing}
            placeholder="4.02" className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm disabled:bg-gray-100" />
        </div>
        <div className="flex items-end gap-2">
          <label className="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" checked={fEquiparado} onChange={e => setFEquiparado(e.target.checked)}
              className="w-4 h-4 accent-primary" />
            <span className="text-sm">Equiparado</span>
          </label>
        </div>
        <div>
          <label className="text-xs font-semibold text-ds-mid block mb-1">Regime Presunção</label>
          <select value={fRegime} onChange={e => setFRegime(e.target.value)} disabled={!fEquiparado}
            className="w-full border border-ds-border rounded-lg px-2 py-1.5 text-sm disabled:bg-gray-100">
            <option value="">— selecione —</option>
            <option value="REDUZIDA">Reduzida</option>
            <option value="CHEIA">Cheia</option>
          </select>
        </div>
      </div>
      {saveErro && <p className="text-red-500 text-xs">{saveErro}</p>}
      <div className="flex gap-2">
        <button onClick={handleSalvar} disabled={saving}
          className="px-4 py-1.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors">
          {saving ? 'Salvando...' : 'Salvar'}
        </button>
        <button onClick={() => { setEditing(null); setShowForm(false); resetForm() }}
          className="px-4 py-1.5 border border-ds-border text-ds-mid rounded-lg text-sm hover:bg-ds-input transition-colors">
          Cancelar
        </button>
      </div>
    </div>
  )

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <p className="text-sm text-ds-mid">
          Configure quais pares CNAE + Código LC 116 são equiparados a estabelecimento hospitalar.
        </p>
        <button onClick={() => { setShowForm(v => !v); setEditing(null); resetForm() }}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-600 transition-colors">
          <Plus size={15} /> Adicionar Regra
        </button>
      </div>

      {(showForm || editing) && !editing && <FormInline />}

      {regras.length === 0 ? (
        <p className="text-sm text-ds-light italic py-8 text-center">Nenhuma regra cadastrada.</p>
      ) : (
        <div className="border border-ds-border rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-xs text-ds-mid uppercase">
              <tr>
                <th className="w-8" />
                <th className="px-4 py-2 text-left">CNAE</th>
                <th className="px-4 py-2 text-left">Código LC 116</th>
                <th className="px-4 py-2 text-center">Equiparado</th>
                <th className="px-4 py-2 text-left">Regime</th>
                <th className="px-4 py-2 text-left">Cadastrado em</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {regras.flatMap(r => {
                const isExp = expanded.has(r.id)
                const isEdit = editing === r.id
                return [
                  <tr key={r.id} className="border-t border-ds-border hover:bg-ds-input/30">
                    <td className="px-2 py-2 text-center">
                      <button onClick={() => toggleExpand(r.id)}
                        className="text-ds-light hover:text-ds-mid transition-colors">
                        {isExp ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                      </button>
                    </td>
                    <td className="px-4 py-2 font-mono text-xs">{r.cnae}</td>
                    <td className="px-4 py-2 font-mono text-xs">{r.codigoLc116}</td>
                    <td className="px-4 py-2 text-center">
                      {r.indicadorEquiparacao
                        ? <CheckCircle size={15} className="inline text-secondary" />
                        : <X size={15} className="inline text-ds-light" />}
                    </td>
                    <td className="px-4 py-2 text-xs">
                      {r.regimePresuncao
                        ? <span className="px-2 py-0.5 bg-primary-50 text-primary-700 rounded text-[11px] font-medium">{r.regimePresuncao}</span>
                        : '—'}
                    </td>
                    <td className="px-4 py-2 text-ds-light text-xs">{fmtDate(r.createdAt)}</td>
                    <td className="px-4 py-2">
                      <button onClick={() => iniciarEdicao(r)}
                        className="p-1 text-ds-light hover:text-primary transition-colors" title="Editar">
                        <Pencil size={13} />
                      </button>
                    </td>
                  </tr>,
                  isEdit && (
                    <tr key={`edit-${r.id}`} className="border-t border-ds-border bg-yellow-50">
                      <td colSpan={7} className="px-4 py-3"><FormInline /></td>
                    </tr>
                  ),
                  isExp && !isEdit && (
                    <tr key={`hist-${r.id}`} className="border-t border-ds-border bg-gray-50">
                      <td colSpan={7} className="px-6 py-3">
                        <p className="text-xs font-semibold text-ds-mid mb-2">Histórico de Alterações</p>
                        {r.historico.length === 0 ? (
                          <p className="text-xs text-ds-light italic">Nenhuma alteração registrada.</p>
                        ) : (
                          <table className="text-xs w-full">
                            <thead><tr className="text-ds-light">
                              <th className="text-left pb-1 pr-4">Campo</th>
                              <th className="text-left pb-1 pr-4">Antes</th>
                              <th className="text-left pb-1 pr-4">Depois</th>
                              <th className="text-left pb-1 pr-4">Por</th>
                              <th className="text-left pb-1">Data</th>
                            </tr></thead>
                            <tbody>
                              {r.historico.map(h => (
                                <tr key={h.id} className="border-t border-gray-200">
                                  <td className="py-1 pr-4 font-medium text-ds-nav">{h.campoAlterado}</td>
                                  <td className="py-1 pr-4 text-red-500">{h.valorAnterior ?? '—'}</td>
                                  <td className="py-1 pr-4 text-secondary-600">{h.valorNovo ?? '—'}</td>
                                  <td className="py-1 pr-4 text-ds-light">{h.alteradoPor ?? '—'}</td>
                                  <td className="py-1 text-ds-light">{fmtDate(h.alteradoEm)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        )}
                      </td>
                    </tr>
                  ),
                ].filter(Boolean)
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

// ─── Aba Serviços Cadastrados ──────────────────────────────────────────────────

function AbaServicos() {
  const [servicos, setServicos] = useState<ServicoLc116[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState('')

  useEffect(() => {
    getServicos()
      .then(setServicos)
      .catch(e => setErro(e instanceof Error ? e.message : 'Erro'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="text-ds-mid text-sm py-8 text-center">Carregando...</p>
  if (erro) return <p className="text-red-500 text-sm py-4">{erro}</p>

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        <strong>Estes são os valores padrão do catálogo LC 116/2003</strong> — fixos por tipo de serviço.
        As alíquotas efetivas usadas nos cálculos fiscais e no preview de produção são as configuradas
        na aba <strong>"Alíquotas por Competência"</strong>, que substituem estes valores padrão.
      </div>
      <div className="border border-ds-border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-ds-mid uppercase">
            <tr>
              <th className="px-4 py-2 text-left">Código LC 116</th>
              <th className="px-4 py-2 text-left">Descrição</th>
              <th className="px-4 py-2 text-right">ISS</th>
              <th className="px-4 py-2 text-right">IR</th>
              <th className="px-4 py-2 text-right">CSLL</th>
              <th className="px-4 py-2 text-right">PIS</th>
              <th className="px-4 py-2 text-right">COFINS</th>
            </tr>
          </thead>
          <tbody>
            {servicos.map(s => (
              <tr key={s.id} className="border-t border-ds-border hover:bg-ds-input/30">
                <td className="px-4 py-2 font-mono text-xs font-semibold">{s.codigoLc116}</td>
                <td className="px-4 py-2 text-ds-nav">{s.descricaoPadrao}</td>
                <td className="px-4 py-2 text-right text-xs">{fmtPct(s.aliquotaIss / 100)}</td>
                <td className="px-4 py-2 text-right text-xs">{fmtPct(s.aliquotaIr / 100)}</td>
                <td className="px-4 py-2 text-right text-xs">{fmtPct(s.aliquotaCsll / 100)}</td>
                <td className="px-4 py-2 text-right text-xs">{fmtPct(s.aliquotaPis / 100)}</td>
                <td className="px-4 py-2 text-right text-xs">{fmtPct(s.aliquotaCofins / 100)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ─── Aba IBS/CBS 2027 ─────────────────────────────────────────────────────────

function gerarCompetencias(n = 24): string[] {
  const result: string[] = []
  const now = new Date()
  for (let i = -18; i < n; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    result.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return result.sort()
}

const COMPETENCIAS_IBS = gerarCompetencias(24)

function RegimeBadge({ ibsCbsAtivo, homologado }: { ibsCbsAtivo: boolean; homologado: boolean }) {
  if (ibsCbsAtivo) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold bg-violet-50 text-violet-700">
        <Zap size={10} /> IBS/CBS 2027
        {!homologado && <span className="ml-0.5 text-amber-500">• A homologar</span>}
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold bg-primary-50 text-primary">
      Regime Atual
      {!homologado && <span className="ml-0.5 text-amber-500">• A homologar</span>}
    </span>
  )
}

function ParametroIbsCard({
  param, onHomologar, homologando,
}: {
  param: ParametroFiscal
  onHomologar: (id: string) => void
  homologando: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  return (
    <div className={[
      'bg-white rounded-xl border shadow-sm overflow-hidden',
      param.ibsCbsAtivo ? 'border-violet-200' : 'border-ds-border',
      !param.homologado ? 'ring-1 ring-amber-300/60' : '',
    ].join(' ')}>
      <div className="flex items-center gap-3 px-5 py-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-bold text-ds-mid">{fmtComp(param.competenciaInicio)}</span>
            <span className="text-xs text-ds-light">vigência a partir de</span>
            <span className="text-xs font-mono text-ds-mid">{param.vigenciaInicio}</span>
            <RegimeBadge ibsCbsAtivo={param.ibsCbsAtivo} homologado={param.homologado} />
          </div>
          {param.observacoes && (
            <p className="text-xs text-ds-light mt-1 truncate">{param.observacoes}</p>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {param.homologado ? (
            <span className="inline-flex items-center gap-1 text-xs text-green-600 font-medium">
              <CheckCircle2 size={13} /> Homologado
            </span>
          ) : (
            <button
              onClick={() => onHomologar(param.id)}
              disabled={homologando}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg border border-amber-300 text-amber-700 hover:bg-amber-50 disabled:opacity-50 transition-colors"
            >
              <CheckCircle2 size={13} /> Homologar
            </button>
          )}
          <button
            onClick={() => setExpanded(e => !e)}
            className="p-1.5 rounded-lg text-ds-light hover:bg-ds-input transition-colors"
          >
            {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          </button>
        </div>
      </div>
      {expanded && (
        <div className="border-t border-ds-border px-5 py-4 bg-ds-surface">
          {param.ibsCbsAtivo ? (
            <div className="bg-violet-50 border border-violet-200 rounded-lg p-4">
              <p className="text-xs font-semibold text-violet-700 uppercase tracking-wide mb-2">
                Regime IBS/CBS (Reforma Tributária 2027)
              </p>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-xs text-ds-light">Alíquota Base IBS/CBS</p>
                  <p className="font-semibold text-violet-800">{fmtPct(param.aliqIbsCbs)}</p>
                </div>
                <div>
                  <p className="text-xs text-ds-light">Redução Saúde (NBS 200029)</p>
                  <p className="font-semibold text-violet-800">{fmtPct(param.reducaoIbsCbsSaude)}</p>
                </div>
                <div>
                  <p className="text-xs text-ds-light">Alíquota Efetiva Saúde</p>
                  <p className="font-bold text-violet-900 text-base">{fmtPct(param.aliqIbsCbsEfetiva)}</p>
                </div>
                <div>
                  <p className="text-xs text-ds-light">ISS Municipal</p>
                  <p className="font-semibold text-ds-mid">{fmtPct(param.aliqIss)}</p>
                </div>
              </div>
              <p className="text-xs text-violet-600 mt-2 italic">
                IR, CSLL, PIS e COFINS substituídos pelo IBS/CBS neste regime.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-5 gap-3 text-center">
              {[
                { label: 'ISS',    value: param.aliqIss    },
                { label: 'IR',     value: param.aliqIr     },
                { label: 'CSLL',   value: param.aliqCsll   },
                { label: 'PIS',    value: param.aliqPis    },
                { label: 'COFINS', value: param.aliqCofins },
              ].map(({ label, value }) => (
                <div key={label} className="bg-white border border-ds-border rounded-lg p-3">
                  <p className="text-xs text-ds-light font-medium">{label}</p>
                  <p className="text-base font-bold text-ds-mid mt-0.5">{fmtPct(value)}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function AbaIbsCbs() {
  const [params, setParams]           = useState<ParametroFiscal[]>([])
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState<string | null>(null)
  const [showForm, setShowForm]       = useState(false)
  const [saving, setSaving]           = useState(false)
  const [homologando, setHomologando] = useState(false)
  const [competencia, setCompetencia] = useState('2027-01')
  const [aliqIbsCbs, setAliqIbsCbs]   = useState('0.01')
  const [reducao, setReducao]         = useState('0.60')
  const [aliqIss, setAliqIss]         = useState('0.02')
  const [obs, setObs]                 = useState('')
  const [formError, setFormError]     = useState('')

  function load() {
    setLoading(true)
    parametrosFiscaisApi.listar()
      .then(r => setParams(Array.isArray(r) ? r : [r]))
      .catch(e => setError(e instanceof Error ? e.message : 'Erro'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  async function handleSalvar() {
    setSaving(true); setFormError('')
    const aliqNum    = parseFloat(aliqIbsCbs) || 0
    const reducaoNum = parseFloat(reducao) || 0
    try {
      await parametrosFiscaisApi.criarIbsCbs({
        competenciaInicio: competencia,
        aliqIbsCbs: aliqNum,
        reducaoSaude: reducaoNum,
        aliqIss: parseFloat(aliqIss) || 0.02,
        observacoes: obs || undefined,
      } as IbsCbsRequest)
      setShowForm(false)
      setAliqIbsCbs('0.01'); setReducao('0.60'); setAliqIss('0.02'); setObs('')
      load()
    } catch (e: unknown) {
      setFormError(e instanceof Error ? e.message : 'Erro ao salvar parâmetro')
    } finally { setSaving(false) }
  }

  async function handleHomologar(id: string) {
    setHomologando(true)
    try {
      const updated = await parametrosFiscaisApi.homologar(id)
      setParams(ps => ps.map(p => p.id === id ? updated : p))
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Erro ao homologar')
    } finally { setHomologando(false) }
  }

  const pendentes  = params.filter(p => !p.homologado).length
  const ibsCbsList = params.filter(p => p.ibsCbsAtivo)
  const aliqNum    = parseFloat(aliqIbsCbs) || 0
  const reducaoNum = parseFloat(reducao) || 0
  const efetiva    = aliqNum * (1 - reducaoNum)

  if (loading) return <p className="text-ds-mid text-sm py-8 text-center">Carregando...</p>

  return (
    <div className="space-y-4">
      {error && (
        <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
          <AlertTriangle size={15} className="shrink-0" /> {error}
          <button onClick={() => setError(null)} className="ml-auto"><X size={14} /></button>
        </div>
      )}

      <div className="flex items-center justify-between">
        <p className="text-sm text-ds-mid">
          Parâmetros da Reforma Tributária 2027 (IBS/CBS). Vigências a partir de 2027-01
          substituem IR, CSLL, PIS e COFINS pelo IBS/CBS efetivo com redução de saúde (NBS 200029).
        </p>
        <button
          onClick={() => setShowForm(v => !v)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-violet-600 text-white rounded-lg text-sm font-medium hover:bg-violet-700 transition-colors shrink-0 ml-4"
        >
          <Plus size={15} /> Novo IBS/CBS
        </button>
      </div>

      {pendentes > 0 && (
        <div className="flex items-center gap-3 bg-amber-50 border border-amber-200 rounded-xl px-5 py-3 text-sm text-amber-800">
          <AlertTriangle size={16} className="text-amber-500 shrink-0" />
          <span>
            <strong>{pendentes} parâmetro{pendentes > 1 ? 's' : ''}</strong> pendente{pendentes > 1 ? 's' : ''} de homologação.
            A contabilidade deve confirmar antes do go-live.
          </span>
        </div>
      )}

      {ibsCbsList.length > 0 && (
        <div className="bg-violet-50 border border-violet-200 rounded-xl px-5 py-4 flex items-center gap-6">
          <Zap size={22} className="text-violet-600 shrink-0" />
          <div>
            <p className="text-xs font-semibold text-violet-700 uppercase tracking-wide">
              Reforma Tributária 2027 configurada
            </p>
            <p className="text-sm text-violet-800 mt-0.5">
              {ibsCbsList.length} parâmetro{ibsCbsList.length > 1 ? 's' : ''} IBS/CBS ·{' '}
              alíquota efetiva saúde: <strong>{fmtPct(ibsCbsList[0].aliqIbsCbsEfetiva)}</strong>
            </p>
            <p className="text-xs text-violet-500 mt-1">
              Motor seleciona regime pela competência. Até 2026-12 usa regime atual; 2027-01+ usa IBS/CBS.
            </p>
          </div>
          <div className="ml-auto shrink-0">
            {ibsCbsList[0].homologado
              ? <span className="inline-flex items-center gap-1 text-green-700 text-xs font-semibold"><CheckCircle2 size={14} /> Homologado</span>
              : <span className="inline-flex items-center gap-1 text-amber-600 text-xs font-semibold"><Clock size={14} /> Aguardando homologação</span>
            }
          </div>
        </div>
      )}

      {showForm && (
        <div className="bg-violet-50 border border-violet-200 rounded-xl p-5 space-y-4">
          <div className="flex items-center gap-2">
            <Zap size={16} className="text-violet-600" />
            <h3 className="font-semibold text-violet-800 text-sm">Novo Parâmetro IBS/CBS — Reforma Tributária 2027</h3>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-medium text-ds-mid">Competência de Início</label>
              <select value={competencia} onChange={e => setCompetencia(e.target.value)}
                className="w-full text-sm border border-ds-border rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
                {COMPETENCIAS_IBS.map(c => <option key={c} value={c}>{fmtComp(c)} ({c})</option>)}
              </select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-ds-mid">ISS Municipal (fração decimal)</label>
              <input type="number" step="0.0001" min="0" max="1" value={aliqIss}
                onChange={e => setAliqIss(e.target.value)}
                className="w-full text-sm border border-ds-border rounded-lg px-3 py-2" />
              <p className="text-xs text-ds-light">Ex: 0.02 = 2%</p>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-ds-mid">Alíquota Base IBS/CBS (fração decimal)</label>
              <input type="number" step="0.0001" min="0" max="1" value={aliqIbsCbs}
                onChange={e => setAliqIbsCbs(e.target.value)}
                className="w-full text-sm border border-ds-border rounded-lg px-3 py-2" />
              <p className="text-xs text-ds-light">Fase-teste 2027: 0.01 (1%)</p>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-ds-mid">Redução para Saúde — NBS 200029 (fração)</label>
              <input type="number" step="0.0001" min="0" max="1" value={reducao}
                onChange={e => setReducao(e.target.value)}
                className="w-full text-sm border border-ds-border rounded-lg px-3 py-2" />
              <p className="text-xs text-ds-light">Padrão: 0.60 (60%)</p>
            </div>
          </div>
          <div className="bg-violet-100 rounded-lg px-4 py-3 flex items-center gap-4">
            <div>
              <p className="text-xs text-violet-600 font-medium">Alíquota Efetiva Saúde</p>
              <p className="text-xl font-bold text-violet-900">{(efetiva * 100).toFixed(4).replace(/\.?0+$/, '')}%</p>
            </div>
            <div className="text-xs text-violet-600 italic">
              = {aliqIbsCbs} × (1 − {reducao}) = {efetiva.toFixed(6)}
            </div>
            <div className="ml-auto text-xs text-violet-500">IR, CSLL, PIS, COFINS = 0% neste regime</div>
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-ds-mid">Observações</label>
            <textarea value={obs} onChange={e => setObs(e.target.value)} rows={2}
              placeholder="Ex: Alíquotas conforme publicação do Comitê Gestor IBS..."
              className="w-full text-sm border border-ds-border rounded-lg px-3 py-2 resize-none" />
          </div>
          {formError && <p className="text-red-500 text-xs">{formError}</p>}
          <div className="flex justify-end gap-2">
            <button onClick={() => { setShowForm(false); setFormError('') }} disabled={saving}
              className="px-4 py-1.5 border border-ds-border text-ds-mid rounded-lg text-sm hover:bg-ds-input transition-colors">
              Cancelar
            </button>
            <button onClick={handleSalvar} disabled={saving || !aliqNum || !reducaoNum}
              className="flex items-center gap-1 px-4 py-1.5 bg-violet-600 text-white rounded-lg text-sm font-medium hover:bg-violet-700 disabled:opacity-50 transition-colors">
              <Zap size={14} />
              {saving ? 'Salvando...' : 'Salvar Parâmetro IBS/CBS'}
            </button>
          </div>
        </div>
      )}

      <div>
        <div className="flex items-center gap-2 mb-3">
          <Settings2 size={15} className="text-ds-light" />
          <h2 className="text-sm font-semibold text-ds-mid uppercase tracking-wide">
            Histórico de Parâmetros ({params.length})
          </h2>
        </div>
        {params.length === 0 ? (
          <div className="border border-ds-border rounded-xl p-12 text-center">
            <Settings2 size={36} className="mx-auto text-ds-light opacity-40 mb-3" />
            <p className="text-ds-light text-sm">Nenhum parâmetro IBS/CBS configurado.</p>
            <p className="text-ds-light text-xs mt-1">
              Clique em "Novo IBS/CBS" para parametrizar a Reforma Tributária 2027.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {params.map(p => (
              <ParametroIbsCard key={p.id} param={p} onHomologar={handleHomologar} homologando={homologando} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Página Principal ──────────────────────────────────────────────────────────

export function ConfiguracaoFiscalPage() {
  const [tab, setTab] = useState<Tab>('aliquotas')

  const tabs: { id: Tab; label: string }[] = [
    { id: 'aliquotas',   label: 'Alíquotas por Competência' },
    { id: 'ibscbs',      label: 'Parâm. IBS/CBS 2027'       },
    { id: 'equiparacao', label: 'Regras de Equiparação'     },
    { id: 'servicos',    label: 'Serviços Cadastrados'      },
  ]

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-bold text-ds-nav">Fiscal</h1>
        <p className="text-sm text-ds-light mt-0.5">
          Alíquotas por competência · Parâmetros IBS/CBS 2027 · Equiparação hospitalar · Catálogo LC 116.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-ds-border gap-1">
        {tabs.map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={[
              'px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors',
              tab === t.id
                ? 'border-primary text-primary'
                : 'border-transparent text-ds-mid hover:text-ds-nav',
            ].join(' ')}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Conteúdo */}
      <div>
        {tab === 'aliquotas'   && <AbaAliquotas />}
        {tab === 'ibscbs'      && <AbaIbsCbs />}
        {tab === 'equiparacao' && <AbaEquiparacao />}
        {tab === 'servicos'    && <AbaServicos />}
      </div>
    </div>
  )
}
