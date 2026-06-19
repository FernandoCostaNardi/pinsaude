const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

export interface Servico {
  id: string
  codigoLc116: string
  cnae?: string
  descricaoPadrao: string
  indicadorEquiparacao: boolean
  aliquotaIss: number
  aliquotaIr: number
  aliquotaCsll: number
  aliquotaPis: number
  aliquotaCofins: number
}

function authHeaders(): HeadersInit {
  return { Authorization: `Bearer ${getAccessToken()}` }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = `Erro ${res.status}`
    try { const b = await res.json(); msg = b.mensagem ?? b.message ?? msg } catch { /* ignore */ }
    throw new Error(msg)
  }
  return res.json()
}

async function listar(): Promise<Servico[]> {
  const res = await fetch('/api/servicos', { headers: authHeaders() })
  return handleResponse<Servico[]>(res)
}

export const servicosApi = { listar }
