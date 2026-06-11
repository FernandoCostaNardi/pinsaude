const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

const BASE = '/api/empresas'

export type RegimePresuncao = 'REDUZIDA' | 'CHEIA'

export type StatusCertificado = 'VALIDO' | 'EXPIRANDO' | 'VENCIDO' | 'NAO_CONFIGURADO'

export interface AliquotaCompetencia {
  id: string
  competencia: string  // YYYY-MM
  iss: number
  ir: number
  csll: number
  pis: number
  cofins: number
  regimePresuncao: RegimePresuncao
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

export interface ConfiguracaoFiscal {
  empresaId: string
  cnaeCodigo: string | null
  cnaeDescricao: string | null
  codigoLc116: string | null
  indicadorEquiparacaoHospitalar: boolean
  vencimentoCertificadoA1: string | null   // ISO date YYYY-MM-DD
  statusCertificado: StatusCertificado
  updatedBy: string | null
  updatedAt: string | null
  historicoAliquotas: AliquotaCompetencia[]
}

export interface AliquotaRequest {
  competencia: string
  iss: number
  ir: number
  csll: number
  pis: number
  cofins: number
  regimePresuncao: RegimePresuncao
}

export interface ConfiguracaoFiscalRequest {
  cnaeCodigo: string | null
  cnaeDescricao: string | null
  codigoLc116: string | null
  indicadorEquiparacaoHospitalar: boolean
  vencimentoCertificadoA1: string | null
  aliquota: AliquotaRequest
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getAccessToken()}`,
      ...(init?.headers ?? {}),
    },
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(`Erro ${res.status}: ${body}`)
  }
  if (res.status === 204) return undefined as unknown as T
  return res.json() as Promise<T>
}

export const configuracaoFiscalApi = {
  buscar: (empresaId: string) =>
    request<ConfiguracaoFiscal>(`${BASE}/${empresaId}/configuracao-fiscal`),

  salvar: (empresaId: string, data: ConfiguracaoFiscalRequest) =>
    request<ConfiguracaoFiscal>(`${BASE}/${empresaId}/configuracao-fiscal`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
}
