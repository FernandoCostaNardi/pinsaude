// APU-00 — regras da apuração mensal (parametrizáveis, a homologar pela contabilidade) e simulador.
// Convenção do fiscal: alíquotas e percentuais em FRAÇÃO (0.08 = 8%), valores em CENTAVOS.

const STORAGE_KEY = 'pinsaude_tokens'

function getAccessToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

function authHeaders(): HeadersInit {
  return { Authorization: `Bearer ${getAccessToken()}`, 'Content-Type': 'application/json' }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let msg = `Erro ${res.status}`
    try {
      const b = await res.json()
      msg = b.mensagem ?? b.message ?? b.detail ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export type TributoApuracao = 'IRPJ' | 'CSLL' | 'PIS' | 'COFINS' | 'ISS' | 'CBS' | 'IBS'
export type AplicacaoRegime = 'LEGADO' | 'IBS_CBS' | 'AMBOS'
export type BaseCalculo = 'RECEITA_BRUTA' | 'BASE_PRESUMIDA' | 'SOMA_NOTAS'
export type RegimeReconhecimento = 'CAIXA' | 'COMPETENCIA'
export type Periodicidade = 'MENSAL' | 'TRIMESTRAL'
export type AjusteVencimento = 'ANTECIPA' | 'POSTERGA' | 'NENHUM'

export const TRIBUTOS_APURACAO: TributoApuracao[] = ['IRPJ', 'CSLL', 'PIS', 'COFINS', 'ISS', 'CBS', 'IBS']

export interface TributoRegra {
  tributo: TributoApuracao
  ativo: boolean
  aplicaEm: AplicacaoRegime
  aliquota: number | null
  baseCalculo: BaseCalculo
  regimeReconhecimento: RegimeReconhecimento
  periodicidade: Periodicidade
  deduzRetencao: boolean
  codigoReceita: string | null
  guia: string | null
  vencimentoDia: number | null
  vencimentoMesesApos: number | null
  vencimentoAjuste: AjusteVencimento | null
}

interface RegraBase {
  competenciaInicio: string
  presuncaoIrpjEquiparado: number
  presuncaoCsllEquiparado: number
  presuncaoIrpjPadrao: number
  presuncaoCsllPadrao: number
  aliqAdicionalIrpj: number
  limiteAdicionalIrpjMensal: number
  tetoReceitaAnual: number
  alertaTetoPercentual: number
  valorMinimoGuia: number
  fonte: string | null
  observacoes: string | null
  tributos: TributoRegra[]
}

export interface RegraApuracao extends RegraBase {
  id: string
  cnpjId: string | null
  global: boolean
  homologado: boolean
  homologadoPor: string | null
  homologadoEm: string | null
  atualizadoPor: string | null
  createdAt: string
  updatedAt: string
}

export interface RegraApuracaoRequest extends RegraBase {
  /** Empresa (só para quem não tem cnpj_id no token); vazio = padrão global */
  cnpj?: string | null
}

export interface MesSimulacao {
  competencia: string
  receitaCompetenciaEquiparada: number
  receitaCompetenciaPadrao: number
  receitaCaixaEquiparada: number
  receitaCaixaPadrao: number
  retencoes: Partial<Record<TributoApuracao, number>>
  cbsNotas: number
  ibsNotas: number
}

export interface SimulacaoRequest {
  competencia: string
  cnpj?: string | null
  regraId?: string | null
  regime?: 'LEGADO' | 'IBS_CBS' | null
  meses: MesSimulacao[]
}

export interface TributoApurado {
  tributo: TributoApuracao
  periodicidade: Periodicidade
  regimeReconhecimento: RegimeReconhecimento
  baseCalculo: BaseCalculo
  periodoInicio: string
  periodoFim: string
  fechaNesteMes: boolean
  receita: number
  base: number
  aliquota: number | null
  valorDevido: number
  adicional: number
  retencoes: number
  saldo: number
  saldoCredor: number
  abaixoMinimo: boolean
  valorARecolher: number
  vencimento: string | null
  codigoReceita: string | null
  guia: string | null
  memoria: string[]
}

export interface ResultadoApuracao {
  competencia: string
  cnpj: string | null
  regime: 'LEGADO' | 'IBS_CBS'
  regraId: string | null
  regraCompetenciaInicio: string
  regraGlobal: boolean
  homologado: boolean
  aHomologar: boolean
  tributos: TributoApurado[]
  totalDevido: number
  totalRetencoes: number
  totalARecolher: number
  avisos: string[]
}

const BASE = '/api/fiscal/regras-apuracao'

export const regrasApuracaoApi = {
  async listar(cnpj?: string): Promise<RegraApuracao[]> {
    const q = cnpj ? `?cnpj=${encodeURIComponent(cnpj)}` : ''
    return handleResponse(await fetch(`${BASE}${q}`, { headers: authHeaders() }))
  },
  async criar(req: RegraApuracaoRequest): Promise<RegraApuracao> {
    return handleResponse(await fetch(BASE, { method: 'POST', headers: authHeaders(), body: JSON.stringify(req) }))
  },
  async atualizar(id: string, req: RegraApuracaoRequest): Promise<RegraApuracao> {
    return handleResponse(await fetch(`${BASE}/${id}`, { method: 'PUT', headers: authHeaders(), body: JSON.stringify(req) }))
  },
  async homologar(id: string): Promise<RegraApuracao> {
    return handleResponse(await fetch(`${BASE}/${id}/homologar`, { method: 'PUT', headers: authHeaders() }))
  },
  async excluir(id: string): Promise<void> {
    return handleResponse(await fetch(`${BASE}/${id}`, { method: 'DELETE', headers: authHeaders() }))
  },
  async simular(req: SimulacaoRequest): Promise<ResultadoApuracao> {
    return handleResponse(await fetch(`${BASE}/simular`, { method: 'POST', headers: authHeaders(), body: JSON.stringify(req) }))
  },
}
