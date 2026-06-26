const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

function authHeaders(): HeadersInit {
  return {
    Authorization: `Bearer ${getAccessToken()}`,
    'Content-Type': 'application/json',
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = `Erro ${res.status}`
    try {
      const body = await res.json()
      msg = body.message ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export type TipoTomador = 'HOSPITAL' | 'CLINICA' | 'OPERADORA' | 'PACIENTE_PF'

export interface Tomador {
  id: string
  tipo: TipoTomador
  cnpjCpf: string
  razaoSocialNome: string
  municipio: string | null
  inscricaoMunicipal: string | null
  indicadorRetencaoFederal: boolean
  indicadorRetencaoIss: boolean
  email: string | null
  telefone: string | null
  logradouro: string | null
  bairro: string | null
  cep: string | null
  uf: string | null
  pais: string | null
}

export interface TomadorRequest {
  tipo: string
  cnpjCpf: string
  razaoSocialNome: string
  municipio: string
  inscricaoMunicipal: string
  indicadorRetencaoFederal: boolean
  indicadorRetencaoIss: boolean
  email: string
  telefone: string
  logradouro: string
  bairro: string
  cep: string
  uf: string
  pais: string
}

export interface ReceitaFederalData {
  cnpj: string
  razaoSocial: string
  nomeFantasia: string | null
  municipio: string | null
  uf: string | null
}

export const tomadoresApi = {
  async listar(q?: string): Promise<Tomador[]> {
    const params = q ? `?q=${encodeURIComponent(q)}` : ''
    const res = await fetch(`/api/tomadores${params}`, {
      headers: authHeaders(),
    })
    return handleResponse<Tomador[]>(res)
  },

  async buscarPorId(id: string): Promise<Tomador> {
    const res = await fetch(`/api/tomadores/${id}`, {
      headers: authHeaders(),
    })
    return handleResponse<Tomador>(res)
  },

  async criar(req: TomadorRequest): Promise<Tomador> {
    const res = await fetch('/api/tomadores', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<Tomador>(res)
  },

  async atualizar(id: string, req: TomadorRequest): Promise<Tomador> {
    const res = await fetch(`/api/tomadores/${id}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<Tomador>(res)
  },

  async deletar(id: string): Promise<void> {
    const res = await fetch(`/api/tomadores/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  async consultarReceita(cnpj: string): Promise<ReceitaFederalData | null> {
    const digits = cnpj.replace(/\D/g, '')
    const res = await fetch(`/api/tomadores/receita/${digits}`, {
      headers: authHeaders(),
    })
    if (res.status === 404) return null
    return handleResponse<ReceitaFederalData>(res)
  },
}
