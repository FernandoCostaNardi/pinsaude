import { X, CheckCircle2, Clock, FileText, XCircle, Building2, Stethoscope, Tag, Calendar } from 'lucide-react'
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
  medicoNome: string
  onClose: () => void
}

export function ProducaoDetalheModal({ producao, medicoNome, onClose }: Props) {
  if (!producao) return null

  const { tomador, servico, valorBruto, status } = producao
  const { label: statusLabel, cls: statusCls, Icon: StatusIcon } = STATUS_CFG[status]

  const taxaPin       = calcPct(valorBruto, 15)
  const issRetido     = tomador.retencaoIss      ? calcPct(valorBruto, servico.aliquotaIss)   : 0
  const irRetido      = tomador.retencaoFederal  ? calcPct(valorBruto, servico.aliquotaIr)    : 0
  const csllRetido    = tomador.retencaoFederal  ? calcPct(valorBruto, servico.aliquotaCsll)  : 0
  const pisRetido     = tomador.retencaoFederal  ? calcPct(valorBruto, servico.aliquotaPis)   : 0
  const cofinsRetido  = tomador.retencaoFederal  ? calcPct(valorBruto, servico.aliquotaCofins): 0
  const totalRetencoes = issRetido + irRetido + csllRetido + pisRetido + cofinsRetido
  const valorLiquido  = valorBruto - taxaPin - totalRetencoes

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
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-ds-light hover:text-ds-mid hover:bg-ds-input transition-colors"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="px-6 py-5 space-y-6">
          {/* Médico */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <Stethoscope size={14} className="text-primary" />
              <SectionTitle>Médico</SectionTitle>
            </div>
            <InfoRow label="Nome" value={medicoNome || producao.medicoId} />
            <InfoRow label="ID" value={<span className="font-mono text-xs">{producao.medicoId}</span>} />
          </div>

          {/* Tomador */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <Building2 size={14} className="text-primary" />
              <SectionTitle>Tomador de Serviço</SectionTitle>
            </div>
            <InfoRow label="Razão Social / Nome" value={tomador.razaoSocialNome} />
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

          {/* Serviço */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <Tag size={14} className="text-primary" />
              <SectionTitle>Serviço (LC 116/2003)</SectionTitle>
            </div>
            <InfoRow label="Código" value={<span className="font-mono">{servico.codigoLc116}</span>} />
            <InfoRow label="Descrição" value={servico.descricaoPadrao} />
            <div className="mt-2 bg-ds-surface rounded-lg p-3 grid grid-cols-5 gap-2">
              {[
                { label: 'ISS', value: servico.aliquotaIss },
                { label: 'IR', value: servico.aliquotaIr },
                { label: 'CSLL', value: servico.aliquotaCsll },
                { label: 'PIS', value: servico.aliquotaPis },
                { label: 'COFINS', value: servico.aliquotaCofins },
              ].map(({ label, value }) => (
                <div key={label} className="text-center">
                  <p className="text-[10px] text-ds-light font-medium">{label}</p>
                  <p className="text-sm font-semibold text-ds-mid">{Number(value).toFixed(2)}%</p>
                </div>
              ))}
            </div>
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
          <div>
            <SectionTitle>Composição Fiscal</SectionTitle>
            <div className="bg-ds-surface rounded-xl p-4 space-y-0.5">
              <FiscalRow label="Valor Bruto" value={valorBruto} />
              <FiscalRow label="Taxa Pin Saúde (15%)" value={taxaPin} indent negative />
              {issRetido > 0 && (
                <FiscalRow label={`ISS Retido (${Number(servico.aliquotaIss).toFixed(2)}%)`} value={issRetido} indent negative />
              )}
              {irRetido > 0 && (
                <FiscalRow label={`IR Retido (${Number(servico.aliquotaIr).toFixed(2)}%)`} value={irRetido} indent negative />
              )}
              {csllRetido > 0 && (
                <FiscalRow label={`CSLL Retido (${Number(servico.aliquotaCsll).toFixed(2)}%)`} value={csllRetido} indent negative />
              )}
              {pisRetido > 0 && (
                <FiscalRow label={`PIS Retido (${Number(servico.aliquotaPis).toFixed(2)}%)`} value={pisRetido} indent negative />
              )}
              {cofinsRetido > 0 && (
                <FiscalRow label={`COFINS Retido (${Number(servico.aliquotaCofins).toFixed(2)}%)`} value={cofinsRetido} indent negative />
              )}
              {totalRetencoes > 0 && (
                <div className="flex justify-between py-1 text-xs text-ds-light border-t border-dashed border-ds-border mt-1">
                  <span>Total de retenções</span>
                  <span className="text-red-500">({formatBRL(totalRetencoes)})</span>
                </div>
              )}
              <FiscalRow label="Valor Líquido ao Médico" value={valorLiquido} bold />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
