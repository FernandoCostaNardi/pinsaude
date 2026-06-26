const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

export type RegimeTributario = 'SIMPLES_NACIONAL' | 'LUCRO_PRESUMIDO' | 'LUCRO_REAL'

export interface Empresa {
  id: string
  cnpj: string
  razaoSocial: string
  inscricaoMunicipal: string
  municipio: string | null
  codigoMunicipioIbge: string | null
  regimeTributario: RegimeTributario
  ativo: boolean
  createdAt: string
  updatedAt: string
  logradouro: string | null
  bairro: string | null
  uf: string | null
  cep: string | null
  telefone: string | null
  emailContato: string | null
}

export interface EmpresaRequest {
  cnpj: string
  razaoSocial: string
  inscricaoMunicipal: string
  municipio: string
  codigoMunicipioIbge: string
  regimeTributario: RegimeTributario
  logradouro: string | null
  bairro: string | null
  uf: string | null
  cep: string | null
  telefone: string | null
  emailContato: string | null
}

export interface EmpresaPage {
  content: Empresa[]
  page: number
  size: number
  totalElements: number
  totalPages: number
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
      msg = body.mensagem ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

async function listar(page = 0, size = 1000): Promise<EmpresaPage> {
  const res = await fetch(`/api/empresas?page=${page}&size=${size}`, { headers: authHeaders() })
  return handleResponse<EmpresaPage>(res)
}

async function criar(data: EmpresaRequest): Promise<Empresa> {
  const res = await fetch('/api/empresas', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<Empresa>(res)
}

async function atualizar(id: string, data: EmpresaRequest): Promise<Empresa> {
  const res = await fetch(`/api/empresas/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<Empresa>(res)
}

async function remover(id: string): Promise<void> {
  const res = await fetch(`/api/empresas/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse<void>(res)
}

async function buscarPorId(id: string): Promise<Empresa> {
  const res = await fetch(`/api/empresas/${id}`, { headers: authHeaders() })
  return handleResponse<Empresa>(res)
}

export const empresasApi = { listar, criar, atualizar, remover, buscarPorId }
