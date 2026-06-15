import rawMunicipios from '../data/municipios.json'

export interface Municipio {
  codigo: string
  nome: string
  uf: string
}

export const municipios: Municipio[] = (rawMunicipios as Array<{ c: string | null; n: string | null; u: string | null }>)
  .filter(m => m.c != null && m.n != null && m.u != null)
  .map(m => ({
    codigo: m.c as string,
    nome: m.n as string,
    uf: m.u as string,
  }))
