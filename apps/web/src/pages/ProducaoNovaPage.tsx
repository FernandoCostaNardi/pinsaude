import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowLeft, Building2, Calculator, CheckCircle2, ChevronDown,
  AlertCircle, Loader2, Stethoscope, Hospital, FileText, type LucideIcon,
} from 'lucide-react'
import { Button, Spinner, Alert } from '@pinsaude/ui'
import { Tomador, tomadoresApi } from '../api/tomadoresApi'
import { Medico, medicosApi } from '../api/medicosApi'
import { ProducaoRequest, producoesApi } from '../api/producoesApi'
import { Empresa, empresasApi } from '../api/empresasApi'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function parseBRL(str: string): number {
  const digits = str.replace(/\D/g, '')
  return digits ? parseInt(digits, 10) : 0
}

function maskBRL(centavos: number): string {
  if (!centavos) return ''
  return (centavos / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatPct(fracao: number): string {
  return `${(fracao * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`
}

function currentCompetencia(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function competenciaLabel(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho',
                 'Julho','Agosto','Setembro','Outubro','Novembro','Dezembro']
  return `${meses[parseInt(mes, 10) - 1]} / ${ano}`
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

// ─── Autocomplete genérico ────────────────────────────────────────────────────

interface AutocompleteItem { id: string; label: string; highlight?: string; sublabel?: string }

function Autocomplete({
  items, value, onChange, onClear, placeholder, disabled, loading: ext,
}: {
  items: AutocompleteItem[]
  value: AutocompleteItem | null
  onChange: (item: AutocompleteItem) => void
  onClear: () => void
  placeholder: string
  disabled?: boolean
  loading?: boolean
}) {
  const [query, setQuery]     = useState('')
  const [open, setOpen]       = useState(false)
  const containerRef          = useRef<HTMLDivElement>(null)

  const filtered = items.filter(i =>
    i.label.toLowerCase().includes(query.toLowerCase()) ||
    (i.highlight ?? '').toLowerCase().includes(query.toLowerCase()) ||
    (i.sublabel ?? '').toLowerCase().includes(query.toLowerCase())
  ).slice(0, 20)

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
        if (!value) setQuery('')
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [value])

  function handleSelect(item: AutocompleteItem) {
    onChange(item)
    setQuery('')
    setOpen(false)
  }

  function handleClear() {
    onClear()
    setQuery('')
    setOpen(false)
  }

  return (
    <div ref={containerRef} className="relative">
      {value ? (
        <div className="flex items-center justify-between border border-ds-border rounded-lg px-3 py-2 bg-white">
          <div>
            <div className="text-sm font-medium text-ds-mid">{value.label}</div>
            {value.highlight && <div className="text-xs font-medium text-red-600">{value.highlight}</div>}
            {value.sublabel && <div className="text-xs text-ds-light">{value.sublabel}</div>}
          </div>
          <button onClick={handleClear} className="text-ds-light hover:text-ds-mid ml-2 text-xs underline shrink-0">
            Alterar
          </button>
        </div>
      ) : (
        <div className="relative">
          <input
            value={query}
            onChange={e => { setQuery(e.target.value); setOpen(true) }}
            onFocus={() => setOpen(true)}
            placeholder={placeholder}
            disabled={disabled}
            className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:bg-ds-surface disabled:text-ds-light"
          />
          {ext && (
            <Loader2 size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-ds-light animate-spin" />
          )}
          {!ext && <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-ds-light pointer-events-none" />}

          {open && filtered.length > 0 && (
            <div className="absolute z-20 w-full mt-1 bg-white border border-ds-border rounded-lg shadow-lg max-h-56 overflow-y-auto">
              {filtered.map(item => (
                <button
                  key={item.id}
                  onMouseDown={e => { e.preventDefault(); handleSelect(item) }}
                  className="w-full text-left px-3 py-2.5 hover:bg-primary-50 transition-colors border-b border-ds-border last:border-0"
                >
                  <div className="text-sm font-medium text-ds-mid">{item.label}</div>
                  {item.highlight && <div className="text-xs font-medium text-red-600">{item.highlight}</div>}
                  {item.sublabel && <div className="text-xs text-ds-light">{item.sublabel}</div>}
                </button>
              ))}
            </div>
          )}
          {open && !ext && filtered.length === 0 && query.length >= 2 && (
            <div className="absolute z-20 w-full mt-1 bg-white border border-ds-border rounded-lg shadow-lg px-3 py-3 text-sm text-ds-light">
              Nenhum resultado encontrado
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Blocos visuais ───────────────────────────────────────────────────────────

function Field({ label, required, children, error, hint }: {
  label: string; required?: boolean; children: React.ReactNode; error?: string; hint?: React.ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <label className="block text-sm font-medium text-ds-mid">
        {label}{required && <span className="text-red-500 ml-0.5">*</span>}
      </label>
      {children}
      {hint && !error && <div className="text-[11px] text-ds-light">{hint}</div>}
      {error && <p className="text-xs text-red-500 flex items-center gap-1"><AlertCircle size={11} />{error}</p>}
    </div>
  )
}

// Card de etapa numerada: fica esmaecido e bloqueado enquanto a etapa anterior não foi concluída.
function Etapa({ numero, titulo, icon: Icon, ativa, concluida, children }: {
  numero: number
  titulo: string
  icon: LucideIcon
  ativa: boolean
  concluida: boolean
  children: React.ReactNode
}) {
  return (
    <div className={`bg-white rounded-xl border border-ds-border shadow-sm p-6 space-y-4 transition-opacity ${
      ativa ? '' : 'opacity-50 pointer-events-none'
    }`}>
      <div className="flex items-center gap-3 border-b border-ds-border pb-3">
        <span className={`w-7 h-7 rounded-full text-xs font-bold flex items-center justify-center ${
          concluida ? 'bg-green-600 text-white' : ativa ? 'bg-primary text-white' : 'bg-gray-200 text-gray-500'
        }`}>
          {concluida ? <CheckCircle2 size={14} /> : numero}
        </span>
        <Icon size={16} className="text-primary" />
        <h2 className="font-semibold text-ds-mid text-sm uppercase tracking-wide">{titulo}</h2>
      </div>
      {children}
    </div>
  )
}

function LinhaResumo({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-3 py-2 border-b border-ds-border/60 last:border-0">
      <span className="text-xs text-ds-light shrink-0">{label}</span>
      <span className="text-xs font-semibold text-ds-mid text-right">{children}</span>
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────
// Processo individual (um médico por produção), guiado em etapas:
//   1. Médico → 2. Tomador (só os alocados ao médico, EPIC-15) → 3. Competência e valor.
// A Empresa Emissora vem do vínculo Tomador ↔ Empresa Pin (PINSAUDE-13.12): com 1 empresa
// vinculada é resolvida sozinha; só pede escolha quando o tomador tem 2+ empresas, ou nenhuma
// (fallback para o catálogo completo, com aviso para completar o cadastro).
// Serviço (LC 116/2003) e CNAE são definidos na emissão da NFS-e.
// Esta tela é só a do backoffice — o Portal do Médico tem a sua (PortalProducaoNovaPage.tsx).

export function ProducaoNovaPage() {
  const navigate = useNavigate()

  const [medicos, setMedicos]   = useState<Medico[]>([])
  const [empresas, setEmpresas] = useState<Empresa[]>([])
  const [loadingData, setLoadingData] = useState(true)

  const [medico, setMedico] = useState<AutocompleteItem | null>(null)
  const [tomadoresMedico, setTomadoresMedico]   = useState<Tomador[]>([])
  const [tomadoresLoading, setTomadoresLoading] = useState(false)
  const [tomador, setTomador] = useState<AutocompleteItem | null>(null)
  // Cadastro completo do médico selecionado: a listagem de médicos não traz a taxa Pin
  // acordada (taxaPinPct), só o detalhe (GET /api/medicos/{id}).
  const [medicoDetalhe, setMedicoDetalhe] = useState<Medico | null>(null)
  const [medicoDetalheLoading, setMedicoDetalheLoading] = useState(false)
  const [medicoDetalheErro, setMedicoDetalheErro] = useState<string | null>(null)
  const [empresaEscolhida, setEmpresaEscolhida] = useState('')
  const [competencia, setCompetencia] = useState(currentCompetencia())
  const [valorStr, setValorStr]   = useState('')
  const [descricao, setDescricao] = useState('')

  const [submitLoading, setSubmitLoading] = useState(false)
  const [globalError, setGlobalError] = useState<string | null>(null)

  const competencias = generateCompetencias()

  useEffect(() => {
    Promise.all([
      medicosApi.listar(0, 1000, 'ATIVO').catch(() => ({ content: [] as Medico[] })),
      empresasApi.listar(0, 1000).catch(() => ({ content: [] as Empresa[] })),
    ]).then(([mp, ep]) => {
      setMedicos(mp.content)
      setEmpresas(ep.content)
    }).finally(() => setLoadingData(false))
  }, [])

  // Etapa 2 depende do médico: carrega só os tomadores onde ele está alocado.
  useEffect(() => {
    setTomador(null)
    if (!medico) { setTomadoresMedico([]); return }
    let cancelled = false
    setTomadoresLoading(true)
    tomadoresApi.listar(undefined, medico.id)
      .then(ts => { if (!cancelled) setTomadoresMedico(ts) })
      .catch(() => { if (!cancelled) setTomadoresMedico([]) })
      .finally(() => { if (!cancelled) setTomadoresLoading(false) })
    return () => { cancelled = true }
  }, [medico?.id])

  useEffect(() => { setEmpresaEscolhida('') }, [tomador?.id])

  useEffect(() => {
    setMedicoDetalhe(null)
    setMedicoDetalheErro(null)
    if (!medico) return
    let cancelled = false
    setMedicoDetalheLoading(true)
    medicosApi.buscarPorId(medico.id)
      .then(m => { if (!cancelled) setMedicoDetalhe(m) })
      .catch(() => { if (!cancelled) setMedicoDetalheErro('Não foi possível carregar a taxa Pin acordada com este médico. Tente selecioná-lo novamente.') })
      .finally(() => { if (!cancelled) setMedicoDetalheLoading(false) })
    return () => { cancelled = true }
  }, [medico?.id])

  // Tomadores com faturamento por grupo geram produção pelo Fechamento por Grupo (PINSAUDE-13.11).
  const tomadoresDisponiveis = tomadoresMedico.filter(t => !t.temGrupoFaturamento)
  const qtdOcultosPorGrupo   = tomadoresMedico.length - tomadoresDisponiveis.length

  const medicoObj  = medico  ? medicos.find(m => m.id === medico.id) ?? null : null
  const tomadorObj = tomador ? tomadoresMedico.find(t => t.id === tomador.id) ?? null : null

  // Empresa emissora derivada do tomador.
  const empresaIdsVinculadas = tomadorObj?.empresas?.map(v => v.empresaId) ?? []
  const empresaAutomatica    = empresaIdsVinculadas.length === 1 ? empresaIdsVinculadas[0] : null
  const opcoesEmpresa: Empresa[] = empresaIdsVinculadas.length > 1
    ? empresas.filter(e => empresaIdsVinculadas.includes(e.id))
    : empresas
  const precisaEscolherEmpresa = !!tomadorObj && !empresaAutomatica
  const empresaId   = empresaAutomatica ?? (empresaEscolhida || null)
  const empresaInfo = empresaId ? empresas.find(e => e.id === empresaId) ?? null : null

  const valorCentavos = parseBRL(valorStr)
  // Sem fallback fixo: a produção só é confirmada depois que a taxa do cadastro do médico carregou.
  const taxaPinPct    = medicoDetalhe?.taxaPinPct != null ? Number(medicoDetalhe.taxaPinPct) : null
  const taxaPin       = taxaPinPct != null ? Math.round(valorCentavos * taxaPinPct) : 0

  const etapa1Ok = !!medico
  const etapa2Ok = etapa1Ok && !!tomador && !!empresaId
  const etapa3Ok = etapa2Ok && !!competencia && valorCentavos > 0 && taxaPinPct != null

  const faltando = [
    !medico && 'médico',
    !tomador && 'tomador',
    tomador && !empresaId && 'empresa emissora',
    valorCentavos <= 0 && 'valor',
    medico && taxaPinPct == null && !medicoDetalheLoading && 'taxa Pin do médico',
  ].filter(Boolean) as string[]

  async function handleSubmit() {
    if (!etapa3Ok || !medico || !tomador || taxaPinPct == null) return
    const req: ProducaoRequest = {
      tomadorId:             tomador.id,
      servicoId:             null,
      competencia,
      descricaoComplementar: descricao.trim() || undefined,
      empresaId,
      participantes: [{ medicoId: medico.id, valorBruto: valorCentavos, taxaPinPct }],
    }
    setSubmitLoading(true)
    setGlobalError(null)
    try {
      await producoesApi.criar(req)
      navigate('/producao')
    } catch (e: unknown) {
      setGlobalError(e instanceof Error ? e.message : 'Erro ao registrar produção')
    } finally {
      setSubmitLoading(false)
    }
  }

  const medicoItems: AutocompleteItem[] = medicos.map(m => ({
    id: m.id,
    label: m.nome,
    sublabel: `CRM ${m.crm}-${m.crmUf}${m.especialidade ? ` · ${m.especialidade}` : ''}`,
  }))

  const tomadorItems: AutocompleteItem[] = tomadoresDisponiveis.map(t => ({
    id: t.id,
    label: t.razaoSocialNome,
    highlight: t.nomeFantasia ?? undefined,
    sublabel: t.municipio ?? undefined,
  }))

  if (loadingData) return (
    <div className="flex items-center justify-center h-64">
      <Spinner size="lg" />
    </div>
  )

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate('/producao')}
          className="p-2 rounded-lg hover:bg-ds-input text-ds-light hover:text-ds-mid transition-colors">
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-ds-mid">Nova Produção Médica</h1>
          <p className="text-sm text-ds-light mt-0.5">Escolha o médico, o tomador onde ele atua e informe o valor produzido</p>
        </div>
      </div>

      {globalError && <Alert variant="error" onClose={() => setGlobalError(null)}>{globalError}</Alert>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <div className="lg:col-span-3 space-y-5">

          {/* Etapa 1 — Médico */}
          <Etapa numero={1} titulo="Médico" icon={Stethoscope} ativa concluida={etapa1Ok}>
            <Field label="Médico" required>
              <Autocomplete
                items={medicoItems}
                value={medico}
                onChange={setMedico}
                onClear={() => setMedico(null)}
                placeholder="Buscar médico por nome..."
              />
            </Field>
            {medico && (
              <div className="flex items-center justify-between rounded-lg bg-ds-input/60 border border-ds-border px-4 py-2.5">
                <span className="text-xs text-ds-light">Taxa Pin Saúde acordada</span>
                {medicoDetalheLoading
                  ? <Loader2 size={14} className="animate-spin text-ds-light" />
                  : taxaPinPct != null
                    ? <span className="text-sm font-bold text-ds-mid">{formatPct(taxaPinPct)}</span>
                    : <span className="text-xs text-red-500">indisponível</span>}
              </div>
            )}
            {medicoDetalheErro && (
              <p className="text-xs text-red-500 flex items-center gap-1">
                <AlertCircle size={11} /> {medicoDetalheErro}
              </p>
            )}
            {medicos.length === 0 && (
              <p className="text-xs text-amber-600 flex items-center gap-1">
                <AlertCircle size={11} /> Nenhum médico ativo encontrado. Ative um médico primeiro.
              </p>
            )}
          </Etapa>

          {/* Etapa 2 — Tomador (e empresa emissora derivada dele) */}
          <Etapa numero={2} titulo="Tomador" icon={Hospital} ativa={etapa1Ok} concluida={etapa2Ok}>
            {etapa1Ok && !tomadoresLoading && tomadoresDisponiveis.length === 0 ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                {tomadoresMedico.length === 0
                  ? 'Este médico ainda não está alocado a nenhum tomador. Faça a alocação no perfil do médico ou no cadastro de Tomadores.'
                  : 'Os tomadores deste médico usam faturamento por grupo — a produção deles é gerada pelo Fechamento por Grupo.'}
              </div>
            ) : (
              <Field
                label="Tomador (Hospital / Clínica / Operadora)"
                required
                hint={etapa1Ok && !tomadoresLoading && (
                  <>
                    {tomadoresDisponiveis.length === 1
                      ? '1 tomador onde este médico está alocado.'
                      : `${tomadoresDisponiveis.length} tomadores onde este médico está alocado.`}
                    {qtdOcultosPorGrupo > 0 && ' Tomadores com faturamento por grupo não aparecem aqui.'}
                  </>
                )}
              >
                <Autocomplete
                  items={tomadorItems}
                  value={tomador}
                  onChange={setTomador}
                  onClear={() => setTomador(null)}
                  placeholder={etapa1Ok ? 'Buscar tomador...' : 'Selecione o médico primeiro'}
                  disabled={!etapa1Ok}
                  loading={tomadoresLoading}
                />
              </Field>
            )}

            {tomadorObj && empresaAutomatica && (
              <div className="flex items-start gap-3 rounded-lg bg-primary-50 border border-primary-100 px-4 py-3">
                <Building2 size={16} className="text-primary mt-0.5 shrink-0" />
                <div className="min-w-0">
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-primary">Empresa emissora</p>
                  <p className="text-sm font-semibold text-ds-mid truncate">
                    {empresaInfo ? empresaInfo.razaoSocial : 'Empresa Pin vinculada ao tomador'}
                  </p>
                  {empresaInfo && <p className="text-xs text-ds-light">{empresaInfo.cnpj}</p>}
                </div>
              </div>
            )}

            {precisaEscolherEmpresa && (
              <Field
                label="Empresa Emissora (Pin Saúde)"
                required
                hint={empresaIdsVinculadas.length > 1
                  ? 'Este tomador está vinculado a mais de uma empresa Pin — escolha qual emite a nota.'
                  : undefined}
              >
                {empresaIdsVinculadas.length === 0 && (
                  <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                    Este tomador não tem empresa Pin vinculada. Escolha abaixo e, para não precisar fazer
                    isso de novo, vincule a empresa no cadastro do tomador (aba "Empresas Pin").
                  </p>
                )}
                <select
                  value={empresaEscolhida}
                  onChange={e => setEmpresaEscolhida(e.target.value)}
                  className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid"
                >
                  <option value="">Selecione a empresa...</option>
                  {opcoesEmpresa.map(e => (
                    <option key={e.id} value={e.id}>{e.razaoSocial} — {e.cnpj}</option>
                  ))}
                </select>
              </Field>
            )}
          </Etapa>

          {/* Etapa 3 — Produção */}
          <Etapa numero={3} titulo="Produção" icon={FileText} ativa={etapa2Ok} concluida={etapa3Ok}>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Field label="Competência" required>
                <select
                  value={competencia}
                  onChange={e => setCompetencia(e.target.value)}
                  className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 text-ds-mid"
                >
                  {competencias.map(c => (
                    <option key={c} value={c}>{competenciaLabel(c)}</option>
                  ))}
                </select>
              </Field>
              <Field label="Valor Bruto" required>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-xs font-medium text-ds-mid">R$</span>
                  <input
                    value={valorStr}
                    onChange={e => {
                      const raw = e.target.value.replace(/\D/g, '')
                      setValorStr(maskBRL(parseInt(raw || '0', 10)))
                    }}
                    inputMode="numeric"
                    placeholder="0,00"
                    className="w-full pl-8 pr-3 py-2 border border-ds-border rounded-lg text-sm text-right focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </Field>
            </div>
            <Field label="Descrição Complementar">
              <textarea
                value={descricao}
                onChange={e => setDescricao(e.target.value)}
                placeholder="Informações adicionais sobre a produção (opcional)"
                rows={2}
                className="w-full border border-ds-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
              />
            </Field>
          </Etapa>

          <div className="flex items-center justify-between">
            <Button variant="ghost" onClick={() => navigate('/producao')} disabled={submitLoading}>
              Cancelar
            </Button>
            <div className="flex flex-col items-end gap-1">
              {faltando.length > 0 && !submitLoading && (
                <p className="text-xs text-ds-light text-right">Faltam: {faltando.join(', ')}</p>
              )}
              <Button onClick={handleSubmit} disabled={!etapa3Ok || submitLoading} className="min-w-40">
                {submitLoading
                  ? <><Loader2 size={15} className="mr-2 animate-spin" />Confirmando...</>
                  : <><CheckCircle2 size={15} className="mr-2" />Confirmar Produção</>}
              </Button>
            </div>
          </div>
        </div>

        {/* Resumo (2/5) */}
        <div className="lg:col-span-2">
          <div className="bg-white rounded-xl border border-ds-border shadow-sm p-5 sticky top-6 space-y-4">
            <div className="flex items-center gap-2">
              <Calculator size={16} className="text-primary" />
              <h3 className="font-semibold text-ds-mid text-sm">Resumo da Produção</h3>
            </div>
            <div>
              <LinhaResumo label="Médico">{medicoObj?.nome ?? '—'}</LinhaResumo>
              <LinhaResumo label="Tomador">{tomadorObj?.razaoSocialNome ?? '—'}</LinhaResumo>
              <LinhaResumo label="Empresa emissora">
                {empresaInfo?.razaoSocial ?? (empresaId ? 'Vinculada ao tomador' : '—')}
              </LinhaResumo>
              <LinhaResumo label="Competência">{competenciaLabel(competencia)}</LinhaResumo>
            </div>
            <div className="space-y-2 pt-1">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold text-ds-mid">Valor Bruto</span>
                <span className="text-lg font-black text-ds-mid">{formatBRL(valorCentavos)}</span>
              </div>
              {valorCentavos > 0 && taxaPinPct != null && (
                <>
                  <div className="flex items-center justify-between text-xs text-ds-light">
                    <span>Taxa Pin Saúde ({formatPct(taxaPinPct)})</span>
                    <span>− {formatBRL(taxaPin)}</span>
                  </div>
                  <div className="flex items-center justify-between py-2 px-3 bg-primary-50 rounded-lg">
                    <span className="text-sm font-bold text-ds-mid">Líquido ao médico</span>
                    <span className="text-base font-black text-primary">{formatBRL(valorCentavos - taxaPin)}</span>
                  </div>
                </>
              )}
            </div>
            <p className="text-xs text-ds-light leading-relaxed border-t border-ds-border pt-3">
              O Serviço (LC 116/2003), o CNAE e o cálculo dos impostos da nota são definidos na
              etapa de emissão da NFS-e.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
