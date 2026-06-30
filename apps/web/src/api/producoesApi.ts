const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

export interface TomadorResumo {
  id: string
  razaoSocialNome: string
  municipio?: string
  retencaoFederal: boolean
  retencaoIss: boolean
}

export interface ServicoResumo {
  id: string
  codigoLc116: string
  descricaoPadrao: string
  aliquotaIss: number
  aliquotaIr: number
  aliquotaCsll: number
  aliquotaPis: number
  aliquotaCofins: number
}

export interface Participacao {
  id: string
  medicoId: string
  valorBruto: number
  taxaPinPct: number
  taxaPin: number
  valorLiquido: number
}

export interface Producao {
  id: string
  medicoId: string | null     // null para multi-médico; 1º participante para single
  empresaId: string | null
  participantes: Participacao[]
  tomador: TomadorResumo
  servico: ServicoResumo
  valorBruto: number
  competencia: string
  descricaoComplementar?: string
  status: 'RASCUNHO' | 'CONFIRMADA' | 'EMITIDA' | 'CANCELADA'
  createdAt: string
}

export interface ParticipacaoRequest {
  medicoId: string
  valorBruto: number
  taxaPinPct?: number
}

export interface ProducaoRequest {
  tomadorId: string
  servicoId: string
  competencia: string
  descricaoComplementar?: string
  empresaId: string | null
  participantes: ParticipacaoRequest[]
}

export interface PreviewCalculoRequest {
  servicoId: string
  tomadorId: string
  valorBruto: number
}

export interface PreviewCalculoResponse {
  valorBruto: number
  taxaPin: number
  issRetido: number
  irRetido: number
  csllRetido: number
  pisRetido: number
  cofinsRetido: number
  totalRetencoes: number
  valorLiquidoMedico: number  // sempre 85% do bruto — não afetado por impostos
  resultadoPin: number        // taxaPin - totalRetencoes (lucro Pin após tributos)
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

async function listar(params?: {
  status?: string
  competencia?: string
  medicoId?: string
  tomadorId?: string
  periodoInicio?: string
  periodoFim?: string
}): Promise<Producao[]> {
  const q = new URLSearchParams()
  if (params?.status)        q.set('status', params.status)
  if (params?.competencia)   q.set('competencia', params.competencia)
  if (params?.medicoId)      q.set('medicoId', params.medicoId)
  if (params?.tomadorId)     q.set('tomadorId', params.tomadorId)
  if (params?.periodoInicio) q.set('periodoInicio', params.periodoInicio)
  if (params?.periodoFim)    q.set('periodoFim', params.periodoFim)
  const res = await fetch(`/api/producoes${q.size ? `?${q}` : ''}`, { headers: authHeaders() })
  return handleResponse<Producao[]>(res)
}

async function buscarPorId(id: string): Promise<Producao> {
  const res = await fetch(`/api/producoes/${id}`, { headers: authHeaders() })
  return handleResponse<Producao>(res)
}

async function criar(req: ProducaoRequest): Promise<Producao> {
  const res = await fetch('/api/producoes', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  return handleResponse<Producao>(res)
}

async function previewCalculo(req: PreviewCalculoRequest): Promise<PreviewCalculoResponse> {
  const res = await fetch('/api/producoes/preview-calculo', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  return handleResponse<PreviewCalculoResponse>(res)
}

export const producoesApi = { listar, buscarPorId, criar, previewCalculo }
