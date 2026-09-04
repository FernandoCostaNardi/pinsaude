import type { TipoEscala } from '../utils/tipoEscala'

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
  quantidade: number | null
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
  // PINSAUDE: grupo de faturamento explícito — necessário desde que Setor Operacional virou um
  // catálogo reutilizável entre vários grupos (o setor sozinho não basta mais pra saber a qual
  // grupo/NFS-e esta frequência pertence).
  grupoId: string | null
  servicoOperacionalId: string
  servicoOperacionalNome: string | null
  competencia: string
  especialidade: string | null
  tipoMedico: TipoEscala | null
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
  // PINSAUDE-13.26: modalidade/ocorrência escolhidas uma única vez na criação da frequência —
  // null para frequências legadas sem modalidade fixa (ver CLAUDE.md). Quando modalidadeId não é
  // null, o formulário de lançamento de plantão não pergunta mais modalidade/ocorrência.
  modalidadeId: string | null
  modalidadeNome: string | null
  modalidadeTipo: TipoEscala | null
  modalidadeTurno: string | null
  modalidadeHorario: string | null
  modalidadeHoras: number | null
  modalidadeHorasSemanais: number | null
  modalidadeValorCentavos: number
  modalidadeDeslocamentoCentavos: number
  ocorrenciaId: string | null
  ocorrenciaNome: string | null
  // PINSAUDE-13.26 (ajuste pós-implantação): valor da ocorrência aplicado UMA ÚNICA VEZ sobre o
  // valor da modalidade — não mais por lançamento (evita inflar o total com N plantões). Sempre
  // calculado quando modalidade+ocorrência estão fixas, mesmo sem nenhum item lançado ainda
  // (preview); só entra em totalValorCentavos quando há pelo menos 1 plantão.
  ocorrenciaValorCentavos: number
}

// Edição pós-criação: só Competência e Setor Operacional são editáveis (Tomador, Tipo de
// Escala, Modalidade e Ocorrência permanecem fixos — se algo além disso estiver errado, o
// caminho continua sendo excluir e criar de novo).
export interface FrequenciaMedicaEditRequest {
  competencia: string
  grupoId: string
  servicoOperacionalId: string
}

export interface FrequenciaMedicaRequest {
  tomadorId: string
  medicoId: string
  grupoId: string
  servicoOperacionalId: string
  competencia: string
  tipoMedico: TipoEscala
  // Modalidade (obrigatória) e ocorrência (opcional) só são escolhidas aqui, na criação, quando
  // tipoMedico é um tipo "fixo" (DIARISTA/EVOLUCIONISTA — ver isTipoModalidadeFixa em
  // utils/tipoEscala.ts) — nesse caso o formulário de lançamento de plantão nunca mais pergunta
  // nenhuma das duas. Para tipos "por lançamento" (PLANTONISTA/EVOLUCIONISTA_FDS), os dois campos
  // devem vir undefined — modalidade e ocorrência voltam a ser escolhidas a cada plantão lançado
  // (ajuste pós-implantação, reverte parte do comportamento fixo introduzido em PINSAUDE-13.26 —
  // ver CLAUDE.md).
  modalidadeId?: string
  ocorrenciaId?: string
}

// PINSAUDE-13.25 (ajuste pós-implantação): horasTrabalhadas não é mais informado direto pelo
// cliente — para modalidade Diarista o médico digita horaInicio/horaFim ("HH:mm") e o backend
// deriva a quantidade de horas (ver FrequenciaService.calcularHorasTrabalhadas).
//
// PINSAUDE-13.26: modalidadeId/ocorrenciaId deixaram de ser obrigatórios aqui — quando a
// frequência já tem modalidade fixa (ver FrequenciaMedicaResp.modalidadeId), o backend ignora
// qualquer valor enviado nestes dois campos e usa sempre o da frequência. Só frequências
// legadas sem modalidade fixa ainda exigem modalidadeId aqui.
export interface FrequenciaItemRequest {
  modalidadeId?: string
  dataExecucao: string
  ocorrencia?: string
  horaInicio?: string
  horaFim?: string
  ocorrenciaId?: string
  // Só modalidade SERVICOS — quantidade de serviços realizados neste lançamento.
  quantidade?: number
}

// Garante que os campos de lista de FrequenciaMedicaResp sejam sempre arrays. Sem isso, um
// backend rodando uma versão mais antiga (sem `progressoSemanal`/`progressoMetas`, ou uma
// versão diferente que não devolve `itens`) faz esses campos chegarem como `undefined` no
// front — qualquer `.length` direto nesses campos (usado em vários pontos das telas de
// Frequências) quebra a tela inteira ao abrir uma competência. Normalizar aqui, num único
// lugar, protege todas as telas sem espalhar `?? []` em cada leitura.
function normalizeFrequencia(f: FrequenciaMedicaResp): FrequenciaMedicaResp {
  return {
    ...f,
    itens: f.itens ?? [],
    progressoMetas: f.progressoMetas ?? [],
    progressoSemanal: f.progressoSemanal ?? [],
  }
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
    const data = await handleResponse<FrequenciaMedicaResp[]>(res)
    return data.map(normalizeFrequencia)
  },

  async criar(req: FrequenciaMedicaRequest): Promise<FrequenciaMedicaResp> {
    const res = await fetch('/api/frequencias', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return normalizeFrequencia(await handleResponse<FrequenciaMedicaResp>(res))
  },

  async buscarPorId(id: string): Promise<FrequenciaMedicaResp> {
    const res = await fetch(`/api/frequencias/${id}`, { headers: authHeaders() })
    return normalizeFrequencia(await handleResponse<FrequenciaMedicaResp>(res))
  },

  // Edita Competência e Setor Operacional de uma frequência já criada. Bloqueado só quando
  // status = FATURADA (já entrou no Fechamento/NFS-e).
  async atualizar(id: string, req: FrequenciaMedicaEditRequest): Promise<FrequenciaMedicaResp> {
    const res = await fetch(`/api/frequencias/${id}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(req),
    })
    return normalizeFrequencia(await handleResponse<FrequenciaMedicaResp>(res))
  },

  // Permitido em qualquer status exceto FATURADA. Itens são apagados em cascata pelo backend.
  async excluir(id: string): Promise<void> {
    const res = await fetch(`/api/frequencias/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    return handleResponse<void>(res)
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
    return normalizeFrequencia(await handleResponse<FrequenciaMedicaResp>(res))
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
    return normalizeFrequencia(await handleResponse<FrequenciaMedicaResp>(res))
  },

  async getDocumentoUrl(id: string): Promise<string> {
    const res = await fetch(`/api/frequencias/${id}/documento/url`, { headers: authHeaders() })
    const data = await handleResponse<{ url: string }>(res)
    return data.url
  },
}
