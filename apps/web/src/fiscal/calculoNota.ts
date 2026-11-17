// CBS-21 — prévia dos tributos de uma nota pelo motor fiscal (POST /api/motor-fiscal/calcular).
//
// As telas de emissão, prévia e detalhe da produção não calculam mais tributo nenhum: montam o
// pedido aqui, mandam ao motor e só desenham a resposta. Assim o regime (LEGADO ou IBS_CBS), as
// alíquotas de CBS/IBS do cronograma, o fator do ISS e as retenções vêm da mesma regra do backend.
//
// Alíquotas de ISS/IR/CSLL/PIS/COFINS: as mesmas que as telas já usavam (tomador sobrepõe o
// serviço LC116), enviadas ao motor como override. No regime novo o motor ignora PIS/COFINS.

import type { CalculoFiscalRequest, CalculoFiscalResult } from '../api/fiscalApi'
import type { ServicoResumo } from '../api/producoesApi'
import type { TipoTomador, TomadorAliquota } from '../api/tomadoresApi'
import {
  TRIBUTOS_COM_ALIQUOTA_CADASTRADA, isTipoTributo, totalTributos, tributosDoRegime, valoresTributo,
  type RegimeTributario, type TipoTributo, type ValoresTributo,
} from './tributos'
import type { SituacaoTributo } from '../components/LinhasTributo'

export interface EntradaCalculoNota {
  valorBruto: number
  competencia: string
  tomador: {
    retencaoFederal: boolean
    retencaoIss: boolean
    /** PACIENTE_PF = pessoa física; sem tipo (tomador não carregado) conta como PJ */
    tipo?: TipoTomador | null
    /** Alíquotas específicas do tomador (percentual: 5 = 5%) */
    aliquotas?: readonly TomadorAliquota[] | null
  }
  servico: ServicoResumo
}

const ALIQ_SERVICO: Record<typeof TRIBUTOS_COM_ALIQUOTA_CADASTRADA[number], keyof ServicoResumo> = {
  ISS: 'aliquotaIss',
  IR: 'aliquotaIr',
  CSLL: 'aliquotaCsll',
  PIS: 'aliquotaPis',
  COFINS: 'aliquotaCofins',
}

/** Alíquotas em percentual (5 = 5%): a do tomador quando cadastrada, senão a do serviço. */
export function aliquotasEfetivasPct(entrada: Pick<EntradaCalculoNota, 'tomador' | 'servico'>):
  Record<typeof TRIBUTOS_COM_ALIQUOTA_CADASTRADA[number], number> {
  const out = {} as Record<typeof TRIBUTOS_COM_ALIQUOTA_CADASTRADA[number], number>
  for (const t of TRIBUTOS_COM_ALIQUOTA_CADASTRADA) {
    const doTomador = entrada.tomador.aliquotas?.find(a => a.tipoTributo === t)
    out[t] = doTomador ? Number(doTomador.valorAliquota) : Number(entrada.servico[ALIQ_SERVICO[t]] ?? 0)
  }
  return out
}

export function montarCalculoRequest(entrada: EntradaCalculoNota): CalculoFiscalRequest {
  const pct = aliquotasEfetivasPct(entrada)
  const aliquotasOverride: Record<string, number> = {}
  // Arredonda para 8 casas: 0,65 / 100 em ponto flutuante vira 0.006500000000000001
  for (const t of TRIBUTOS_COM_ALIQUOTA_CADASTRADA) aliquotasOverride[t] = Number((pct[t] / 100).toFixed(8))
  const { servico } = entrada
  return {
    competencia: entrada.competencia,
    valorBruto: entrada.valorBruto,
    tomadorPj: entrada.tomador.tipo !== 'PACIENTE_PF',
    indicadorRetencaoFederal: entrada.tomador.retencaoFederal,
    indicadorRetencaoIss: entrada.tomador.retencaoIss,
    equiparacaoHospitalar: false,
    aliquotasOverride,
    reducaoIbsCbs: servico.reducaoIbsCbs ?? null,
    cstIbsCbs: servico.cstIbsCbs || null,
    cClassTrib: servico.cClassTrib || null,
    // Acerto "por fora" por tomador (CBS-01) ainda não tem cadastro: dentro do preço.
    cbsIbsPorFora: null,
  }
}

