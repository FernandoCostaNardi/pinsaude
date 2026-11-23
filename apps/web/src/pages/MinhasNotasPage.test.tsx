import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { PainelDetalhe } from './MinhasNotasPage'
import type { NotaPortal } from '../api/portalApi'

// CBS-22 — o detalhe da nota no portal segue o regime gravado na nota.

const base: NotaPortal = {
  id: 'n1', producaoId: 'p1', competencia: '2026-12', tomadorNome: 'Hospital X',
  valorBrutoCentavos: 100000, valorLiquidoMedicoCentavos: 85000, taxaPinCentavos: 15000,
  valorIss: 2000, valorIr: 1500, valorCsll: 1000, valorPis: 650, valorCofins: 3000,
  status: 'EMITIDA', numeroNota: '123', temXml: false, temPdf: false, protocolo: null,
  emitidaAt: null, createdAt: '2026-12-05T10:00:00Z',
}

const render = (nota: NotaPortal) =>
  renderToStaticMarkup(
    <MemoryRouter>
      <PainelDetalhe nota={nota} onClose={() => {}} />
    </MemoryRouter>,
  )

describe('MinhasNotas — PainelDetalhe', () => {
  it('nota antiga (sem regime gravado) continua com PIS/COFINS', () => {
    const html = render(base)
    expect(html).toContain('PIS Retido')
    expect(html).toContain('COFINS Retida')
    expect(html).not.toMatch(/>CBS</)
    // 20 + 15 + 10 + 6,50 + 30 = 81,50
    expect(html).toContain('81,50')
  })

  it('nota do regime novo mostra CBS/IBS e não PIS/COFINS', () => {
    const html = render({
      ...base, competencia: '2027-01', valorPis: 0, valorCofins: 0,
      regimeTributario: 'IBS_CBS', valorCbs: 3500, valorIbs: 40, fatorIss: 1,
    })
    expect(html).toMatch(/>CBS</)
    expect(html).toMatch(/>IBS</)
    expect(html).not.toContain('PIS Retido')
    expect(html).not.toContain('COFINS Retida')
    // 20 + 15 + 10 + 35 + 0,40 = 80,40
    expect(html).toContain('80,40')
  })

  it('regime novo ignora PIS/COFINS gravados por engano no total', () => {
    const html = render({ ...base, regimeTributario: 'IBS_CBS', valorCbs: 0, valorIbs: 0, fatorIss: 1 })
    expect(html).not.toContain('PIS Retido')
    // só ISS + IR + CSLL = 45,00
    expect(html).toContain('45,00')
  })
})
