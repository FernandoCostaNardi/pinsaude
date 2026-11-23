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
    try { const b = await res.json(); msg = b.mensagem ?? b.message ?? msg } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export interface ParametroFiscal {
  id: string
  cnpjId: string
  vigenciaInicio: string   // "YYYY-MM-DD"
  competenciaInicio: string // "YYYY-MM"
  aliqIss: number
  aliqIr: number
  aliqCsll: number
  aliqPis: number
  aliqCofins: number
  ibsCbsAtivo: boolean
  aliqIbsCbs: number | null
  reducaoIbsCbsSaude: number | null
  aliqIbsCbsEfetiva: number | null  // calculada: aliqIbsCbs * (1 - reducao)
  homologado: boolean
  homologadoPor: string | null
  homologadoEm: string | null
  observacoes: string | null
  createdAt: string
}

/** Liga a chave CBS/IBS (CBS-14). ISS/IR/CSLL/PIS/COFINS são copiados do parâmetro vigente. */
export interface IbsCbsRequest {
  competenciaInicio: string
  /** @deprecated alíquotas vêm do cronograma da reforma */
  aliqIbsCbs?: number
  /** @deprecated a redução vem do serviço LC116 */
  reducaoSaude?: number
  aliqIss?: number
  observacoes?: string
  /** Empresa (só para quem não tem cnpj_id no token, ex.: gestão) */
  cnpj?: string
}

export interface ParametroFiscalRequest {
  competenciaInicio: string
  aliqIss: number
  aliqIr: number
  aliqCsll: number
  aliqPis: number
  aliqCofins: number
  ibsCbsAtivo: boolean
  aliqIbsCbs?: number
  reducaoIbsCbsSaude?: number
  observacoes?: string
}

async function listar(competencia?: string): Promise<ParametroFiscal | ParametroFiscal[]> {
  const q = competencia ? `?competencia=${competencia}` : ''
  const res = await fetch(`/api/parametros-fiscais${q}`, { headers: authHeaders() })
  return handleResponse<ParametroFiscal | ParametroFiscal[]>(res)
}

async function criar(req: ParametroFiscalRequest): Promise<ParametroFiscal> {
  const res = await fetch('/api/parametros-fiscais', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  return handleResponse<ParametroFiscal>(res)
}

async function criarIbsCbs(req: IbsCbsRequest): Promise<ParametroFiscal> {
  const res = await fetch('/api/parametros-fiscais/ibs-cbs', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  return handleResponse<ParametroFiscal>(res)
}

/** Desliga a chave CBS/IBS (422 se já há nota no regime novo no período). */
async function desligarIbsCbs(id: string): Promise<ParametroFiscal> {
  const res = await fetch(`/api/parametros-fiscais/${id}/ibs-cbs`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return handleResponse<ParametroFiscal>(res)
}

async function homologar(id: string): Promise<ParametroFiscal> {
  const res = await fetch(`/api/parametros-fiscais/${id}/homologar`, {
    method: 'PUT',
    headers: authHeaders(),
  })
  return handleResponse<ParametroFiscal>(res)
}

// ─── Cronograma da reforma (CBS-10) ───────────────────────────────────────────

/** Um ano do cronograma. Alíquotas em fração (0.1120 = 11,2%); nulas = ainda não fixadas. */
export interface CronogramaReformaAno {
  ano: number
  aliqCbs: number | null
  aliqIbsUf: number | null
  aliqIbsMun: number | null
  /** IBS estadual + municipal */
  aliqIbs: number | null
  /** Fração do ISS cobrada no ano (1 até 2028, 0 em 2033) */
  fatorIss: number
  aliquotasDefinitivas: boolean
  fonte: string | null
  atualizadoPor: string | null
  atualizadoEm: string | null
}

export interface CronogramaReformaRequest {
  aliqCbs: number | null
  aliqIbsUf: number | null
  aliqIbsMun: number | null
  fatorIss: number
  aliquotasDefinitivas: boolean
  fonte: string | null
}

async function listarCronograma(): Promise<CronogramaReformaAno[]> {
  const res = await fetch('/api/fiscal/cronograma-reforma', { headers: authHeaders() })
  return handleResponse<CronogramaReformaAno[]>(res)
}

async function atualizarCronograma(ano: number, req: CronogramaReformaRequest): Promise<CronogramaReformaAno> {
  const res = await fetch(`/api/fiscal/cronograma-reforma/${ano}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  return handleResponse<CronogramaReformaAno>(res)
}

export const parametrosFiscaisApi = {
  listar, criar, criarIbsCbs, desligarIbsCbs, homologar, listarCronograma, atualizarCronograma,
}
