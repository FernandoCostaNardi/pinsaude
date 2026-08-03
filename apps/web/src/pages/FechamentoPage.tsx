import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Search, Loader2, AlertCircle, CheckCircle2, FileText,
  ArrowRight, CalendarDays, PackageCheck, ChevronDown,
  ReceiptText, History, TriangleAlert,
} from 'lucide-react'
import { Button, Alert } from '@pinsaude/ui'
import { tomadoresApi, Tomador } from '../api/tomadoresApi'
import {
  fechamentosApi,
  FechamentoPreviewResp,
  FechamentoResp,
  ModalidadeDetalhe,
} from '../api/fechamentosApi'
import { useAuth } from '../auth/AuthContext'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function formatCompetenciaFull(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho',
                 'Julho','Agosto','Setembro','Outubro','Novembro','Dezembro']
  return `${meses[parseInt(mes, 10) - 1]} de ${ano}`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

function generateCompetencias(): string[] {
  const result: string[] = []
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    result.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return result
}

const COMPETENCIAS = generateCompetencias()

// ─── Helpers de formatação ────────────────────────────────────────────────────

function fmtNum(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtHoras(h: number | null): string {
  if (!h) return ''
  return h % 1 === 0 ? String(Math.round(h)) : h.toFixed(1).replace('.', ',')
}

// ─── Tabela de Modalidades ────────────────────────────────────────────────────

function TabelaModalidades({ modalidades }: { modalidades: ModalidadeDetalhe[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs border-collapse">
        <thead>
          <tr className="bg-gray-800 text-white">
            <th className="px-3 py-2 text-left font-semibold">TIPO DE SERVIÇO</th>
            <th className="px-2 py-2 text-center font-semibold w-10">H</th>
            <th className="px-2 py-2 text-right font-semibold w-24"></th>
            <th className="px-2 py-2 text-right font-semibold w-24">Deslocamento</th>
            <th className="px-2 py-2 text-right font-semibold w-24">VALOR</th>
            <th className="px-2 py-2 text-right font-semibold w-20">QUANT</th>
            <th className="px-3 py-2 text-right font-semibold w-28">TOTAL</th>
          </tr>
        </thead>
        <tbody>
          {modalidades.map((m, i) => {
            const isNoturno = m.turno === 'NOTURNO'
            const rowCls = m.fds
              ? 'bg-yellow-100 text-yellow-900'
              : isNoturno
              ? 'bg-gray-200 text-gray-800'
              : i % 2 === 0 ? 'bg-white' : 'bg-gray-50'
            const hasDesl = m.deslocamentoCentavos > 0
            return (
              <tr key={m.modalidadeId} className={`border-b border-gray-200 ${rowCls}`}>
                <td className="px-3 py-1.5 font-medium">{m.nome}</td>
                <td className="px-2 py-1.5 text-center tabular-nums">{fmtHoras(m.horas)}</td>
                <td className="px-2 py-1.5 text-right tabular-nums text-gray-500">
                  {hasDesl ? fmtNum(m.valorUnitarioCentavos) : ''}
                </td>
                <td className="px-2 py-1.5 text-right tabular-nums text-gray-500">
                  {hasDesl ? fmtNum(m.deslocamentoCentavos) : ''}
                </td>
                <td className="px-2 py-1.5 text-right tabular-nums font-medium">
                  {fmtNum(m.valorItemCentavos)}
                </td>
                <td className="px-2 py-1.5 text-right tabular-nums">
                  {m.quantidade > 0 ? `${m.quantidade},00` : '0,00'}
                </td>
                <td className="px-3 py-1.5 text-right tabular-nums font-semibold">
                  {m.totalCentavos > 0 ? fmtNum(m.totalCentavos) : '0,00'}
                </td>
              </tr>
            )
          })}
        </tbody>
        <tfoot>
          <tr className="border-t-2 border-gray-400">
            <td colSpan={6} />
            <td className="px-3 py-2 text-right font-bold tabular-nums text-sm">
              {fmtNum(modalidades.reduce((s, m) => s + m.totalCentavos, 0))}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

// ─── Totalizador por Grupo → Setor ────────────────────────────────────────────

function TotalizadorPorGrupo({ preview }: { preview: FechamentoPreviewResp }) {
  const totalGeral = preview.totalCentavos
  const nfIndex = useMemo(() => {
    const map: Record<string, number> = {}
    preview.grupos.forEach((g, i) => { map[g.grupoId] = i + 1 })
    return map
  }, [preview.grupos])

  return (
    <div className="text-xs">
      {preview.grupos.map(grupo => (
        <div key={grupo.grupoId} className="mb-1">
          {/* Nome do grupo como cabeçalho de seção */}
          <div className="px-3 py-1 text-[11px] font-bold text-ds-mid italic">
            {grupo.nome}
          </div>
          {/* Setores do grupo */}
          <div>
            {grupo.setores.map(setor => (
              <div key={setor.setorId}
                className="flex items-baseline justify-between px-6 py-0.5 border-b border-dotted border-gray-200">
                <span className={`text-ds-mid ${setor.setorNome.toUpperCase().includes('UTI') ? 'text-blue-600 font-medium' : ''}`}>
                  {setor.setorNome}
                </span>
                <span className="tabular-nums font-medium text-ds-text ml-4 shrink-0">
                  {fmtNum(setor.totalCentavos)}
                </span>
              </div>
            ))}
          </div>
          {/* Subtotal do grupo */}
          <div className="flex items-baseline justify-between px-6 py-1 font-bold text-sm border-b border-gray-300">
            <span />
            <span className="tabular-nums">{fmtNum(grupo.totalCentavos)}</span>
          </div>
        </div>
      ))}

      {/* Total Geral */}
      <div className="flex items-baseline justify-between px-6 py-2 mt-2 border-t-2 border-gray-400">
        <span className="font-bold italic text-sm">Total Geral</span>
        <span className="tabular-nums font-bold text-base">{fmtNum(totalGeral)}</span>
      </div>

      {/* Seção Fechamento (NF por grupo) */}
      {preview.grupos.length > 0 && (
        <div className="mt-4 border border-gray-300 rounded">
          <div className="bg-gray-100 px-4 py-1.5 text-center font-bold text-[11px] uppercase tracking-wide border-b border-gray-300">
            Fechamento
          </div>
          {preview.grupos.map(grupo => {
            const idx = nfIndex[grupo.grupoId]
            return (
              <div key={grupo.grupoId}
                className="flex items-baseline justify-between px-4 py-1 border-b border-dotted border-gray-200 last:border-b-0">
                <span className="text-ds-mid">
                  NF{idx} {grupo.nome}
                </span>
                <span className="tabular-nums font-semibold text-ds-text ml-4 shrink-0">
                  {fmtNum(grupo.totalCentavos)}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ─── Main Page ─────────────────────────────────────────────────────────────────

export function FechamentoPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const roles = user?.realm_access?.roles ?? []
  const canExecute = roles.some(r => ['operacao', 'gestao'].includes(r))

  const [tomadores, setTomadores] = useState<Tomador[]>([])
  const [tomadorId, setTomadorId] = useState('')
  const [competencia, setCompetencia] = useState(COMPETENCIAS[1] ?? COMPETENCIAS[0])
  const [preview, setPreview] = useState<FechamentoPreviewResp | null>(null)
  const [resultado, setResultado] = useState<FechamentoResp | null>(null)
  const [historico, setHistorico] = useState<FechamentoResp[]>([])
  const [tomadorNomeMap, setTomadorNomeMap] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)
  const [loadingExec, setLoadingExec] = useState(false)
  const [loadingHist, setLoadingHist] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Carrega tomadores e histórico ao montar
  useEffect(() => {
    tomadoresApi.listar().then(ts => {
      setTomadores(ts)
      const map: Record<string, string> = {}
      ts.forEach(t => { map[t.id] = t.razaoSocialNome })
      setTomadorNomeMap(map)
    }).catch(console.error)

    fechamentosApi.listar()
      .then(setHistorico)
      .catch(console.error)
      .finally(() => setLoadingHist(false))
  }, [])

  const recarregarHistorico = useCallback(() => {
    fechamentosApi.listar()
      .then(setHistorico)
      .catch(console.error)
  }, [])

  const handlePreview = async () => {
    if (!tomadorId || !competencia) return
    setLoading(true)
    setError(null)
    setPreview(null)
    setResultado(null)
    try {
      const data = await fechamentosApi.preview(tomadorId, competencia)
      setPreview(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar preview')
    } finally {
      setLoading(false)
    }
  }

  const handleExecutar = async () => {
    if (!preview || !tomadorId || !competencia) return
    setLoadingExec(true)
    setError(null)
    try {
      const data = await fechamentosApi.executar({ tomadorId, competencia })
      setResultado(data)
      setPreview(null)
      recarregarHistorico()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao fechar competência')
    } finally {
      setLoadingExec(false)
    }
  }

  const tomadorSelecionado = tomadores.find(t => t.id === tomadorId)
  const totalGrupos = preview?.grupos.length ?? 0
  const totalFrequencias = preview?.totalFrequencias ?? 0

  return (
    <div className="flex flex-col h-full overflow-hidden bg-ds-bg">
      {/* Header */}
      <div className="px-5 pt-5 pb-4 border-b border-ds-border bg-white shrink-0">
        <div className="flex items-center gap-3 mb-1">
          <PackageCheck size={20} className="text-primary" />
          <h1 className="text-xl font-bold text-ds-text">Fechamento por Grupo</h1>
        </div>
        <p className="text-sm text-ds-light">
          Totalize frequências médicas por competência e gere uma NFS-e por grupo de faturamento.
        </p>
      </div>

      <div className="flex-1 overflow-auto p-5 space-y-5">

        {/* ── Filtros ─────────────────────────────────────────────── */}
        <div className="bg-white rounded-xl border border-ds-border p-5">
          <p className="text-sm font-semibold text-ds-mid mb-4 flex items-center gap-2">
            <Search size={14} /> Selecionar Tomador e Competência
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-[1fr_180px_auto] gap-3">
            {/* Tomador */}
            <div className="relative">
              <select
                value={tomadorId}
                onChange={e => { setTomadorId(e.target.value); setPreview(null); setResultado(null); setError(null) }}
                className="w-full h-10 pl-3 pr-8 text-sm border border-ds-border rounded-lg bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 appearance-none"
              >
                <option value="">— Selecione o tomador —</option>
                {tomadores.map(t => (
                  <option key={t.id} value={t.id}>{t.razaoSocialNome}</option>
                ))}
              </select>
              <ChevronDown size={14} className="absolute right-2.5 top-3 text-ds-light pointer-events-none" />
            </div>

            {/* Competência */}
            <div className="relative">
              <select
                value={competencia}
                onChange={e => { setCompetencia(e.target.value); setPreview(null); setResultado(null); setError(null) }}
                className="w-full h-10 pl-3 pr-8 text-sm border border-ds-border rounded-lg bg-white text-ds-text focus:outline-none focus:ring-2 focus:ring-primary/30 appearance-none"
              >
                {COMPETENCIAS.map(c => (
                  <option key={c} value={c}>{formatCompetenciaFull(c)}</option>
                ))}
              </select>
              <ChevronDown size={14} className="absolute right-2.5 top-3 text-ds-light pointer-events-none" />
            </div>

            {/* Botão */}
            <Button
              onClick={handlePreview}
              disabled={!tomadorId || !competencia || loading}
              className="h-10 px-5"
            >
              {loading ? <Loader2 size={15} className="animate-spin mr-2" /> : <Search size={15} className="mr-2" />}
              Gerar Preview
            </Button>
          </div>
        </div>

        {/* ── Erro ───────────────────────────────────────────────── */}
        {error && (
          <Alert variant="error">
            <AlertCircle size={15} className="shrink-0 mt-0.5" />
            <span>{error}</span>
          </Alert>
        )}

        {/* ── Loading preview ─────────────────────────────────────── */}
        {loading && (
          <div className="flex items-center justify-center py-16 text-ds-light gap-3">
            <Loader2 size={24} className="animate-spin text-primary" />
            <span className="text-sm">Calculando totalizador...</span>
          </div>
        )}

        {/* ── Preview ─────────────────────────────────────────────── */}
        {preview && (
          <div className="space-y-4">

            {/* Cabeçalho do preview */}
            <div className="bg-white rounded-xl border border-ds-border p-4">
              <div className="flex items-start justify-between gap-4 flex-wrap">
                <div>
                  <div className="flex items-center gap-2 mb-0.5">
                    <CalendarDays size={15} className="text-primary" />
                    <span className="text-sm font-semibold text-ds-text">
                      Totalizador — {formatCompetenciaFull(competencia)}
                    </span>
                  </div>
                  <p className="text-xs text-ds-light">{tomadorSelecionado?.razaoSocialNome}</p>
                </div>
                <div className="flex items-center gap-6 flex-wrap">
                  <div className="text-center">
                    <p className="text-2xl font-bold text-primary">{totalGrupos}</p>
                    <p className="text-xs text-ds-light">NFS-e a emitir</p>
                  </div>
                  <div className="text-center">
                    <p className="text-2xl font-bold text-ds-text">{totalFrequencias}</p>
                    <p className="text-xs text-ds-light">frequência{totalFrequencias !== 1 ? 's' : ''}</p>
                  </div>
                  <div className="text-center">
                    <p className="text-2xl font-bold text-green-700">{formatBRL(preview.totalCentavos)}</p>
                    <p className="text-xs text-ds-light">total geral</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Sem frequências */}
            {preview.grupos.length === 0 && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-6 flex items-center gap-3">
                <TriangleAlert size={20} className="text-yellow-600 shrink-0" />
                <div>
                  <p className="font-semibold text-yellow-800">Nenhuma frequência encontrada</p>
                  <p className="text-sm text-yellow-700 mt-0.5">
                    Não há frequências médicas cadastradas para {tomadorSelecionado?.razaoSocialNome} em {formatCompetenciaFull(competencia)}.
                  </p>
                </div>
              </div>
            )}

            {/* ── Totalizador detalhado (igual à imagem) ──────────── */}
            {preview.grupos.length > 0 && (
              <div className="bg-white rounded-xl border border-ds-border overflow-hidden">
                {/* Tabela de modalidades (top) */}
                {preview.modalidades.length > 0 && (
                  <div className="border-b border-ds-border">
                    <TabelaModalidades modalidades={preview.modalidades} />
                  </div>
                )}

                {/* Totalizador por grupo → setor + Fechamento */}
                <div className="p-5">
                  <TotalizadorPorGrupo preview={preview} />
                </div>
              </div>
            )}

            {/* Ação de fechar */}
            {preview.grupos.length > 0 && canExecute && (
              <div className="bg-white rounded-xl border border-ds-border p-5 flex items-center justify-between gap-4 flex-wrap">
                <div>
                  <p className="font-semibold text-ds-text">Fechar Competência</p>
                  <p className="text-sm text-ds-light mt-0.5">
                    Serão criadas <strong>{totalGrupos} produção{totalGrupos !== 1 ? 'ões' : ''}</strong> e as
                    frequências serão marcadas como <em>Faturadas</em>.
                  </p>
                </div>
                <Button
                  onClick={handleExecutar}
                  disabled={loadingExec}
                  className="bg-green-600 hover:bg-green-700 text-white shrink-0"
                >
                  {loadingExec
                    ? <><Loader2 size={15} className="animate-spin mr-2" /> Fechando...</>
                    : <><PackageCheck size={15} className="mr-2" /> Fechar Competência</>}
                </Button>
              </div>
            )}

            {/* Ação de fechar — sem permissão */}
            {preview.grupos.length > 0 && !canExecute && (
              <div className="bg-ds-surface border border-ds-border rounded-xl p-4 text-sm text-ds-light flex items-center gap-2">
                <AlertCircle size={14} />
                Apenas perfis <strong>operação</strong> ou <strong>gestão</strong> podem fechar a competência.
              </div>
            )}
          </div>
        )}

        {/* ── Resultado do fechamento ───────────────────────────── */}
        {resultado && (
          <div className="space-y-4">
            <Alert variant="success">
              <CheckCircle2 size={15} className="shrink-0 mt-0.5" />
              <span>
                Competência <strong>{formatCompetenciaFull(resultado.competencia)}</strong> fechada com sucesso!
                {' '}{resultado.producoes.length} produção{resultado.producoes.length !== 1 ? 'ões criadas' : ' criada'}.
                Total: <strong>{formatBRL(resultado.totalCentavos)}</strong>.
              </span>
            </Alert>

            <div className="bg-white rounded-xl border border-ds-border overflow-hidden">
              <div className="px-5 py-3 border-b border-ds-border bg-ds-surface">
                <p className="text-sm font-semibold text-ds-mid flex items-center gap-2">
                  <FileText size={14} /> Produções geradas — emitir NFS-e
                </p>
              </div>
              <div className="divide-y divide-ds-border">
                {resultado.producoes.map(p => (
                  <div key={p.producaoId} className="flex items-center justify-between px-5 py-3.5 hover:bg-ds-surface/50">
                    <div className="min-w-0">
                      <p className="font-medium text-ds-text">{p.grupoNome}</p>
                      <p className="text-sm text-ds-light">{formatBRL(p.totalCentavos)}</p>
                    </div>
                    <Button
                      onClick={() => navigate(`/notas/emitir/${p.producaoId}`)}
                      className="ml-4 text-sm py-1.5 px-3 shrink-0"
                    >
                      Emitir NFS-e <ArrowRight size={13} className="ml-1.5" />
                    </Button>
                  </div>
                ))}
              </div>
              <div className="px-5 py-3 border-t border-ds-border bg-ds-surface flex justify-end">
                <Button
                  onClick={() => navigate('/notas/lote')}
                  variant="outline"
                  className="text-sm"
                >
                  <ReceiptText size={14} className="mr-1.5" />
                  Emitir em lote
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* ── Histórico de Fechamentos ──────────────────────────── */}
        <div className="bg-white rounded-xl border border-ds-border overflow-hidden">
          <div className="px-5 py-3 border-b border-ds-border bg-ds-surface flex items-center justify-between">
            <p className="text-sm font-semibold text-ds-mid flex items-center gap-2">
              <History size={14} /> Histórico de Fechamentos
            </p>
            {tomadorId && (
              <button
                onClick={() => fechamentosApi.listar(tomadorId).then(setHistorico).catch(console.error)}
                className="text-xs text-primary hover:underline"
              >
                Filtrar por tomador selecionado
              </button>
            )}
          </div>

          {loadingHist ? (
            <div className="flex items-center justify-center py-10 text-ds-light gap-2">
              <Loader2 size={18} className="animate-spin text-primary" />
              <span className="text-sm">Carregando histórico...</span>
            </div>
          ) : historico.length === 0 ? (
            <div className="py-10 text-center text-sm text-ds-light">
              Nenhum fechamento realizado ainda.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-ds-surface text-ds-light text-xs uppercase tracking-wide">
                  <tr>
                    <th className="px-4 py-3 text-left">Tomador</th>
                    <th className="px-4 py-3 text-left">Competência</th>
                    <th className="px-4 py-3 text-left">Status</th>
                    <th className="px-4 py-3 text-right">Total</th>
                    <th className="px-4 py-3 text-left">Produções</th>
                    <th className="px-4 py-3 text-left">Fechado em</th>
                    <th className="px-4 py-3"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-ds-border">
                  {historico.map(f => (
                    <tr key={f.id} className="hover:bg-ds-surface/50">
                      <td className="px-4 py-3 font-medium text-ds-text max-w-[200px] truncate">
                        {tomadorNomeMap[f.tomadorId] ?? f.tomadorId.substring(0, 8) + '…'}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        {formatCompetencia(f.competencia)}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium ${
                          f.status === 'FECHADO'
                            ? 'bg-green-50 text-green-700'
                            : 'bg-yellow-50 text-yellow-700'
                        }`}>
                          {f.status === 'FECHADO'
                            ? <CheckCircle2 size={10} />
                            : <Loader2 size={10} className="animate-spin" />}
                          {f.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right font-semibold text-ds-text whitespace-nowrap">
                        {formatBRL(f.totalCentavos)}
                      </td>
                      <td className="px-4 py-3 text-ds-mid">
                        {f.producoes.length} grupo{f.producoes.length !== 1 ? 's' : ''}
                      </td>
                      <td className="px-4 py-3 text-ds-light whitespace-nowrap">
                        {f.fechadoEm ? formatDate(f.fechadoEm) : '—'}
                      </td>
                      <td className="px-4 py-3">
                        {f.producoes.length > 0 && (
                          <div className="flex gap-1.5 flex-wrap justify-end">
                            {f.producoes.slice(0, 2).map(p => (
                              <button
                                key={p.producaoId}
                                onClick={() => navigate(`/notas/emitir/${p.producaoId}`)}
                                title={`Emitir NFS-e: ${p.grupoNome}`}
                                className="text-xs text-primary hover:underline flex items-center gap-1"
                              >
                                <FileText size={11} />
                                {p.grupoNome.length > 18 ? p.grupoNome.substring(0, 18) + '…' : p.grupoNome}
                              </button>
                            ))}
                            {f.producoes.length > 2 && (
                              <span className="text-xs text-ds-light">
                                +{f.producoes.length - 2} mais
                              </span>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

      </div>
    </div>
  )
}
