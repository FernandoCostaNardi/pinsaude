import { useEffect, useRef, useState } from 'react'
import { X, Printer, Send, Loader2, AlertTriangle } from 'lucide-react'
import { Producao } from '../api/producoesApi'
import { tomadoresApi, Tomador } from '../api/tomadoresApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetencia(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro']
  return `${meses[parseInt(mes, 10) - 1]} de ${ano}`
}

function formatCnpj(digits: string): string {
  const d = digits.replace(/\D/g, '')
  if (d.length === 14) return d.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5')
  if (d.length === 11) return d.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4')
  return digits
}

function calcPct(valorCentavos: number, aliquotaPct: number): number {
  return Math.round(valorCentavos * aliquotaPct / 100)
}

// ─── Linha de valor fiscal ────────────────────────────────────────────────────

function ValorRow({ label, value, destaque }: { label: string; value: number; destaque?: boolean }) {
  return (
    <tr className={destaque ? 'bg-primary-50' : ''}>
      <td className={`px-3 py-1.5 text-xs border-b border-r border-gray-200 ${destaque ? 'font-bold text-primary' : 'text-gray-600'}`}>
        {label}
      </td>
      <td className={`px-3 py-1.5 text-xs text-right border-b border-gray-200 tabular-nums ${destaque ? 'font-bold text-primary' : 'text-gray-800'}`}>
        {formatBRL(value)}
      </td>
    </tr>
  )
}

// ─── Célula de dado ───────────────────────────────────────────────────────────

function Campo({ label, value, fullWidth }: { label: string; value: string | null | undefined; fullWidth?: boolean }) {
  return (
    <div className={`${fullWidth ? 'col-span-2' : ''} min-w-0`}>
      <p className="text-[9px] font-bold text-gray-500 uppercase tracking-wider mb-0.5">{label}</p>
      <p className="text-xs text-gray-900 font-medium truncate">{value || '—'}</p>
    </div>
  )
}

// ─── Seção ────────────────────────────────────────────────────────────────────

function Secao({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="border border-gray-300 rounded overflow-hidden">
      <div className="bg-primary px-3 py-1">
        <p className="text-[10px] font-bold text-white uppercase tracking-wider">{title}</p>
      </div>
      <div className="px-3 py-2.5 grid grid-cols-2 gap-x-6 gap-y-3 bg-white">
        {children}
      </div>
    </div>
  )
}

// ─── Modal ────────────────────────────────────────────────────────────────────

interface Props {
  producao: Producao
  cnpjPrestador: string | null | undefined
  onClose: () => void
  onConfirmar: () => void
  emitindo: boolean
}

