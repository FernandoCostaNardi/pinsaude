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

export interface MedicoParticipacaoPreview {
  medicoId: string
  totalCentavos: number
}

export interface GrupoPreview {
  grupoId: string
  nome: string
  descricaoNota: string
  descricaoInterpolada: string
  servicoLc116Id: string
  medicos: MedicoParticipacaoPreview[]
  totalCentavos: number
}

export interface FechamentoPreviewResp {
  tomadorId: string
  competencia: string
  grupos: GrupoPreview[]
  totalCentavos: number
  totalFrequencias: number
}

export interface ProducaoRef {
  grupoId: string
  grupoNome: string
  producaoId: string
  totalCentavos: number
}

export interface FechamentoResp {
  id: string
  tomadorId: string
  competencia: string
  status: string
  totalCentavos: number
  producoes: ProducaoRef[]
  createdAt: string
  fechadoEm: string | null
}

export interface FechamentoRequest {
  tomadorId: string
  competencia: string
}

export const fechamentosApi = {
  async preview(tomadorId: string, competencia: string): Promise<FechamentoPreviewResp> {
    const q = new URLSearchParams({ tomadorId, competencia })
    const res = await fetch(`/api/fechamentos/preview?${q}`, { headers: authHeaders() })
    return handleResponse<FechamentoPreviewResp>(res)
  },

  async executar(req: FechamentoRequest): Promise<FechamentoResp> {
    const res = await fetch('/api/fechamentos', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<FechamentoResp>(res)
  },

  async listar(tomadorId?: string): Promise<FechamentoResp[]> {
    const q = new URLSearchParams()
    if (tomadorId) q.set('tomadorId', tomadorId)
    const res = await fetch(`/api/fechamentos?${q}`, { headers: authHeaders() })
    return handleResponse<FechamentoResp[]>(res)
  },

  async buscarPorId(id: string): Promise<FechamentoResp> {
    const res = await fetch(`/api/fechamentos/${id}`, { headers: authHeaders() })
    return handleResponse<FechamentoResp>(res)
  },
}
