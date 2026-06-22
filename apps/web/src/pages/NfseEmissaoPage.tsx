import { useEffect, useRef, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, CheckCircle2, Clock, XCircle, AlertTriangle,
  FileDown, FileText, RotateCcw, ShieldCheck, Loader2, Eye,
} from 'lucide-react'
import { Spinner, Alert } from '@pinsaude/ui'
import { useAuth } from '../auth/useAuth'
import { producoesApi, Producao } from '../api/producoesApi'
import {
  emitirNfse, getNotaStatus, aprovarNota, downloadComAuth, downloadXmlUrl, downloadPdfUrl,
  NotaFiscal, StatusNota,
} from '../api/nfseApi'
import { NfsePreviewModal } from './NfsePreviewModal'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function calcPct(valorCentavos: number, aliquotaPct: number): number {
  return Math.round(valorCentavos * aliquotaPct / 100)
}

// ─── Status da NFS-e ─────────────────────────────────────────────────────────

type StepStatus = 'done' | 'active' | 'pending' | 'error'

interface StepDef {
  status: StatusNota
  label: string
  desc: string
}

const STEPS: StepDef[] = [
  { status: 'PENDENTE',             label: 'Aguardando fila',    desc: 'Nota criada e aguardando processamento' },
  { status: 'PROCESSANDO',          label: 'Processando',        desc: 'Comunicando com prefeitura...' },
  { status: 'EMITIDA',              label: 'Emitida',            desc: 'NFS-e emitida com sucesso' },
]

const STATUS_META: Record<StatusNota, { label: string; cls: string; Icon: React.ElementType; isTerminal: boolean }> = {
  PENDENTE:                { label: 'Na fila',              cls: 'text-blue-600',   Icon: Clock,          isTerminal: false },
  PROCESSANDO:             { label: 'Processando',          cls: 'text-primary',    Icon: Loader2,        isTerminal: false },
  EMITIDA:                 { label: 'Emitida',              cls: 'text-green-600',  Icon: CheckCircle2,   isTerminal: true  },
  CANCELADA:               { label: 'Cancelada',            cls: 'text-gray-500',   Icon: XCircle,        isTerminal: true  },
  ERRO:                    { label: 'Erro',                 cls: 'text-red-600',    Icon: XCircle,        isTerminal: true  },
  REJEITADA:               { label: 'Rejeitada',            cls: 'text-red-600',    Icon: XCircle,        isTerminal: true  },
  AGUARDANDO_EMISSAO_MANUAL: { label: 'Emissão Manual',    cls: 'text-orange-600', Icon: AlertTriangle,  isTerminal: true  },
  AGUARDANDO_VALIDACAO:    { label: 'Aguardando validação', cls: 'text-yellow-600', Icon: ShieldCheck,    isTerminal: false },
}

function getStepStatus(step: StepDef, current: StatusNota): StepStatus {
  const order: StatusNota[] = ['PENDENTE', 'PROCESSANDO', 'EMITIDA']
  const stepIdx = order.indexOf(step.status)
  const currIdx = order.indexOf(current)

  if (current === 'ERRO' || current === 'REJEITADA' || current === 'CANCELADA') {
    return stepIdx < currIdx ? 'done' : stepIdx === currIdx ? 'error' : 'pending'
  }
  if (currIdx < 0) return 'pending'
  if (stepIdx < currIdx) return 'done'
  if (stepIdx === currIdx) return 'active'
  return 'pending'
}

// ─── Step Visual ─────────────────────────────────────────────────────────────

function StepItem({ step, status, isLast }: { step: StepDef; status: StepStatus; isLast: boolean }) {
  const colors = {
    done:    'bg-green-500 text-white border-green-500',
    active:  'bg-primary text-white border-primary animate-pulse',
    pending: 'bg-white text-ds-light border-ds-border',
    error:   'bg-red-500 text-white border-red-500',
  }
  const textColor = {
    done: 'text-green-700', active: 'text-primary font-semibold', pending: 'text-ds-light', error: 'text-red-600',
  }
  return (
    <div className="flex items-start gap-3">
      <div className="flex flex-col items-center">
        <div className={`w-8 h-8 rounded-full border-2 flex items-center justify-center text-xs font-bold ${colors[status]}`}>
          {status === 'done' ? '✓' : status === 'error' ? '✗' : status === 'active' ? <Loader2 size={14} className="animate-spin" /> : '·'}
        </div>
        {!isLast && <div className={`w-0.5 h-8 mt-1 ${status === 'done' ? 'bg-green-400' : 'bg-ds-border'}`} />}
      </div>
      <div className="pt-1.5">
        <p className={`text-sm ${textColor[status]}`}>{step.label}</p>
        <p className="text-xs text-ds-light">{step.desc}</p>
      </div>
    </div>
  )
}