export function NfsePreviewModal({ producao, cnpjPrestador, onClose, onConfirmar, emitindo }: Props) {
  const [tomador, setTomador] = useState<Tomador | null>(null)
  const [loadingTomador, setLoadingTomador] = useState(true)
  const documentRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    tomadoresApi.buscarPorId(producao.tomador.id)
      .then(setTomador)
      .catch(() => setTomador(null))
      .finally(() => setLoadingTomador(false))
  }, [producao.tomador.id])

  const { servico, valorBruto, competencia } = producao
  const taxaPin        = calcPct(valorBruto, 15)
  const valorLiquido   = valorBruto - taxaPin
  const issRetido      = producao.tomador.retencaoIss     ? calcPct(valorBruto, servico.aliquotaIss)    : 0
  const irRetido       = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaIr)     : 0
  const csllRetido     = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCsll)   : 0
  const pisRetido      = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaPis)    : 0
  const cofinsRetido   = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCofins) : 0
  const totalRetencoes = issRetido + irRetido + csllRetido + pisRetido + cofinsRetido

  const discriminacao = [
    `PRESTAÇÃO DE SERVIÇOS MÉDICOS - COMPETÊNCIA ${formatCompetencia(competencia).toUpperCase()}`,
    `SERVIÇO: ${servico.codigoLc116} - ${servico.descricaoPadrao}`,
    '',
    `VALOR BRUTO DO SERVIÇO: ${formatBRL(valorBruto)}`,
    `TAXA DE ADMINISTRAÇÃO PIN SAÚDE (15%): (${formatBRL(taxaPin)})`,
    `VALOR LÍQUIDO AO MÉDICO: ${formatBRL(valorLiquido)}`,
    '',
    totalRetencoes > 0
      ? `TRIBUTOS RETIDOS PELO TOMADOR: ${formatBRL(totalRetencoes)}`
      : 'TRIBUTOS: RECOLHIDOS PELA PRESTADORA (SEM RETENÇÃO NA FONTE)',
  ].filter(Boolean).join('\n')

  const handlePrint = () => {
    const content = documentRef.current?.innerHTML
    if (!content) return
    const w = window.open('', '_blank', 'width=800,height=900')
    if (!w) return
    w.document.write(`
      <html><head><title>Preview NFS-e — Pin Saúde</title>
      <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }
        @media print { body { padding: 0; } }
        .watermark {
          position: fixed; top: 50%; left: 50%;
          transform: translate(-50%,-50%) rotate(-45deg);
          font-size: 60px; font-weight: 900; color: rgba(0,0,0,0.06);
          white-space: nowrap; pointer-events: none; z-index: 9999; letter-spacing: 4px;
        }
      </style>
      </head><body>
      <div class="watermark">PRÉVIA — NÃO É DOCUMENTO FISCAL</div>
      ${content}
      </body></html>
    `)
    w.document.close()
    w.focus()
    setTimeout(() => w.print(), 500)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/50" />

      <div
        className="relative bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[92vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        {/* Toolbar */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-200 shrink-0">
          <div>
            <h2 className="text-base font-bold text-gray-800">Preview da NFS-e</h2>
            <p className="text-xs text-gray-500 mt-0.5">Revise os dados antes de enviar para emissão</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handlePrint}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-gray-200 text-gray-600 text-xs font-medium hover:bg-gray-50 transition-colors"
            >
              <Printer size={13} />
              Imprimir
            </button>
            <button
              onClick={onConfirmar}
              disabled={emitindo}
              className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-primary text-white text-xs font-semibold hover:bg-primary-600 disabled:opacity-50 transition-colors"
            >
              {emitindo ? <Loader2 size={13} className="animate-spin" /> : <Send size={13} />}
              {emitindo ? 'Emitindo...' : 'Confirmar e Emitir'}
            </button>
            <button onClick={onClose} className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors">
              <X size={16} />
            </button>
          </div>
        </div>

        {/* Alert de preview */}
        <div className="mx-5 mt-3 shrink-0 flex items-center gap-2 bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2">
          <AlertTriangle size={13} className="text-yellow-600 shrink-0" />
          <p className="text-xs text-yellow-800">
            Este é apenas um preview para validação. O documento real será gerado e numerado pela prefeitura após a emissão.
          </p>
        </div>

        {/* Documento */}
        <div className="overflow-y-auto flex-1 px-5 pb-5 mt-3">
          <div ref={documentRef} className="relative bg-white border-2 border-gray-300 rounded overflow-hidden select-text">

            {/* Watermark */}
            <div
              className="absolute inset-0 flex items-center justify-center pointer-events-none z-10 overflow-hidden"
              aria-hidden
            >
              <p
                className="text-gray-200 font-black text-2xl whitespace-nowrap"
                style={{ transform: 'rotate(-35deg)', letterSpacing: '3px', userSelect: 'none' }}
              >
                PRÉVIA — NÃO É DOCUMENTO FISCAL
              </p>
            </div>

            {/* Cabeçalho */}
            <div className="bg-primary px-4 py-3 flex items-center justify-between">
              <div>
                <p className="text-white font-black text-sm tracking-wide">NOTA FISCAL DE SERVIÇOS ELETRÔNICA</p>
                <p className="text-primary-100 text-[10px] mt-0.5">NFS-e — Lei Complementar 116/2003</p>
              </div>
              <div className="text-right">
                <p className="text-primary-100 text-[9px] uppercase">Competência</p>
                <p className="text-white font-bold text-sm">{formatCompetencia(competencia)}</p>
              </div>
            </div>

            {/* Número / RPS (provisório) */}
            <div className="bg-primary-50 border-b border-primary-100 px-4 py-2 flex items-center justify-between">
              <div className="flex items-center gap-6">
                <div>
                  <p className="text-[9px] text-primary-600 uppercase font-bold">Número da NFS-e</p>
                  <p className="text-sm font-black text-primary">A SER GERADO</p>
                </div>
                <div>
                  <p className="text-[9px] text-primary-600 uppercase font-bold">RPS (Provisório)</p>
                  <p className="text-sm font-bold text-primary-700">PRÉVIA</p>
                </div>
              </div>
              <div className="text-right">
                <p className="text-[9px] text-primary-600 uppercase font-bold">Data Prevista de Emissão</p>
                <p className="text-xs font-bold text-primary-700">{new Date().toLocaleDateString('pt-BR')}</p>
              </div>
            </div>

            <div className="p-4 space-y-3">
              {/* Prestador */}
              <Secao title="Prestador de Serviços">
                <Campo label="CNPJ"         value={formatCnpj(cnpjPrestador ?? '')} />
                <Campo label="Razão Social"  value="Pin Saúde Serviços Médicos" />
                <Campo label="Inscrição Municipal" value="—" />
                <Campo label="Regime Tributário"   value="Lucro Presumido" />
              </Secao>

              {/* Tomador */}
              <Secao title="Tomador de Serviços">
                {loadingTomador ? (
                  <div className="col-span-2 flex items-center gap-2 py-2">
                    <Loader2 size={12} className="animate-spin text-primary" />
                    <span className="text-xs text-gray-500">Carregando dados do tomador...</span>
                  </div>
                ) : (
                  <>
                    <Campo label="CNPJ / CPF"       value={tomador ? formatCnpj(tomador.cnpjCpf) : '—'} />
                    <Campo label="Inscrição Municipal" value={tomador?.inscricaoMunicipal || '—'} />
                    <Campo label="Razão Social / Nome" value={producao.tomador.razaoSocialNome} fullWidth />
                    <Campo label="Município"           value={producao.tomador.municipio || tomador?.municipio || '—'} />
                    <Campo label="Retenção Federal"    value={producao.tomador.retencaoFederal ? 'Sim — retém IR, CSLL, PIS, COFINS' : 'Não'} />
                    <Campo label="Retenção ISS"        value={producao.tomador.retencaoIss ? 'Sim — retém na fonte' : 'Não'} />
                  </>
                )}
              </Secao>

              {/* Serviço */}
              <Secao title="Descrição do Serviço">
                <Campo label="Código LC 116/2003" value={servico.codigoLc116} />
                <Campo label="Competência"        value={formatCompetencia(competencia)} />
                <Campo label="Descrição"          value={servico.descricaoPadrao} fullWidth />
              </Secao>

              {/* Discriminação */}
              <div className="border border-gray-300 rounded overflow-hidden">
                <div className="bg-primary px-3 py-1">
                  <p className="text-[10px] font-bold text-white uppercase tracking-wider">Discriminação dos Serviços</p>
                </div>
                <div className="bg-white px-3 py-2.5">
                  <pre className="text-[10px] text-gray-700 font-mono whitespace-pre-wrap leading-5">{discriminacao}</pre>
                </div>
              </div>

              {/* Valores */}
              <div className="border border-gray-300 rounded overflow-hidden">
                <div className="bg-primary px-3 py-1">
                  <p className="text-[10px] font-bold text-white uppercase tracking-wider">Valores e Tributos</p>
                </div>
                <div className="bg-white grid grid-cols-2 gap-0">
                  {/* Coluna esquerda: tributos */}
                  <div className="border-r border-gray-200">
                    <table className="w-full">
                      <thead>
                        <tr className="bg-gray-50">
                          <th className="px-3 py-1.5 text-[9px] font-bold text-gray-500 uppercase text-left border-b border-r border-gray-200">Tributo</th>
                          <th className="px-3 py-1.5 text-[9px] font-bold text-gray-500 uppercase text-right border-b border-gray-200">Valor</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td className="px-3 py-1.5 text-xs text-gray-600 border-b border-r border-gray-200">
                            ISS ({Number(servico.aliquotaIss).toFixed(2)}%)
                            {issRetido > 0 && <span className="ml-1 text-[9px] text-orange-600 font-semibold">RETIDO</span>}
                          </td>
                          <td className="px-3 py-1.5 text-xs text-right border-b border-gray-200 tabular-nums text-gray-800">
                            {formatBRL(calcPct(valorBruto, servico.aliquotaIss))}
                          </td>
                        </tr>
                        <tr>
                          <td className="px-3 py-1.5 text-xs text-gray-600 border-b border-r border-gray-200">
                            IR ({Number(servico.aliquotaIr).toFixed(2)}%)
                            {irRetido > 0 && <span className="ml-1 text-[9px] text-orange-600 font-semibold">RETIDO</span>}
                          </td>
                          <td className="px-3 py-1.5 text-xs text-right border-b border-gray-200 tabular-nums text-gray-800">
                            {formatBRL(calcPct(valorBruto, servico.aliquotaIr))}
                          </td>
                        </tr>
                        <tr>
                          <td className="px-3 py-1.5 text-xs text-gray-600 border-b border-r border-gray-200">
                            CSLL ({Number(servico.aliquotaCsll).toFixed(2)}%)
                            {csllRetido > 0 && <span className="ml-1 text-[9px] text-orange-600 font-semibold">RETIDO</span>}
                          </td>
                          <td className="px-3 py-1.5 text-xs text-right border-b border-gray-200 tabular-nums text-gray-800">
                            {formatBRL(calcPct(valorBruto, servico.aliquotaCsll))}
                          </td>
                        </tr>
                        <tr>
                          <td className="px-3 py-1.5 text-xs text-gray-600 border-b border-r border-gray-200">
                            PIS ({Number(servico.aliquotaPis).toFixed(2)}%)
                            {pisRetido > 0 && <span className="ml-1 text-[9px] text-orange-600 font-semibold">RETIDO</span>}
                          </td>
                          <td className="px-3 py-1.5 text-xs text-right border-b border-gray-200 tabular-nums text-gray-800">
                            {formatBRL(calcPct(valorBruto, servico.aliquotaPis))}
                          </td>
                        </tr>
                        <tr>
                          <td className="px-3 py-1.5 text-xs text-gray-600 border-r border-gray-200">
                            COFINS ({Number(servico.aliquotaCofins).toFixed(2)}%)
                            {cofinsRetido > 0 && <span className="ml-1 text-[9px] text-orange-600 font-semibold">RETIDO</span>}
                          </td>
                          <td className="px-3 py-1.5 text-xs text-right tabular-nums text-gray-800">
                            {formatBRL(calcPct(valorBruto, servico.aliquotaCofins))}
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>

                  {/* Coluna direita: resumo financeiro */}
                  <div>
                    <table className="w-full">
                      <thead>
                        <tr className="bg-gray-50">
                          <th className="px-3 py-1.5 text-[9px] font-bold text-gray-500 uppercase text-left border-b border-r border-gray-200">Composição</th>
                          <th className="px-3 py-1.5 text-[9px] font-bold text-gray-500 uppercase text-right border-b border-gray-200">Valor</th>
                        </tr>
                      </thead>
                      <tbody>
                        <ValorRow label="Valor Bruto dos Serviços" value={valorBruto} />
                        {totalRetencoes > 0 && (
                          <ValorRow label={`Deduções / Retenções na Fonte`} value={totalRetencoes} />
                        )}
                        <ValorRow label="Taxa Pin Saúde (15%)" value={taxaPin} />
                        <ValorRow label="Valor Líquido ao Médico (85%)" value={valorLiquido} destaque />
                      </tbody>
                    </table>

                    {/* Resumo de repasse */}
                    <div className="border-t-2 border-primary mx-2 my-2 pt-2">
                      <div className="bg-green-50 rounded p-2 text-center">
                        <p className="text-[9px] text-green-700 font-bold uppercase">Repasse Garantido ao Médico</p>
                        <p className="text-base font-black text-green-800 mt-0.5">{formatBRL(valorLiquido)}</p>
                        <p className="text-[9px] text-green-600 mt-0.5">Sempre 85% do valor bruto</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Observações fiscais */}
              <div className="border border-gray-200 rounded bg-gray-50 px-3 py-2">
                <p className="text-[9px] text-gray-500 leading-4">
                  Documento gerado com base na LC 116/2003 e na legislação municipal vigente.
                  Os valores de retenção indicados são de responsabilidade do tomador de serviços.
                  O número definitivo da NFS-e será atribuído pela prefeitura no momento da emissão.
                  CNPJ Prestador: {formatCnpj(cnpjPrestador ?? '')} — Pin Saúde Serviços Médicos.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
