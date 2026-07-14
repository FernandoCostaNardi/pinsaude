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
