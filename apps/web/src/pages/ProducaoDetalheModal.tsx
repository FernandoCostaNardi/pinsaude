import { X, CheckCircle2, Clock, FileText, XCircle, Building2, Stethoscope, Calendar, Send } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Producao } from '../api/producoesApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function formatPct(fracao: number): string {
  return `${(fracao * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`
}

function calcPct(valorCentavos: number, aliquotaPct: number): number {
  return Math.round(valorCentavos * aliquotaPct / 100)
}

// ─── Sub-components ──────────────────────────────────────────────────────────

const STATUS_CFG = {
  RASCUNHO:   { label: 'Rascunho',   cls: 'bg-gray-100 text-gray-700',    Icon: Clock        },
  CONFIRMADA: { label: 'Confirmada', cls: 'bg-primary-50 text-primary',    Icon: CheckCircle2 },
  EMITIDA:    { label: 'Emitida',    cls: 'bg-green-50 text-green-700',    Icon: FileText     },
  CANCELADA:  { label: 'Cancelada',  cls: 'bg-red-50 text-red-700',        Icon: XCircle      },
} as const

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h3 className="text-xs font-semibold text-ds-light uppercase tracking-wider mb-3">{children}</h3>
}

function InfoRow({ label, value, highlight }: { label: string; value: React.ReactNode; highlight?: boolean }) {
  return (
    <div className="flex items-start justify-between py-2 border-b border-ds-border last:border-0">
      <span className="text-sm text-ds-light">{label}</span>
      <span className={`text-sm font-medium text-right ml-4 ${highlight ? 'text-primary font-semibold' : 'text-ds-mid'}`}>
        {value}
      </span>
    </div>
  )
}

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

// ─── Modal ────────────────────────────────────────────────────────────────────

interface Props {
  producao: Producao | null
  medicoNomeMap: Record<string, string>
  onClose: () => void
}

