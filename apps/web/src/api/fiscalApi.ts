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
  tipoTributo: 'ISS' | 'IR' | 'CSLL' | 'PIS' | 'COFINS'
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
}

export async function getServicos(): Promise<ServicoLc116[]> {
  const r = await fetch('/api/servicos', { headers: authHeaders() })
  if (!r.ok) throw new Error(`Erro ao buscar serviços: ${r.status}`)
  return r.json()
}
