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
      msg = body.mensagem ?? body.message ?? msg
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  return res.json()
}

export interface NotaPortal {
  id: string
  producaoId: string
  competencia: string
  tomadorNome: string | null
  valorBrutoCentavos: number
  valorLiquidoMedicoCentavos: number
  taxaPinCentavos: number
  valorIss: number
  valorIr: number
  valorCsll: number
  valorPis: number
  valorCofins: number
  status: string
  numeroNota: string | null
  temXml: boolean
  temPdf: boolean
  protocolo: string | null
  emitidaAt: string | null
  createdAt: string
}

export interface PerfilMedico {
  id: string
  nome: string
  email: string
  crm: string
  crmUf: string
  especialidade: string | null
  status: string
}

export interface EmpresaPortal {
  id: string
  razaoSocial: string
  cnpj: string
  municipio: string | null
  inscricaoMunicipal: string | null
}

export interface ExtratoLancamento {
  tipo: 'CREDITO' | 'DEBITO'
  categoria: 'NFS_E' | 'ISS' | 'IR' | 'CSLL' | 'PIS' | 'COFINS' | 'TAXA_PIN'
  descricao: string
  valor: number       // centavos, sempre positivo
  saldoApos: number   // centavos
  competencia: string
  referencia: string
  dataRef: string     // ISO datetime
}

export interface ExtratoPortal {
  saldoPeriodo: number
  totalCreditos: number
  totalDebitos: number
  totalRetencoes: number
  totalTaxaPin: number
  lancamentos: ExtratoLancamento[]
}

export interface DashboardPortal {
  saldoDisponivelCentavos: number
  valorAReceberCentavos: number
  totalProduzidoCentavos: number
  totalNotasEmitidas: number
  totalProducoes: number
  ultimasNotas: NotaPortal[]
  ultimosRepasses: unknown[]
}

export interface TomadorPortal {
  id: string
  razaoSocial: string
  municipio: string | null
}

// modalidades: Modalidade(s) de referência configuradas no cadastro do setor (pedido do cliente:
// pode ter mais de uma) — usadas pra (1) derivar o Tipo de Escala da frequência automaticamente
// quando há só 1, ou oferecer a escolha quando há mais de 1, e (2), pra Diarista, usar a
// modalidade direto (só 1 opção) ou oferecer a escolha entre as opções Diarista do setor (2+).
// Lista vazia pra setores sem nenhuma modalidade configurada (legados).
export interface SetorModalidadeResumoPortal {
  id: string
  nome: string
  tipo: TipoEscala
}

export interface SetorOperacionalPortal {
  id: string
  nome: string
  categoria: string | null
  modalidades: SetorModalidadeResumoPortal[]
}

export interface ProducaoPortal {
  id: string
  competencia: string
  tomadorNome: string
  servicoDescricao: string
  valorBrutoCentavos: number
  valorLiquidoEstimadoCentavos: number
  status: string
  createdAt: string
}

async function getDashboard(): Promise<DashboardPortal> {
  const res = await fetch('/api/portal/dashboard', { headers: authHeaders() })
  return handleResponse<DashboardPortal>(res)
}

async function getNotas(params?: { competencia?: string; status?: string }): Promise<NotaPortal[]> {
  const search = new URLSearchParams()
  if (params?.competencia) search.set('competencia', params.competencia)
  if (params?.status) search.set('status', params.status)
  const qs = search.toString() ? `?${search}` : ''
  const res = await fetch(`/api/portal/notas${qs}`, { headers: authHeaders() })
  return handleResponse<NotaPortal[]>(res)
}

async function getProducao(params?: { competencia?: string }): Promise<ProducaoPortal[]> {
  const search = new URLSearchParams()
  if (params?.competencia) search.set('competencia', params.competencia)
  const qs = search.toString() ? `?${search}` : ''
  const res = await fetch(`/api/portal/producao${qs}`, { headers: authHeaders() })
  return handleResponse<ProducaoPortal[]>(res)
}

async function downloadXml(notaId: string): Promise<void> {
  const res = await fetch(`/api/portal/notas/${notaId}/xml`, { headers: authHeaders() })
  if (!res.ok) throw new Error(`Erro ao baixar XML: ${res.status}`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `nfse-${notaId}.xml`
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadPdf(notaId: string): Promise<void> {
  const res = await fetch(`/api/portal/notas/${notaId}/pdf`, { headers: authHeaders() })
  if (!res.ok) throw new Error(`Erro ao baixar PDF: ${res.status}`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `nfse-${notaId}.pdf`
  a.click()
  URL.revokeObjectURL(url)
}

async function getExtrato(params?: { dtInicio?: string; dtFim?: string }): Promise<ExtratoPortal> {
  const search = new URLSearchParams()
  if (params?.dtInicio) search.set('dtInicio', params.dtInicio)
  if (params?.dtFim)    search.set('dtFim',    params.dtFim)
  const qs = search.toString() ? `?${search}` : ''
  const res = await fetch(`/api/portal/extrato${qs}`, { headers: authHeaders() })
  return handleResponse<ExtratoPortal>(res)
}

async function getPerfil(): Promise<PerfilMedico> {
  const res = await fetch('/api/portal/perfil', { headers: authHeaders() })
  return handleResponse<PerfilMedico>(res)
}

async function getVinculosEmpresa(): Promise<EmpresaPortal[]> {
  const res = await fetch('/api/portal/vinculos-empresa', { headers: authHeaders() })
  return handleResponse<EmpresaPortal[]>(res)
}

async function getTomadoresAlocados(): Promise<TomadorPortal[]> {
  const res = await fetch('/api/portal/tomadores', { headers: authHeaders() })
  return handleResponse<TomadorPortal[]>(res)
}

// Setores Operacionais que o médico logado está autorizado a exercer neste tomador — só
// relevante quando o tomador tem `exigeFrequencia: true` (ver Tomador em tomadoresApi.ts).
async function getSetoresDoMedicoNoTomador(tomadorId: string): Promise<SetorOperacionalPortal[]> {
  const res = await fetch(`/api/portal/tomadores/${tomadorId}/setores`, { headers: authHeaders() })
  return handleResponse<SetorOperacionalPortal[]>(res)
}

export const portalApi = {
  getDashboard, getNotas, getProducao, downloadXml, downloadPdf,
  getExtrato, getPerfil, getVinculosEmpresa, getTomadoresAlocados, getSetoresDoMedicoNoTomador,
}
