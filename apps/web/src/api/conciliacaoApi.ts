const STORAGE_KEY = 'pinsaude_tokens'

function getToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

function authHeaders(): Record<string, string> {
  return { Authorization: `Bearer ${getToken()}` }
}

export type BancoEnum = 'INTER' | 'BTG' | 'OUTRO'
export type StatusImportacao = 'OK' | 'PROCESSANDO' | 'ERRO'
export type TipoLancamento = 'CREDITO' | 'DEBITO'
export type StatusConciliacao = 'PENDENTE' | 'CONCILIADO' | 'IGNORADO'

export interface ExtratoResponse {
  id: string
  nomeArquivo: string
  banco: BancoEnum
  periodoInicio: string
  periodoFim: string
  statusImportacao: StatusImportacao
  totalLancamentos: number
  createdBy: string | null
  dataUpload: string
}

export interface ConciliacaoResumo {
  conciliacaoId: string
  producaoId: string
  tomadorNome: string | null
  valorBruto: number
  competencia: string | null
  tipoMatch: 'AUTOMATICO' | 'MANUAL'
  scoreConfianca: number
  dataConciliacao: string
}

export interface LancamentoExtratoResponse {
  id: string
  extratoId: string
  dataLancamento: string
  descricao: string
  valorCentavos: number
  tipo: TipoLancamento
  identificadorExterno: string | null
  statusConciliacao: StatusConciliacao
  scoreMatch: number
  conciliacao: ConciliacaoResumo | null
}

export interface CandidatoMatchResponse {
  producaoId: string
  tomadorNome: string
  valorBruto: number
  competencia: string
  score: number
}

export interface ProducaoResumo {
  id: string
  tomadorNome: string
  municipio: string | null
  valorBruto: number
  competencia: string
}

export async function uploadExtrato(
  arquivo: File,
  banco: BancoEnum,
  dataInicio: string,
  dataFim: string,
): Promise<ExtratoResponse> {
  const form = new FormData()
  form.append('arquivo', arquivo)
  form.append('banco', banco)
  form.append('data_inicio', dataInicio)
  form.append('data_fim', dataFim)

  const res = await fetch('/api/conciliacao/extratos/upload', {
    method: 'POST',
    headers: authHeaders(),
    body: form,
  })

  if (!res.ok) {
    if (res.status === 409) throw new Error('409: Extrato já importado anteriormente para este período.')
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`${res.status}: ${text}`)
  }
  return res.json()
}

export async function listarExtratos(): Promise<ExtratoResponse[]> {
  const res = await fetch('/api/conciliacao/extratos', {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`${res.status}: ${res.statusText}`)
  return res.json()
}

export async function listarLancamentos(
  extratoId: string,
  status?: StatusConciliacao,
): Promise<LancamentoExtratoResponse[]> {
  const url = status
    ? `/api/conciliacao/extratos/${extratoId}/lancamentos?status=${status}`
    : `/api/conciliacao/extratos/${extratoId}/lancamentos`
  const res = await fetch(url, { headers: authHeaders() })
  if (!res.ok) throw new Error(`${res.status}: ${res.statusText}`)
  return res.json()
}

export async function getSugestoes(lancamentoId: string): Promise<CandidatoMatchResponse[]> {
  const res = await fetch(`/api/conciliacao/lancamentos/${lancamentoId}/sugestoes`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`${res.status}: ${res.statusText}`)
  return res.json()
}

export async function conciliarLancamento(
  lancamentoId: string,
  producaoId: string,
  observacao?: string,
): Promise<void> {
  const res = await fetch(`/api/conciliacao/lancamentos/${lancamentoId}/conciliar`, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ producaoId, observacao: observacao ?? null }),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`${res.status}: ${text}`)
  }
}

export async function ignorarLancamento(lancamentoId: string): Promise<void> {
  const res = await fetch(`/api/conciliacao/lancamentos/${lancamentoId}/ignorar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`${res.status}: ${text}`)
  }
}

export async function desfazerConciliacao(lancamentoId: string): Promise<void> {
  const res = await fetch(`/api/conciliacao/lancamentos/${lancamentoId}/conciliacao`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`${res.status}: ${text}`)
  }
}

type RawProducao = {
  id: string
  tomador?: { razaoSocialNome?: string; nomeFantasia?: string } | null
  valorBruto: number
  competencia: string
  municipio?: string | null
}

export async function listarProducoesParaBusca(): Promise<ProducaoResumo[]> {
  const res = await fetch('/api/producoes', { headers: authHeaders() })
  if (!res.ok) throw new Error(`${res.status}: ${res.statusText}`)
  const data = (await res.json()) as RawProducao[]
  return data.map((p) => ({
    id: p.id,
    tomadorNome: p.tomador?.razaoSocialNome ?? p.tomador?.nomeFantasia ?? '—',
    valorBruto: p.valorBruto,
    competencia: p.competencia,
    municipio: p.municipio ?? null,
  }))
}