/** O que as telas precisam para desenhar a nota calculada. Valores em centavos. */
export interface NotaCalculada {
  regime: RegimeTributario
  /** Tributos a mostrar, na ordem de exibição (sem PIS/COFINS no regime novo, sem ISS em 2033) */
  tributos: TipoTributo[]
  /** Valor apurado de cada tributo */
  valores: ValoresTributo
  /** Só os retidos pelo tomador; os demais ficam 0 */
  valoresRetidos: ValoresTributo
  retidos: TipoTributo[]
  situacao: Partial<Record<TipoTributo, SituacaoTributo>>
  /** Alíquotas em percentual, já com fator do ISS e redução de CBS/IBS */
  aliquotasPct: Partial<Record<TipoTributo, number>>
  totalRetencoes: number
  /** Soma dos tributos mostrados */
  totalTributos: number
  /** Tributos que saem da cota da Pin (CBS/IBS por fora são pagos pelo tomador) */
  tributosDaPin: number
  cbsIbsPorFora: boolean
  baseCalculoIbsCbs: number
  /** Bruto, ou bruto + CBS + IBS quando por fora */
  valorTotalNota: number
  /** Valor total da nota menos as retenções */
  valorLiquidoNota: number
  cenario: string | null
  calculo: CalculoFiscalResult
}

/** Retidos pela regra do motor, para backend que ainda não devolve tributosRetidos. */
function retidosPorCenario(calc: CalculoFiscalResult, req: CalculoFiscalRequest): TipoTributo[] {
  const pj = req.tomadorPj
  const fed = req.indicadorRetencaoFederal
  const lista: TipoTributo[] = []
  if (pj && req.indicadorRetencaoIss) lista.push('ISS')
  if (pj && fed) lista.push('IR', 'CSLL', 'PIS', 'COFINS')
  if (!pj && !req.equiparacaoHospitalar) lista.push('IR')
  const v = valoresTributo(calc)
  return lista.filter(t => (v[t] ?? 0) > 0)
}

export function interpretarCalculo(calc: CalculoFiscalResult, req: CalculoFiscalRequest): NotaCalculada {
  const regime: RegimeTributario = calc.regime ?? 'LEGADO'
  const fatorIss = calc.fatorIss ?? 1
  const tributos = tributosDoRegime(regime, fatorIss)
  const valores = valoresTributo(calc)

  const retidos = (calc.tributosRetidos
    ? calc.tributosRetidos.filter(isTipoTributo)
    : retidosPorCenario(calc, req)
  ).filter(t => tributos.includes(t))

  const valoresRetidos: ValoresTributo = {}
  const situacao: Partial<Record<TipoTributo, SituacaoTributo>> = {}
  for (const t of tributos) {
    const retido = retidos.includes(t)
    valoresRetidos[t] = retido ? valores[t] ?? 0 : 0
    situacao[t] = retido ? 'RETIDO' : 'PRESTADORA'
  }

  const over = req.aliquotasOverride ?? {}
  const aliquotasPct: Partial<Record<TipoTributo, number>> = {
    ISS: (over.ISS ?? 0) * 100 * Number(fatorIss),
    IR: (over.IR ?? 0) * 100,
    CSLL: (over.CSLL ?? 0) * 100,
    PIS: (over.PIS ?? 0) * 100,
    COFINS: (over.COFINS ?? 0) * 100,
    CBS: Number(calc.aliqCbs ?? 0) * 100,
    IBS: Number(calc.aliqIbs ?? 0) * 100,
  }

  const porFora = !!calc.cbsIbsPorFora
  const total = totalTributos(tributos, valores)
  const cbsIbs = (valores.CBS ?? 0) + (valores.IBS ?? 0)
  const totalRetencoes = calc.totalRetencoes
  const valorTotalNota = calc.valorTotalNota ?? calc.valorBruto

  return {
    regime,
    tributos,
    valores,
    valoresRetidos,
    retidos,
    situacao,
    aliquotasPct,
    totalRetencoes,
    totalTributos: total,
    tributosDaPin: porFora ? total - cbsIbs : total,
    cbsIbsPorFora: porFora,
    baseCalculoIbsCbs: calc.baseCalculoIbsCbs ?? calc.valorBruto,
    valorTotalNota,
    valorLiquidoNota: valorTotalNota - totalRetencoes,
    cenario: calc.cenario ?? null,
    calculo: calc,
  }
}

/** Texto da retenção federal conforme os tributos do regime ("IR, CSLL, PIS e COFINS" ou "IR e CSLL"). */
export function rotuloRetencaoFederal(tributos: readonly TipoTributo[]): string {
  const fed = (['IR', 'CSLL', 'PIS', 'COFINS'] as const).filter(t => tributos.includes(t))
  if (fed.length <= 1) return fed.join('')
  return `${fed.slice(0, -1).join(', ')} e ${fed[fed.length - 1]}`
}