// ─── Linha fiscal ─────────────────────────────────────────────────────────────

function FiscalRow({ label, value, indent, negative, bold }: {
  label: string; value: number; indent?: boolean; negative?: boolean; bold?: boolean
}) {
  return (
    <div className={`flex items-center justify-between py-1.5 ${bold ? 'border-t border-ds-border mt-1 pt-2.5' : ''}`}>
      <span className={`text-sm ${indent ? 'pl-3 text-ds-light' : ''} ${bold ? 'font-semibold text-ds-mid' : 'text-ds-mid'}`}>
        {indent && <span className="text-ds-light mr-1">−</span>}
        {label}
      </span>
      <span className={`text-sm font-medium ${negative ? 'text-red-600' : bold ? 'text-primary text-base font-bold' : 'text-ds-mid'}`}>
        {negative ? `(${formatBRL(value)})` : formatBRL(value)}
      </span>
    </div>
  )
}

// ─── Página principal ─────────────────────────────────────────────────────────

export function NfseEmissaoPage() {
  const { producaoId } = useParams<{ producaoId: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()

  const [producao, setProducao] = useState<Producao | null>(null)
  const [nota, setNota] = useState<NotaFiscal | null>(null)
  const [loading, setLoading] = useState(true)
  const [emitindo, setEmitindo] = useState(false)
  const [aprovando, setAprovando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [primeiraNotaMedico, setPrimeiraNotaMedico] = useState(false)
  const [showPreview, setShowPreview] = useState(false)

  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const pararPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current)
      pollingRef.current = null
    }
  }, [])

  const atualizarStatus = useCallback(async () => {
    if (!producaoId) return
    try {
      const n = await getNotaStatus(producaoId)
      setNota(n)
      if (STATUS_META[n.status].isTerminal || n.status === 'AGUARDANDO_VALIDACAO' || n.status === 'AGUARDANDO_EMISSAO_MANUAL') {
        pararPolling()
      }
    } catch {
      // silencioso — nota pode não existir ainda
    }
  }, [producaoId, pararPolling])

  // Carrega produção ao montar
  useEffect(() => {
    if (!producaoId) return
    producoesApi.buscarPorId(producaoId)
      .then(setProducao)
      .catch(e => setErro(e.message))
      .finally(() => setLoading(false))
  }, [producaoId])

  // Se já existe nota para esta produção, inicia polling
  useEffect(() => {
    if (!producaoId) return
    getNotaStatus(producaoId)
      .then(n => {
        setNota(n)
        if (!STATUS_META[n.status].isTerminal && n.status !== 'AGUARDANDO_VALIDACAO' && n.status !== 'AGUARDANDO_EMISSAO_MANUAL') {
          pollingRef.current = setInterval(atualizarStatus, 3000)
        }
      })
      .catch(() => {/* nota ainda não existe */})
    return pararPolling
  }, [producaoId, atualizarStatus, pararPolling])

  const handleEmitir = async () => {
    if (!producao || !producaoId) return
    setEmitindo(true)
    setErro(null)
    try {
      const { tomador, servico, valorBruto, competencia, medicoId } = producao
      const taxaPin       = calcPct(valorBruto, 15)
      const issRetido     = tomador.retencaoIss     ? calcPct(valorBruto, servico.aliquotaIss)    : 0
      const irRetido      = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaIr)     : 0
      const csllRetido    = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCsll)   : 0
      const pisRetido     = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaPis)    : 0
      const cofinsRetido  = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCofins) : 0

      const res = await emitirNfse({
        producaoId, medicoId, tomadorId: tomador.id,
        competencia, valorBruto, taxaPin,
        valorIss: issRetido, valorIr: irRetido, valorCsll: csllRetido,
        valorPis: pisRetido, valorCofins: cofinsRetido,
        tomadorNome: tomador.razaoSocialNome ?? null,
      })
      setPrimeiraNotaMedico(res.primeiraNotaMedico)
      await atualizarStatus()

      // Inicia polling se nota ainda está em processamento
      if (!STATUS_META[res.status].isTerminal && res.status !== 'AGUARDANDO_VALIDACAO') {
        pollingRef.current = setInterval(atualizarStatus, 3000)
      }
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao emitir NFS-e')
    } finally {
      setEmitindo(false)
    }
  }

  const handleAprovar = async () => {
    if (!nota) return
    setAprovando(true)
    setErro(null)
    try {
      await aprovarNota(nota.notaId)
      await atualizarStatus()
      // Após aprovação, nota vai para PENDENTE — inicia polling
      pollingRef.current = setInterval(atualizarStatus, 3000)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao aprovar nota')
    } finally {
      setAprovando(false)
    }
  }

  const handleDownload = async (tipo: 'xml' | 'pdf') => {
    if (!nota) return
    try {
      const url = tipo === 'xml' ? downloadXmlUrl(nota.notaId) : downloadPdfUrl(nota.notaId)
      const ext = tipo === 'xml' ? 'xml' : 'pdf'
      await downloadComAuth(url, `nfse-${nota.numeroNota ?? nota.notaId}.${ext}`)
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : 'Erro ao baixar arquivo')
    }
  }

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Spinner />
      </div>
    )
  }

  if (!producao) {
    return (
      <div className="flex-1 p-8">
        <Alert variant="error">Produção não encontrada.</Alert>
      </div>
    )
  }

  const { tomador, servico, valorBruto } = producao
  const taxaPin        = calcPct(valorBruto, 15)
  const issRetido      = tomador.retencaoIss     ? calcPct(valorBruto, servico.aliquotaIss)    : 0
  const irRetido       = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaIr)     : 0
  const csllRetido     = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCsll)   : 0
  const pisRetido      = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaPis)    : 0
  const cofinsRetido   = tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCofins) : 0
  const totalRetidos   = issRetido + irRetido + csllRetido + pisRetido + cofinsRetido
  const valorLiquidoNota = valorBruto - totalRetidos
  const valorRepasse   = valorBruto - taxaPin
  // Totais brutos de cada tributo — retido ou não, sempre custo fiscal da Pin
  const issTotal    = calcPct(valorBruto, servico.aliquotaIss)
  const irTotal     = calcPct(valorBruto, servico.aliquotaIr)
  const csllTotal   = calcPct(valorBruto, servico.aliquotaCsll)
  const pisTotal    = calcPct(valorBruto, servico.aliquotaPis)
  const cofinsTotal = calcPct(valorBruto, servico.aliquotaCofins)
  const totalTributos = issTotal + irTotal + csllTotal + pisTotal + cofinsTotal
  const resultadoPin  = taxaPin - totalTributos

  const currentStatus  = nota?.status ?? null
  const meta           = currentStatus ? STATUS_META[currentStatus] : null
  const isGestaoOrContabil = user?.realm_access?.roles.some(r => r === 'gestao' || r === 'contabil') ?? false
  const cnpjPrestador  = user?.cnpj_id?.replace(/\D/g, '') ?? null

  return (
    <div className="flex-1 overflow-auto bg-ds-surface">
      {/* Topbar */}
      <div className="bg-white border-b border-ds-border px-6 py-4 flex items-center gap-3">
        <button
          onClick={() => navigate('/producao')}
          className="p-1.5 rounded-lg text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-lg font-bold text-ds-mid">Emissão de NFS-e</h1>
          <p className="text-xs text-ds-light">
            {formatCompetencia(producao.competencia)} · {tomador.razaoSocialNome}
          </p>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-6 py-6 grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* ── Painel Esquerdo: dados da produção ── */}
        <div className="space-y-4">
          {/* Cabeçalho da produção */}
          <div className="bg-white rounded-2xl border border-ds-border p-5 space-y-3">
            <h2 className="text-sm font-bold text-ds-mid uppercase tracking-wider">Dados da Produção</h2>

            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-ds-light">Tomador</span>
                <span className="font-medium text-ds-mid">{tomador.razaoSocialNome}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-ds-light">Serviço</span>
                <span className="font-medium text-ds-mid text-right max-w-48">{servico.codigoLc116} — {servico.descricaoPadrao}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-ds-light">Competência</span>
                <span className="font-medium text-ds-mid">{formatCompetencia(producao.competencia)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-ds-light">Valor Bruto</span>
                <span className="font-bold text-ds-mid text-base">{formatBRL(valorBruto)}</span>
              </div>
            </div>
          </div>

          {/* Breakdown fiscal */}
          <div className="bg-white rounded-2xl border border-ds-border p-5">
            <h2 className="text-sm font-bold text-ds-mid uppercase tracking-wider mb-4">Composição da NFS-e</h2>

            {/* Valores da nota — o que o tomador vê */}
            <div className="bg-primary-50 border border-primary-100 rounded-xl p-4 mb-3">
              <p className="text-xs font-semibold text-primary-700 uppercase tracking-wide mb-2">Nota Fiscal ao Tomador</p>
              <FiscalRow label="Valor dos Serviços" value={valorBruto} />
              {issRetido   > 0 && <FiscalRow label={`ISS retido pelo tomador (${servico.aliquotaIss}%)`}    value={issRetido}   indent negative />}
              {irRetido    > 0 && <FiscalRow label={`IR retido pelo tomador (${servico.aliquotaIr}%)`}       value={irRetido}    indent negative />}
              {csllRetido  > 0 && <FiscalRow label={`CSLL retido pelo tomador (${servico.aliquotaCsll}%)`}  value={csllRetido}  indent negative />}
              {pisRetido   > 0 && <FiscalRow label={`PIS retido pelo tomador (${servico.aliquotaPis}%)`}    value={pisRetido}   indent negative />}
              {cofinsRetido > 0 && <FiscalRow label={`COFINS retido pelo tomador (${servico.aliquotaCofins}%)`} value={cofinsRetido} indent negative />}
              <div className="border-t-2 border-primary-200 mt-2 pt-2.5 flex justify-between">
                <span className="font-bold text-primary-800 text-sm">Valor Líquido da Nota</span>
                <span className="font-bold text-primary">{formatBRL(valorLiquidoNota)}</span>
              </div>
              {totalRetidos === 0 && (
                <p className="text-xs text-primary-600 mt-1 italic">
                  Sem retenções — tributos recolhidos pela Pin Saúde via guia própria.
                </p>
              )}
            </div>

            {/* Distribuição interna — para controle de repasse */}
            <div className="bg-ds-surface rounded-xl p-4">
              <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-1">Controle Interno — Repasse</p>
              <p className="text-[10px] text-ds-light mb-3">Não consta na NFS-e. Base para o módulo de Repasses (EPIC-09).</p>

              {/* Distribuição do bruto */}
              <div className="mb-3">
                <FiscalRow label="Valor Bruto" value={valorBruto} />
                <FiscalRow label="Repasse ao Médico (85%)" value={valorRepasse} indent />
                <FiscalRow label="Taxa Pin Saúde (15%)" value={taxaPin} indent />
              </div>

              {/* Apuração real da Pin — o que sobra dos 15% após tributos */}
              <div className="border-t border-ds-border pt-3">
                <p className="text-[10px] font-semibold text-ds-mid uppercase tracking-wide mb-2">Apuração Pin Saúde</p>
                <FiscalRow label="Pin retém (15% do bruto)" value={taxaPin} />
                <FiscalRow
                  label={`ISS (${servico.aliquotaIss}%) ${issRetido > 0 ? '— retido pelo tomador' : '— a recolher via guia'}`}
                  value={issTotal} indent negative
                />
                <FiscalRow
                  label={`IR (${servico.aliquotaIr}%) ${irRetido > 0 ? '— retido pelo tomador' : '— a recolher via guia'}`}
                  value={irTotal} indent negative
                />
                <FiscalRow
                  label={`CSLL (${servico.aliquotaCsll}%) ${csllRetido > 0 ? '— retido' : '— via guia'}`}
                  value={csllTotal} indent negative
                />
                <FiscalRow
                  label={`PIS (${servico.aliquotaPis}%) ${pisRetido > 0 ? '— retido' : '— via guia'}`}
                  value={pisTotal} indent negative
                />
                <FiscalRow
                  label={`COFINS (${servico.aliquotaCofins}%) ${cofinsRetido > 0 ? '— retido' : '— via guia'}`}
                  value={cofinsTotal} indent negative
                />
                <div className="border-t-2 border-ds-border mt-2 pt-2.5 flex justify-between">
                  <span className="font-bold text-ds-mid text-sm">Resultado Pin Saúde</span>
                  <span className={`font-bold text-sm ${resultadoPin >= 0 ? 'text-primary' : 'text-red-600'}`}>
                    {formatBRL(resultadoPin)}
                  </span>
                </div>
                <p className="text-[10px] text-ds-light mt-2 italic">
                  O médico sempre recebe 85% do bruto. Os tributos saem dos 15% da Pin, retidos ou não.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* ── Painel Direito: status + ações ── */}
        <div className="space-y-4">
          {/* Alerta de erro */}
          {erro && <Alert variant="error">{erro}</Alert>}

          {/* Card status */}
          <div className="bg-white rounded-2xl border border-ds-border p-5">
            <h2 className="text-sm font-bold text-ds-mid uppercase tracking-wider mb-4">Status da Emissão</h2>

            {!nota ? (
              /* Ainda não foi emitida */
              <div className="text-center py-8">
                <FileText size={40} className="mx-auto text-ds-light mb-3" />
                <p className="text-ds-mid font-medium mb-1">NFS-e não emitida</p>
                <p className="text-ds-light text-sm mb-5">
                  Revise o preview da nota antes de enviar para emissão.
                </p>
                <div className="flex flex-col items-center gap-2">
                  <button
                    onClick={() => setShowPreview(true)}
                    disabled={producao.status !== 'CONFIRMADA'}
                    className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl border-2 border-primary text-primary font-semibold text-sm hover:bg-primary-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    <Eye size={16} />
                    Revisar e Emitir
                  </button>
                  {producao.status !== 'CONFIRMADA' && (
                    <p className="text-xs text-orange-600">
                      Produção precisa estar CONFIRMADA para emitir NFS-e.
                    </p>
                  )}
                </div>
              </div>
            ) : (
              /* Nota existe — mostrar status */
              <div className="space-y-5">
                {/* Status badge */}
                {meta && (
                  <div className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-semibold ${meta.cls} bg-current/10`}>
                    <meta.Icon size={16} className={`${meta.cls} ${currentStatus === 'PROCESSANDO' ? 'animate-spin' : ''}`} />
                    <span className={meta.cls}>{meta.label}</span>
                  </div>
                )}

                {/* Stepper (só para fluxo normal) */}
                {currentStatus !== 'AGUARDANDO_VALIDACAO' && currentStatus !== 'AGUARDANDO_EMISSAO_MANUAL' && (
                  <div className="space-y-0">
                    {STEPS.map((step, i) => (
                      <StepItem
                        key={step.status}
                        step={step}
                        status={getStepStatus(step, currentStatus!)}
                        isLast={i === STEPS.length - 1}
                      />
                    ))}
                  </div>
                )}

                {/* Aguardando Validação */}
                {currentStatus === 'AGUARDANDO_VALIDACAO' && (
                  <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-4 space-y-3">
                    <div className="flex items-start gap-2">
                      <ShieldCheck size={18} className="text-yellow-600 mt-0.5" />
                      <div>
                        <p className="font-semibold text-yellow-800 text-sm">1ª nota deste médico</p>
                        <p className="text-yellow-700 text-xs mt-0.5">
                          A primeira nota de um médico novo requer validação manual antes da emissão.
                          {primeiraNotaMedico && ' Notifique a equipe contábil.'}
                        </p>
                      </div>
                    </div>
                    {isGestaoOrContabil && (
                      <button
                        onClick={handleAprovar}
                        disabled={aprovando}
                        className="w-full inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-yellow-600 text-white text-sm font-semibold hover:bg-yellow-700 disabled:opacity-50 transition-colors"
                      >
                        {aprovando ? <Loader2 size={14} className="animate-spin" /> : <CheckCircle2 size={14} />}
                        {aprovando ? 'Aprovando...' : 'Aprovar e Emitir'}
                      </button>
                    )}
                  </div>
                )}

                {/* Aguardando emissão manual */}
                {currentStatus === 'AGUARDANDO_EMISSAO_MANUAL' && (
                  <div className="bg-orange-50 border border-orange-200 rounded-xl p-4">
                    <div className="flex items-start gap-2">
                      <AlertTriangle size={18} className="text-orange-600 mt-0.5" />
                      <div>
                        <p className="font-semibold text-orange-800 text-sm">Emissão manual necessária</p>
                        <p className="text-orange-700 text-xs mt-0.5">
                          {nota.observacoes ?? 'O agregador fiscal não conseguiu processar automaticamente. Emita manualmente na prefeitura.'}
                        </p>
                      </div>
                    </div>
                  </div>
                )}

                {/* Erro / Rejeitada */}
                {(currentStatus === 'ERRO' || currentStatus === 'REJEITADA') && nota.observacoes && (
                  <div className="bg-red-50 border border-red-200 rounded-xl p-4">
                    <div className="flex items-start gap-2">
                      <XCircle size={18} className="text-red-600 mt-0.5" />
                      <div>
                        <p className="font-semibold text-red-800 text-sm">Motivo da {currentStatus === 'REJEITADA' ? 'rejeição' : 'falha'}</p>
                        <p className="text-red-700 text-xs mt-0.5">{nota.observacoes}</p>
                      </div>
                    </div>
                  </div>
                )}

                {/* Número e protocolo quando emitida */}
                {currentStatus === 'EMITIDA' && (
                  <div className="bg-green-50 border border-green-200 rounded-xl p-4 space-y-1.5">
                    <p className="text-xs font-semibold text-green-700 uppercase">Nota Emitida</p>
                    <div className="flex justify-between text-sm">
                      <span className="text-green-600">Número</span>
                      <span className="font-mono font-bold text-green-800">{nota.numeroNota}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-green-600">Protocolo</span>
                      <span className="font-mono text-xs text-green-700">{nota.protocolo}</span>
                    </div>
                    {nota.emitidaAt && (
                      <div className="flex justify-between text-sm">
                        <span className="text-green-600">Emitida em</span>
                        <span className="text-green-700">
                          {new Date(nota.emitidaAt).toLocaleString('pt-BR')}
                        </span>
                      </div>
                    )}
                  </div>
                )}

                {/* Indicador de polling */}
                {!STATUS_META[currentStatus!].isTerminal && currentStatus !== 'AGUARDANDO_VALIDACAO' && (
                  <div className="flex items-center gap-2 text-xs text-ds-light">
                    <RotateCcw size={12} className="animate-spin" />
                    Atualizando a cada 3 segundos...
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Downloads — só quando emitida */}
          {currentStatus === 'EMITIDA' && (
            <div className="bg-white rounded-2xl border border-ds-border p-5">
              <h2 className="text-sm font-bold text-ds-mid uppercase tracking-wider mb-3">Downloads</h2>
              <div className="flex gap-3">
                <button
                  onClick={() => handleDownload('xml')}
                  className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border border-ds-border text-ds-mid text-sm font-medium hover:bg-ds-input transition-colors"
                >
                  <FileDown size={16} />
                  XML
                </button>
                <button
                  onClick={() => handleDownload('pdf')}
                  className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-primary text-white text-sm font-semibold hover:bg-primary-600 transition-colors"
                >
                  <FileText size={16} />
                  PDF (DANFSE)
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Modal de preview */}
      {showPreview && producao && (
        <NfsePreviewModal
          producao={producao}
          cnpjPrestador={cnpjPrestador}
          onClose={() => setShowPreview(false)}
          onConfirmar={() => {
            setShowPreview(false)
            handleEmitir()
          }}
          emitindo={emitindo}
        />
      )}
    </div>
  )
}
