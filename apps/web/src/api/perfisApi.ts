const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  const { accessToken } = JSON.parse(raw)
  return accessToken
}

function authHeaders(): HeadersInit {
  return {
    Authorization: `Bearer ${getAccessToken()}`,
    'Content-Type': 'application/json',
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    let mensagem = `Erro ${res.status}`
    try {
      const json = JSON.parse(text)
      mensagem = json.mensagem ?? json.message ?? json.error_description ?? json.error ?? mensagem
    } catch {
      if (text) mensagem = text
    }
    throw new Error(mensagem)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export interface PerfilCustomizado {
  id:               string
  nome:             string
  keycloakRoleName: string
  permissoes:       string[]
  criadoPor:        string | null
  createdAt:        string
  updatedAt:        string
}

export interface PerfilCustomizadoPayload {
  nome:       string
  permissoes: string[]
}

export const perfisApi = {
  listar(): Promise<PerfilCustomizado[]> {
    return fetch('/api/perfis', { headers: authHeaders() }).then(handleResponse<PerfilCustomizado[]>)
  },

  criar(payload: PerfilCustomizadoPayload): Promise<PerfilCustomizado> {
    return fetch('/api/perfis', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(payload),
    }).then(handleResponse<PerfilCustomizado>)
  },

  atualizar(id: string, payload: PerfilCustomizadoPayload): Promise<PerfilCustomizado> {
    return fetch(`/api/perfis/${id}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(payload),
    }).then(handleResponse<PerfilCustomizado>)
  },

  excluir(id: string): Promise<void> {
    return fetch(`/api/perfis/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    }).then(handleResponse<void>)
  },
}
