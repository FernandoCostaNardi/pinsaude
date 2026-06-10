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
    const body = await res.text().catch(() => '')
    throw new Error(body || `Erro ${res.status}`)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export interface Usuario {
  id:       string
  email:    string
  nome:     string
  perfil:   string
  ativo:    boolean
  pendente: boolean
}

export interface ConvitePayload {
  email:  string
  nome:   string
  perfil: string
}

export const usersApi = {
  listar(): Promise<Usuario[]> {
    return fetch('/api/usuarios', { headers: authHeaders() }).then(handleResponse<Usuario[]>)
  },

  convidar(payload: ConvitePayload): Promise<Usuario> {
    return fetch('/api/usuarios', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(payload),
    }).then(handleResponse<Usuario>)
  },

  alterarPerfil(id: string, perfil: string): Promise<Usuario> {
    return fetch(`/api/usuarios/${id}/perfil`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify({ perfil }),
    }).then(handleResponse<Usuario>)
  },

  alterarStatus(id: string, ativo: boolean): Promise<Usuario> {
    return fetch(`/api/usuarios/${id}/status`, {
      method: 'PATCH',
      headers: authHeaders(),
      body: JSON.stringify({ ativo }),
    }).then(handleResponse<Usuario>)
  },

  reenviarConvite(id: string): Promise<void> {
    return fetch(`/api/usuarios/${id}/reenviar-convite`, {
      method: 'POST',
      headers: authHeaders(),
    }).then(handleResponse<void>)
  },
}
