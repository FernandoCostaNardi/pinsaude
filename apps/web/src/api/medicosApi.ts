const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

export type StatusMedico = 'RASCUNHO' | 'ATIVO' | 'INATIVO' | 'SUSPENSO'
export type TipoPix = 'CPF' | 'CNPJ' | 'EMAIL' | 'TELEFONE' | 'ALEATORIA'

export type TipoRecebimento = 'PIX' | 'TED'
export type TipoConta = 'CORRENTE' | 'POUPANCA'

export interface DadosBancariosMedico {
  tipoRecebimento?: TipoRecebimento
  // PIX
  tipoPix?: TipoPix
  chavePix?: string
  cpfsAdicionaisSplit?: string
  // TED
  bancoCodigo?: string
  bancoNome?: string
  agencia?: string
  conta?: string
  tipoConta?: TipoConta
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
  tipoRecebimento: TipoRecebimento
  // PIX
  tipoPix: TipoPix | null
  chavePix: string | null
  cpfsAdicionaisSplit: string | null
  // TED
  bancoCodigo: string | null
  bancoNome: string | null
  agencia: string | null
  conta: string | null
  tipoConta: TipoConta | null
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

async function listar(page = 0, size = 1000, status?: string): Promise<MedicoPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  const res = await fetch(`/api/medicos?${params}`, { headers: authHeaders() })
  return handleResponse<MedicoPage>(res)
}

async function buscarPorId(id: string): Promise<Medico> {
  const res = await fetch(`/api/medicos/${id}`, { headers: authHeaders() })
  return handleResponse<Medico>(res)
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

export const medicosApi = { listar, buscarPorId, criar, atualizar, ativar, inativar, atualizarDadosBancarios }
