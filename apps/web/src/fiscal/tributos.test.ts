import { describe, expect, it } from 'vitest'
import {
  TRIBUTOS_LEGADO, totalTributos, tributosDaNota, tributosDoRegime, valoresTributo,
} from './tributos'

describe('tributosDaNota', () => {
  it('nota antiga sem regime gravado mostra PIS/COFINS', () => {
    expect(tributosDaNota({})).toEqual(['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'])
  })

  it('nota LEGADO mostra PIS/COFINS', () => {
    expect(tributosDaNota({ regimeTributario: 'LEGADO', fatorIss: null }))
      .toEqual(['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'])
  })

  it('nota IBS_CBS troca PIS/COFINS por CBS/IBS e mantém ISS, IR e CSLL', () => {
    expect(tributosDaNota({ regimeTributario: 'IBS_CBS', fatorIss: 1 }))
      .toEqual(['ISS', 'IR', 'CSLL', 'CBS', 'IBS'])
  })

  it('ISS sai quando o fator do ano é zero (2033)', () => {
    expect(tributosDaNota({ regimeTributario: 'IBS_CBS', fatorIss: 0 }))
      .toEqual(['IR', 'CSLL', 'CBS', 'IBS'])
  })
})

describe('tributosDoRegime', () => {
  it('regime desconhecido cai no antigo e devolve cópia', () => {
    const l = tributosDoRegime(null)
    expect(l).toEqual(TRIBUTOS_LEGADO)
    l.push('CBS')
    expect(TRIBUTOS_LEGADO).toHaveLength(5)
  })
})

describe('valoresTributo / totalTributos', () => {
  it('soma só os tributos visíveis', () => {
    const v = valoresTributo({ valorIss: 500, valorIr: 150, valorCsll: 100, valorPis: 65, valorCofins: 300,
      valorCbs: 400, valorIbs: 30 })
    expect(totalTributos(['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'], v)).toBe(1115)
    expect(totalTributos(['ISS', 'IR', 'CSLL', 'CBS', 'IBS'], v)).toBe(1180)
  })

  it('campos ausentes viram zero', () => {
    expect(valoresTributo({ valorIss: 10 })).toEqual({ ISS: 10, IR: 0, CSLL: 0, PIS: 0, COFINS: 0, CBS: 0, IBS: 0 })
  })
})
