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
      msg = body.mensagem ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export type TipoConta = 'CORRENTE' | 'POUPANCA'

export interface ContaBancaria {
  id: string
  banco: string
  agencia: string
  conta: string
  tipoConta: TipoConta
  chavePix: string | null
  principal: boolean
  ativo: boolean
  createdAt: string
}

export interface ContaBancariaRequest {
  banco: string
  agencia: string
  conta: string
  tipoConta: TipoConta
  chavePix: string
  principal: boolean
}

const base = (empresaId: string) => `/api/empresas/${empresaId}/contas`

async function listar(empresaId: string): Promise<ContaBancaria[]> {
  const res = await fetch(base(empresaId), { headers: authHeaders() })
  return handleResponse<ContaBancaria[]>(res)
}

async function criar(empresaId: string, data: ContaBancariaRequest): Promise<ContaBancaria> {
  const res = await fetch(base(empresaId), {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<ContaBancaria>(res)
}

async function atualizar(empresaId: string, id: string, data: ContaBancariaRequest): Promise<ContaBancaria> {
  const res = await fetch(`${base(empresaId)}/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<ContaBancaria>(res)
}

async function remover(empresaId: string, id: string): Promise<void> {
  const res = await fetch(`${base(empresaId)}/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse<void>(res)
}

export const contasBancariasApi = { listar, criar, atualizar, remover }
