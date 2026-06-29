import { useEffect, useRef, useState } from 'react'
import { X, Printer, Send, Loader2, AlertTriangle } from 'lucide-react'
import { Producao } from '../api/producoesApi'
import { Empresa } from '../api/empresasApi'
import { tomadoresApi, Tomador } from '../api/tomadoresApi'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatBRL(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatCompetenciaMesAno(comp: string): string {
  const [ano, mes] = comp.split('-')
  const meses = ['JANEIRO','FEVEREIRO','MARÇO','ABRIL','MAIO','JUNHO',
                  'JULHO','AGOSTO','SETEMBRO','OUTUBRO','NOVEMBRO','DEZEMBRO']
  return `${meses[parseInt(mes, 10) - 1]}/${ano}`
}

function formatCompetenciaMMYYYY(comp: string): string {
  const [ano, mes] = comp.split('-')
  return `${mes.padStart(2,'0')}/${ano}`
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

// ─── Componentes da DANFSe ────────────────────────────────────────────────────

function SecaoHeader({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-gray-700 px-3 py-1">
      <p className="text-[9px] font-bold text-white uppercase tracking-widest">{children}</p>
    </div>
  )
}

function CamposGrid({ children, cols = 2 }: { children: React.ReactNode; cols?: number }) {
  return (
    <div className={`px-3 py-2 grid gap-x-4 gap-y-2 bg-white`}
         style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}>
      {children}
    </div>
  )
}

function Campo({ label, value, span }: { label: string; value: string | null | undefined; span?: number }) {
  return (
    <div style={span ? { gridColumn: `span ${span}` } : undefined}>
      <p className="text-[8px] font-bold text-gray-500 uppercase tracking-wider">{label}</p>
      <p className="text-[11px] text-gray-900 font-medium leading-tight mt-0.5">{value || '—'}</p>
    </div>
  )
}

function LinhaValor({
  label, value, negativo, destaque, sub,
}: {
  label: string; value: number; negativo?: boolean; destaque?: boolean; sub?: boolean
}) {
  return (
    <div className={`flex justify-between py-1 ${destaque ? 'border-t-2 border-gray-400 mt-1' : sub ? 'pl-3' : ''}`}>
      <span className={`text-[10px] ${destaque ? 'font-bold text-gray-900' : sub ? 'text-gray-500' : 'text-gray-700'}`}>
        {sub && <span className="text-gray-400 mr-1">(−)</span>}{label}
      </span>
      <span className={`text-[10px] tabular-nums ${destaque ? 'font-bold text-gray-900' : negativo ? 'text-red-600' : 'text-gray-800'}`}>
        {negativo ? `(${formatBRL(value)})` : formatBRL(value)}
      </span>
    </div>
  )
}

// ─── Props ───────────────────────────────────────────────────────────────────

interface Props {
  producao: Producao
  cnpjPrestador: string | null | undefined
  empresaInfo: Empresa | null
  medicoNomeMap: Record<string, string>
  onClose: () => void
  onConfirmar: () => void
  emitindo: boolean
}

// ─── Modal ────────────────────────────────────────────────────────────────────

export function NfsePreviewModal({
  producao, cnpjPrestador, empresaInfo, medicoNomeMap, onClose, onConfirmar, emitindo,
}: Props) {
  const [tomador, setTomador] = useState<Tomador | null>(null)
  const [loadingTomador, setLoadingTomador] = useState(true)
  const documentRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    tomadoresApi.buscarPorId(producao.tomador.id)
      .then(setTomador)
      .catch(() => setTomador(null))
      .finally(() => setLoadingTomador(false))
  }, [producao.tomador.id])

  const { servico, valorBruto, competencia, participantes } = producao

  const issRetido    = producao.tomador.retencaoIss     ? calcPct(valorBruto, servico.aliquotaIss)    : 0
  const irRetido     = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaIr)     : 0
  const csllRetido   = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCsll)   : 0
  const pisRetido    = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaPis)    : 0
  const cofinsRetido = producao.tomador.retencaoFederal ? calcPct(valorBruto, servico.aliquotaCofins) : 0
  const totalFederal = irRetido + csllRetido + pisRetido + cofinsRetido
  const totalRetidos = issRetido + totalFederal
  const valorLiquido = valorBruto - totalRetidos

  // Nomes dos médicos para a discriminação
  const medicoNomes = participantes
    .map(p => medicoNomeMap[p.medicoId])
    .filter(Boolean)
  const medicoNomesStr = medicoNomes.length > 0 ? medicoNomes.join(', ') : null

  // Endereço do prestador
  const prestadorEndereco = [
    empresaInfo?.logradouro,
    empresaInfo?.bairro,
  ].filter(Boolean).join(', ')
  const prestadorLocalidade = [
    empresaInfo?.municipio,
    empresaInfo?.uf,
  ].filter(Boolean).join(' - ')

  // Endereço do tomador
  const tomadorEndereco = [
    tomador?.logradouro,
    tomador?.bairro,
  ].filter(Boolean).join(', ')
  const tomadorLocalidade = [
    tomador?.municipio || producao.tomador.municipio,
    tomador?.uf,
  ].filter(Boolean).join(' - ')

  const handlePrint = () => {
    const node = documentRef.current
    if (!node) return
    const w = window.open('', '_blank', 'width=860,height=1100')
    if (!w) return

    // Copia todos os estilos do app (Tailwind + Vite HMR styles) para a janela de impressão
    const linkTags = Array.from(document.querySelectorAll('link[rel="stylesheet"]'))
      .map(el => `<link rel="stylesheet" href="${(el as HTMLLinkElement).href}">`)
      .join('\n')
    const styleTags = Array.from(document.querySelectorAll('style'))
      .map(el => `<style>${el.textContent}</style>`)
      .join('\n')

    // Serializa o nó do documento com a tag externa (preserva classes de layout do container)
    const outerHtml = node.outerHTML

    w.document.write(`<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <title>DANFSe — Pin Saúde</title>
  ${linkTags}
  ${styleTags}
  <style>
    body { padding: 16px; background: #fff; }
    @media print {
      body { padding: 0; }
      @page { margin: 8mm; size: A4 portrait; }
    }
  </style>
</head>
<body>
${outerHtml}
</body>
</html>`)
    w.document.close()
    w.focus()
    // Aguarda estilos carregarem (link tags são async) antes de abrir o diálogo de impressão
    let printed = false
    const doPrint = () => {
      if (printed) return
      printed = true
      clearInterval(poll)
      setTimeout(() => w.print(), 300)
    }
    const poll = setInterval(() => {
      if (w.document.readyState === 'complete') doPrint()
    }, 50)
    setTimeout(doPrint, 2000)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/50" />

      <div
        className="relative bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[94vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        {/* Toolbar */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-200 shrink-0">
          <div>
            <h2 className="text-base font-bold text-gray-800">Prévia da NFS-e (DANFSe)</h2>
            <p className="text-xs text-gray-500 mt-0.5">Revise os dados antes de confirmar a emissão</p>
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

        {/* Alert */}
        <div className="mx-5 mt-3 shrink-0 flex items-center gap-2 bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2">
          <AlertTriangle size={13} className="text-yellow-600 shrink-0" />
          <p className="text-xs text-yellow-800">
            Prévia para validação. O número e protocolo definitivos serão gerados pela prefeitura na emissão.
          </p>
        </div>

        {/* Documento */}
        <div className="overflow-y-auto flex-1 px-5 pb-5 mt-3">
          <div
            ref={documentRef}
            className="relative bg-white border-2 border-gray-400 text-[11px] select-text overflow-hidden"
          >
            {/* Marca d'água */}
            <div
              className="absolute inset-0 flex items-center justify-center pointer-events-none z-10 overflow-hidden"
              aria-hidden
            >
              <p
                className="font-black whitespace-nowrap"
                style={{
                  transform: 'rotate(-38deg)',
                  fontSize: '42px',
                  color: 'rgba(0,0,0,0.04)',
                  letterSpacing: '3px',
                  userSelect: 'none',
                }}
              >
                PRÉVIA — NÃO É DOCUMENTO FISCAL
              </p>
            </div>

            {/* ── Cabeçalho ── */}
            <div className="bg-gray-800 px-4 py-2.5 flex items-center justify-between">
              <div>
                <p className="text-white font-black text-sm tracking-wide">NOTA FISCAL DE SERVIÇOS ELETRÔNICA — NFS-e</p>
                <p className="text-gray-300 text-[9px] mt-0.5">
                  Documento Auxiliar da NFS-e (DANFSe) · LC 116/2003 · PRÉVIA
                </p>
              </div>
              <div className="text-right">
                <p className="text-gray-400 text-[8px] uppercase">Competência</p>
                <p className="text-white font-bold text-sm">{formatCompetenciaMMYYYY(competencia)}</p>
              </div>
            </div>

            {/* ── Número / RPS ── */}
            <div className="border-b border-gray-300 px-4 py-2 grid grid-cols-4 gap-4 bg-gray-50">
              {[
                { label: 'Número da NFS-e', value: 'A SER GERADO' },
                { label: 'Número DPS/RPS', value: 'PRÉVIA' },
                { label: 'Data de Emissão', value: new Date().toLocaleDateString('pt-BR') },
                { label: 'Competência', value: formatCompetenciaMMYYYY(competencia) },
              ].map(({ label, value }) => (
                <div key={label}>
                  <p className="text-[8px] font-bold text-gray-500 uppercase tracking-wider">{label}</p>
                  <p className="text-[11px] font-bold text-gray-800 mt-0.5">{value}</p>
                </div>
              ))}
            </div>

            {/* ── Emitente / Prestador ── */}
            <SecaoHeader>Emitente da NFS-e — Prestador do Serviço</SecaoHeader>
            <CamposGrid cols={4}>
              <Campo label="CNPJ / CPF / NIF" value={formatCnpj(cnpjPrestador ?? '')} />
              <Campo label="Inscrição Municipal" value={empresaInfo?.inscricaoMunicipal || '—'} />
              <Campo label="Telefone" value={empresaInfo?.telefone || '—'} />
              <Campo label="E-mail" value={empresaInfo?.emailContato || '—'} />
              <Campo label="Nome / Razão Social" value={empresaInfo?.razaoSocial || 'Pin Saúde Serviços Médicos Ltda'} span={2} />
              <Campo label="Regime Tributário"
                     value={empresaInfo?.regimeTributario?.replace('_', ' ') || 'LUCRO PRESUMIDO'} />
              <Campo label="Simples Nacional" value="Não optante" />
              <Campo
                label="Endereço"
                value={prestadorEndereco || 'Av. Presidente Getúlio Vargas, 1605, Lj 09'}
                span={2}
              />
              <Campo label="Município" value={prestadorLocalidade || 'Olinda - PE'} />
              <Campo label="CEP" value={empresaInfo?.cep || '53030-010'} />
            </CamposGrid>

            {/* ── Tomador ── */}
            <SecaoHeader>Tomador do Serviço</SecaoHeader>
            {loadingTomador ? (
              <div className="px-3 py-4 flex items-center gap-2 bg-white">
                <Loader2 size={13} className="animate-spin text-primary" />
                <span className="text-xs text-gray-500">Carregando dados do tomador...</span>
              </div>
            ) : (
              <CamposGrid cols={4}>
                <Campo label="CNPJ / CPF / NIF" value={tomador ? formatCnpj(tomador.cnpjCpf) : '—'} />
                <Campo label="Inscrição Municipal" value={tomador?.inscricaoMunicipal || '—'} />
                <Campo label="Telefone" value={tomador?.telefone || '—'} />
                <Campo label="E-mail" value={tomador?.email || '—'} />
                <Campo label="Nome / Razão Social" value={producao.tomador.razaoSocialNome} span={3} />
                <Campo label="País" value={tomador?.pais || 'Brasil'} />
                <Campo
                  label="Endereço"
                  value={tomadorEndereco || '—'}
                  span={2}
                />
                <Campo label="Município" value={tomadorLocalidade || '—'} />
                <Campo label="CEP" value={tomador?.cep || '—'} />
              </CamposGrid>
            )}

            {/* ── Serviço Prestado ── */}
            <SecaoHeader>Serviço Prestado</SecaoHeader>
            <CamposGrid cols={4}>
              <Campo label="Cód. de Tributação Nacional" value="04.02.01" />
              <Campo label="Código LC 116/2003" value={servico.codigoLc116} />
              <Campo label="Local da Prestação"
                     value={`${empresaInfo?.municipio || 'Olinda'} - ${empresaInfo?.uf || 'PE'}`} />
              <Campo label="País da Prestação" value="Brasil" />
              <Campo
                label="Descrição do Serviço"
                value={servico.descricaoPadrao}
                span={4}
              />
            </CamposGrid>

            {/* ── Discriminação ── */}
            <SecaoHeader>Discriminação dos Serviços</SecaoHeader>
            <div className="px-3 py-2.5 bg-white border-b border-gray-200">
              <pre className="text-[10px] text-gray-700 font-mono whitespace-pre-wrap leading-5">
{[
  `Serviços médicos prestados ref ${formatCompetenciaMesAno(competencia)}${medicoNomesStr ? ` — ${medicoNomesStr}` : ''}`,
  ``,
  `Dados bancários: Banco Inter: 077 / Agência: 0001 / Conta Corrente PJ: 3875500-9`,
  `Chave Pix: financeiro@pinsaude.com.br`,
].join('\n')}
              </pre>
            </div>

            {/* ── Tributação Municipal ── */}
            <SecaoHeader>Tributação Municipal — ISSQN</SecaoHeader>
            <CamposGrid cols={4}>
              <Campo label="Tributação do ISSQN"
                     value={issRetido > 0 ? 'Retido pelo Tomador' : 'Operação Tributável'} />
              <Campo label="Município de Incidência"
                     value={`${empresaInfo?.municipio || 'Olinda'} - ${empresaInfo?.uf || 'PE'}`} />
              <Campo label="Regime Especial" value="Nenhum" />
              <Campo label="Suspensão ISSQN" value="Não" />
              <Campo label="BC ISSQN" value={formatBRL(valorBruto)} />
              <Campo label="Alíquota ISSQN" value={`${Number(servico.aliquotaIss).toFixed(2)}%`} />
              <Campo label="ISSQN Retido?" value={issRetido > 0 ? 'Sim' : 'Não Retido'} />
              <Campo label="ISSQN Apurado" value={formatBRL(calcPct(valorBruto, servico.aliquotaIss))} />
            </CamposGrid>

            {/* ── Tributação Federal ── */}
            <SecaoHeader>Tributação Federal</SecaoHeader>
            <CamposGrid cols={4}>
              <Campo label="IRRF"
                     value={irRetido > 0 ? formatBRL(irRetido) : `${formatBRL(calcPct(valorBruto, servico.aliquotaIr))} (a recolher)`} />
              <Campo label="CSLL"
                     value={csllRetido > 0 ? formatBRL(csllRetido) : `${formatBRL(calcPct(valorBruto, servico.aliquotaCsll))} (a recolher)`} />
              <Campo label="PIS"
                     value={pisRetido > 0 ? formatBRL(pisRetido) : `${formatBRL(calcPct(valorBruto, servico.aliquotaPis))} (a recolher)`} />
              <Campo label="COFINS"
                     value={cofinsRetido > 0 ? formatBRL(cofinsRetido) : `${formatBRL(calcPct(valorBruto, servico.aliquotaCofins))} (a recolher)`} />
              {totalFederal > 0 && (
                <Campo
                  label="Contrib. Sociais Retidas (PIS/COFINS/CSLL)"
                  value={formatBRL(csllRetido + pisRetido + cofinsRetido)}
                  span={2}
                />
              )}
              <Campo
                label={totalFederal > 0 ? 'Total Retenções Federais' : 'Observação'}
                value={totalFederal > 0
                  ? formatBRL(totalFederal)
                  : 'Tributos federais a recolher pela prestadora via DARF'}
                span={totalFederal > 0 ? 2 : 4}
              />
            </CamposGrid>

            {/* ── Valor Total ── */}
            <SecaoHeader>Valor Total da NFS-e</SecaoHeader>
            <div className="px-4 py-3 bg-white grid grid-cols-2 gap-6">
              <div>
                <LinhaValor label="Valor dos Serviços" value={valorBruto} />
                {issRetido > 0 && (
                  <LinhaValor label={`ISSQN Retido (${Number(servico.aliquotaIss).toFixed(2)}%)`} value={issRetido} sub negativo />
                )}
                {totalFederal > 0 && (
                  <LinhaValor label="Total Retenções Federais" value={totalFederal} sub negativo />
                )}
                <LinhaValor
                  label={totalRetidos > 0 ? 'Valor Líquido da NFS-e' : 'Valor Líquido da NFS-e (sem retenções)'}
                  value={valorLiquido}
                  destaque
                />
              </div>
              <div className="pl-4 border-l border-gray-200 text-[9px] text-gray-500 space-y-1.5">
                <p className="font-bold text-gray-700 text-[10px]">Composição dos Tributos</p>
                {[
                  { nome: 'ISS', aliq: servico.aliquotaIss, retido: issRetido > 0 },
                  { nome: 'IR',  aliq: servico.aliquotaIr,  retido: irRetido > 0 },
                  { nome: 'CSLL',aliq: servico.aliquotaCsll,retido: csllRetido > 0 },
                  { nome: 'PIS', aliq: servico.aliquotaPis, retido: pisRetido > 0 },
                  { nome: 'COFINS',aliq: servico.aliquotaCofins, retido: cofinsRetido > 0 },
                ].map(t => (
                  <div key={t.nome} className="flex justify-between">
                    <span>
                      {t.nome} ({Number(t.aliq).toFixed(2)}%)
                      {' '}
                      <span className={`px-1 rounded text-[8px] font-bold ${
                        t.retido ? 'bg-orange-100 text-orange-700' : 'bg-blue-50 text-blue-600'
                      }`}>
                        {t.retido ? 'RETIDO' : 'PRESTADORA'}
                      </span>
                    </span>
                    <span className="tabular-nums text-gray-700">
                      {formatBRL(calcPct(valorBruto, t.aliq))}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* ── Rodapé ── */}
            <div className="border-t border-gray-200 px-4 py-2 bg-gray-50">
              <p className="text-[8px] text-gray-400 leading-4">
                Documento gerado com base na LC 116/2003 e legislação municipal vigente. Valores de retenção são de
                responsabilidade do tomador de serviços. Número definitivo será atribuído pela prefeitura na emissão.
                CNPJ Prestador: {formatCnpj(cnpjPrestador ?? '')} — Pin Saúde Serviços Médicos.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
