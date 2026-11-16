import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buscarRegimeFiscal, limparCacheRegimeFiscal } from './useRegimeFiscal'

const resposta = {
  competencia: '2027-01', regime: 'IBS_CBS', tributosVisiveis: ['ISS', 'IR', 'CSLL', 'CBS', 'IBS', 'XYZ'],
  chaveLigada: true, homologado: true, vigenciaParametro: '2027-01',
  aliquotas: { ano: 2027, cbs: 0.112, ibsUf: 0.0005, ibsMun: 0.0005, ibs: 0.001, fatorIss: 1, definitivas: false },
}

describe('buscarRegimeFiscal', () => {
  beforeEach(() => {
    limparCacheRegimeFiscal()
    vi.stubGlobal('sessionStorage', { getItem: () => JSON.stringify({ accessToken: 't' }) })
  })
  afterEach(() => { vi.unstubAllGlobals() })

  it('chama GET /api/fiscal/regime com cnpj só em dígitos e filtra tributos desconhecidos', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => resposta })
    vi.stubGlobal('fetch', fetchMock)
    const r = await buscarRegimeFiscal('2027-01', '12.345.678/0001-90')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/fiscal/regime?competencia=2027-01&cnpj=12345678000190')
    expect(r.tributosVisiveis).toEqual(['ISS', 'IR', 'CSLL', 'CBS', 'IBS'])
  })

  it('reaproveita a mesma consulta para a mesma competência e empresa', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => resposta })
    vi.stubGlobal('fetch', fetchMock)
    await Promise.all([buscarRegimeFiscal('2027-01'), buscarRegimeFiscal('2027-01')])
    await buscarRegimeFiscal('2027-02')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('erro não fica em cache e traz a mensagem do backend', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 422, text: async () => '{"message":"Cronograma da reforma não cadastrado para 2034"}' })
      .mockResolvedValueOnce({ ok: true, json: async () => resposta })
    vi.stubGlobal('fetch', fetchMock)
    await expect(buscarRegimeFiscal('2034-01')).rejects.toThrow('Cronograma da reforma não cadastrado para 2034')
    await expect(buscarRegimeFiscal('2034-01')).resolves.toMatchObject({ regime: 'IBS_CBS' })
  })
})
