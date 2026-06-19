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
    try { const b = await res.json(); msg = b.mensagem ?? b.message ?? msg } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export interface ParametroFiscal {
  id: string
  cnpjId: string
  vigenciaInicio: string   // "YYYY-MM-DD"
  competenciaInicio: string // "YYYY-MM"
  aliqIss: number
  aliqIr: number
  aliqCsll: number
  aliqPis: number
  aliqCofins: number
  ibsCbsAtivo: boolean
  aliqIbsCbs: number | null
  reducaoIbsCbsSaude: number | null
  aliqIbsCbsEfetiva: number | null  // calculada: aliqIbsCbs * (1 - reducao)
  homologado: boolean
  observacoes: string | null
  createdAt: string
}

export interface IbsCbsRequest {
  competenciaInicio: string
  aliqIbsCbs: number
  reducaoSaude: number
  aliqIss?: number
  observacoes?: string
}

export interface ParametroFiscalRequest {
  competenciaInicio: string
  aliqIss: number
  aliqIr: number
  aliqCsll: number
  aliqPis: number
  aliqCofins: number
  ibsCbsAtivo: boolean
  aliqIbsCbs?: number
  reducaoIbsCbsSaude?: number
  observacoes?: string
}

async function listar(competencia?: string): Promise<ParametroFiscal | ParametroFiscal[]> {
  const q = competencia ? `?competencia=${competencia}` : ''
  const res = await fetch(`/api/parametros-fiscais${q}`, { headers: authHeaders() })
  return handleResponse<ParametroFiscal | ParametroFiscal[]>(res)
}

async function criar(req: ParametroFiscalRequest): Promise<ParametroFiscal> {
  const res = await fetch('/api/parametros-fiscais', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  return handleResponse<ParametroFiscal>(res)
}

async function criarIbsCbs(req: IbsCbsRequest): Promise<ParametroFiscal> {
  const res = await fetch('/api/parametros-fiscais/ibs-cbs', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  return handleResponse<ParametroFiscal>(res)
}

async function homologar(id: string): Promise<ParametroFiscal> {
  const res = await fetch(`/api/parametros-fiscais/${id}/homologar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse<ParametroFiscal>(res)
}

export const parametrosFiscaisApi = { listar, criar, criarIbsCbs, homologar }
