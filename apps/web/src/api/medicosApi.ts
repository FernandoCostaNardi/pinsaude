const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

export type StatusMedico = 'RASCUNHO' | 'ATIVO' | 'INATIVO' | 'SUSPENSO'
export type TipoPix = 'CPF' | 'CNPJ' | 'EMAIL' | 'TELEFONE' | 'ALEATORIA'

export interface DadosBancariosMedico {
  tipoPix?: TipoPix
  chavePix?: string
  cpfsAdicionaisSplit?: string
}

export interface ChecklistConduta {
  numeroConselhoVerificado: boolean
  registrosDisciplinares: boolean
  processosMedicos: boolean
  completo: boolean
  verificadoPor?: string
  verificadoEm?: string
}

export interface Medico {
  id: string
  cpf: string
  nome: string
  crm: string
  crmUf: string
  especialidade?: string
  email?: string
  telefone?: string
  status: StatusMedico
  empresaId?: string
  dadosBancarios?: DadosBancariosMedico
  checklist?: ChecklistConduta
  createdAt: string
  updatedAt: string
}

export interface MedicoRequest {
  cpf: string
  nome: string
  crm: string
  crmUf: string
  especialidade: string
  email: string
  telefone: string
  empresaId: string
}

export interface DadosBancariosMedicoRequest {
  tipoPix: TipoPix | null
  chavePix: string | null
  cpfsAdicionaisSplit: string | null
  confirmarAlteracao: true
}

export interface MedicoPage {
  content: Medico[]
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
      msg = body.mensagem ?? body.message ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

async function listar(page = 0, size = 1000): Promise<MedicoPage> {
  const res = await fetch(`/api/medicos?page=${page}&size=${size}`, { headers: authHeaders() })
  return handleResponse<MedicoPage>(res)
}

async function criar(data: MedicoRequest): Promise<Medico> {
  const res = await fetch('/api/medicos', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<Medico>(res)
}

async function atualizar(id: string, data: MedicoRequest): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<Medico>(res)
}

async function ativar(id: string): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}/ativar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse<Medico>(res)
}

async function inativar(id: string): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}/inativar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse<Medico>(res)
}

async function atualizarDadosBancarios(id: string, data: DadosBancariosMedicoRequest): Promise<DadosBancariosMedico> {
  const res = await fetch(`/api/medicos/${id}/dados-bancarios`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })
  return handleResponse<DadosBancariosMedico>(res)
}

export const medicosApi = { listar, criar, atualizar, ativar, inativar, atualizarDadosBancarios }
