import { describe, expect, it } from 'vitest'
import type { CalculoFiscalResult } from '../api/fiscalApi'
import type { ServicoResumo } from '../api/producoesApi'
import {
  aliquotasEfetivasPct, interpretarCalculo, montarCalculoRequest, rotuloRetencaoFederal,
  type EntradaCalculoNota,
} from './calculoNota'

const servico: ServicoResumo = {
  id: 's1', codigoLc116: '4.03', descricaoPadrao: 'Hospitais',
  aliquotaIss: 5, aliquotaIr: 1.5, aliquotaCsll: 1, aliquotaPis: 0.65, aliquotaCofins: 3,
  nbs: '1.2301.19.00', cstIbsCbs: '200', cClassTrib: '200029', reducaoIbsCbs: 0.6,
}

function entrada(over: Partial<EntradaCalculoNota['tomador']> = {}): EntradaCalculoNota {
  return {
    valorBruto: 100_000,
    competencia: '2027-01',
    tomador: { retencaoFederal: true, retencaoIss: false, tipo: 'HOSPITAL', aliquotas: [], ...over },
    servico,
  }
}

const base: CalculoFiscalResult = {
  valorBruto: 100_000, taxaPin: 15_000,
  valorIss: 5_000, valorIr: 1_500, valorCsll: 1_000, valorPis: 0, valorCofins: 0,
  totalRetencoes: 2_500, valorLiquidoMedico: 85_000, resultadoPin: 0,
  totalTributos: 12_000, regime: 'IBS_CBS', valorCbs: 4_400, valorIbs: 100,
  aliqCbs: 0.0448, aliqIbs: 0.001, fatorIss: 1, cbsIbsPorFora: false,
  baseCalculoIbsCbs: 95_694, valorTotalNota: 100_000,
  tributosRetidos: ['IR', 'CSLL'], cenario: 'A',
}

describe('montarCalculoRequest', () => {
  it('manda ao motor as alíquotas do serviço em fração e a classificação CBS/IBS', () => {
    const req = montarCalculoRequest(entrada())
    expect(req.aliquotasOverride).toEqual({ ISS: 0.05, IR: 0.015, CSLL: 0.01, PIS: 0.0065, COFINS: 0.03 })
    expect(req.reducaoIbsCbs).toBe(0.6)
    expect(req.cClassTrib).toBe('200029')
    expect(req.tomadorPj).toBe(true)
  })

  it('alíquota do tomador sobrepõe a do serviço', () => {
    const e = entrada({ aliquotas: [{ id: 'a', tipoTributo: 'ISS', valorAliquota: 2 }] })
    expect(aliquotasEfetivasPct(e).ISS).toBe(2)
    expect(montarCalculoRequest(e).aliquotasOverride?.ISS).toBe(0.02)
  })

  it('paciente PF não é PJ', () => {
    expect(montarCalculoRequest(entrada({ tipo: 'PACIENTE_PF' })).tomadorPj).toBe(false)
  })
})

describe('interpretarCalculo', () => {
  it('regime novo: mostra CBS/IBS, esconde PIS/COFINS e marca retidos pelo motor', () => {
    const req = montarCalculoRequest(entrada())
    const n = interpretarCalculo(base, req)
    expect(n.tributos).toEqual(['ISS', 'IR', 'CSLL', 'CBS', 'IBS'])
    expect(n.retidos).toEqual(['IR', 'CSLL'])
    expect(n.situacao).toMatchObject({ ISS: 'PRESTADORA', IR: 'RETIDO', CBS: 'PRESTADORA' })
    expect(n.valoresRetidos).toMatchObject({ ISS: 0, IR: 1_500, CSLL: 1_000, CBS: 0 })
    expect(n.valorLiquidoNota).toBe(97_500)
    expect(n.totalTributos).toBe(5_000 + 1_500 + 1_000 + 4_400 + 100)
    expect(n.tributosDaPin).toBe(n.totalTributos)
    expect(n.aliquotasPct.CBS).toBeCloseTo(4.48)
  })

  it('regime antigo: PIS/COFINS de volta, sem CBS/IBS', () => {
    const req = montarCalculoRequest({ ...entrada(), competencia: '2026-12' })
    const calc: CalculoFiscalResult = {
      ...base, regime: 'LEGADO', valorPis: 650, valorCofins: 3_000, valorCbs: 0, valorIbs: 0,
      totalRetencoes: 6_150, tributosRetidos: ['IR', 'CSLL', 'PIS', 'COFINS'],
    }
    const n = interpretarCalculo(calc, req)
    expect(n.tributos).toEqual(['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'])
    expect(n.retidos).toEqual(['IR', 'CSLL', 'PIS', 'COFINS'])
    expect(n.valorLiquidoNota).toBe(93_850)
  })

  it('CBS/IBS por fora: total da nota soma CBS/IBS e eles não saem da cota da Pin', () => {
    const req = montarCalculoRequest(entrada())
    const n = interpretarCalculo({ ...base, cbsIbsPorFora: true, valorTotalNota: 104_500 }, req)
    expect(n.valorTotalNota).toBe(104_500)
    expect(n.valorLiquidoNota).toBe(102_000)
    expect(n.tributosDaPin).toBe(n.totalTributos - 4_500)
  })

  it('backend sem tributosRetidos: deduz pelo cenário', () => {
    const req = montarCalculoRequest(entrada({ retencaoIss: true }))
    const n = interpretarCalculo({ ...base, tributosRetidos: undefined }, req)
    expect(n.retidos).toEqual(['ISS', 'IR', 'CSLL'])
  })

  it('2033: ISS extinto sai da lista', () => {
    const req = montarCalculoRequest(entrada())
    const n = interpretarCalculo({ ...base, fatorIss: 0, valorIss: 0 }, req)
    expect(n.tributos).toEqual(['IR', 'CSLL', 'CBS', 'IBS'])
  })
})

describe('rotuloRetencaoFederal', () => {
  it('lista os federais do regime', () => {
    expect(rotuloRetencaoFederal(['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'])).toBe('IR, CSLL, PIS e COFINS')
    expect(rotuloRetencaoFederal(['ISS', 'IR', 'CSLL', 'CBS', 'IBS'])).toBe('IR e CSLL')
  })
})
