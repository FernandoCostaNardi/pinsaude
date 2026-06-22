import { useEffect, useState } from 'react'
import {
  AlertTriangle, CheckCircle, ChevronDown, ChevronRight, Plus, Pencil, X,
} from 'lucide-react'
import {
  getParametrosFiscais, createParametroFiscal, verificarProximaCompetencia,
  getRegrasEquiparacao, createRegraEquiparacao, updateRegraEquiparacao,
  getServicos,
  type ParametroFiscalV2, type RegraEquiparacao, type ServicoLc116,
} from '../api/fiscalApi'

type Tab = 'aliquotas' | 'equiparacao' | 'servicos'

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
      <p className="text-sm text-ds-mid">
        Catálogo de serviços médicos conforme LC 116/2003. Alíquotas padrão — ajustes por competência são feitos na aba "Alíquotas".
      </p>
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

// ─── Página Principal ──────────────────────────────────────────────────────────

export function ConfiguracaoFiscalPage() {
  const [tab, setTab] = useState<Tab>('aliquotas')

  const tabs: { id: Tab; label: string }[] = [
    { id: 'aliquotas',   label: 'Alíquotas por Competência' },
    { id: 'equiparacao', label: 'Regras de Equiparação'     },
    { id: 'servicos',    label: 'Serviços Cadastrados'      },
  ]

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-bold text-ds-nav">Configuração Fiscal</h1>
        <p className="text-sm text-ds-light mt-0.5">
          Alíquotas por competência, regras de equiparação hospitalar e catálogo de serviços LC 116.
          Acesso restrito aos perfis Contábil e Gestão.
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
        {tab === 'equiparacao' && <AbaEquiparacao />}
        {tab === 'servicos'    && <AbaServicos />}
      </div>
    </div>
  )
}
