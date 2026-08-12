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

export interface FrequenciaItemResp {
  id: string
  frequenciaId: string
  modalidadeId: string
  modalidadeNome: string | null
  modalidadeTurno: string | null
  modalidadeHorario: string | null
  modalidadeHoras: number | null
  dataExecucao: string
  ocorrencia: string | null
  ocorrenciaId: string | null
  ocorrenciaNome: string | null
  ocorrenciaValorCentavos: number | null
  horasTrabalhadas: number | null
  horaInicio: string | null
  horaFim: string | null
  valorUnitarioCentavos: number
  deslocamentoCentavos: number
  totalItemCentavos: number
  createdAt: string
}

// Acompanhamento (read-only) de uma modalidade META usada na frequência: quanto já foi
// acumulado e quanto falta para fechar o bloco atual da meta. Não afeta o cálculo do valor.
export interface FrequenciaModalidadeProgresso {
  modalidadeId: string
  modalidadeNome: string
  unidadeCalculo: 'HORA' | 'DIA'
  metaHoras: number | null
  metaDias: number | null
  acumuladoHoras: number
  acumuladoDias: number
  blocosCompletos: number
  restanteBlocoAtual: number
}

// Acompanhamento (read-only) da carga horária semanal de uma modalidade DIARISTA usada na
// frequência: horas lançadas na semana ISO (segunda a domingo) vs. a meta semanal cadastrada.
// Puramente informativo — nunca altera o valor pago (PINSAUDE-13.23).
export interface FrequenciaSemanaProgresso {
  semanaInicio: string
  semanaFim: string
  horasLancadas: number
  metaHoras: number | null
  cumprida: boolean
}

export interface FrequenciaMedicaResp {
  id: string
  tomadorId: string
  medicoId: string
  servicoOperacionalId: string
  servicoOperacionalNome: string | null
  competencia: string
  especialidade: string | null
  tipoMedico: 'PLANTONISTA' | 'DIARISTA' | null
  status: string
  documentoAssinado: boolean
  enviadaTomadorEm: string | null
  fechamentoId: string | null
  producaoId: string | null
  createdAt: string
  itens: FrequenciaItemResp[]
  totalValorCentavos: number
  progressoMetas: FrequenciaModalidadeProgresso[]
  progressoSemanal: FrequenciaSemanaProgresso[]
}

export interface FrequenciaMedicaRequest {
  tomadorId: string
  medicoId: string
  servicoOperacionalId: string
  competencia: string
  tipoMedico: 'PLANTONISTA' | 'DIARISTA'
}

// PINSAUDE-13.25 (ajuste pós-implantação): horasTrabalhadas não é mais informado direto pelo
// cliente — para modalidade Diarista o médico digita horaInicio/horaFim ("HH:mm") e o backend
// deriva a quantidade de horas (ver FrequenciaService.calcularHorasTrabalhadas).
export interface FrequenciaItemRequest {
  modalidadeId: string
  dataExecucao: string
  ocorrencia?: string
  horaInicio?: string
  horaFim?: string
  ocorrenciaId?: string
}

export const frequenciasApi = {
  async listar(params: {
    medicoId?: string
    tomadorId?: string
    setorId?: string
    competencia?: string
    status?: string
  } = {}): Promise<FrequenciaMedicaResp[]> {
    const q = new URLSearchParams()
    if (params.medicoId)    q.set('medicoId',    params.medicoId)
    if (params.tomadorId)   q.set('tomadorId',   params.tomadorId)
    if (params.setorId)     q.set('setorId',     params.setorId)
    if (params.competencia) q.set('competencia', params.competencia)
    if (params.status)      q.set('status',      params.status)
    const res = await fetch(`/api/frequencias?${q}`, { headers: authHeaders() })
    return handleResponse<FrequenciaMedicaResp[]>(res)
  },

  async criar(req: FrequenciaMedicaRequest): Promise<FrequenciaMedicaResp> {
    const res = await fetch('/api/frequencias', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<FrequenciaMedicaResp>(res)
  },

  async buscarPorId(id: string): Promise<FrequenciaMedicaResp> {
    const res = await fetch(`/api/frequencias/${id}`, { headers: authHeaders() })
    return handleResponse<FrequenciaMedicaResp>(res)
  },

  async adicionarItem(id: string, req: FrequenciaItemRequest): Promise<FrequenciaItemResp> {
    const res = await fetch(`/api/frequencias/${id}/itens`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<FrequenciaItemResp>(res)
  },

  async atualizarItem(id: string, itemId: string, req: FrequenciaItemRequest): Promise<FrequenciaItemResp> {
    const res = await fetch(`/api/frequencias/${id}/itens/${itemId}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return handleResponse<FrequenciaItemResp>(res)
  },

  async removerItem(id: string, itemId: string): Promise<void> {
    const res = await fetch(`/api/frequencias/${id}/itens/${itemId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
  },

  async gerarPdf(id: string): Promise<FrequenciaMedicaResp> {
    const res = await fetch(`/api/frequencias/${id}/gerar-pdf`, {
      method: 'PUT',
      headers: authHeaders(),
    })
    return handleResponse<FrequenciaMedicaResp>(res)
  },

  async uploadDocumentoAssinado(id: string, arquivo: File): Promise<FrequenciaMedicaResp> {
    const token = JSON.parse(sessionStorage.getItem('pinsaude_tokens') ?? '{}').accessToken ?? ''
    const form = new FormData()
    form.append('arquivo', arquivo)
    const res = await fetch(`/api/frequencias/${id}/documento`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    })
    return handleResponse<FrequenciaMedicaResp>(res)
  },

  async getDocumentoUrl(id: string): Promise<string> {
    const res = await fetch(`/api/frequencias/${id}/documento/url`, { headers: authHeaders() })
    const data = await handleResponse<{ url: string }>(res)
    return data.url
  },
}
