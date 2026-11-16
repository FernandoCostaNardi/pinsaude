import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import LinhasTributo from './LinhasTributo'
import { tributosDaNota, valoresTributo } from '../fiscal/tributos'

const tributosNaOrdem = (html: string) =>
  [...html.matchAll(/data-tributo="([A-Z]+)"/g)].map(m => m[1])

describe('LinhasTributo', () => {
  const valores = valoresTributo({ valorIss: 500, valorIr: 150, valorCsll: 100, valorPis: 65, valorCofins: 300,
    valorCbs: 1792, valorIbs: 0 })

  it('nota antiga: desenha PIS/COFINS e não CBS/IBS', () => {
    const html = renderToStaticMarkup(<LinhasTributo tributos={tributosDaNota({})} valores={valores} />)
    expect(tributosNaOrdem(html)).toEqual(['ISS', 'IR', 'CSLL', 'PIS', 'COFINS'])
  })

  it('nota no regime novo: desenha CBS/IBS e não PIS/COFINS', () => {
    const html = renderToStaticMarkup(
      <LinhasTributo tributos={tributosDaNota({ regimeTributario: 'IBS_CBS', fatorIss: 1 })} valores={valores} />)
    expect(tributosNaOrdem(html)).toEqual(['ISS', 'IR', 'CSLL', 'CBS', 'IBS'])
    expect(html).toContain('17,92')
  })

  it('oculta zerados, mostra alíquota, selo e total', () => {
    const html = renderToStaticMarkup(
      <LinhasTributo
        tributos={['ISS', 'IR', 'CSLL', 'CBS', 'IBS']}
        valores={valores}
        aliquotasPct={{ ISS: 5, CBS: 3.6 }}
        situacao={{ ISS: 'RETIDO', CBS: 'PRESTADORA' }}
        ocultarZerados
        mostrarTotal
      />)
    expect(tributosNaOrdem(html)).toEqual(['ISS', 'IR', 'CSLL', 'CBS', 'TOTAL'])
    expect(html).toContain('ISS (5,00%)')
    expect(html).toContain('CBS (3,60%)')
    expect(html).toContain('RETIDO')
    expect(html).toContain('PRESTADORA')
    expect(html).toContain('25,42') // 5 + 1,50 + 1 + 17,92
  })
})