export function ProducaoDetalheModal({ producao, medicoNomeMap, onClose }: Props) {
  const navigate = useNavigate()
  if (!producao) return null

  const { tomador, servico, valorBruto, status } = producao
  const { label: statusLabel, cls: statusCls, Icon: StatusIcon } = STATUS_CFG[status]

  // Taxa Pin: vem de cada participação, com o percentual acordado com o médico gravado no
  // lançamento (participacoes_producao.taxa_pin_pct) — nunca um 15% fixo.
  const taxaPin      = producao.participantes.reduce((s, p) => s + p.taxaPin, 0)
  const valorLiquido = producao.participantes.reduce((s, p) => s + p.valorLiquido, 0)
  const pcts         = Array.from(new Set(producao.participantes.map(p => Number(p.taxaPinPct))))
  const taxaPinLabel = pcts.length === 1 ? formatPct(pcts[0]) : 'conforme cada médico'
  const pctLiquido   = pcts.length === 1 ? formatPct(1 - pcts[0]) : null

  // servico pode ser null — o Serviço (LC 116/2003) é definido na emissão da NFS-e. Sem ele
  // não dá pra calcular retenções ainda; a apuração mostra um aviso no lugar dos tributos.
  const issRetido      = servico && tomador.retencaoIss     ? calcPct(valorBruto, servico.aliquotaIss)    : 0
  const irRetido       = servico && tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaIr)     : 0
  const csllRetido     = servico && tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCsll)   : 0
  const pisRetido      = servico && tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaPis)    : 0
  const cofinsRetido   = servico && tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCofins) : 0
  const totalRetencoes = issRetido + irRetido + csllRetido + pisRetido + cofinsRetido

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/40" />

      <div
        className="relative bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-ds-border sticky top-0 bg-white z-10">
          <div>
            <h2 className="text-lg font-bold text-ds-mid">Detalhes da Produção</h2>
            <p className="text-xs text-ds-light mt-0.5">
              {formatCompetencia(producao.competencia)} · {new Date(producao.createdAt).toLocaleDateString('pt-BR')}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold ${statusCls}`}>
              <StatusIcon size={12} />
              {statusLabel}
            </span>
            {status === 'CONFIRMADA' && (
              <button
                onClick={() => { onClose(); navigate(`/notas/emitir/${producao.id}`) }}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-white text-xs font-semibold hover:bg-primary-600 transition-colors"
              >
                <Send size={12} />
                Emitir NFS-e
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="px-6 py-5 space-y-6">
          {/* Participantes */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <Stethoscope size={14} className="text-primary" />
              <SectionTitle>Participantes ({producao.participantes.length})</SectionTitle>
            </div>
            {producao.participantes.map((part, idx) => (
              <div key={part.id} className="py-2 border-b border-ds-border last:border-0">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-ds-mid">
                    {medicoNomeMap[part.medicoId] ?? <span className="font-mono text-xs text-ds-light">{part.medicoId.slice(0, 8)}…</span>}
                  </span>
                  <span className="text-sm text-ds-mid font-semibold">{formatBRL(part.valorBruto)}</span>
                </div>
                <div className="flex justify-between mt-0.5">
                  <span className="text-xs text-ds-light">Participante {idx + 1}</span>
                  <span className="text-xs text-green-600">Líquido: {formatBRL(part.valorLiquido)}</span>
                </div>
              </div>
            ))}
          </div>

          {/* Tomador */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <Building2 size={14} className="text-primary" />
              <SectionTitle>Tomador de Serviço</SectionTitle>
            </div>
            <InfoRow label="Razão Social / Nome" value={tomador.razaoSocialNome} />
            {tomador.nomeFantasia && (
              <InfoRow label="Nome Fantasia" value={<span className="text-red-600">{tomador.nomeFantasia}</span>} />
            )}
            {tomador.municipio && <InfoRow label="Município" value={tomador.municipio} />}
            <InfoRow
              label="Retenção Federal"
              value={tomador.retencaoFederal
                ? <span className="text-orange-600">Sim — retém IR, CSLL, PIS, COFINS</span>
                : <span className="text-green-600">Não</span>}
            />
            <InfoRow
              label="Retenção ISS"
              value={tomador.retencaoIss
                ? <span className="text-orange-600">Sim — retém na fonte</span>
                : <span className="text-green-600">Não</span>}
            />
          </div>

          {/* Competência */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <Calendar size={14} className="text-primary" />
              <SectionTitle>Competência</SectionTitle>
            </div>
            <InfoRow label="Período" value={formatCompetencia(producao.competencia)} />
            <InfoRow label="Registro" value={new Date(producao.createdAt).toLocaleString('pt-BR')} />
            {producao.descricaoComplementar && (
              <InfoRow label="Descrição Complementar" value={producao.descricaoComplementar} />
            )}
          </div>

          {/* Breakdown fiscal */}
          <div className="space-y-3">
            <SectionTitle>Composição Fiscal</SectionTitle>

            {/* Repasse ao médico */}
            <div className="bg-green-50 border border-green-200 rounded-xl p-4">
              <p className="text-xs font-semibold text-green-700 uppercase tracking-wide mb-3">Repasse ao Médico</p>
              <FiscalRow label="Valor Bruto (contrato)" value={valorBruto} />
              <FiscalRow label={`Taxa Pin Saúde (${taxaPinLabel})`} value={taxaPin} indent negative />
              <div className="border-t-2 border-green-300 mt-2 pt-2.5 flex items-center justify-between">
                <span className="font-bold text-green-800 text-sm">Valor Líquido ao Médico</span>
                <span className="font-bold text-green-700 text-base">{formatBRL(valorLiquido)}</span>
              </div>
              {pctLiquido && (
                <p className="text-right text-xs text-green-600 mt-1">{pctLiquido} do valor bruto</p>
              )}
            </div>

            {/* Apuração fiscal Pin */}
            <div className="bg-ds-surface rounded-xl p-4">
              <p className="text-xs font-semibold text-ds-light uppercase tracking-wide mb-3">Apuração Fiscal Pin Saúde</p>
              <FiscalRow label={`Pin Saúde retém (${taxaPinLabel})`} value={taxaPin} />
              {servico && issRetido > 0 && (
                <FiscalRow label={`ISS (${Number(servico.aliquotaIss).toFixed(2)}%)`} value={issRetido} indent negative />
              )}
              {servico && irRetido > 0 && (
                <FiscalRow label={`IR (${Number(servico.aliquotaIr).toFixed(2)}%)`} value={irRetido} indent negative />
              )}
              {servico && csllRetido > 0 && (
                <FiscalRow label={`CSLL (${Number(servico.aliquotaCsll).toFixed(2)}%)`} value={csllRetido} indent negative />
              )}
              {servico && pisRetido > 0 && (
                <FiscalRow label={`PIS (${Number(servico.aliquotaPis).toFixed(2)}%)`} value={pisRetido} indent negative />
              )}
              {servico && cofinsRetido > 0 && (
                <FiscalRow label={`COFINS (${Number(servico.aliquotaCofins).toFixed(2)}%)`} value={cofinsRetido} indent negative />
              )}
              {!servico ? (
                <p className="text-xs text-orange-600 mt-1">
                  Serviço (LC 116/2003) ainda não definido — as retenções aparecem depois que ele for definido na emissão da NFS-e.
                </p>
              ) : totalRetencoes === 0 && (
                <p className="text-xs text-ds-light mt-1">Tomador não faz retenções sobre esta nota.</p>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
