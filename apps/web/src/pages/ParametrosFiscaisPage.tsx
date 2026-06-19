import { useEffect, useState } from 'react'
import {
  Settings2, Plus, CheckCircle2, Clock, AlertTriangle,
  ChevronDown, ChevronUp, Zap, FileText,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import { ParametroFiscal, IbsCbsRequest, parametrosFiscaisApi } from '../api/parametrosFiscaisApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function fmtPct(v: number | null | undefined): string {
  if (v == null) return '—'
  return `${(Number(v) * 100).toFixed(4).replace(/\.?0+$/, '')}%`
}

function fmtComp(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function gerarCompetencias(n = 24): string[] {
  const result: string[] = []
  const now = new Date()
  // incluir competências futuras para 2027
  for (let i = -18; i < n; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    result.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return result.sort()
}

const COMPETENCIAS = gerarCompetencias(24)

// ─── Sub-components ──────────────────────────────────────────────────────────

function RegimeBadge({ ibsCbsAtivo, homologado }: { ibsCbsAtivo: boolean; homologado: boolean }) {
  if (ibsCbsAtivo) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold bg-violet-50 text-violet-700">
        <Zap size={10} />
        IBS/CBS 2027
        {!homologado && <span className="ml-0.5 text-amber-500">• A homologar</span>}
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold bg-primary-50 text-primary">
      <FileText size={10} />
      Regime Atual
      {!homologado && <span className="ml-0.5 text-amber-500">• A homologar</span>}
    </span>
  )
}

function EfetivaBadge({ efetiva }: { efetiva: number | null }) {
  if (efetiva == null) return null
  return (
    <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded bg-violet-100 text-violet-700 text-xs font-mono">
      efetiva {fmtPct(efetiva)}
    </span>
  )
}

// ─── Card de parâmetro ────────────────────────────────────────────────────────

function ParametroCard({
  param,
  onHomologar,
  homologando,
}: {
  param: ParametroFiscal
  onHomologar: (id: string) => void
  homologando: boolean
}) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className={`bg-white rounded-xl border shadow-sm overflow-hidden transition-all
      ${param.ibsCbsAtivo ? 'border-violet-200' : 'border-ds-border'}
      ${!param.homologado ? 'ring-1 ring-amber-300/60' : ''}
    `}>
      {/* Header */}
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
            <Button
              size="sm"
              variant="ghost"
              onClick={() => onHomologar(param.id)}
              disabled={homologando}
              className="text-amber-600 hover:text-amber-700 hover:bg-amber-50 border border-amber-300"
            >
              <CheckCircle2 size={13} className="mr-1" />
              Homologar
            </Button>
          )}
          <button
            onClick={() => setExpanded(e => !e)}
            className="p-1.5 rounded-lg text-ds-light hover:bg-ds-input transition-colors"
          >
            {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
          </button>
        </div>
      </div>

      {/* Expanded detail */}
      {expanded && (
        <div className="border-t border-ds-border px-5 py-4 bg-ds-surface">
          {param.ibsCbsAtivo ? (
            <div className="space-y-3">
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
            </div>
          ) : (
            <div className="grid grid-cols-5 gap-3 text-center">
              {[
                { label: 'ISS',    value: param.aliqIss },
                { label: 'IR',     value: param.aliqIr },
                { label: 'CSLL',   value: param.aliqCsll },
                { label: 'PIS',    value: param.aliqPis },
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

// ─── Formulário IBS/CBS ───────────────────────────────────────────────────────

function FormIbsCbs({
  onSalvar,
  onCancel,
  loading,
}: {
  onSalvar: (req: IbsCbsRequest) => void
  onCancel: () => void
  loading: boolean
}) {
  const [competencia, setCompetencia]   = useState('2027-01')
  const [aliqIbsCbs, setAliqIbsCbs]     = useState('0.01')
  const [reducao, setReducao]           = useState('0.60')
  const [aliqIss, setAliqIss]           = useState('0.02')
  const [obs, setObs]                   = useState('')

  const aliqNum   = parseFloat(aliqIbsCbs) || 0
  const reducaoNum = parseFloat(reducao) || 0
  const efetiva   = aliqNum * (1 - reducaoNum)

  function submit() {
    onSalvar({
      competenciaInicio: competencia,
      aliqIbsCbs: aliqNum,
      reducaoSaude: reducaoNum,
      aliqIss: parseFloat(aliqIss) || 0.02,
      observacoes: obs || undefined,
    })
  }

  const selectCls = "text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white"

  return (
    <div className="bg-violet-50 border border-violet-200 rounded-xl p-5 space-y-4">
      <div className="flex items-center gap-2">
        <Zap size={16} className="text-violet-600" />
        <h3 className="font-semibold text-violet-800 text-sm">Novo Parâmetro IBS/CBS — Reforma Tributária 2027</h3>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <label className="text-xs font-medium text-ds-mid">Competência de Início</label>
          <select value={competencia} onChange={e => setCompetencia(e.target.value)} className={selectCls + " w-full"}>
            {COMPETENCIAS.map(c => <option key={c} value={c}>{fmtComp(c)} ({c})</option>)}
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-xs font-medium text-ds-mid">ISS Municipal (fração)</label>
          <input type="number" step="0.0001" min="0" max="1" value={aliqIss}
            onChange={e => setAliqIss(e.target.value)}
            className="w-full text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30" />
          <p className="text-xs text-ds-light">Ex: 0.02 = 2%</p>
        </div>

        <div className="space-y-1">
          <label className="text-xs font-medium text-ds-mid">Alíquota Base IBS/CBS (fração)</label>
          <input type="number" step="0.0001" min="0" max="1" value={aliqIbsCbs}
            onChange={e => setAliqIbsCbs(e.target.value)}
            className="w-full text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30" />
          <p className="text-xs text-ds-light">Fase-teste 2027: 0.01 (1%)</p>
        </div>

        <div className="space-y-1">
          <label className="text-xs font-medium text-ds-mid">Redução para Saúde (NBS 200029)</label>
          <input type="number" step="0.0001" min="0" max="1" value={reducao}
            onChange={e => setReducao(e.target.value)}
            className="w-full text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30" />
          <p className="text-xs text-ds-light">Padrão: 0.60 (60%)</p>
        </div>
      </div>

      {/* Preview da alíquota efetiva */}
      <div className="bg-violet-100 rounded-lg px-4 py-3 flex items-center gap-4">
        <div>
          <p className="text-xs text-violet-600 font-medium">Alíquota Efetiva Saúde</p>
          <p className="text-xl font-bold text-violet-900">{(efetiva * 100).toFixed(4).replace(/\.?0+$/, '')}%</p>
        </div>
        <div className="text-xs text-violet-600 italic">
          = {aliqIbsCbs} × (1 − {reducao}) = {efetiva.toFixed(6)}
        </div>
        <div className="ml-auto text-xs text-violet-500">
          IR, CSLL, PIS, COFINS = 0% neste regime
        </div>
      </div>

      <div className="space-y-1">
        <label className="text-xs font-medium text-ds-mid">Observações</label>
        <textarea value={obs} onChange={e => setObs(e.target.value)} rows={2}
          placeholder="Ex: Alíquotas a homologar conforme publicação do Comitê Gestor IBS..."
          className="w-full text-sm border border-ds-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none" />
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={loading}>Cancelar</Button>
        <Button onClick={submit} disabled={loading || !aliqNum || !reducaoNum}>
          <Zap size={14} className="mr-1" />
          Salvar Parâmetro IBS/CBS
        </Button>
      </div>
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function ParametrosFiscaisPage() {
  const [params, setParams]       = useState<ParametroFiscal[]>([])
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState<string | null>(null)
  const [showForm, setShowForm]   = useState(false)
  const [saving, setSaving]       = useState(false)
  const [homologando, setHomologando] = useState(false)

  function load() {
    setLoading(true)
    parametrosFiscaisApi.listar()
      .then(r => setParams(Array.isArray(r) ? r : [r]))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  async function handleSalvarIbsCbs(req: IbsCbsRequest) {
    setSaving(true)
    setError(null)
    try {
      await parametrosFiscaisApi.criarIbsCbs(req)
      setShowForm(false)
      load()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Erro ao salvar parâmetro')
    } finally {
      setSaving(false)
    }
  }

  async function handleHomologar(id: string) {
    setHomologando(true)
    setError(null)
    try {
      const updated = await parametrosFiscaisApi.homologar(id)
      setParams(ps => ps.map(p => p.id === id ? updated : p))
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Erro ao homologar parâmetro')
    } finally {
      setHomologando(false)
    }
  }

  const pendentes = params.filter(p => !p.homologado).length
  const ibsCbs    = params.filter(p => p.ibsCbsAtivo)

  if (loading) return (
    <div className="flex items-center justify-center h-64"><Spinner size="lg" /></div>
  )

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-ds-mid">Parâmetros Fiscais</h1>
          <p className="text-sm text-ds-light mt-1">
            Configuração de alíquotas por vigência · Suporte à Reforma Tributária 2027 (IBS/CBS)
          </p>
        </div>
        <Button onClick={() => setShowForm(v => !v)}>
          <Plus size={15} className="mr-1" />
          Novo IBS/CBS
        </Button>
      </div>

      {error && <Alert variant="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Alertas de governança */}
      {pendentes > 0 && (
        <div className="flex items-center gap-3 bg-amber-50 border border-amber-200 rounded-xl px-5 py-3 text-sm text-amber-800">
          <AlertTriangle size={16} className="text-amber-500 shrink-0" />
          <span>
            <strong>{pendentes} parâmetro{pendentes > 1 ? 's' : ''}</strong> pendente{pendentes > 1 ? 's' : ''} de homologação.
            A contabilidade deve confirmar os valores antes do go-live.
          </span>
        </div>
      )}

      {/* Resumo IBS/CBS */}
      {ibsCbs.length > 0 && (
        <div className="bg-violet-50 border border-violet-200 rounded-xl px-5 py-4 flex items-center gap-6">
          <Zap size={22} className="text-violet-600 shrink-0" />
          <div>
            <p className="text-xs font-semibold text-violet-700 uppercase tracking-wide">
              Reforma Tributária 2027 configurada
            </p>
            <p className="text-sm text-violet-800 mt-0.5">
              {ibsCbs.length} parâmetro{ibsCbs.length > 1 ? 's' : ''} IBS/CBS ·{' '}
              alíquota efetiva saúde:{' '}
              <strong>{fmtPct(ibsCbs[0].aliqIbsCbsEfetiva)}</strong>
              <EfetivaBadge efetiva={ibsCbs[0].aliqIbsCbsEfetiva} />
            </p>
            <p className="text-xs text-violet-500 mt-1">
              Motor seleciona regime pela competência do fato gerador.
              Competências até 2026-12 usam o regime atual; 2027-01+ usam IBS/CBS.
            </p>
          </div>
          <div className="ml-auto">
            {ibsCbs[0].homologado
              ? <span className="inline-flex items-center gap-1 text-green-700 text-xs font-semibold"><CheckCircle2 size={14} /> Homologado</span>
              : <span className="inline-flex items-center gap-1 text-amber-600 text-xs font-semibold"><Clock size={14} /> Aguardando homologação</span>
            }
          </div>
        </div>
      )}

      {/* Formulário inline */}
      {showForm && (
        <FormIbsCbs
          onSalvar={handleSalvarIbsCbs}
          onCancel={() => setShowForm(false)}
          loading={saving}
        />
      )}

      {/* Lista de parâmetros */}
      <div>
        <div className="flex items-center gap-2 mb-3">
          <Settings2 size={15} className="text-ds-light" />
          <h2 className="text-sm font-semibold text-ds-mid uppercase tracking-wide">
            Histórico de Parâmetros ({params.length})
          </h2>
        </div>

        {params.length === 0 ? (
          <div className="bg-white rounded-xl border border-ds-border p-12 text-center">
            <Settings2 size={36} className="mx-auto text-ds-light opacity-40 mb-3" />
            <p className="text-ds-light text-sm">
              Nenhum parâmetro fiscal configurado.
            </p>
            <p className="text-ds-light text-xs mt-1">
              Clique em "Novo IBS/CBS" para parametrizar a Reforma Tributária 2027.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {params.map(p => (
              <ParametroCard
                key={p.id}
                param={p}
                onHomologar={handleHomologar}
                homologando={homologando}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
