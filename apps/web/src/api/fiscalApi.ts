import {
  isTipoTributo, type RegimeTributario, type TipoTributo, type TributoComAliquotaCadastrada,
} from '../fiscal/tributos'

const STORAGE_KEY = 'pinsaude_tokens'

function getToken(): string {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) throw new Error('Não autenticado')
  return JSON.parse(raw).accessToken
}

function authHeaders() {
  return { Authorization: `Bearer ${getToken()}`, 'Content-Type': 'application/json' }
}

// ─── Alíquotas por Tributo (parametros_fiscais V2) ────────────────────────────

export interface ParametroFiscalV2 {
  id: string
  tipoTributo: TributoComAliquotaCadastrada
  competenciaInicio: string
  competenciaFim: string | null
  valorAliquota: number   // fração decimal: 0.0200
  valorAliquotaPct: number // percentual: 2.00
  descricao: string | null
  createdBy: string | null
  createdAt: string
}

export interface ParametroFiscalV2Request {
  tipoTributo: string
  competenciaInicio: string
  competenciaFim?: string | null
  valorAliquota: number  // fração decimal
  descricao?: string | null
}

export async function getParametrosFiscais(): Promise<ParametroFiscalV2[]> {
  const r = await fetch('/api/fiscal/parametros', { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao buscar alíquotas: ${r.status}`)
  return r.json()
}

export async function createParametroFiscal(req: ParametroFiscalV2Request): Promise<ParametroFiscalV2> {
  const r = await fetch('/api/fiscal/parametros', {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(req),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
  return r.json()
}

export async function verificarProximaCompetencia(): Promise<{ proximaCompetenciaConfigurada: boolean }> {
  const r = await fetch('/api/fiscal/parametros/verificar-proxima-competencia', { headers: authHeaders() })
  if (!r.ok) throw new Error('Erro ao verificar próxima competência')
  return r.json()
}

// ─── Regras de Equiparação ────────────────────────────────────────────────────

export interface HistoricoRegra {
  id: string
  campoAlterado: string
  valorAnterior: string | null
  valorNovo: string | null
  alteradoPor: string | null
  alteradoEm: string
}

export interface RegraEquiparacao {
  id: string
  cnae: string
  codigoLc116: string
  indicadorEquiparacao: boolean
  regimePresuncao: 'REDUZIDA' | 'CHEIA' | null
  createdAt: string
  historico: HistoricoRegra[]
}

export interface RegraEquiparacaoRequest {
  cnae: string
  codigoLc116: string
  indicadorEquiparacao: boolean
  regimePresuncao?: string | null
}

export async function getRegrasEquiparacao(): Promise<RegraEquiparacao[]> {
  const r = await fetch('/api/fiscal/regras-equiparacao', { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao buscar regras: ${r.status}`)
  return r.json()
}

export async function createRegraEquiparacao(req: RegraEquiparacaoRequest): Promise<RegraEquiparacao> {
  const r = await fetch('/api/fiscal/regras-equiparacao', {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(req),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
  return r.json()
}

export async function updateRegraEquiparacao(id: string, req: RegraEquiparacaoRequest): Promise<RegraEquiparacao> {
  const r = await fetch(`/api/fiscal/regras-equiparacao/${id}`, {
    method: 'PUT', headers: authHeaders(), body: JSON.stringify(req),
  })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    throw new Error(txt || `Erro ${r.status}`)
  }
  return r.json()
}

// ─── Motor Fiscal — cálculo por competência ───────────────────────────────────

export interface CalculoFiscalRequest {
  competencia: string
  valorBruto: number
  tomadorPj: boolean
  indicadorRetencaoFederal: boolean
  indicadorRetencaoIss: boolean
  equiparacaoHospitalar: boolean
  /** Alíquotas do tomador (chave = ISS|IR|CSLL|PIS|COFINS, valor = fração decimal 0.05 = 5%). Omitir para usar as da empresa. */
  aliquotasOverride?: Record<string, number>
  // Reforma tributária (CBS-13): classificação do serviço LC116 e acerto com o tomador.
  /** Fração: 0.6 = 60% */
  reducaoIbsCbs?: number | null
  cstIbsCbs?: string | null
  cClassTrib?: string | null
  /** true = CBS/IBS somados ao preço; false/omitido = dentro do bruto */
  cbsIbsPorFora?: boolean | null
}

export interface CalculoFiscalResult {
  valorBruto: number
  taxaPin: number
  valorIss: number
  valorIr: number
  valorCsll: number
  valorPis: number
  valorCofins: number
  totalRetencoes: number
  valorLiquidoMedico: number
  resultadoPin: number
  // Reforma tributária (CBS-13). Opcionais para não quebrar quem só lê o regime antigo.
  /** Soma de todos os tributos (ISS + federais + CBS + IBS) */
  totalTributos?: number
  regime?: RegimeTributario
  valorCbs?: number
  valorIbs?: number
  /** Frações efetivas, já com a redução do serviço */
  aliqCbs?: number
  aliqIbs?: number
  fatorIss?: number
  cstIbsCbs?: string | null
  cClassTrib?: string | null
  cbsIbsPorFora?: boolean
  /** Base de CBS/IBS (bruto sem CBS/IBS quando estão dentro do preço) */
  baseCalculoIbsCbs?: number
  /** Bruto + CBS + IBS quando por fora; senão igual ao bruto */
  valorTotalNota?: number
}

export async function calcularFiscal(req: CalculoFiscalRequest): Promise<CalculoFiscalResult> {
  const r = await fetch('/api/motor-fiscal/calcular', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
  })
  if (!r.ok) throw new Error(`Erro no cálculo fiscal: ${r.status}`)
  return r.json()
}

// ─── Serviços Cadastrados (faturamento service) ───────────────────────────────

export interface ServicoLc116 {
  id: string
  codigoLc116: string
  descricaoPadrao: string
  aliquotaIss: number
  aliquotaIr: number
  aliquotaCsll: number
  aliquotaPis: number
  aliquotaCofins: number
  /** Classificação CBS/IBS do serviço (CBS-12). Nulos = serviço ainda não classificado. */
  nbs?: string | null
  cstIbsCbs?: string | null
  cClassTrib?: string | null
  /** Fração: 0.6 = 60% de redução */
  reducaoIbsCbs?: number | null
}

export interface ServicoClassificacaoRequest {
  nbs: string
  cstIbsCbs: string
  cClassTrib: string
  /** Fração: 0.6 = 60% */
  reducaoIbsCbs: number
}

export async function atualizarClassificacaoServico(
  id: string,
  req: ServicoClassificacaoRequest,
): Promise<ServicoLc116> {
  const r = await fetch(`/api/servicos/${id}/classificacao-tributaria`, {
    method: 'PUT',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!r.ok) {
    let msg = `Erro ao salvar classificação: ${r.status}`
    try { const b = await r.json(); msg = b.mensagem ?? b.message ?? b.detail ?? msg } catch { /* ignore */ }
    throw new Error(msg)
  }
  return r.json()
}

export async function getServicos(): Promise<ServicoLc116[]> {
  const r = await fetch('/api/servicos', { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao buscar serviços: ${r.status}`)
  return r.json()
}

// ─── Regime por competência (CBS-15) ──────────────────────────────────────────

/** Alíquotas do ano no cronograma da reforma (frações: 0.1120 = 11,2%), sem a redução do serviço. */
export interface AliquotasAnoReforma {
  ano: number
  cbs: number | null
  ibsUf: number | null
  ibsMun: number | null
  /** IBS estadual + municipal */
  ibs: number | null
  fatorIss: number | null
  definitivas: boolean
}

export interface RegimeCompetencia {
  competencia: string
  regime: RegimeTributario
  /** Tributos do regime, na ordem de exibição */
  tributosVisiveis: TipoTributo[]
  chaveLigada: boolean
  homologado: boolean
  /** AAAA-MM em que o parâmetro vigente começou; null se a empresa não tem parâmetro */
  vigenciaParametro: string | null
  /** null no regime LEGADO */
  aliquotas: AliquotasAnoReforma | null
}

/**
 * GET /api/fiscal/regime — regime da empresa na competência, com a mesma regra do motor fiscal.
 * {@code cnpj} só é usado por quem não tem cnpj_id no token (gestão); para os demais vale o token.
 */
export async function getRegimeCompetencia(competencia: string, cnpj?: string | null): Promise<RegimeCompetencia> {
  const qs = new URLSearchParams({ competencia })
  if (cnpj) qs.set('cnpj', cnpj.replace(/\D/g, ''))
  const r = await fetch(`/api/fiscal/regime?${qs}`, { headers: authHeaders() })
  if (!r.ok) {
    const txt = await r.text().catch(() => '')
    let msg = txt
    try { msg = JSON.parse(txt).message || txt } catch { /* resposta não-JSON */ }
    throw new Error(msg || `Erro ao buscar o regime fiscal: ${r.status}`)
  }
  const body = await r.json() as RegimeCompetencia
  return { ...body, tributosVisiveis: body.tributosVisiveis.filter(isTipoTributo) }
}
